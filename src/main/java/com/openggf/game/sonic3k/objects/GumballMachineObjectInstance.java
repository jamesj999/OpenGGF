package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
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
 * The parent gumball machine object implements a 3-state state machine driven
 * by ROM $38 bit flags (shared with children for the ball-ejection chain):
 * <ul>
 *   <li>IDLE: waiting for player to enter trigger range; gated by bit 1.</li>
 *   <li>SPIN: playing the spin animation; sets bit 3 at the end to signal container.</li>
 *   <li>POST_TRIGGER: waiting for container to clear bit 1 (loc_60EA2), then IDLE.</li>
 * </ul>
 * The container child watches the parent's bit 3, runs byte_6145B animation,
 * spawns the ball on the first frame, then calls back to clear bits 1+3.
 * Separately, springs set bit 1 on the dispenser when they crumble; the
 * dispenser sees its own bit 1, spawns 16 ejection effects, and self-destroys.
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
        POST_TRIGGER
    }

    // ===== ROM constants =====

    // ROM word_60D16: dc.w -$24, $48, -8, $10 = (xOffset=-36, width=72, yOffset=-8, height=16)
    // Check_PlayerInRange uses (xOffset..xOffset+width) x (yOffset..yOffset+height),
    // giving a 72x16 pixel activation zone centered around the machine.
    private static final int ACTIVATE_X_MIN = -36;  // xOffset
    private static final int ACTIVATE_X_MAX = 36;   // xOffset + width
    private static final int ACTIVATE_Y_MIN = -8;   // yOffset
    private static final int ACTIVATE_Y_MAX = 8;    // yOffset + height

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

    // ===== Machine Y drift / slot tracking =====
    //
    // ROM: 28 bytes at $FF2000..$FF201B, 14 word-sized "slots" (pairs of bumpers).
    // Initialized to 0xFF at machine spawn (ROM 127413-127419).
    // Tested by sub_6126C as words: word is "occupied" when non-zero.
    // Individual bumpers clear their byte when bumped (ROM 127692-127695).
    // ROM fills 36 bytes ($24) at $FF2000 at init. The iteration in sub_6126C only
    // reads 28 bytes (14 words), but the extra bytes are kept defensively so any
    // future subtype in [28..35] range won't corrupt adjacent memory.
    private static final int SLOT_COUNT_BYTES = 36;
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

    // ROM $38 field bit flags — shared state with children for ball-ejection chain.
    private byte flagByte38;

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
                this, CONTAINER_OFFSET_Y));

        // 3. Exit trigger — relative to machine, +0x2A0 Y
        spawnChild(() -> new ExitTriggerChild(
                buildSpawnAt(px + EXIT_TRIGGER_OFFSET_X, py + EXIT_TRIGGER_OFFSET_Y)));

        // 4-7. Platforms — follow machine via Refresh_ChildPosition.
        // ROM sub_61362 + RawAni_61388 = [0, 1, 0, $16]: each platform child gets a
        // per-slot mapping frame indexed by (subtype - 6) / 2 where subtype is the
        // child's CreateChild1_Normal index (6, 8, $A, $C for the 4 platform slots).
        // Children 3 (left), 5 (right): frame 0 (top cap tiles).
        // Child 4 (center): frame 1 (top cap center tiles).
        // Child 6 (extra): frame 0x16 — the MAIN MACHINE BODY sprite.
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_LEFT_OFFSET_X, py + PLATFORM_LEFT_OFFSET_Y),
                "GumballPlatformLeft", PLATFORM_LEFT_OFFSET_Y, 0x00));
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_CENTER_OFFSET_X, py + PLATFORM_CENTER_OFFSET_Y),
                "GumballPlatformCenter", PLATFORM_CENTER_OFFSET_Y, 0x01));
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_RIGHT_OFFSET_X, py + PLATFORM_RIGHT_OFFSET_Y),
                "GumballPlatformRight", PLATFORM_RIGHT_OFFSET_Y, 0x00));
        spawnChild(() -> new PlatformChild(
                buildSpawnAt(px + PLATFORM_EXTRA_OFFSET_X, py + PLATFORM_EXTRA_OFFSET_Y),
                "GumballPlatformExtra", PLATFORM_EXTRA_OFFSET_Y, 0x16));

        // 5th overlay child for the extra platform: the glass dome shine (sprite-mask
        // effect). ROM sub_61362 detects frame=$16 on the child and calls
        // CreateChild6_Simple with ChildObjDat_6144A, which spawns loc_610B6 using
        // ObjDat3_613EC (mapping frame $17, palette 0, art_tile 0). The overlay
        // follows the extra platform's position (ROM loc_610C6 copies parent pos).
        spawnChild(() -> new BodyOverlayChild(
                buildSpawnAt(px + PLATFORM_EXTRA_OFFSET_X, py + PLATFORM_EXTRA_OFFSET_Y),
                PLATFORM_EXTRA_OFFSET_Y));

        // 4 springs at bottom of stage (ROM: ChildObjDat_61424 as children of dispenser).
        // Absolute positions: dispenser (0x100, 0x310) + offsets (-$30..$30, -$18).
        // Springs crumble on first use and are respawned by REP gumball.
        springs.clear();
        springOriginalPositions.clear();
        for (int springX : SPRING_X_OFFSETS) {
            final int sx = DISPENSER_ABSOLUTE_X + springX;
            final int sy = DISPENSER_ABSOLUTE_Y + SPRING_Y_OFFSET;
            springOriginalPositions.add(new int[]{sx, sy});
            final DispenserChild dispenserRef = dispenser;
            GumballSpringChild spring = spawnChild(() -> new GumballSpringChild(
                    buildSpawnAt(sx, sy), this, dispenserRef));
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

        switch (state) {
            case IDLE -> updateIdle(playerEntity);
            case SPIN -> updateSpin();
            case POST_TRIGGER -> updatePostTrigger();
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

    // ===== ROM $38 field bit flags =====

    /** ROM bit 1: set when SPIN begins, cleared when container anim completes. Gates re-trigger. */
    public boolean isBit1Set() { return (flagByte38 & 0x02) != 0; }
    public void setMachineBit1() { flagByte38 |= 0x02; }

    /** ROM bit 3: set at end of SPIN, signals container to animate + spawn ball. */
    public boolean isBit3Set() { return (flagByte38 & 0x08) != 0; }
    public void setMachineBit3() { flagByte38 |= 0x08; }

    /** ROM loc_60EA2: container anim complete. */
    public void clearMachineBits1and3() { flagByte38 &= ~0x0A; }

    /** ROM bit 7: machine has seen first random=0 roll (for ball subtype selection). */
    public boolean isBit7Set() { return (flagByte38 & 0x80) != 0; }
    public void setBit7(boolean value) {
        if (value) {
            flagByte38 |= (byte) 0x80;
        } else {
            flagByte38 &= (byte) 0x7F;
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
        if (isBit1Set()) {
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
            setMachineBit1();
            state = State.SPIN;
            LOGGER.fine("GumballMachine: IDLE -> SPIN (player in range)");
        }
    }

    /**
     * SPIN state: Animate the spin sequence.
     * ROM: frames [3,5,6,7,$14,5,$F4,$7F,5,5,$FC] with per-frame timing.
     * <p>
     * At end of animation, sets machine bit 3 to signal the container
     * (loc_60E5C -> loc_60E8C) to animate and spawn a ball.
     */
    private void updateSpin() {
        int frameIndex = spinTimer / SPIN_FRAME_DURATION;
        if (frameIndex < SPIN_FRAMES.length) {
            currentFrame = SPIN_FRAMES[frameIndex];
        }
        spinTimer++;

        if (spinTimer >= SPIN_TOTAL_FRAMES) {
            currentFrame = IDLE_MAPPING_FRAME;
            setMachineBit3();
            state = State.POST_TRIGGER;
            LOGGER.fine("GumballMachine: SPIN -> POST_TRIGGER (bit 3 set, container signaled)");
        }
    }

    /**
     * POST_TRIGGER state: Wait for container to clear bit 1 (ROM loc_60EA2).
     * While bit 1 remains set, the machine is busy. Once cleared, return to IDLE.
     */
    private void updatePostTrigger() {
        if (!isBit1Set()) {
            state = State.IDLE;
            LOGGER.fine("GumballMachine: POST_TRIGGER -> IDLE (container finished)");
        }
    }

    // ===== Ball subtype selection =====

    /**
     * ROM byte_612E0 — random index (0-15) to subtype mapping.
     * Applied by sub_612A8 to give gumball items a subtype distribution.
     */
    private static final int[] SUBTYPE_LOOKUP = {
            0, 3, 1, 4, 2, 4, 5, 4, 6, 3, 7, 4, 5, 6, 7, 2
    };

    /**
     * ROM sub_612A8 (line 127983): random 0-15 indexes byte_612E0 to choose ball subtype.
     * <p>
     * ROM behavior (bset #7 sets Z based on OLD bit value):
     * <ul>
     *   <li>On first zero-roll: bit 7 was CLEAR (old=0) → Z=1 → beq branches keeping d0=0
     *       → LUT[0] = 0 (EXTRA LIFE subtype)</li>
     *   <li>On subsequent zero-rolls: bit 7 was SET (old=1) → Z=0 → fall through
     *       → moveq #3,d0 → LUT[3] = 4 (push player subtype)</li>
     * </ul>
     */
    public int chooseBallSubtype() {
        int r = rng.nextInt(16);
        if (r == 0) {
            boolean wasSet = isBit7Set();
            setBit7(true);
            if (wasSet) {
                r = 3; // ROM: fall through with d0=3 → LUT[3]=4
            }
            // else: r stays 0 → LUT[0]=0 (first zero-roll gives EXTRA LIFE)
        }
        return SUBTYPE_LOOKUP[r];
    }

    /** Called by ContainerDisplayChild when it activates (parent bit 3 set). */
    public void onContainerSpawnBall(int x, int y) {
        int subtype = chooseBallSubtype();
        ObjectSpawn gumballSpawn = new ObjectSpawn(x, y, 0xEB, subtype, 0, false, 0);
        spawnChild(() -> new GumballItemObjectInstance(gumballSpawn, 0, true));
        LOGGER.fine("GumballMachine: container spawned ball, subtype=" + subtype);
    }

    /** ROM loc_60EA2: called by container when animation completes. */
    public void onContainerAnimComplete() {
        clearMachineBits1and3();
        LOGGER.fine("GumballMachine: container animation complete, bits 1+3 cleared");
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
        // Destroy any surviving old springs before replacing them, so stale
        // entries don't linger in the object manager.
        for (GumballSpringChild old : springs) {
            if (old != null && !old.isDestroyed()) {
                old.setDestroyed(true);
            }
        }
        springs.clear();

        // If dispenser was destroyed (by spring → bit 1 chain), respawn it first
        // so newly-spawned springs have a live parent to signal.
        if (dispenser == null || dispenser.isDestroyed()) {
            dispenser = spawnChild(() -> new DispenserChild(
                    buildSpawnAt(DISPENSER_ABSOLUTE_X, DISPENSER_ABSOLUTE_Y)));
        }

        // Spawn fresh springs linked to the live dispenser.
        for (int[] pos : springOriginalPositions) {
            final int sx = pos[0];
            final int sy = pos[1];
            final DispenserChild dispenserRef = dispenser;
            GumballSpringChild spring = spawnChild(() -> new GumballSpringChild(
                    buildSpawnAt(sx, sy), this, dispenserRef));
            springs.add(spring);
        }
        LOGGER.fine("GumballMachine: respawned dispenser + " + springs.size() + " springs");
    }

    // ===== Rendering =====

    @Override
    public boolean isPersistent() {
        // The gumball machine is the central object of the bonus stage; keep it active
        return true;
    }

    @Override
    public boolean isHighPriority() {
        // ROM: ObjDat_GumballMachine uses make_art_tile(ArtTile_BonusStage, 1, 1)
        // — VDP priority bit = 1. Must render in front of high-priority FG tiles
        // (the machine body chunks in the level layout).
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
     * ROM sub_61314 / loc_60D96: When a spring crumbles, it sets bit 1 on the
     * dispenser; the dispenser sees its own bit 1, spawns 16 ejection effects
     * from byte_61342 + sub_61320, and self-destroys.
     */
    static class DispenserChild extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        // ROM sub_61314: d1=$4B (halfWidth=75), d2=$10 (airHalfHeight=16), d3=$11 (groundHalfHeight=17)
        private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(75, 16, 17);
        private static final int MAPPING_FRAME = 0x13; // ROM ObjDat3_61398 byte 2

        private boolean springBitSet;  // ROM bit 1 of $38

        DispenserChild(ObjectSpawn spawn) {
            super(spawn, "GumballDispenser");
        }

        @Override
        public boolean isHighPriority() {
            // ROM: ObjDat3_61398 make_art_tile(ArtTile_BonusStage, 1, 1) — VDP priority 1
            return true;
        }

        /** ROM loc_60E44: called by spring when it crumbles. */
        public void setSpringBit() { this.springBitSet = true; }

        /** ROM btst #1, $38(a1) — siblings chain-crumble when this bit is set. */
        public boolean isSpringBitSet() { return springBitSet; }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (springBitSet) {
                // ROM loc_60D96: spawn 16 ejection effects + delete self.
                for (int i = 0; i < 16; i++) {
                    final int idx = i;
                    spawnChild(() -> new EjectionEffectChild(
                            buildSpawnAt(spawn.x(), spawn.y()), idx));
                }
                setDestroyed(true);
            }
        }

        @Override
        public boolean isSolidFor(PlayableEntity playerEntity) { return !springBitSet; }

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
            // No action — pure platform
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
     * ROM loc_6101E / loc_61032 / sub_61320 / byte_61342.
     * 16 spawned when dispenser deletes. Each has a different subtype (0-15) giving
     * a unique position offset and timer duration.
     */
    static class EjectionEffectChild extends AbstractObjectInstance {
        private static final int MAPPING_FRAME = 0x15;

        // ROM byte_61342: 16 signed (dx, dy) offsets
        private static final int[][] OFFSETS = {
                {  -8,    8}, {   8,    8}, {  -8,   -8}, {   8,   -8},
                {-0x18,   8}, { 0x18,   8}, {-0x18,  -8}, { 0x18,  -8},
                {-0x28,   8}, { 0x28,   8}, {-0x28,  -8}, { 0x28,  -8},
                {-0x38,   8}, { 0x38,   8}, {-0x38,  -8}, { 0x38,  -8}
        };

        private int timer;  // ROM $2E(a0)
        private final int drawX;
        private final int drawY;

        EjectionEffectChild(ObjectSpawn spawn, int subtype) {
            super(spawn, "GumballEjectionEffect");
            // ROM CreateChild6_Simple assigns subtypes via addq.w #2,d2 → 0,2,4,...,30.
            // Timer ($2E) = subtype, so effects live for subtype+1 frames (1..31).
            // We pass 0..15 as the OFFSETS index, so double it to get the ROM timer value.
            timer = subtype * 2;
            int[] off = OFFSETS[subtype];
            drawX = spawn.x() + off[0];
            drawY = spawn.y() + off[1];
        }

        @Override
        public boolean isHighPriority() {
            // ROM: ObjDat3_613D4 make_art_tile(ArtTile_BonusStage, 1, 1) — VDP priority 1
            return true;
        }

        @Override public boolean isPersistent() { return false; }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            timer--;
            if (timer < 0) {
                // ROM loc_61032: `subq.b #1,$2E(a0) / bpl draw / move.l #MoveChkDel,(a0)`.
                // After the timer expires, the ROM swaps the update routine to
                // MoveChkDel, which does: `MoveSprite` (applies gravity +$38 to y_vel
                // each frame, then updates pos from velocity) + `Sprite_CheckDeleteXY`
                // (delete when off-screen).
                //
                // CRITICAL: sub_61320 (the init routine) only writes timer ($2E) and
                // position offsets — it NEVER initializes x_vel or y_vel. So effects
                // start with zero velocity. Gravity (+$38/frame) accumulates slowly,
                // and the effect falls a short distance before the off-screen check
                // deletes it. With no horizontal velocity, off-screen only happens
                // vertically (stage camera is fixed in the gumball bonus stage).
                //
                // Our engine takes the shortcut of destroying the effect immediately
                // on timer expiry, which is visually indistinguishable for this
                // transient debris (no x drift, tiny y drop before deletion anyway).
                // This is intentional ROM-accurate-enough behaviour.
                setDestroyed(true);
            }
        }

        @Override public int getPriorityBucket() { return RenderPriority.clamp(PRIORITY_BUCKET); }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
            if (r == null) return;
            r.drawFrameIndex(MAPPING_FRAME, drawX, drawY, false, false);
        }
    }

    /**
     * Ball container display child — visual animation of the gumball container.
     * <p>
     * ROM: byte_6145B container animation (loc_60E5C -> loc_60E8C -> loc_60EA2).
     * Positioned at (0, +0x24) from parent machine.
     * <p>
     * State machine:
     * <ul>
     *   <li>DORMANT — idle, watches parent's bit 3; renders IDLE_FRAME.</li>
     *   <li>ANIMATING — runs byte_6145B pairs; on first frame, calls
     *       onContainerSpawnBall(). When complete, calls onContainerAnimComplete()
     *       (clears parent bits 1+3) and returns to DORMANT.</li>
     * </ul>
     */
    static class ContainerDisplayChild extends AbstractObjectInstance {

        // ROM byte_6145B pairs (frame, timer-value). Timer value+1 runs frames.
        private static final int[][] ANIM_PAIRS = {
                {2, 3}, {3, 3}, {4, 0xF}, {3, 3}, {2, 3}
        };
        private static final int IDLE_FRAME = 2;

        private enum State { DORMANT, ANIMATING }

        private final GumballMachineObjectInstance parent;
        private final int offsetFromMachine; // Y offset (ROM: +$24)
        private State state = State.DORMANT;
        private int animStep;
        private int animTimer;
        private int currentFrame = IDLE_FRAME;

        ContainerDisplayChild(ObjectSpawn spawn, GumballMachineObjectInstance parent,
                              int offsetFromMachine) {
            super(spawn, "GumballContainer");
            this.parent = parent;
            this.offsetFromMachine = offsetFromMachine;
        }

        @Override
        public boolean isHighPriority() {
            // ROM: ObjDat3_613BC make_art_tile(ArtTile_BonusStage, 1, 1) — VDP priority 1
            return true;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (state == State.DORMANT) {
                if (parent.isBit3Set()) {
                    state = State.ANIMATING;
                    animStep = 0;
                    animTimer = ANIM_PAIRS[0][1] + 1;
                    currentFrame = ANIM_PAIRS[0][0];
                    int spawnY = parent.getCurrentY() + offsetFromMachine;
                    parent.onContainerSpawnBall(spawn.x(), spawnY);
                }
                return;
            }
            // ANIMATING
            animTimer--;
            if (animTimer <= 0) {
                animStep++;
                if (animStep >= ANIM_PAIRS.length) {
                    parent.onContainerAnimComplete();
                    state = State.DORMANT;
                    currentFrame = IDLE_FRAME;
                    return;
                }
                currentFrame = ANIM_PAIRS[animStep][0];
                animTimer = ANIM_PAIRS[animStep][1] + 1;
            }
        }

        @Override
        public int getX() { return spawn.x(); }

        @Override
        public int getY() { return parent.getCurrentY() + offsetFromMachine; }

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
            renderer.drawFrameIndex(currentFrame, spawn.x(), getY(), false, false);
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
     * <p>
     * Each of the 4 platform slots renders a DIFFERENT mapping frame from
     * RawAni_61388 [0, 1, 0, $16], set by sub_61362 based on the child's
     * subtype (spawn slot index in ChildObjDat_613F8).
     */
    static class PlatformChild extends AbstractObjectInstance {

        /** Y offset from the machine's savedY (machine-relative). */
        private final int offsetFromMachine;

        /** Per-instance mapping frame from RawAni_61388 (ROM sub_61362). */
        private final int mappingFrame;

        PlatformChild(ObjectSpawn spawn, String name, int offsetFromMachine, int mappingFrame) {
            super(spawn, name);
            this.offsetFromMachine = offsetFromMachine;
            this.mappingFrame = mappingFrame;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public boolean isHighPriority() {
            // ROM: ObjDat3_6138C uses make_art_tile(ArtTile_BonusStage, 0, 0) — priority 0.
            // However, these sprites overlay FG tiles that form the static machine body.
            // With the VSCROLL system, the FG tiles at the machine position may have
            // high priority in the engine's tilemap data. Setting sprites to high priority
            // ensures they render in front of those tiles (matching the visual effect of
            // ROM where the FG tiles at the machine position are low-priority).
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
            renderer.drawFrameIndex(mappingFrame, spawn.x(), renderY, false, false, 0);
        }
    }

    /**
     * Glass dome shine overlay (sprite-mask shine effect) for the machine body.
     * <p>
     * ROM loc_610B6 / ObjDat3_613EC (mapping frame $17). Spawned by sub_61362 via
     * CreateChild6_Simple(ChildObjDat_6144A) when the extra platform child's
     * mapping frame is $16 (the body sprite). Follows the parent's position every
     * frame (ROM loc_610C6 copies x_pos/y_pos from parent3). Uses palette 0 /
     * art_tile 0 per the ObjDat3_613EC `make_art_tile($000, 0, 0)` definition and
     * sets the global Spritemask_flag on the VDP each frame.
     * <p>
     * Priority $180 in ROM — slightly lower than the machine body's $200 so it
     * renders just after the body. In our bucketed renderer this is still
     * priority bucket 1 (behind Sonic).
     */
    static class BodyOverlayChild extends AbstractObjectInstance {

        private static final int MAPPING_FRAME = 0x17;

        /** Y offset from the machine's current Y (matches extra platform offset). */
        private final int offsetFromMachine;

        BodyOverlayChild(ObjectSpawn spawn, int offsetFromMachine) {
            super(spawn, "GumballBodyShine");
            this.offsetFromMachine = offsetFromMachine;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public boolean isHighPriority() {
            // Render in front of FG tiles so the shine overlay is visible
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            // ROM loc_610C6 just copies parent position; rendering reads the
            // machine's current Y live, so no per-frame state updates needed.
        }

        @Override
        public int getPriorityBucket() {
            // ROM ObjDat3_613EC priority = $180 (below machine body's $200).
            // Keep it in the same bucket (1) as the rest of the machine so it
            // draws after the body but still behind Sonic.
            return RenderPriority.clamp(1);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
            if (renderer == null) {
                return;
            }
            GumballMachineObjectInstance machine = GumballMachineObjectInstance.current();
            int renderY = (machine != null)
                    ? machine.getCurrentY() + offsetFromMachine
                    : spawn.y();
            // ROM ObjDat3_613EC uses make_art_tile($000, 0, 0) — palette 0.
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

        // ROM: short bounce animation before crumbling.
        // Ani_Spring anim 1 is `dc.b 0, 1, 0, 0, 2, 2, 2, 2, 2, 2, $FD, 0` — 10 animation entries.
        private static final int CRUMBLE_DELAY_FRAMES = 10;

        // ROM: Map_Spring frames — frame 0 idle, frame 1 compressed (played on bounce)
        private static final int IDLE_FRAME = 0;
        private static final int COMPRESSED_FRAME = 1;

        private final GumballMachineObjectInstance parent;
        private final DispenserChild dispenser;
        private boolean triggered;
        private int crumbleTimer;
        private boolean signaledDispenser;

        GumballSpringChild(ObjectSpawn spawn, GumballMachineObjectInstance parent,
                           DispenserChild dispenser) {
            super(spawn, "GumballSpring");
            this.parent = parent;
            this.dispenser = dispenser;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            // ROM: btst #1, $38(a1) / bne.s loc_60E36 — if a sibling spring already
            // crumbled, the dispenser's bit 1 is set; siblings chain-delete at the
            // start of their update each frame.
            if (!triggered && dispenser != null && dispenser.isSpringBitSet()) {
                setDestroyed(true);
                return;
            }
            if (triggered) {
                // ROM: continue animating, then delete when prev_anim == 1
                crumbleTimer--;
                if (crumbleTimer <= 0) {
                    // ROM loc_60E44: crumble sets bit 1 on parent (dispenser).
                    if (!signaledDispenser && dispenser != null) {
                        dispenser.setSpringBit();
                        signaledDispenser = true;
                    }
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

            // ROM sub_22F98 (sonic3k.asm lines 47714-47766) — full red vertical spring bounce.

            // addq.w #8, y_pos(a1) — nudge player DOWN 8 pixels (start of compression)
            player.setY((short) (player.getY() + 8));

            // move.w $30(a0), y_vel(a1) — upward velocity (-$1000 for red spring)
            player.setYSpeed((short) BOUNCE_STRENGTH);
            // ROM sub_22F98 does not clear ground_vel for plain vertical red spring
            // (subtype 0) — ground_vel is only touched for flip-related subtypes.

            // bset #1, status(a1) — Status_InAir
            player.setAir(true);

            // bclr #3, status(a1) — clear Status_OnObj
            player.setOnObject(false);

            // clr.b jumping(a1)
            player.setJumping(false);

            // clr.b spin_dash_flag(a1)
            player.setSpindash(false);

            // move.b #$10, anim(a1) — player SPRING animation
            player.setAnimationId(Sonic3kAnimationIds.SPRING);

            // ROM sub_22F98 does not apply move_lock — removed for ROM parity.

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
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_SPRING);
            if (renderer == null) {
                return;
            }
            // Frame 0 idle, frame 1 compressed (played while the bounce/crumble plays).
            int frame = triggered ? COMPRESSED_FRAME : IDLE_FRAME;
            renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false, 0);
        }
    }
}
