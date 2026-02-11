package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 points popup object (Obj29).
 * <p>
 * Uses Sonic 1 point mappings loaded by {@code Sonic1ObjectArtProvider}:
 * frame 0=100, 1=200, 2=500, 3=1000, 4=10, 5=10000, 6=100000.
 * Physics matches ROM Obj29 (8.8 Y velocity with gravity and delete on non-negative velocity).
 */
public class Sonic1PointsObjectInstance extends AbstractObjectInstance {
    private static final int INITIAL_Y_VEL = -0x300;
    private static final int GRAVITY = 0x18;

    private final PatternSpriteRenderer renderer;
    private int currentX;
    private int ySubpixel;
    private int yVel;
    private int scoreFrame;

    public Sonic1PointsObjectInstance(ObjectSpawn spawn, LevelManager levelManager, int points) {
        super(spawn, "S1Points");
        this.renderer = levelManager.getObjectRenderManager().getPointsRenderer();
        this.currentX = spawn.x();
        this.ySubpixel = spawn.y() << 8;
        this.yVel = INITIAL_Y_VEL;
        setScore(points);
    }

    public void setScore(int points) {
        switch (points) {
            case 10 -> this.scoreFrame = 4;
            case 100 -> this.scoreFrame = 0;
            case 200 -> this.scoreFrame = 1;
            case 500 -> this.scoreFrame = 2;
            case 1000 -> this.scoreFrame = 3;
            case 10000 -> this.scoreFrame = 5;
            case 100000 -> this.scoreFrame = 6;
            default -> this.scoreFrame = 0;
        }
    }

    /**
     * ROM-translated callers can assign Obj29 frame index directly.
     */
    public void setScoreFrameIndex(int frameIndex) {
        this.scoreFrame = Math.max(0, frameIndex);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (yVel >= 0) {
            setDestroyed(true);
            return;
        }
        ySubpixel += yVel;
        yVel += GRAVITY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || renderer == null) {
            return;
        }
        renderer.drawFrameIndex(scoreFrame, currentX, ySubpixel >> 8, false, false);
    }

    public int getX() {
        return currentX;
    }

    public int getY() {
        return ySubpixel >> 8;
    }
}
