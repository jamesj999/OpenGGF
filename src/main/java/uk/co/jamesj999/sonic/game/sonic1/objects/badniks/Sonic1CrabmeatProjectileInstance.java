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
 * Crabmeat Projectile (energy ball) - Fired by Crabmeat in opposite directions.
 * Uses shared object ID 0x1F with routine 6/8 in the disassembly.
 * <p>
 * Based on docs/s1disasm/_incObj/1F Crabmeat.asm (Crab_BallMain / Crab_BallMove).
 * <p>
 * Behavior:
 * <ul>
 *   <li>Launched with horizontal velocity (±$100) and upward velocity (-$400)</li>
 *   <li>Gravity applied each frame via ObjectFall ($38 subpixels/frame²)</li>
 *   <li>Alternates between ball animation frames 5 and 6 at speed 1</li>
 *   <li>Collision type $87: HURT category + size index 7</li>
 *   <li>Deleted when falling below level bottom boundary + $E0</li>
 * </ul>
 */
public class Sonic1CrabmeatProjectileInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // Collision: obColType = $87 -> category HURT ($80), size index 7
    // Size 7: width=$06 (6px), height=$06 (6px)
    private static final int COLLISION_SIZE_INDEX = 0x07;

    // Standard Mega Drive gravity: $38 subpixels/frame² (ObjectFall)
    private static final int GRAVITY = 0x38;

    // Animation: speed 1 = each frame shows for 2 game frames
    private static final int ANIM_SPEED = 2;

    // Mapping frames for projectile: ball1=5, ball2=6
    private static final int BALL_FRAME_1 = 5;
    private static final int BALL_FRAME_2 = 6;

    // Level bottom margin for deletion: $E0 (224 pixels)
    private static final int BOTTOM_MARGIN = 0xE0;

    private int currentX;
    private int currentY;
    private final int xVelocity;
    private int yVelocity;
    private int xSubpixel;
    private int ySubpixel;
    private final Sonic1CrabmeatBadnikInstance parent;
    private final LevelManager levelManager;

    private int animTimer;
    private int renderedFrame;

    /**
     * Creates a Crabmeat projectile.
     *
     * @param x    Starting X position
     * @param y    Starting Y position
     * @param xVel X velocity in subpixels (±$100)
     * @param yVel Initial Y velocity in subpixels (-$400, upward)
     * @param parent Reference to parent Crabmeat
     * @param levelManager Level manager reference
     */
    public Sonic1CrabmeatProjectileInstance(int x, int y, int xVel, int yVel,
            Sonic1CrabmeatBadnikInstance parent, LevelManager levelManager) {
        super(new ObjectSpawn(x, y, 0x1F, 0, 0, false, 0), "CrabmeatBall");
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.yVelocity = yVel;
        this.xSubpixel = 0;
        this.ySubpixel = 0;
        this.parent = parent;
        this.levelManager = levelManager;
        this.animTimer = 0;
        this.renderedFrame = BALL_FRAME_1;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Apply gravity (ObjectFall: addi.w #$38,obVelY(a0))
        yVelocity += GRAVITY;

        // SpeedToPos: apply velocity with subpixel precision
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += yVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;

        // Check if below level bottom boundary + $E0
        if (!isOnScreen(BOTTOM_MARGIN)) {
            setDestroyed(true);
            return;
        }

        // Animate: alternate between ball frame 1 and 2 at speed 1
        animTimer++;
        if (animTimer >= ANIM_SPEED) {
            animTimer = 0;
            renderedFrame = (renderedFrame == BALL_FRAME_1) ? BALL_FRAME_2 : BALL_FRAME_1;
        }
    }

    @Override
    public int getCollisionFlags() {
        // HURT category ($80) + size index 7
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x1F, 0, 0, false, 0);
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.CRABMEAT);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(renderedFrame, currentX, currentY, false, false);
    }
}
