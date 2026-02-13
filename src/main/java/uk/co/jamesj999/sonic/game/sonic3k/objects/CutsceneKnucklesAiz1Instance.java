package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Cutscene Knuckles object for the AIZ1 intro cinematic.
 *
 * Disassembly reference: sonic3k.asm CutsceneKnux_AIZ1 (loc_61DBE onward).
 *
 * The ROM uses stride-2 routine dispatch (0, 2, 4, 6, 8, 10, 12).
 * We use the raw stride-2 values so routine IDs match the disassembly.
 *
 * Knuckles is spawned by the AizPlaneIntroInstance at routine 0x14
 * (when player X >= 0x918). He falls in, stands, paces, laughs while
 * collecting the scattered emeralds, then exits to trigger the title card.
 *
 * Routine overview:
 *   0  (0x00) - Init: set position, mapping_frame, y_radius, spawn rock child, load palette
 *   2  (0x02) - Wait trigger: poll parent status bit 7, then set velocity and become visible
 *   4  (0x04) - Fall: animate fall, MoveSprite with gravity, land on floor
 *   6  (0x06) - Stand: wait 0x7F frames, then flip facing and start pacing
 *   8  (0x08) - Pace: walk left then right, collecting emeralds, then laugh
 *  10  (0x0A) - Laugh: animate laugh for 0x3F frames, then start exit walk
 *  12  (0x0C) - Exit: walk offscreen, unlock controls, spawn title card, delete self
 */
