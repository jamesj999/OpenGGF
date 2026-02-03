package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Spiker Drill (0x93) - drill projectile thrown by Spiker in HTZ.
 * Moves vertically at a constant speed and flips horizontally each frame.
 */
public class SpikerDrillObjectInstance extends AbstractObjectInstance implements TouchResponseProvider {
    private static final int COLLISION_SIZE_INDEX = 0x12; // From Obj92_SubObjData
    private static final int Y_VELOCITY = 0x200; // 2 pixels/frame in 8.8 fixed

    private int currentX;
    private int currentY;
    private int yVelocity;
    private int ySubpixel;
    private boolean hFlip;
    private final boolean vFlip;

    public SpikerDrillObjectInstance(ObjectSpawn spawn, int x, int y, boolean xFlip, boolean yFlip) {
        super(spawn, "SpikerDrill");
        this.currentX = x;
        this.currentY = y;
        this.hFlip = xFlip;
        this.vFlip = yFlip;
        // ROM: if y_flip set, velocity stays +2 (down). Otherwise it's negated.
        this.yVelocity = yFlip ? Y_VELOCITY : -Y_VELOCITY;
        this.ySubpixel = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!isOnScreen()) {
            setDestroyed(true);
            return;
        }

        hFlip = !hFlip; // bchg #render_flags.x_flip, render_flags(a0)

        int yPos32 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos32 += yVelocity;
        currentY = yPos32 >> 8;
        ySubpixel = yPos32 & 0xFF;
    }

    @Override
    public int getCollisionFlags() {
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
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
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SPIKER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(4, currentX, currentY, hFlip, vFlip);
    }
}
