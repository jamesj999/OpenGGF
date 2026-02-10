package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Newtron Missile - Projectile fired by Type 1 (green) Newtron.
 * <p>
 * Reuses the Buzz Bomber missile object (id_Missile = 0x23) with obSubtype=1,
 * which routes to Msl_FromNewt (routine 8) in the disassembly.
 * <p>
 * Based on docs/s1disasm/_incObj/23 Buzz Bomber Missile.asm, Msl_FromNewt routine.
 * <p>
 * Unlike Buzz Bomber missiles, Newtron missiles:
 * <ul>
 *   <li>Skip the flare countdown/animation phase entirely</li>
 *   <li>Enable collision (obColType=$87) immediately</li>
 *   <li>Use animation 1 (active ball) from the start</li>
 *   <li>Delete based on render bit (off-screen check) rather than collision flag</li>
 *   <li>Have no Y velocity (fly horizontally only)</li>
 * </ul>
 */
public class Sonic1NewtronMissileInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // Collision: obColType = $87 -> category HURT ($80), size index 7
    // Size 7: width=$06 (6px), height=$06 (6px)
    private static final int COLLISION_SIZE_INDEX = 0x07;

    // Active missile animation: speed=1 means each frame shows for 2 game frames
    // From Ani_Missile animation 1: dc.b 1, 2, 3, afEnd
    private static final int ACTIVE_ANIM_SPEED = 2;

    private int currentX;
    private int currentY;
    private final int xVelocity;
    private int xSubpixel;
    private final boolean facingLeft;

    private int animTimer;
    private int animFrame;

    /**
     * Creates a missile spawned by a Type 1 Newtron.
     *
     * @param x         Starting X position (Newtron X + offset)
     * @param y         Starting Y position (Newtron Y - 8)
     * @param xVel      X velocity in subpixels ($200 or -$200)
     * @param facingLeft Direction the parent Newtron was facing
     */
    public Sonic1NewtronMissileInstance(int x, int y, int xVel, boolean facingLeft) {
        super(new ObjectSpawn(x, y, 0x23, 1, 0, false, 0), "NewtronMissile");
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.xSubpixel = 0;
        this.facingLeft = facingLeft;
        this.animTimer = 0;
        this.animFrame = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Msl_FromNewt: tst.b obRender(a0) / bpl.s Msl_Delete
        // Check if still on-screen; delete if not
        if (!isOnScreen(32)) {
            setDestroyed(true);
            return;
        }

        // SpeedToPos: apply horizontal velocity with subpixel precision
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += xVelocity;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        // Animate: alternate frames 2-3 (Ball1/Ball2) at speed 1
        animTimer++;
        if (animTimer >= ACTIVE_ANIM_SPEED) {
            animTimer = 0;
            animFrame = (animFrame == 0) ? 1 : 0;
        }
    }

    @Override
    public int getCollisionFlags() {
        // HURT category ($80) + size index 7 - enabled immediately
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x23, 1, 0, false, 0);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3); // obPriority = 3
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        // Newtron missile reuses Buzz Bomber missile art with animation 1 (active ball)
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BUZZ_BOMBER_MISSILE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Active missile frames: 2 + animFrame (Ball1=2, Ball2=3 in missile sprite sheet)
        int renderedFrame = 2 + animFrame;
        renderer.drawFrameIndex(renderedFrame, currentX, currentY, !facingLeft, false);
    }
}