public class CutsceneKnucklesAiz1Instance extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(CutsceneKnucklesAiz1Instance.class.getName());

    // -----------------------------------------------------------------------
    // ROM constants (sonic3k.asm CutsceneKnux_AIZ1)
    // -----------------------------------------------------------------------

    /** Frames per pace direction (0x29 = 41 frames). */
    public static final int PACE_TIMER = 0x29;

    /** Stand-still countdown frames before pacing (0x7F = 127 frames). */
    public static final int STAND_TIMER = 0x7F;

    /** Laugh animation countdown frames (0x3F = 63 frames). */
    public static final int LAUGH_TIMER = 0x3F;

    /** Pace walk speed in subpixels (0x600 = 6 pixels/frame). */
    public static final int PACE_VELOCITY = 0x600;

    /** Initial fall Y velocity in subpixels (upward = negative). */
    public static final int FALL_INIT_Y_VEL = -0x600;

    /** Initial fall X velocity in subpixels (rightward drift). */
    public static final int FALL_INIT_X_VEL = 0x80;

    /** Initial X position (world coordinates). */
    public static final int INIT_X = 0x1400;

    /** Initial Y position (world coordinates). */
    public static final int INIT_Y = 0x440;

    /** Standard S3K gravity in subpixels per frame. */
    private static final int GRAVITY = 0x38;

    /** Y collision radius from ROM (y_radius = 0x13). */
    private static final int Y_RADIUS = 0x13;

    /** Initial mapping frame from ROM. */
    private static final int INIT_MAPPING_FRAME = 8;

    /** Mapping frame set after landing. */
    private static final int LANDED_MAPPING_FRAME = 0x16;

    // -----------------------------------------------------------------------
    // Mutable state
    // -----------------------------------------------------------------------

    /** Routine counter (stride-2: 0, 2, 4, 6, 8, 10, 12). */
    private int routine;

    /** Current world X position (pixels). */
    private int currentX;

    /** Current world Y position (pixels). */
    private int currentY;

    /** Fractional X accumulator (subpixels, 0-255). */
    private int xSub;

    /** Fractional Y accumulator (subpixels, 0-255). */
    private int ySub;

    /** X velocity in subpixels (256 = 1 pixel per frame). */
    private int xVel;

    /** Y velocity in subpixels (256 = 1 pixel per frame). */
    private int yVel;

    /** General-purpose countdown timer. */
    private int waitTimer;

    /** Current mapping frame index. */
    private int mappingFrame;

    /** Whether Knuckles is facing left. */
    private boolean facingLeft;

    /** Pace phase: false = first pass (left), true = return pass (right). */
    private boolean paceReturnPhase;

    /** Whether the parent has signaled the trigger (status bit 7). */
    private boolean triggered;

    /** Whether Knuckles is visible (render_flags bit 7). */
    private boolean visible;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public CutsceneKnucklesAiz1Instance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnucklesAIZ1");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.routine = 0;
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = 0;
        this.yVel = 0;
        this.waitTimer = 0;
        this.mappingFrame = INIT_MAPPING_FRAME;
        this.facingLeft = false;
        this.paceReturnPhase = false;
        this.triggered = false;
        this.visible = false;
    }

    // -----------------------------------------------------------------------
    // ObjectInstance interface
    // -----------------------------------------------------------------------

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 0  -> routine0Init();
            case 2  -> routine2WaitTrigger();
            case 4  -> routine4Fall();
            case 6  -> routine6Stand();
            case 8  -> routine8Pace();
            case 10 -> routine10Laugh();
            case 12 -> routine12Exit();
            default -> {
                // Invalid routine - no-op
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No-op: art loading and rendering handled in later tasks (Task 10).
    }

    // -----------------------------------------------------------------------
    // Routine accessors (for test and external use)
    // -----------------------------------------------------------------------

    public int getRoutine() {
        return routine;
    }

    public int getXVel() {
        return xVel;
    }

    public int getYVel() {
        return yVel;
    }

    public int getWaitTimer() {
        return waitTimer;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    public boolean isFacingLeft() {
        return facingLeft;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Called by the parent intro object to signal the trigger (status bit 7).
     * Knuckles transitions from wait to fall on the next update.
     */
    public void trigger() {
        this.triggered = true;
    }

    // -----------------------------------------------------------------------
    // Routine 0 (loc_61DBE): Init
    // -----------------------------------------------------------------------

    /**
     * Set position to (0x1400, 0x440), mapping_frame = 8, y_radius = 0x13.
     * Spawn rock child object and load palette.
     */
    private void routine0Init() {
        LOG.fine("Routine 0: init Knuckles cutscene at (" + INIT_X + ", " + INIT_Y + ")");

        currentX = INIT_X;
        currentY = INIT_Y;
        mappingFrame = INIT_MAPPING_FRAME;

        // TODO: Spawn rock child object (ChildObjDat_6659A via CreateChild1_Normal) - Task 12
        // TODO: Load palette via sub_65DD6

        routine = 2;
    }

    // -----------------------------------------------------------------------
    // Routine 2 (loc_61DF4): Wait Trigger
    // -----------------------------------------------------------------------

    /**
     * Poll parent's status bit 7. When triggered:
     * - Set visible
     * - Set y_vel = -0x600, x_vel = 0x80
     * - Load Pal_CutsceneKnux to palette line 1
     * - Advance to fall routine
     */
    private void routine2WaitTrigger() {
        if (!triggered) {
            return;
        }

        LOG.fine("Routine 2: trigger received, starting fall");

        visible = true;
        yVel = FALL_INIT_Y_VEL;
        xVel = FALL_INIT_X_VEL;

        // TODO: Load Pal_CutsceneKnux to palette line 1

        routine = 4;
    }

    // -----------------------------------------------------------------------
    // Routine 4 (loc_61E24): Fall
    // -----------------------------------------------------------------------

    /**
     * Animate fall frames (byte_666AF). Apply MoveSprite with gravity.
     * On floor collision: snap Y, mapping_frame = 0x16, timer = 0x7F.
     */
    private void routine4Fall() {
        // TODO: Animate fall frames (byte_666AF)

        // MoveSprite: apply velocity to position with subpixel accumulation.
        // X movement
        int xTotal = (xSub & 0xFF) + (xVel & 0xFF);
        currentX += (xVel >> 8) + (xTotal >> 8);
        xSub = xTotal & 0xFF;

        // Y movement with gravity
        yVel += GRAVITY;
        int yTotal = (ySub & 0xFF) + (yVel & 0xFF);
        currentY += (yVel >> 8) + (yTotal >> 8);
        ySub = yTotal & 0xFF;

        // TODO: ObjCheckFloorDist terrain collision - will be integrated in Task 13.
        // When floor is hit (d1 < 0):
        //   snap Y to floor
        //   mappingFrame = LANDED_MAPPING_FRAME (0x16)
        //   waitTimer = STAND_TIMER (0x7F)
        //   routine = 6
    }

    /**
     * Called externally when terrain collision detects floor contact during fall.
     * Snaps Y to the given ground position and transitions to stand routine.
     *
     * @param groundY the Y coordinate to snap to
     */
    public void landOnGround(int groundY) {
        currentY = groundY;
        yVel = 0;
        xVel = 0;
        mappingFrame = LANDED_MAPPING_FRAME;
        waitTimer = STAND_TIMER;
        routine = 6;
        LOG.fine("Routine 4: landed at Y=" + groundY + ", transitioning to stand");
    }

    // -----------------------------------------------------------------------
    // Routine 6 (loc_61E64): Stand
    // -----------------------------------------------------------------------

    /**
     * Countdown waitTimer (0x7F frames). When expired:
     * - Flip facing direction
     * - Set walk animation (byte_666A9)
     * - x_vel = -0x600 (walk left)
     * - timer = 0x29 (pace frames)
     */
    private void routine6Stand() {
        waitTimer--;
        if (waitTimer <= 0) {
            waitTimer = PACE_TIMER;
            facingLeft = true;
            xVel = -PACE_VELOCITY;
            paceReturnPhase = false;

            // TODO: Set walk animation (byte_666A9)

            routine = 8;
            LOG.fine("Routine 6: stand complete, starting pace left");
        }
    }

    // -----------------------------------------------------------------------
    // Routine 8 (loc_61E96): Pace
    // -----------------------------------------------------------------------

    /**
     * Animate walk + MoveSprite2 + countdown timer.
     *
     * First pass (left): walk left for 0x29 frames, then reverse direction.
     * Return pass (right): walk right for 0x29 frames, then transition to laugh.
     */
    private void routine8Pace() {
        // TODO: Animate walk frames

        // MoveSprite2: apply velocity to position (no gravity).
        int xTotal = (xSub & 0xFF) + (xVel & 0xFF);
        currentX += (xVel >> 8) + (xTotal >> 8);
        xSub = xTotal & 0xFF;

        waitTimer--;
        if (waitTimer <= 0) {
            if (!paceReturnPhase) {
                // First pass complete: reverse to walk right
                xVel = PACE_VELOCITY;
                facingLeft = false;
                waitTimer = PACE_TIMER;
                paceReturnPhase = true;
                LOG.fine("Routine 8: pace left complete, reversing to right");
            } else {
                // Return pass complete: transition to laugh
                xVel = 0;
                mappingFrame = LANDED_MAPPING_FRAME;
                waitTimer = LAUGH_TIMER;
                routine = 10;
                LOG.fine("Routine 8: pace right complete, transitioning to laugh");

                // TODO: Load laugh animation (loc_62056)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Routine 10 (loc_61EE0): Laugh
    // -----------------------------------------------------------------------

    /**
     * Animate laugh + countdown timer (0x3F frames).
     * When expired: set walk animation, x_vel = 0x600 (walk right to exit).
     * Spawn Obj_Song_Fade_ToLevelMusic (fade to AIZ music).
     */
    private void routine10Laugh() {
        // TODO: Animate laugh frames

        waitTimer--;
        if (waitTimer <= 0) {
            xVel = PACE_VELOCITY;
            facingLeft = false;

            // TODO: Set walk animation (byte_666A9)
            // TODO: Spawn Obj_Song_Fade_ToLevelMusic (fade to AIZ music)

            routine = 12;
            LOG.fine("Routine 10: laugh complete, transitioning to exit");
        }
    }

    // -----------------------------------------------------------------------
    // Routine 12 (loc_61F10): Exit
    // -----------------------------------------------------------------------

    /**
     * Animate + MoveSprite2 until offscreen (render_flags bit 7 clear).
     * When offscreen:
     * - Clear Palette_cycle_counters
     * - Unlock player controls
     * - Spawn title card
     * - Set Level_started_flag = 0x91
     * - Delete self
     */
    private void routine12Exit() {
        // TODO: Animate walk frames

        // MoveSprite2: apply velocity to position (no gravity).
        int xTotal = (xSub & 0xFF) + (xVel & 0xFF);
        currentX += (xVel >> 8) + (xTotal >> 8);
        xSub = xTotal & 0xFF;

        // Check if offscreen (render_flags bit 7 clear).
        if (!isOnScreen()) {
            LOG.fine("Routine 12: offscreen, cleaning up");

            // TODO: Clear Palette_cycle_counters
            // TODO: Unlock player controls
            // TODO: Spawn title card
            // TODO: Set Level_started_flag = 0x91

            setDestroyed(true);
        }
    }
}
