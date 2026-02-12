package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * Slicer (0xA1) - Praying mantis Badnik from Metropolis Zone.
 * Walks back and forth on platforms, detects the player, and throws
 * its pincers as homing projectiles.
 *
 * Based on disassembly ObjA1 (s2.asm:75322).
 *
 * State machine:
 *   Routine 0 (INIT): Set velocity, radius, transition to WALKING
 *   Routine 2 (WALKING): Walk, check floor, detect player for throw
 *   Routine 4 (EDGE_WAIT): Wait at platform edge, then reverse direction
 *   Routine 6 (THROW_WINDUP): Raise arms (frame 3), wait 8 frames
 *   Routine 8 (AFTER_THROW): Display until off-screen (frame 4, no claws)
 */
public class SlicerBadnikInstance extends AbstractBadnikInstance {
    // From ObjA1_SubObjData: collision_flags = 6 (enemy, size index 6)
    private static final int COLLISION_SIZE_INDEX = 0x06;

    // From disassembly: move.w #-$40,d0 (walking speed)
    private static final int WALK_SPEED = 0x40;

    // From disassembly: move.b #$10,y_radius(a0) / move.b #$10,x_radius(a0)
    private static final int Y_RADIUS = 0x10;

    // From disassembly: move.b #$3B,objoff_2A(a0) (edge wait timer)
    private static final int EDGE_WAIT_TIMER = 0x3B;

    // From disassembly: move.b #8,objoff_2A(a0) (throw windup timer)
    private static final int THROW_WINDUP_TIMER = 8;

    // Detection range: addi.w #$80,d2 / cmpi.w #$100,d2 → horizontal +-$80
    private static final int DETECT_RANGE_X = 0x80;
    // Detection range: addi.w #$40,d3 / cmpi.w #$80,d3 → vertical +-$40
    private static final int DETECT_RANGE_Y = 0x40;

    // Floor snap thresholds from disassembly:
    // cmpi.w #-8,d1 / blt.s loc_38444 / cmpi.w #$C,d1 / bge.s loc_38444
    private static final int FLOOR_MIN_DIST = -8;
    private static final int FLOOR_MAX_DIST = 0x0C;

    // Animation: Ani_objA1 = {$13, 0, 2, $FF} → frames 0,2 at speed $13 (20 ticks)
    private static final int WALK_ANIM_SPEED = 0x13 + 1; // Duration = value + 1

    // Pincer spawn offsets from ObjA1_Pincer_Offsets:
    // Pincer 0: x=+6, y=0 (relative to body, before x_flip adjustment)
    // Pincer 1: x=-$10, y=0
    private static final int PINCER_0_X_OFFSET = 6;
    private static final int PINCER_1_X_OFFSET = -0x10;

    // Pincer initial x_vel from disassembly: move.w #-$200,d0
    private static final int PINCER_INIT_X_VEL = 0x200;

    // Pincer homing timer from disassembly: move.w #$78,objoff_2A(a1)
    private static final int PINCER_HOMING_TIMER = 0x78;

    private enum State {
        WALKING,    // Routine 2: walk + detect player
        EDGE_WAIT,  // Routine 4: pause at edge, then reverse
        THROW_WINDUP, // Routine 6: raise arms, preparing to throw
        AFTER_THROW   // Routine 8: just display (MarkObjGone)
    }

    private State state;
    private int timer;
    private int xSubpixel; // Fractional X position for subpixel movement
    private int ySubpixel; // Fractional Y position for subpixel movement
    private int walkAnimTimer;
    private boolean walkAnimToggle; // Toggles between frame 0 and frame 2

    public SlicerBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Slicer");
        this.currentX = spawn.x();
        this.currentY = spawn.y();

        // ROM: move.w #-$40,d0 / btst #render_flags.x_flip / beq.s + / neg.w d0
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlip;
        this.xVelocity = facingLeft ? -WALK_SPEED : WALK_SPEED;

