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

import java.util.ArrayList;
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

    // Machine and most children render at bucket 1 (behind Sonic at bucket 2).
    // ROM VDP priority is 2-tier; our bucket system uses spawn order within
    // a bucket, so placing the machine at bucket 1 keeps Sonic always visible.
    private static final int PRIORITY_BUCKET = 1;

    // ROM: byte_61450 = [3, 5, 6, 7, $14, 5, $FF]
    // First byte (3) is the per-frame timer, NOT a mapping frame.
    // Actual animation frames: 5, 6, 7, $14, 5.
    // ROM timer stores (value) then decrements via bpl check (runs value+1 frames).
    private static final int[] SPIN_FRAMES = {5, 6, 7, 0x14, 5};
    private static final int SPIN_FRAME_DURATION = 4; // ROM timer=3 + 1 for bpl check
    private static final int SPIN_TOTAL_FRAMES = SPIN_FRAMES.length * SPIN_FRAME_DURATION;

    // ROM: ObjDat_GumballMachine byte 2 = 5 — default mapping frame (machine body)
    private static final int IDLE_MAPPING_FRAME = 5;

    // ROM: Obj_GumballMachine init subtracts $100 from y_pos at spawn.
    // The visible machine and all children sit 256 pixels above the placement position.
    private static final int MACHINE_Y_OFFSET = -0x100;

    // ===== Child offsets from parent position =====

    // Dispenser: ABSOLUTE world position (ROM loc_60D58 hardcodes to $100, $310).
    // The dispenser is at the BOTTOM of the stage, far below the machine body.
    private static final int DISPENSER_ABSOLUTE_X = 0x100;
    private static final int DISPENSER_ABSOLUTE_Y = 0x310;

    // Kept for legacy reference but no longer used for dispenser positioning.
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

    // Springs: 4 vertical red springs at the bottom of the stage.
    // ROM: ChildObjDat_61424 spawns 4 springs as children of the dispenser (loc_60D58)
    // at offsets (-$30, -$18), (-$10, -$18), ($10, -$18), ($30, -$18).
    // The dispenser is at absolute (0x100, 0x310), so springs are at absolute (0xD0-0x130, 0x2F8).
    // Y offset from machine-adjusted Y: dispenser_y - machine_y - $18 = 0 - 0x18 = -$18
    private static final int[] SPRING_X_OFFSETS = { -0x30, -0x10, 0x10, 0x30 };
    private static final int SPRING_Y_OFFSET = -0x18;

    // ===== Gumball ejection constants =====

    // ROM: Gumball items ejected per trigger (1-3 random)
    private static final int MIN_GUMBALLS = 1;
    private static final int MAX_GUMBALLS = 3;

    // ROM: Balls spawn with y_vel=0 (default) and fall via gravity (+$10/frame)
    private static final int GUMBALL_INITIAL_Y_VEL = 0;

    // ROM: Gumball X position spread from dispenser
    private static final int GUMBALL_X_SPREAD = 0x10;

    // ROM: byte_6145B container animation frames: [2,3,3,3,4,F,3,3,2,3,F4]
    // Total ~40 frames of spin animation before machine can re-trigger.
    // This models the container animation ownership of bit 3 on the machine
    // (loc_60E5C → loc_60E8C → loc_60EA2) which is cleared with bit 1 at
    // animation completion, preventing re-trigger while active.
    private static final int TRIGGER_COOLDOWN_FRAMES = 40;

    // ===== Machine Y drift / slot tracking =====
    //
    // ROM: 28 bytes at $FF2000..$FF201B, 14 word-sized "slots" (pairs of bumpers).
    // Initialized to 0xFF at machine spawn (ROM 127413-127419).
    // Tested by sub_6126C as words: word is "occupied" when non-zero.
    // Individual bumpers clear their byte when bumped (ROM 127692-127695).
    private static final int SLOT_COUNT_BYTES = 28;
    private static final int SLOT_WORD_COUNT = 14;
    private static final int DRIFT_PER_EMPTY_SLOT = 0x20;
    private static final int DRIFT_STEP_PER_FRAME = 4;

    private final byte[] slotRam = new byte[SLOT_COUNT_BYTES];

    // ROM $3A: original Y (saved at init, before any drift).
    private int savedY;

    // ROM $3C: current drift target (savedY + emptySlotPrefix*0x20).
    private int targetY;

    // ROM y_pos: current Y, updated +4/frame until >= targetY.
    private int currentY;

    // ROM bit 0 of $38: set by bumpers when they clear a slot; machine recounts.
    private boolean slotRecalcNeeded;

    private boolean driftInitialized;

    // ===== Instance state =====

    private State state = State.IDLE;
    private int spinTimer;
    private int currentFrame = IDLE_MAPPING_FRAME;
    private final Random rng;
    private DispenserChild dispenser;

    // Trigger cooldown prevents rapid re-triggering. ROM clears bits 1+3 on
    // machine when container animation finishes (loc_60EA2).
    private int triggerCooldownFrames;

    // Tracks active gumball springs for respawn on REP gumball collect.
    private final List<GumballSpringChild> springs = new ArrayList<>();
    private final List<int[]> springOriginalPositions = new ArrayList<>();

    private boolean childrenSpawned;

    // Single-instance reference for cross-object coordination (REP gumball
    // needs to call back into the machine to respawn springs). Only one
    // gumball bonus stage is active at a time.
    private static GumballMachineObjectInstance currentInstance;

    /**
     * @return the current active gumball machine, or null if none
     */
    public static GumballMachineObjectInstance current() {
        return currentInstance;
    }

    public GumballMachineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "GumballMachine");

        // ROM: Seed RNG from frame counter
        this.rng = new Random(System.nanoTime());

        // Register as the active machine so gumball items can trigger callbacks
        currentInstance = this;
    }

    private void spawnChildren() {
        int px = spawn.x();
        int py = spawn.y() + MACHINE_Y_OFFSET;

        // 1. Dispenser — ABSOLUTE (0x100, 0x310) per ROM loc_60D58.
        // Independent of machine position; sits at the bottom of the stage.
        dispenser = spawnChild(() -> new DispenserChild(
                buildSpawnAt(DISPENSER_ABSOLUTE_X, DISPENSER_ABSOLUTE_Y)));

        // 2. Ball container display — follows machine (y+0x24 via Refresh_ChildPosition)
        spawnChild(() -> new ContainerDisplayChild(
                buildSpawnAt(px + CONTAINER_OFFSET_X, py + CONTAINER_OFFSET_Y),
                CONTAINER_OFFSET_Y));

        // 3. Exit trigger — relative to machine, +0x2A0 Y
        spawnChild(() -> new ExitTriggerChild(
                buildSpawnAt(px + EXIT_TRIGGER_OFFSET_X, py + EXIT_TRIGGER_OFFSET_Y)));

        // 4-7. Platforms — follow machine via Refresh_ChildPosition
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_LEFT_OFFSET_X, py + PLATFORM_LEFT_OFFSET_Y),
                "GumballPlatformLeft", PLATFORM_LEFT_OFFSET_Y));
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_CENTER_OFFSET_X, py + PLATFORM_CENTER_OFFSET_Y),
                "GumballPlatformCenter", PLATFORM_CENTER_OFFSET_Y));
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_RIGHT_OFFSET_X, py + PLATFORM_RIGHT_OFFSET_Y),
                "GumballPlatformRight", PLATFORM_RIGHT_OFFSET_Y));
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_EXTRA_OFFSET_X, py + PLATFORM_EXTRA_OFFSET_Y),
                "GumballPlatformExtra", PLATFORM_EXTRA_OFFSET_Y));

        // 4 springs at bottom of stage (ROM: ChildObjDat_61424 as children of dispenser).
        // Absolute positions: dispenser (0x100, 0x310) + offsets (-$30..$30, -$18).
        // Springs crumble on first use and are respawned by REP gumball.
        springs.clear();
        springOriginalPositions.clear();
        for (int springX : SPRING_X_OFFSETS) {
            final int sx = DISPENSER_ABSOLUTE_X + springX;
            final int sy = DISPENSER_ABSOLUTE_Y + SPRING_Y_OFFSET;
            springOriginalPositions.add(new int[]{sx, sy});
            GumballSpringChild spring = spawnChild(() -> new GumballSpringChild(
                    buildSpawnAt(sx, sy), this));
            springs.add(spring);
        }
    }

    // ===== State machine =====

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // Initialize drift state on first update (separated from child spawning
        // so tests can exercise drift logic without requiring services()).
        if (!driftInitialized) {
            initDrift();
        }

        // Spawn children on first update — can't do it in constructor because
        // services() isn't available until ObjectManager injects them.
        if (!childrenSpawned) {
            childrenSpawned = true;
            // Ensure we're the current machine (constructor ran before any other
            // machine may have loaded).
            currentInstance = this;
            spawnChildren();
        }

        // ROM sub_6126C: recount empty slots and apply drift each frame.
        applyDrift();

        // Tick trigger cooldown regardless of state (ROM: container animation
        // owns the re-trigger gate).
        if (triggerCooldownFrames > 0) {
            triggerCooldownFrames--;
        }

        switch (state) {
            case IDLE -> updateIdle(playerEntity);
            case SPIN -> updateSpin();
            case TRIGGERED -> updateTriggered();
            case POST_TRIGGER -> updatePostTrigger(playerEntity);
        }
    }

    /**
     * ROM Obj_GumballMachine init (line 127399).
     * <ul>
     *   <li>line 127405: move.w y_pos(a0),$3A(a0) — save initial Y after -$100 offset.</li>
     *   <li>lines 127413-127419: init slot RAM ($FF2000..$FF201B) to 0xFF.</li>
     * </ul>
     * Package-private so unit tests can drive drift logic without requiring the
     * child-spawn path (which needs injected ObjectServices).
     */
    void initDrift() {
        driftInitialized = true;
        savedY = spawn.y() + MACHINE_Y_OFFSET;
        currentY = savedY;
        targetY = savedY;
        java.util.Arrays.fill(slotRam, (byte) 0xFF);
        slotRecalcNeeded = false;
    }

    /** ROM sub_6126C (line 127949). Recount empty-slot prefix, apply drift. */
    void applyDrift() {
        if (slotRecalcNeeded) {
            slotRecalcNeeded = false;
            int accum = 0;
            for (int i = 0; i < SLOT_WORD_COUNT; i++) {
                int lo = slotRam[i * 2] & 0xFF;
                int hi = slotRam[i * 2 + 1] & 0xFF;
                int word = (hi << 8) | lo;
                if (word != 0) {
                    break; // ROM: tst.w / bne.s loc_6128A
                }
                accum += DRIFT_PER_EMPTY_SLOT;
            }
            targetY = savedY + accum;
        }
        if (currentY < targetY) {
            currentY += DRIFT_STEP_PER_FRAME;
        }
    }

    /** Called by GumballBumperObjectInstance on player bump. ROM lines 127692-127698. */
    public void onBumperHit(int subtype) {
        if (subtype < 0 || subtype >= SLOT_COUNT_BYTES) {
            return;
        }
        slotRam[subtype] = 0;
        slotRecalcNeeded = true;
    }

    /** Returns the machine's current (drifted) Y position. */
    public int getCurrentY() {
        return currentY;
    }

    /** Returns the machine's saved (initial) Y position. Package-private for tests. */
    int getSavedY() {
        return savedY;
    }

    /** Returns the current drift target Y. Package-private for tests. */
    int getTargetY() {
        return targetY;
    }

    /**
     * IDLE state: Check if player is within activation range.
     * ROM: checks player centre vs object position with asymmetric box.
     */
    private void updateIdle(PlayableEntity playerEntity) {
        if (playerEntity == null) {
            return;
        }

        // ROM: bit 1 on machine gates re-trigger. Container animation clears
        // bits 1+3 at loc_60EA2 when byte_6145B sequence completes. Until then,
        // the machine is locked out of re-spinning.
        if (triggerCooldownFrames > 0) {
            return;
        }

        int playerX = playerEntity.getCentreX();
        int playerY = playerEntity.getCentreY();
        int dx = playerX - spawn.x();
        int dy = playerY - currentY;

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
        int dy = playerY - (spawn.y() + MACHINE_Y_OFFSET);

        // Wait for player to leave the activation range
        if (dx < ACTIVATE_X_MIN || dx > ACTIVATE_X_MAX
                || dy < ACTIVATE_Y_MIN || dy > ACTIVATE_Y_MAX) {
            state = State.IDLE;
            LOGGER.fine("GumballMachine: POST_TRIGGER -> IDLE (player exited range)");
        }
    }

    // ===== Gumball ejection =====

    /**
     * ROM byte_612E0 — random index (0-15) to subtype mapping.
     * Applied by sub_612A8 to give gumball items a subtype distribution.
     */
    private static final int[] SUBTYPE_LOOKUP = {
            0, 3, 1, 4, 2, 4, 5, 4, 6, 3, 7, 4, 5, 6, 7, 2
    };

    /**
     * ROM: loc_60E8C — each machine trigger spawns EXACTLY 1 ball from
     * ChildObjDat_61444 at the container display position. The ball's
     * initial y_vel is 0 (set by ObjDat3_613E0 defaults), gravity +0x10/frame
     * pulls it down through the dispenser.
     */
    private void ejectGumballs() {
        // ROM: balls spawn at container display position (container = machine + $24 Y)
        int ejectX = spawn.x() + CONTAINER_OFFSET_X;
        int ejectY = spawn.y() + MACHINE_Y_OFFSET + CONTAINER_OFFSET_Y;

        // ROM sub_612A8: random 0-15 indexes byte_612E0 to choose subtype (0-7).
        int randomIndex = rng.nextInt(16);
        int subtype = SUBTYPE_LOOKUP[randomIndex];

        ObjectSpawn gumballSpawn = new ObjectSpawn(ejectX, ejectY, 0xEB, subtype, 0, false, 0);
        spawnChild(() -> new GumballItemObjectInstance(gumballSpawn, GUMBALL_INITIAL_Y_VEL, true));

        // ROM: container animation runs ~40 frames (byte_6145B) during which
        // bits 1+3 are set on the machine, blocking re-trigger.
        triggerCooldownFrames = TRIGGER_COOLDOWN_FRAMES;

        LOGGER.fine("GumballMachine: ejected 1 gumball, subtype=" + subtype
                + ", cooldown=" + triggerCooldownFrames);
    }

    /**
     * Respawns any destroyed gumball springs at their original positions.
     * Called when the REP (subtype 1) gumball is collected.
     * <p>
     * ROM: loc_61130 respawns the dispenser, which respawns its 4 spring
     * children via ChildObjDat_61424. We replicate that by iterating over
     * our tracked springs and re-spawning any that have been destroyed.
     */
    public void respawnSprings() {
        if (springs.size() != springOriginalPositions.size()) {
            LOGGER.warning("GumballMachine: spring tracking mismatch ("
                    + springs.size() + " vs " + springOriginalPositions.size() + ")");
            return;
        }

        int respawned = 0;
        for (int i = 0; i < springs.size(); i++) {
            GumballSpringChild existing = springs.get(i);
            if (existing == null || existing.isDestroyed()) {
                int[] pos = springOriginalPositions.get(i);
                final int sx = pos[0];
                final int sy = pos[1];
                GumballSpringChild replacement = spawnChild(() ->
                        new GumballSpringChild(buildSpawnAt(sx, sy), this));
                springs.set(i, replacement);
                respawned++;
            }
        }
        LOGGER.fine("GumballMachine: respawned " + respawned + " springs");
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

        // Render at currentY so the machine visually slides as bumpers clear.
        int renderY = driftInitialized ? currentY : (spawn.y() + MACHINE_Y_OFFSET);
        renderer.drawFrameIndex(currentFrame, spawn.x(), renderY, false, false);
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
        // ROM sub_61314: d1=$4B (halfWidth=75), d2=$10 (airHalfHeight=16), d3=$11 (groundHalfHeight=17)
        private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(75, 16, 17);
        private static final int MAPPING_FRAME = 0x13; // ROM ObjDat3_61398 byte 2

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
            // ROM sub_61314 calls SolidObjectFull — 4-sided collision.
            return false;
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

        /** Y offset from the machine's savedY (machine-relative). */
        private final int offsetFromMachine;

        ContainerDisplayChild(ObjectSpawn spawn, int offsetFromMachine) {
            super(spawn, "GumballContainer");
            this.offsetFromMachine = offsetFromMachine;
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
            // Track the machine's drifted Y position so the container slides in sync.
            GumballMachineObjectInstance machine = GumballMachineObjectInstance.current();
            int renderY = (machine != null)
                    ? machine.getCurrentY() + offsetFromMachine
                    : spawn.y();
            renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), renderY, false, false);
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
     * Platform child — visual-only decoration.
     * <p>
     * ROM loc_61012 only does Refresh_ChildPosition + Draw_Sprite.
     * NO solid collision — the platforms are decorative, not standable.
     */
    static class PlatformChild extends AbstractObjectInstance {

        // ROM ObjDat3_6138C byte 2 = 0 — platform uses mapping frame 0
        private static final int MAPPING_FRAME = 0;

        /** Y offset from the machine's savedY (machine-relative). */
        private final int offsetFromMachine;

        PlatformChild(ObjectSpawn spawn, String name, int offsetFromMachine) {
            super(spawn, name);
            this.offsetFromMachine = offsetFromMachine;
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
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY_BUCKET);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
            if (renderer == null) {
                return;
            }
            // Track the machine's drifted Y position so platforms slide in sync.
            GumballMachineObjectInstance machine = GumballMachineObjectInstance.current();
            int renderY = (machine != null)
                    ? machine.getCurrentY() + offsetFromMachine
                    : spawn.y();
            // ROM: ObjDat3_6138C uses make_art_tile(ArtTile_BonusStage, 0, 0) — palette 0
            renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), renderY, false, false, 0);
        }
    }

    /**
     * Gumball bonus stage crumbling spring.
     * <p>
     * ROM: loc_60DAC (dispenser's spring children). Uses SolidObjectFull2_1P
     * for solid-from-above collision. When player stands on it:
     * <ul>
     *   <li>Sets bit 5 on spring and plays bounce animation via sub_22F98</li>
     *   <li>Animation plays until prev_anim == 1 (end of bounce)</li>
     *   <li>Spring deletes self, sets bit 1 on parent (dispenser)</li>
     *   <li>Parent sees bit 1 → deletes itself (chained cleanup)</li>
     * </ul>
     * REP gumball (subtype 1, loc_61130) respawns the dispenser, which
     * respawns all 4 springs. In our implementation, the machine owns the
     * spring references and respawns them directly.
     * <p>
     * Solid params from ROM sub_22F98: halfWidth=$1B (27), airHalfHeight=8,
     * groundHalfHeight=$10 (16).
     */
    static class GumballSpringChild extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        // ROM: Obj_Spring params — halfWidth=$1B, airHalfHeight=8, groundHalfHeight=$10
        private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(27, 8, 16);

        // ROM: red spring uses strength -$1000
        private static final int BOUNCE_STRENGTH = -0x1000;

        // ROM: short bounce animation before crumbling
        private static final int CRUMBLE_DELAY_FRAMES = 8;

        // ROM: Map_Spring frame for red vertical spring (frame 0)
        private static final int MAPPING_FRAME = 0;

        private final GumballMachineObjectInstance parent;
        private boolean triggered;
        private int crumbleTimer;

        GumballSpringChild(ObjectSpawn spawn, GumballMachineObjectInstance parent) {
            super(spawn, "GumballSpring");
            this.parent = parent;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (triggered) {
                // ROM: continue animating, then delete when prev_anim == 1
                crumbleTimer--;
                if (crumbleTimer <= 0) {
                    setDestroyed(true);
                }
            }
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return SOLID_PARAMS;
        }

        @Override
        public boolean isTopSolidOnly() {
            // ROM: SolidObjectFull2_1P — solid from above only
            return true;
        }

        @Override
        public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
            if (triggered || !contact.standing()) {
                return;
            }
            if (!(playerEntity instanceof AbstractPlayableSprite player)) {
                return;
            }

            // ROM: sub_22F98 — bounce player upward, set airborne, play spring anim
            player.setYSpeed((short) BOUNCE_STRENGTH);
            player.setAir(true);
            player.setGSpeed((short) 0);

            // ROM: play sfx_Spring (0xB1)
            try {
                services().playSfx(Sonic3kSfx.SPRING.id);
            } catch (Exception e) {
                // Ignore
            }

            triggered = true;
            crumbleTimer = CRUMBLE_DELAY_FRAMES;
            LOGGER.fine("GumballSpring: triggered, will crumble in "
                    + CRUMBLE_DELAY_FRAMES + " frames");
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
            renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), false, false, 0);
        }
    }
}
