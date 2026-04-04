package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Object 0x86 - Gumball Machine (Sonic 3 &amp; Knuckles Gumball bonus stage).
 * <p>
 * ROM reference: sonic3k.asm Obj_GumballMachine (line 127399).
 * <p>
 * The parent gumball machine object implements a 4-state state machine:
 * <ul>
 *   <li>IDLE: waiting for player to enter trigger range</li>
 *   <li>SPIN: playing the spin animation after activation</li>
 *   <li>TRIGGERED: signaling the dispenser to eject gumball items</li>
 *   <li>POST_TRIGGER: waiting for the player to exit range before returning to IDLE</li>
 * </ul>
 * <p>
 * On init, spawns 7 children: dispenser, ball container display, exit trigger,
 * 3 upper platforms, and 1 extra platform.
 * <p>
 * ROM attributes (ObjDat3 for GumballMachine):
 * <ul>
 *   <li>Mappings: Map_GumballBonus</li>
 *   <li>Art tile: make_art_tile(ArtTile_BonusStage, 1, 1) = palette 1, high priority</li>
 *   <li>Priority: $0200</li>
 * </ul>
 */
public class GumballMachineObjectInstance extends AbstractObjectInstance {

    private static final Logger LOGGER = Logger.getLogger(GumballMachineObjectInstance.class.getName());

    // ===== State machine =====

    private enum State {
        IDLE,
        SPIN,
        TRIGGERED,
        POST_TRIGGER
    }

    // ===== ROM constants =====

    // ROM: Player proximity check for activation (Obj_GumballMachine IDLE state)
    // X range: -36 to +72, Y range: -8 to +16
    private static final int ACTIVATE_X_MIN = -36;
    private static final int ACTIVATE_X_MAX = 72;
    private static final int ACTIVATE_Y_MIN = -8;
    private static final int ACTIVATE_Y_MAX = 16;

    // ROM: priority $0200 -> bucket 2
    private static final int PRIORITY_BUCKET = 2;

    // ROM: Spin animation frame sequence [3,5,6,7,$14,5,$F4,$7F,5,5,$FC]
    // $F4 = -12 (signed byte) -> frame wait command
    // $7F = end-of-frame-wait marker
    // $FC = loop back / end
    // Simplified: display frames 3,5,6,7,0x14,5 then 5,5 with delays
    private static final int[] SPIN_FRAMES = {3, 5, 6, 7, 0x14, 5, 5, 5};
    private static final int SPIN_FRAME_DURATION = 3; // frames per animation frame
    private static final int SPIN_TOTAL_FRAMES = SPIN_FRAMES.length * SPIN_FRAME_DURATION;

    // ROM: Mapping frame for idle state (machine body)
    private static final int IDLE_MAPPING_FRAME = 0;

    // ===== Child offsets from parent position =====

    // Dispenser: at parent position
    private static final int DISPENSER_OFFSET_X = 0;
    private static final int DISPENSER_OFFSET_Y = 0;

    // Ball container display: (0, +0x24)
    private static final int CONTAINER_OFFSET_X = 0;
    private static final int CONTAINER_OFFSET_Y = 0x24;

    // Exit trigger: (0, +0x2A0)
    private static final int EXIT_TRIGGER_OFFSET_X = 0;
    private static final int EXIT_TRIGGER_OFFSET_Y = 0x2A0;

    // Platform left: (-0x38, -0x2C)
    private static final int PLATFORM_LEFT_OFFSET_X = -0x38;
    private static final int PLATFORM_LEFT_OFFSET_Y = -0x2C;

    // Platform center: (0, -0x2C)
    private static final int PLATFORM_CENTER_OFFSET_X = 0;
    private static final int PLATFORM_CENTER_OFFSET_Y = -0x2C;

    // Platform right: (+0x38, -0x2C)
    private static final int PLATFORM_RIGHT_OFFSET_X = 0x38;
    private static final int PLATFORM_RIGHT_OFFSET_Y = -0x2C;

    // Platform extra: (0, -0x28)
    private static final int PLATFORM_EXTRA_OFFSET_X = 0;
    private static final int PLATFORM_EXTRA_OFFSET_Y = -0x28;

    // ===== Gumball ejection constants =====

    // ROM: Gumball items ejected per trigger (1-3 random)
    private static final int MIN_GUMBALLS = 1;
    private static final int MAX_GUMBALLS = 3;

    // ROM: Gumball initial Y velocity range (negative = upward)
    private static final int GUMBALL_Y_VEL_MIN = -0x400;
    private static final int GUMBALL_Y_VEL_MAX = -0x200;

    // ROM: Gumball X position spread from dispenser
    private static final int GUMBALL_X_SPREAD = 0x10;

    // ===== Instance state =====

    private State state = State.IDLE;
    private int spinTimer;
    private int currentFrame = IDLE_MAPPING_FRAME;
    private final Random rng;
    private DispenserChild dispenser;

