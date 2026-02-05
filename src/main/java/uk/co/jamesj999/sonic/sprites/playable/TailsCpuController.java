package uk.co.jamesj999.sonic.sprites.playable;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;

/**
 * CPU controller for Tails (Miles Prower) NPC follower behavior.
 * Implements ROM-accurate AI state machine from Sonic 2's TailsCPU_Control.
 *
 * In Normal state, Tails replays Sonic's recorded inputs from 17 frames ago
 * (via Sonic_Stat_Record_Buf). The AI only overrides with forced direction
 * when Tails is too far from Sonic's recorded position, and adds AI jumps
 * only under strict conditions.
 *
 * State machine: INIT -> NORMAL -> FLYING/PANIC -> NORMAL (cycles)
 */
public class TailsCpuController {

    /**
     * Number of frames behind Sonic that Tails replays.
     * ROM: delay = 0x10 entries, multiplied by 4 bytes + 4 = 68 byte offset = 17 entries.
     */
    private static final int FOLLOW_DELAY_FRAMES = 17;

    /** Horizontal distance threshold to stop forcing direction (ROM: 0x10 = 16 pixels) */
    private static final int HORIZONTAL_SNAP_THRESHOLD = 16;

    /** Large horizontal gap that triggers AI jump (ROM: 0x40 = 64 pixels) */
    private static final int JUMP_DISTANCE_TRIGGER = 64;

    /** Minimum vertical gap to trigger AI jump (ROM: 0x20 = 32 pixels) */
    private static final int JUMP_HEIGHT_THRESHOLD = 32;

    /** Frames off-screen before despawning (ROM: 0x12C = 300 frames) */
    private static final int DESPAWN_TIMEOUT = 300;

    /** Y offset above Sonic for respawn position (ROM: 0xC0 = 192 pixels) */
    private static final int RESPAWN_Y_OFFSET = 192;

    /** Maximum flying acceleration per frame (ROM: 0x0C = 12) */
    private static final int MAX_FLY_ACCEL = 12;

    /** Spindash charge cycle in Panic state (ROM: every 128 frames) */
    private static final int PANIC_SPINDASH_INTERVAL = 128;

    /** Tails helicopter flying animation ID (ROM: AniIDTailsAni_Fly = 0x20) */
    private static final int FLY_ANIM_ID = 0x20;

    public enum State {
        INIT,       // Initial setup
        SPAWNING,   // Waiting to respawn (Sonic must be grounded safely)
        FLYING,     // Flying toward Sonic's delayed position
        NORMAL,     // Standard following with input replay
        PANIC       // Stuck recovery (spindash escape)
    }

    private final AbstractPlayableSprite tails;
    private AbstractPlayableSprite sonic;

    private State state = State.INIT;
    private int despawnCounter = 0;
    private int panicCounter = 0;
    private int stuckCounter = 0;
    private int frameCounter = 0;

    // Virtual input produced each frame
    private boolean inputUp;
    private boolean inputDown;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputJump;
    private boolean jumpingFlag; // Set when AI initiated a jump, cleared on landing

    public TailsCpuController(AbstractPlayableSprite tails) {
        this.tails = tails;
    }

    /**
     * Run one frame of AI logic. Must be called before Tails' movement update.
     * After calling this, read getInputXxx() to get the virtual inputs.
     */
    public void update(int frameCount) {
        this.frameCounter = frameCount;

        // Find Sonic if not cached
        if (sonic == null) {
            sonic = findSonic();
            if (sonic == null) {
                clearInputs();
                return;
            }
        }

        // Clear inputs for this frame
        clearInputs();

        switch (state) {
            case INIT -> updateInit();
            case SPAWNING -> updateSpawning();
            case FLYING -> updateFlying();
            case NORMAL -> updateNormal();
            case PANIC -> updatePanic();
        }
    }

    // -- State Updates --