        this.state = State.WALKING;
        this.timer = 0;
        this.walkAnimTimer = WALK_ANIM_SPEED;
        this.walkAnimToggle = false;
        this.animFrame = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case WALKING -> updateWalking(player);
            case EDGE_WAIT -> updateEdgeWait();
            case THROW_WINDUP -> updateThrowWindup();
            case AFTER_THROW -> { /* Just display - MarkObjGone equivalent */ }
        }
    }

    /**
     * Routine 2 (ObjA1_Main): Walk, check for player, check floor.
     *
     * If on-screen and player is facing same direction and within detection range,
     * transition to THROW_WINDUP. Otherwise, move and snap to floor. If no floor
     * found within [-8, 12], transition to EDGE_WAIT.
     */
    private void updateWalking(AbstractPlayableSprite player) {
        // ROM: _btst #render_flags.on_screen,render_flags(a0) / _beq.s loc_3841C
        // isOnScreenX() matches ROM's on_screen render flag (camera X bounds check via MarkObjGone)
        if (isOnScreenX() && player != null) {
            // ROM: bsr.w Obj_GetOrientationToPlayer
            // Returns d0 = 0 if player is right, 2 if player is left
            // Returns d1 = 0 if player is below, 2 if player is above
            // d2 = x distance (obj - player), d3 = y distance (obj - player)
            int dx = currentX - player.getCentreX();
            int dy = currentY - player.getCentreY();

            // Obj_GetOrientationToPlayer: d0 = 0 if dx >= 0 (player LEFT), 2 if dx < 0 (player RIGHT)
            // ObjA1_Main: if x_flip set (facing right), subq.w #2,d0
            // tst.w d0 / bne.s → only throw if d0 == 0 (player is in front)
            int orientation = dx >= 0 ? 0 : 2;
            if (!facingLeft) {
                orientation -= 2;
            }

            // ROM: tst.w d0 / bne.s loc_3841C → only proceed if d0 == 0
            if (orientation == 0) {
                // ROM: addi.w #$80,d2 / cmpi.w #$100,d2 → |dx| < $80
                int absDx = Math.abs(dx);
                // ROM: addi.w #$40,d3 / cmpi.w #$80,d3 → |dy| < $40
                int absDy = Math.abs(dy);

                if (absDx < DETECT_RANGE_X && absDy < DETECT_RANGE_Y) {
                    // Player detected - transition to throw
                    // ROM: addq.b #4,routine(a0) → routine 6 (THROW_WINDUP)
                    state = State.THROW_WINDUP;
                    animFrame = 3; // Arms raised
                    timer = THROW_WINDUP_TIMER;
                    return;
                }
            }
        }

        // ObjectMove: apply velocity to position using 16.8 fixed-point
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += yVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;

        // ROM: jsr (ObjCheckFloorDist).l
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);

        if (floor.foundSurface() && floor.distance() >= FLOOR_MIN_DIST && floor.distance() < FLOOR_MAX_DIST) {
            // Snap to floor: add.w d1,y_pos(a0)
            currentY += floor.distance();
        } else {
            // No valid floor - go to edge wait state
            // ROM: addq.b #2,routine(a0) → routine 4 (EDGE_WAIT)
            state = State.EDGE_WAIT;
            timer = EDGE_WAIT_TIMER;
            return;
        }

        // Walking animation: Ani_objA1 = {$13, 0, 2, $FF}
        walkAnimTimer--;
        if (walkAnimTimer <= 0) {
            walkAnimTimer = WALK_ANIM_SPEED;
            walkAnimToggle = !walkAnimToggle;
        }
        animFrame = walkAnimToggle ? 2 : 0;
    }

    /**
     * Routine 4 (loc_38466): Count down timer, then reverse direction.
     */
    private void updateEdgeWait() {
        timer--;
        if (timer < 0) {
            // ROM: subq.b #2,routine(a0) → back to routine 2 (WALKING)
            state = State.WALKING;
            // ROM: neg.w x_vel(a0)
            xVelocity = -xVelocity;
            // ROM: bchg #status.npc.x_flip,status(a0)
            facingLeft = !facingLeft;
        }
    }

    /**
     * Routine 6 (loc_38482): Wait with arms raised, then spawn pincers.
     */
    private void updateThrowWindup() {
        timer--;
        if (timer < 0) {
            // ROM: addq.b #2,routine(a0) → routine 8 (AFTER_THROW)
            state = State.AFTER_THROW;
            animFrame = 4; // Body only, no claws
            spawnPincers();
        }
    }

    /**
     * Spawns 2 pincer projectiles (ObjA1_LoadPincers, s2.asm:75492).
     * Each pincer is an independent homing projectile (ObjA2).
     */
    private void spawnPincers() {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) return;

        int[][] offsets = {
            { PINCER_0_X_OFFSET, 0 },
            { PINCER_1_X_OFFSET, 0 }
        };

        for (int[] offset : offsets) {
            int xOff = offset[0];
            // ROM: btst #render_flags.x_flip / beq.s + / neg.w d0
            if (!facingLeft) {
                xOff = -xOff;
            }
            int pincerX = currentX + xOff;
            int pincerY = currentY + offset[1];

            // ROM: move.w #-$200,d0 / btst #render_flags.x_flip / beq.s + / neg.w d0
            int pincerXVel = facingLeft ? -PINCER_INIT_X_VEL : PINCER_INIT_X_VEL;

            SlicerPincerInstance pincer = new SlicerPincerInstance(
                    spawn, this, pincerX, pincerY, pincerXVel, !facingLeft, PINCER_HOMING_TIMER);
            objectManager.addDynamicObject(pincer);
        }
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // Animation state is set directly in state machine methods
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        // From ObjA1_SubObjData: priority = 5
        return RenderPriority.clamp(5);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) return;

        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SLICER);
        if (renderer == null || !renderer.isReady()) return;

        // Sprite art faces left by default; flip when facing right
        renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        super.appendDebugRenderCommands(ctx);
        String stateLabel = "Slicer " + state + " f" + animFrame + " t" + timer;
        ctx.drawWorldLabel(currentX, currentY, -12, stateLabel, Color.YELLOW);
    }
}
