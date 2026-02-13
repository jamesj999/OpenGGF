package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Chaos Emerald scatter object for the AIZ1 intro cinematic.
 *
 * Disassembly reference: sonic3k.asm loc_67900 onward.
 *
 * Seven emeralds are spawned by the intro plane object at routine 0x18
 * (when player X >= 0x13D0). Each emerald is created via CreateChild6_Simple
 * with subtypes 0, 2, 4, 6, 8, 10, 12. The mapping frame is derived from
 * the subtype (subtype >> 1), giving frames 0-6 for the seven emerald colors.
 *
 * All seven emeralds receive the same initial velocity: x_vel = -0x40,
 * y_vel = -0x700 (from Obj_VelocityIndex at byte offset 0x40).
 *
 * Three phases:
 *
 * 1. FALLING (loc_67938): Gravity applied via MoveSprite. Floor check with
 *    ObjCheckFloorDist. When floor is hit (d1 < 0), snap Y and transition
 *    to GROUNDED.
 *
 * 2. GROUNDED (loc_6795C): Each frame, check Knuckles proximity. If within
 *    8 pixels horizontally and Knuckles is moving in the correct direction
 *    (determined by subtype bit 1), the emerald is collected (deleted).
 *
 *    Direction matching logic:
 *    - Subtype bit 1 = 0: collected when Knuckles moves RIGHT (positive x_vel)
 *    - Subtype bit 1 = 1: collected when Knuckles moves LEFT (negative x_vel)
 */
public class AizEmeraldScatterInstance extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(AizEmeraldScatterInstance.class.getName());

    // -----------------------------------------------------------------------
    // Phase enum
    // -----------------------------------------------------------------------

    public enum Phase {
        FALLING,
        GROUNDED
    }

    // -----------------------------------------------------------------------
    // ROM constants
    // -----------------------------------------------------------------------

    /** Horizontal proximity in pixels for Knuckles pickup check. */
    public static final int PICKUP_PROXIMITY = 8;

    /** Y collision radius (y_radius = 4 from ROM). */
    public static final int Y_RADIUS = 4;

    /** Standard S3K gravity in subpixels per frame. */
    public static final int GRAVITY = 0x38;

    /** Initial X velocity in subpixels (from Obj_VelocityIndex offset 0x40). */
    private static final int INIT_X_VEL = -0x40;

    /** Initial Y velocity in subpixels (from Obj_VelocityIndex offset 0x40). */
    private static final int INIT_Y_VEL = -0x700;

    // -----------------------------------------------------------------------
    // Mutable state
    // -----------------------------------------------------------------------

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

    /** Display frame index derived from subtype (0-6 for seven emerald colors). */
    private final int mappingFrame;

    /** Current phase of this emerald's lifecycle. */
    private Phase phase;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public AizEmeraldScatterInstance(ObjectSpawn spawn) {
        super(spawn, "AIZEmeraldScatter");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = INIT_X_VEL;
        this.yVel = INIT_Y_VEL;
        this.mappingFrame = spawn.subtype() >> 1;
        this.phase = Phase.FALLING;
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
        switch (phase) {
            case FALLING -> updateFalling();
            case GROUNDED -> updateGrounded();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering deferred to art loading task (Task 10).
    }

    // -----------------------------------------------------------------------
    // Phase logic
    // -----------------------------------------------------------------------

    /**
     * FALLING phase (loc_67938): Apply gravity via MoveSprite equivalent.
     * Check floor with ObjCheckFloorDist. When floor hit (d1 < 0), snap Y
     * and transition to GROUNDED.
     */
    private void updateFalling() {
        // MoveSprite: add velocity to position with subpixel accumulation.
        // X: position += xVel (16:16 fixed point)
        int xTotal = (xSub & 0xFF) + (xVel & 0xFF);
        currentX += (xVel >> 8) + (xTotal >> 8);
        xSub = xTotal & 0xFF;

        // Y: apply gravity first, then update position
        yVel += GRAVITY;
        int yTotal = (ySub & 0xFF) + (yVel & 0xFF);
        currentY += (yVel >> 8) + (yTotal >> 8);
        ySub = yTotal & 0xFF;

        // TODO: ObjCheckFloorDist terrain collision - will be integrated in Task 13.
        // For now, the phase transition from FALLING to GROUNDED will be wired
        // when terrain collision is available during full integration.
    }

    /**
     * GROUNDED phase (loc_6795C): Check Knuckles proximity each frame.
     * Collection logic delegates to canBeCollectedByVelocity().
     */
    private void updateGrounded() {
        // Knuckles proximity check will be wired in Task 13 (full integration)
        // when the CutsceneKnucklesAiz1Instance reference is available.
        // The core direction-matching logic is exposed via canBeCollectedByVelocity().
    }

    // -----------------------------------------------------------------------
    // Collection logic
    // -----------------------------------------------------------------------

    /**
     * Determines whether this emerald can be collected by Knuckles based on
     * his current X velocity and this emerald's subtype bit 1.
     *
     * ROM logic (loc_6795C):
     * <pre>
     *   move.w  x_vel(a1),d0        ; get Knuckles x_vel
     *   btst    #1,subtype(a0)      ; check subtype bit 1
     *   beq.s   +                   ; if clear, skip negate
     *   neg.w   d0                  ; negate velocity
     * + tst.w   d0
     *   bmi.s   .wrong_direction    ; if negative, wrong direction
     *   ; ... delete self (collected)
     * </pre>
     *
     * @param knuxXVel Knuckles' current x_vel in subpixels
     * @return true if this emerald should be collected (deleted)
     */
    public boolean canBeCollectedByVelocity(int knuxXVel) {
        int adjusted = knuxXVel;
        if ((spawn.subtype() & 2) != 0) {
            adjusted = -adjusted;
        }
        // ROM: tst.w d0 / bmi.s (branch if negative)
        // So collected when adjusted >= 0
        return adjusted >= 0;
    }

    // -----------------------------------------------------------------------
    // Accessors for test and external use
    // -----------------------------------------------------------------------

    public int getMappingFrame() {
        return mappingFrame;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int getXVel() {
        return xVel;
    }

    public int getYVel() {
        return yVel;
    }

    /**
     * Snaps this emerald to the ground at the given Y coordinate
     * and transitions to GROUNDED phase.
     *
     * @param groundY the Y position to snap to
     */
    public void landOnGround(int groundY) {
        this.currentY = groundY;
        this.yVel = 0;
        this.xVel = 0;
        this.phase = Phase.GROUNDED;
    }
}
