package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * Cannonball (0x20) - Projectile thrown by Ball Hog (SBZ).
 * <p>
 * Based on docs/s1disasm/_incObj/20 Cannonball.asm.
 * <p>
 * The cannonball bounces off the floor and eventually explodes. It uses the
 * Ball Hog's sprite sheet (Map_Hog) with frames 4 and 5 (alternating black
 * and red cannonball sprites).
 * <p>
 * Behavior:
 * <ul>
 *   <li>Falls under gravity (ObjectFall: addi.w #$38,obVelY)</li>
 *   <li>When hitting the floor, bounces with obVelY = -$300</li>
 *   <li>Floor angle (d3) controls X velocity direction reversal on slopes</li>
 *   <li>Explosion timer = subtype * 60 frames</li>
 *   <li>When timer expires, changes to ExplosionBomb (object $3F, sfx_Bomb)</li>
 *   <li>Animation alternates frames 4/5 every 6 ticks (obTimeFrame = 5, counts down)</li>
 *   <li>Deleted if Y > level bottom boundary + $E0</li>
 * </ul>
 * <p>
 * Collision: obColType = $87 (HURT category $80, size index $07)
 */
public class Sonic1CannonballInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // --- Collision ---
    // From disassembly: move.b #$87,obColType(a0)
    // $80 = HURT category, $07 = size index
    private static final int COLLISION_SIZE_INDEX = 0x07;

    // --- Physics ---
    // From ObjectFall: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;
    // From disassembly: move.w #-$300,obVelY(a0) - bounce velocity
    private static final int BOUNCE_VELOCITY = -0x300;

    // --- Animation ---
    // From disassembly: move.b #5,obTimeFrame(a0) (6 ticks per frame toggle)
    private static final int FRAME_DURATION = 6;
    // Cannonball uses frames 4 and 5 from Map_Hog
    private static final int BALL_FRAME_1 = 4; // Black cannonball
    private static final int BALL_FRAME_2 = 5; // Red cannonball

    // --- Render ---
    // From disassembly: move.b #3,obPriority(a0)
    private static final int RENDER_PRIORITY = 3;

    // --- Off-screen deletion offset ---
    // From disassembly: addi.w #$E0,d0 (added to v_limitbtm2)
    private static final int OFFSCREEN_Y_MARGIN = 0xE0;

    // --- Instance state ---
    private final LevelManager levelManager;
    private int currentX;
    private int currentY;
    private int xVelocity;
    private int yVelocity;
    private int xSubpixel;
    private int ySubpixel;
    private int explosionTimer; // cbal_time (objoff_30)
    private int animTimer;      // obTimeFrame countdown
    private int currentFrame;   // obFrame: toggles between 4 and 5
    private boolean destroyed;

    /**
     * Creates a cannonball at the given position.
     *
     * @param x           Spawn X position (from Ball Hog + offset)
     * @param y           Spawn Y position (from Ball Hog + offset)
     * @param xVel        Initial X velocity (-$100 left, +$100 right)
     * @param subtype     Ball Hog's subtype (explosion timer = subtype * 60)
     * @param levelManager Level manager reference
     */
    public Sonic1CannonballInstance(int x, int y, int xVel, int subtype,
                                    LevelManager levelManager) {
        super(new ObjectSpawn(x, y, 0x20, subtype, 0, false, 0), "Cannonball");
        this.levelManager = levelManager;
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.yVelocity = 0;       // move.w #0,obVelY(a1)
        this.xSubpixel = 0;
        this.ySubpixel = 0;

        // cbal_time = subtype * 60 frames
        // From disassembly: moveq #0,d0 / move.b obSubtype(a0),d0 / mulu.w #60,d0
        this.explosionTimer = (subtype & 0xFF) * 60;

        // move.b #4,obFrame(a0) - start on frame 4 (black cannonball)
        this.currentFrame = BALL_FRAME_1;
        // obTimeFrame zero-init: first subq will go to -1, immediately toggling frame
        this.animTimer = 0;
        this.destroyed = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        // Cbal_Bounce routine (routine 2, but entered immediately after routine 0 setup)
        updateBounce();
    }

    /**
     * Cbal_Bounce: Main update - fall, bounce, check explosion timer, animate.
     * <pre>
     * Cbal_Bounce:
     *     jsr    (ObjectFall).l
     *     tst.w  obVelY(a0)
     *     bmi.s  Cbal_ChkExplode       ; if moving up, skip floor check
     *     jsr    (ObjFloorDist).l
     *     tst.w  d1                    ; has ball hit the floor?
     *     bpl.s  Cbal_ChkExplode       ; if not, branch
     *
     *     add.w  d1,obY(a0)
     *     move.w #-$300,obVelY(a0)     ; bounce
     *     tst.b  d3                    ; check floor angle
     *     beq.s  Cbal_ChkExplode       ; flat floor: no X change
     *     bmi.s  loc_8CA4              ; negative angle: leftward slope
     *     tst.w  obVelX(a0)
     *     bpl.s  Cbal_ChkExplode       ; moving right on right-slope: no change
     *     neg.w  obVelX(a0)            ; reverse to move right
     *     bra.s  Cbal_ChkExplode
     *
     * loc_8CA4:
     *     tst.w  obVelX(a0)
     *     bmi.s  Cbal_ChkExplode       ; moving left on left-slope: no change
     *     neg.w  obVelX(a0)            ; reverse to move left
     * </pre>
     */
    private void updateBounce() {
        // ObjectFall: X += VelX, VelY += gravity, Y += VelY (gravity applied before move)
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += xVelocity;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        // addi.w #$38,d0 / move.w d0,obVelY(a0) - gravity applied BEFORE Y movement
        yVelocity += GRAVITY;
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += yVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;

        // tst.w obVelY(a0) / bmi.s Cbal_ChkExplode (tests POST-gravity velocity)
        if (yVelocity >= 0) {
            // Only check floor when moving downward
            TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, 7);

            // tst.w d1 / bpl.s Cbal_ChkExplode
            if (floorResult.foundSurface() && floorResult.distance() < 0) {
                currentY += floorResult.distance(); // add.w d1,obY(a0)
                yVelocity = BOUNCE_VELOCITY;         // move.w #-$300,obVelY(a0)

                // Check floor angle (d3) for X velocity direction changes
                byte floorAngle = floorResult.angle();
                // tst.b d3 / beq.s Cbal_ChkExplode
                if (floorAngle != 0) {
                    if (floorAngle < 0) {
                        // bmi.s loc_8CA4: negative angle = leftward slope
                        // tst.w obVelX(a0) / bmi.s Cbal_ChkExplode
                        if (xVelocity >= 0) {
                            // Moving right on left-slope: reverse to left
                            xVelocity = -xVelocity;
                        }
                    } else {
                        // Positive angle = rightward slope
                        // tst.w obVelX(a0) / bpl.s Cbal_ChkExplode
                        if (xVelocity < 0) {
                            // Moving left on right-slope: reverse to right
                            xVelocity = -xVelocity;
                        }
                    }
                }
            }
        }

        // Cbal_ChkExplode: decrement explosion timer
        explosionTimer--;
        if (explosionTimer < 0) {
            // Timer expired: explode
            explode();
            return;
        }

        // Cbal_Animate: frame toggle animation
        animTimer--;
        if (animTimer < 0) {
            // move.b #5,obTimeFrame(a0) - reset timer
            animTimer = FRAME_DURATION - 1;
            // bchg #0,obFrame(a0) - toggle bit 0 of frame (4 <-> 5)
            currentFrame = (currentFrame == BALL_FRAME_1) ? BALL_FRAME_2 : BALL_FRAME_1;
        }

        // Cbal_Display: check if below level bottom
        int levelBottom = Camera.getInstance().getMaxY() & 0xFFFF;
        int deleteThreshold = levelBottom + OFFSCREEN_Y_MARGIN;
        if (currentY > deleteThreshold) {
            // DeleteObject: gone below level
            destroyed = true;
            setDestroyed(true);
            return;
        }

        // Also check general off-screen for cleanup
        if (!isOnScreenX(256)) {
            destroyed = true;
            setDestroyed(true);
        }
    }

    /**
     * Explode the cannonball: spawn ExplosionBomb (object $3F) with bomb sound.
     * <pre>
     * Cbal_Explode:
     *     _move.b #id_ExplosionBomb,obID(a0)
     *     move.b  #0,obRoutine(a0)
     *     bra.w   ExplosionBomb
     * </pre>
     * ExplosionBomb plays sfx_Bomb ($C4) and renders explosion animation.
     */
    private void explode() {
        destroyed = true;
        setDestroyed(true);

        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        // Spawn bomb explosion (object $3F)
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                0x3F, currentX, currentY,
                levelManager.getObjectRenderManager());
        objectManager.addDynamicObject(explosion);

        // sfx_Bomb = $C4 = BOSS_EXPLOSION
        AudioManager.getInstance().playSfx(Sonic1Sfx.BOSS_EXPLOSION.id);
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        if (destroyed) {
            return 0;
        }
        // obColType = $87: HURT category ($80) + size index $07
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // --- Rendering ---

    @Override
    public boolean isPersistent() {
        // DisplaySprite path: persistent while alive
        return !destroyed;
    }

    @Override
    public int getPriorityBucket() {
        // obPriority = 3
        return RenderPriority.clamp(RENDER_PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        // Cannonball uses the Ball Hog sprite sheet (Map_Hog)
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BALL_HOG);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Cannonball does not flip - no obStatus-based flipping
        renderer.drawFrameIndex(currentFrame, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Red hitbox (cannonball hurts Sonic)
        ctx.drawRect(currentX, currentY, 7, 7, 1f, 0f, 0f);

        // Velocity arrow
        if (xVelocity != 0 || yVelocity != 0) {
            int endX = currentX + (xVelocity >> 5);
            int endY = currentY + (yVelocity >> 5);
            ctx.drawArrow(currentX, currentY, endX, endY, 1f, 0.5f, 0f);
        }

        String label = "Ball t" + explosionTimer + " f" + currentFrame;
        ctx.drawWorldLabel(currentX, currentY, -2, label, Color.RED);
    }

    // --- Position accessors ---

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x20, spawn.subtype(), 0, false, 0);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }
}