    private void updateInit() {
        // ROM: TailsCPU_Init - immediately transition to Normal
        state = State.NORMAL;
        despawnCounter = 0;
        stuckCounter = 0;
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0);
        updateNormal();
    }

    private void updateSpawning() {
        // ROM: TailsCPU_Spawning - wait for safe conditions
        if (sonic.getDead()) {
            return;
        }

        // ROM: Waits for frame counter lower byte == 0, Sonic grounded, not rolling,
        // not underwater, prevent_tails_respawn flag not set
        if (!sonic.getAir() && !sonic.getRolling() && !sonic.isJumping()) {
            // Respawn Tails above Sonic
            short sonicX = sonic.getCentreX();
            short sonicY = sonic.getCentreY();

            tails.setX((short) (sonicX - tails.getWidth() / 2));
            tails.setY((short) (sonicY - RESPAWN_Y_OFFSET));
            tails.setXSpeed((short) 0);
            tails.setYSpeed((short) 0);
            tails.setGSpeed((short) 0);
            tails.setAir(true);
            tails.setDead(false);

            state = State.FLYING;
            tails.setForcedAnimationId(FLY_ANIM_ID);
            despawnCounter = 0;
        }
    }

    private void updateFlying() {
        // ROM: TailsCPU_Flying - helicopter chase toward Sonic's delayed position
        tails.setForcedAnimationId(FLY_ANIM_ID);

        // Check despawn while flying
        if (checkDespawn()) {
            return;
        }

        // Get target from Sonic's position record buffer
        int targetX = getDelayedSonicX();
        int targetY = getDelayedSonicY();

        int tailsX = tails.getCentreX();
        int tailsY = tails.getCentreY();

        // ROM: Horizontal movement - distance/16 capped at 12, plus Sonic's speed
        int dx = targetX - tailsX;
        if (dx != 0) {
            int accel = Math.min(Math.abs(dx) / 16 + 1, MAX_FLY_ACCEL);
            accel += Math.abs(sonic.getXSpeed()) / 256;

            int moveAmount = Math.min(accel, Math.abs(dx));
            short newXSpeed;
            if (dx > 0) {
                newXSpeed = (short) (moveAmount * 256);
                inputRight = true;
            } else {
                newXSpeed = (short) (-moveAmount * 256);
                inputLeft = true;
            }
            tails.setXSpeed(newXSpeed);
        }

        // ROM: Vertical movement - 1 pixel per frame toward target
        int dy = targetY - tailsY;
        if (dy > 2) {
            tails.setYSpeed((short) 0x100);
        } else if (dy < -2) {
            tails.setYSpeed((short) -0x100);
        } else {
            tails.setYSpeed((short) 0);
        }

        // Direct position update (bypasses normal physics)
        tails.setX((short) (tails.getX() + tails.getXSpeed() / 256));
        tails.setY((short) (tails.getY() + tails.getYSpeed() / 256));

        // ROM: Check recorded status to decide when to land
        // Transition when aligned with target AND recorded status shows grounded
        byte recordedStatus = sonic.getStatusHistory(FOLLOW_DELAY_FRAMES);
        boolean recordedGrounded = (recordedStatus & AbstractPlayableSprite.STATUS_IN_AIR) == 0;
        boolean reachedTarget = Math.abs(dx) < HORIZONTAL_SNAP_THRESHOLD && Math.abs(dy) < 32;

        if (reachedTarget && recordedGrounded) {
            tails.setXSpeed((short) 0);
            tails.setYSpeed((short) 0);
            tails.setGSpeed((short) 0);
            tails.setForcedAnimationId(-1);
            state = State.NORMAL;
            despawnCounter = 0;
            stuckCounter = 0;
        }
    }

    /**
     * ROM: TailsCPU_Normal - Input replay with AI override.
     * Replays Sonic's recorded inputs from 17 frames ago. AI overrides direction
     * when too far away, and adds jumps under strict conditions.
     */
    private void updateNormal() {
        // Check if Sonic is dead
        if (sonic.getDead()) {
            triggerDespawn();
            return;
        }

        // ROM: Check if Tails is alive (routine < 6)
        if (tails.getDead()) {
            return;
        }

        // Check despawn (off-screen for too long)
        if (checkDespawn()) {
            return;
        }

        // ROM: Panic trigger - move_lock set AND zero inertia
        if (tails.isControlLocked() && tails.getGSpeed() == 0) {
            state = State.PANIC;
            panicCounter = 0;
            stuckCounter = 0;
            return;
        }

        // Stuck detection (grounded, not rolling, zero speed for too long)
        if (tails.getGSpeed() == 0 && !tails.getAir() && !tails.getRolling()) {
            stuckCounter++;
            if (stuckCounter > 120) {
                state = State.PANIC;
                panicCounter = 0;
                stuckCounter = 0;
                return;
            }
        } else {
            stuckCounter = 0;
        }

        // Read Sonic's recorded input and status from 17 frames ago
        short recordedInput = sonic.getInputHistory(FOLLOW_DELAY_FRAMES);
        byte recordedStatus = sonic.getStatusHistory(FOLLOW_DELAY_FRAMES);

        // Get Sonic's delayed position for distance checks
        int targetX = getDelayedSonicX();
        int targetY = getDelayedSonicY();
        int tailsX = tails.getCentreX();
        int tailsY = tails.getCentreY();
        int dx = targetX - tailsX;
        int dy = targetY - tailsY;

        // ROM: Copy Sonic's facing direction from recorded status
        boolean sonicFacedLeft = (recordedStatus & AbstractPlayableSprite.STATUS_FACING_LEFT) != 0;
        tails.setDirection(sonicFacedLeft ? Direction.LEFT : Direction.RIGHT);

        // ROM: Replay directional input from recorded buffer
        boolean replayLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean replayRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        boolean replayUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
        boolean replayDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
        boolean replayJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;

        // Apply replayed directional input
        inputLeft = replayLeft;
        inputRight = replayRight;
        inputUp = replayUp;
        inputDown = replayDown;

        // ROM: AI direction override when too far from recorded position
        // If horizontal distance > 16px, force direction toward target
        if (Math.abs(dx) > HORIZONTAL_SNAP_THRESHOLD) {
            if (dx < 0) {
                inputLeft = true;
                inputRight = false;
            } else {
                inputRight = true;
                inputLeft = false;
            }
        }

        // ROM: Jump replay from recorded input.
        // Must continue relaying jump-held state after the initial trigger,
        // otherwise doJumpHeight() caps ySpeed on frame 2 (minimum jump).
        if (replayJump) {
            inputJump = true;
            if (!jumpingFlag) {
                jumpingFlag = true;
            }
        }

        // ROM: AI jump logic - much more restrictive than simple distance chase
        // Only triggers when: frame_counter & 0xFF == 0 AND distance > 64px horizontally
        // AND Tails is below Sonic by > 32px AND frame_counter & 0x3F == 0
        if (!tails.getAir() && !jumpingFlag) {
            if ((frameCounter & 0xFF) == 0 && Math.abs(dx) > JUMP_DISTANCE_TRIGGER) {
                inputJump = true;
                jumpingFlag = true;
            } else if (dy < -JUMP_HEIGHT_THRESHOLD && (frameCounter & 0x3F) == 0
                    && Math.abs(dx) > JUMP_DISTANCE_TRIGGER) {
                inputJump = true;
                jumpingFlag = true;
            }
        }

        // Reset jump flag when Tails lands
        if (!tails.getAir() && jumpingFlag && !inputJump) {
            jumpingFlag = false;
        }
    }

    /**
     * ROM: TailsCPU_Panic - Stuck recovery via spindash.
     * Faces toward Sonic, holds Down, triggers spindash every 128 frames.
     */
    private void updatePanic() {
        panicCounter++;

        // Face toward Sonic
        int dx = sonic.getCentreX() - tails.getCentreX();
        if (dx > 0) {
            inputRight = true;
            tails.setDirection(Direction.RIGHT);
        } else {
            inputLeft = true;
            tails.setDirection(Direction.LEFT);
        }

        // Hold down to charge spindash
        inputDown = true;

        // ROM: Every 128 frames, release spindash with jump button
        if ((panicCounter & (PANIC_SPINDASH_INTERVAL - 1)) == 0) {
            inputJump = true;
            // After release, return to Normal
            state = State.NORMAL;
            stuckCounter = 0;
            panicCounter = 0;
        }
    }

    // -- Helper Methods --

    private int getDelayedSonicX() {
        return sonic.getCentreX(FOLLOW_DELAY_FRAMES);
    }

    private int getDelayedSonicY() {
        return sonic.getCentreY(FOLLOW_DELAY_FRAMES);
    }

    private boolean checkDespawn() {
        Camera camera = Camera.getInstance();
        int tailsScreenX = tails.getCentreX() - camera.getX();
        int tailsScreenY = tails.getCentreY() - camera.getY();

        // Check if Tails is off-screen
        boolean offScreen = tailsScreenX < -64 || tailsScreenX > 384 ||
                            tailsScreenY < -64 || tailsScreenY > 288;

        if (offScreen) {
            despawnCounter++;
            if (despawnCounter >= DESPAWN_TIMEOUT) {
                triggerDespawn();
                return true;
            }
        } else {
            despawnCounter = 0;
        }
        return false;
    }

    /**
     * Force a despawn (e.g., when Tails falls off-screen).
     * Clears any death state and transitions to SPAWNING.
     */
    public void despawn() {
        tails.setDead(false);
        tails.setDeathCountdown(0);
        triggerDespawn();
    }

    private void triggerDespawn() {
        // ROM: TailsCPU_Despawn - move far off-screen
        tails.setX((short) 0x4000);
        tails.setY((short) 0);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.setAir(true);
        tails.setForcedAnimationId(FLY_ANIM_ID);
        state = State.SPAWNING;
        despawnCounter = 0;
    }

    private void clearInputs() {
        inputUp = false;
        inputDown = false;
        inputLeft = false;
        inputRight = false;
        inputJump = false;
    }

    private AbstractPlayableSprite findSonic() {
        for (var sprite : SpriteManager.getInstance().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable && sprite != tails) {
                if (!playable.isCpuControlled()) {
                    return playable;
                }
            }
        }
        return null;
    }

    // -- Input Getters (read after update()) --

    public boolean getInputUp() { return inputUp; }
    public boolean getInputDown() { return inputDown; }
    public boolean getInputLeft() { return inputLeft; }
    public boolean getInputRight() { return inputRight; }
    public boolean getInputJump() { return inputJump; }
    public State getState() { return state; }

    /**
     * Returns true if Tails is in a flying state where normal physics
     * should be bypassed (the AI moves Tails directly).
     */
    public boolean isFlying() {
        return state == State.FLYING;
    }

    /**
     * Reset the controller (e.g., on level load).
     */
    public void reset() {
        state = State.INIT;
        despawnCounter = 0;
        panicCounter = 0;
        stuckCounter = 0;
        jumpingFlag = false;
        clearInputs();
        tails.setForcedAnimationId(-1);
        sonic = null;
    }
}