    public GumballMachineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "GumballMachine");

        // ROM: Seed RNG from frame counter
        this.rng = new Random(System.nanoTime());

        // Spawn 7 children
        spawnChildren();
    }

    private void spawnChildren() {
        int px = spawn.x();
        int py = spawn.y();

        // 1. Dispenser (at parent position)
        dispenser = spawnChild(() -> new DispenserChild(
                buildSpawnAt(px + DISPENSER_OFFSET_X, py + DISPENSER_OFFSET_Y)));

        // 2. Ball container display
        spawnChild(() -> new ContainerDisplayChild(
                buildSpawnAt(px + CONTAINER_OFFSET_X, py + CONTAINER_OFFSET_Y)));

        // 3. Exit trigger
        spawnChild(() -> new ExitTriggerChild(
                buildSpawnAt(px + EXIT_TRIGGER_OFFSET_X, py + EXIT_TRIGGER_OFFSET_Y)));

        // 4. Platform left
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_LEFT_OFFSET_X, py + PLATFORM_LEFT_OFFSET_Y),
                "GumballPlatformLeft"));

        // 5. Platform center
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_CENTER_OFFSET_X, py + PLATFORM_CENTER_OFFSET_Y),
                "GumballPlatformCenter"));

        // 6. Platform right
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_RIGHT_OFFSET_X, py + PLATFORM_RIGHT_OFFSET_Y),
                "GumballPlatformRight"));

        // 7. Platform extra
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_EXTRA_OFFSET_X, py + PLATFORM_EXTRA_OFFSET_Y),
                "GumballPlatformExtra"));
    }

    // ===== State machine =====

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        switch (state) {
            case IDLE -> updateIdle(playerEntity);
            case SPIN -> updateSpin();
            case TRIGGERED -> updateTriggered();
            case POST_TRIGGER -> updatePostTrigger(playerEntity);
        }
    }

    /**
     * IDLE state: Check if player is within activation range.
     * ROM: checks player centre vs object position with asymmetric box.
     */
    private void updateIdle(PlayableEntity playerEntity) {
        if (playerEntity == null) {
            return;
        }

        int playerX = playerEntity.getCentreX();
        int playerY = playerEntity.getCentreY();
        int dx = playerX - spawn.x();
        int dy = playerY - spawn.y();

        if (dx >= ACTIVATE_X_MIN && dx <= ACTIVATE_X_MAX
                && dy >= ACTIVATE_Y_MIN && dy <= ACTIVATE_Y_MAX) {
            // ROM: play sfx_GumballTab, determine flip, transition to SPIN
            try {
                services().playSfx(Sonic3kSfx.GUMBALL_TAB.id);
            } catch (Exception e) {
                // Prevent audio failure from breaking game logic
            }

            spinTimer = 0;
            state = State.SPIN;
            LOGGER.fine("GumballMachine: IDLE -> SPIN (player in range)");
        }
    }

    /**
     * SPIN state: Animate the spin sequence.
     * ROM: frames [3,5,6,7,$14,5,$F4,$7F,5,5,$FC] with per-frame timing.
     */
    private void updateSpin() {
        int frameIndex = spinTimer / SPIN_FRAME_DURATION;
        if (frameIndex < SPIN_FRAMES.length) {
            currentFrame = SPIN_FRAMES[frameIndex];
        }
        spinTimer++;

        if (spinTimer >= SPIN_TOTAL_FRAMES) {
            state = State.TRIGGERED;
            LOGGER.fine("GumballMachine: SPIN -> TRIGGERED (animation complete)");
        }
    }

    /**
     * TRIGGERED state: Signal dispenser to eject gumballs, then transition.
     */
    private void updateTriggered() {
        // Spawn gumball items from dispenser
        ejectGumballs();

        currentFrame = IDLE_MAPPING_FRAME;
        state = State.POST_TRIGGER;
        LOGGER.fine("GumballMachine: TRIGGERED -> POST_TRIGGER");
    }

    /**
     * POST_TRIGGER state: Wait for player to exit activation range, then return to IDLE.
     */
    private void updatePostTrigger(PlayableEntity playerEntity) {
        if (playerEntity == null) {
            state = State.IDLE;
            return;
        }

        int playerX = playerEntity.getCentreX();
        int playerY = playerEntity.getCentreY();
        int dx = playerX - spawn.x();
        int dy = playerY - spawn.y();

        // Wait for player to leave the activation range
        if (dx < ACTIVATE_X_MIN || dx > ACTIVATE_X_MAX
                || dy < ACTIVATE_Y_MIN || dy > ACTIVATE_Y_MAX) {
            state = State.IDLE;
            LOGGER.fine("GumballMachine: POST_TRIGGER -> IDLE (player exited range)");
        }
    }

    // ===== Gumball ejection =====

    /**
     * Ejects 1-3 random gumball items from the dispenser position.
     * Each gumball gets a random upward Y velocity and slight X offset for spread.
     */
    private void ejectGumballs() {
        int count = MIN_GUMBALLS + rng.nextInt(MAX_GUMBALLS - MIN_GUMBALLS + 1);
        int dispenserX = spawn.x() + DISPENSER_OFFSET_X;
        int dispenserY = spawn.y() + DISPENSER_OFFSET_Y;

        for (int i = 0; i < count; i++) {
            // Random Y velocity between -0x400 and -0x200 (upward)
            int yVel = GUMBALL_Y_VEL_MIN + rng.nextInt(GUMBALL_Y_VEL_MAX - GUMBALL_Y_VEL_MIN);

            // Random X offset for spread
            int xOffset = rng.nextInt(GUMBALL_X_SPREAD * 2 + 1) - GUMBALL_X_SPREAD;

            int gx = dispenserX + xOffset;
            int gy = dispenserY;

            // ROM: subtype for gumball items (1-4 random for visual variety)
            int subtype = 1 + rng.nextInt(4);

            ObjectSpawn gumballSpawn = new ObjectSpawn(gx, gy, 0xEB, subtype, 0, false, 0);
            spawnChild(() -> new GumballItemObjectInstance(gumballSpawn, yVel, true));
        }

        LOGGER.fine("GumballMachine: ejected " + count + " gumballs");
    }

    // ===== Rendering =====

    @Override
    public boolean isPersistent() {
        // The gumball machine is the central object of the bonus stage; keep it active
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
        if (renderer == null) {
            return;
        }

        renderer.drawFrameIndex(currentFrame, spawn.x(), spawn.y(), false, false);
    }

    // =====================================================================
    // Inner child classes
    // =====================================================================

    /**
     * Dispenser child — solid platform at the machine's dispenser position.
     * <p>
     * ROM: When the parent sets the trigger flag, spawns gumball items.
     * In this implementation, ejection is handled directly by the parent's
     * TRIGGERED state, so this child only provides visual/solid presence.
     */
    static class DispenserChild extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        // ROM: SolidObject params for dispenser — approximate from sprite dimensions
        private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(24, 16, 16);
        private static final int MAPPING_FRAME = 1; // Dispenser visual frame

        DispenserChild(ObjectSpawn spawn) {
            super(spawn, "GumballDispenser");
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            // Static presence; ejection handled by parent
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return SOLID_PARAMS;
        }

        @Override
        public boolean isTopSolidOnly() {
            return true;
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            // No special contact behavior
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY_BUCKET);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), false, false);
        }
    }

    /**
     * Ball container display child — visual animation of the gumball container.
     * <p>
     * ROM: Uses Map_GumballBonus with animation frames for the ball container.
     * Positioned at (0, +0x24) from parent.
     */
    static class ContainerDisplayChild extends AbstractObjectInstance {

        private static final int MAPPING_FRAME = 2; // Container visual frame

        ContainerDisplayChild(ObjectSpawn spawn) {
            super(spawn, "GumballContainer");
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            // Static visual; animation can be refined later
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY_BUCKET);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), false, false);
        }
    }

    /**
     * Exit trigger child — detects player in range and signals bonus stage completion.
     * <p>
     * ROM: Positioned at (0, +0x2A0) from parent. Detects player within
     * (-0x100/+0x200 X, -0x10/+0x40 Y) and calls requestExit() on the
     * bonus stage provider.
     * <p>
     * <b>CRITICAL:</b> This is how the bonus stage ends. Without it, the player
     * is stuck in the gumball stage permanently.
     */
    static class ExitTriggerChild extends AbstractObjectInstance {

        // ROM: Exit trigger detection range
        private static final int EXIT_X_MIN = -0x100;
        private static final int EXIT_X_MAX = 0x200;
        private static final int EXIT_Y_MIN = -0x10;
        private static final int EXIT_Y_MAX = 0x40;

        ExitTriggerChild(ObjectSpawn spawn) {
            super(spawn, "GumballExitTrigger");
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (playerEntity == null) {
                return;
            }

            int playerX = playerEntity.getCentreX();
            int playerY = playerEntity.getCentreY();
            int dx = playerX - spawn.x();
            int dy = playerY - spawn.y();

            if (dx >= EXIT_X_MIN && dx <= EXIT_X_MAX
                    && dy >= EXIT_Y_MIN && dy <= EXIT_Y_MAX) {
                LOGGER.info("GumballExitTrigger: player in exit range, requesting bonus stage exit");
                try {
                    services().requestBonusStageExit();
                } catch (Exception e) {
                    LOGGER.warning("GumballExitTrigger: failed to request exit: " + e.getMessage());
                }
            }
        }

        @Override
        public int getPriorityBucket() {
            // Non-visible trigger; use low priority
            return RenderPriority.clamp(0);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Invisible trigger — no rendering
        }
    }

    /**
     * Platform child — solid platform for the player to stand on.
     * <p>
     * ROM: Uses SolidObject with standard platform dimensions.
     * Top-solid only so the player can jump up through them.
     */
    static class PlatformChild extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        // ROM: Platform solid params — approximate from gumball stage layout
        private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(32, 8, 8);
        private static final int MAPPING_FRAME = 4; // Platform visual frame

        PlatformChild(ObjectSpawn spawn, String name) {
            super(spawn, name);
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            // Static platform — no per-frame logic
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return SOLID_PARAMS;
        }

        @Override
        public boolean isTopSolidOnly() {
            return true;
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            // No special contact behavior
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY_BUCKET);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), false, false);
        }
    }
}
