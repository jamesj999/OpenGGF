package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Lightweight gravity-affected rock fragment for the BreakObjectToPieces effect.
 * Spawned by {@link CutsceneKnucklesRockChild} when the rock breaks apart.
 *
 * Each fragment receives a scattered velocity from a ROM velocity table
 * and falls with gravity until offscreen, then deletes itself.
 */
public class AizRockFragmentChild extends AbstractObjectInstance {

    /** Standard S3K gravity in subpixels per frame. */
    private static final int GRAVITY = 0x38;

    /**
     * ROM velocity table word_2A8B0 for 12 fragments (BreakObjectToPieces).
     * Each entry: {xVel, yVel} in subpixels. 6 symmetric pairs.
     */
    static final int[][] FRAGMENT_VELOCITIES = {
            { -0x100, -0x200 }, {  0x100, -0x200 },
            {  -0xE0, -0x1C0 }, {   0xE0, -0x1C0 },
            {  -0xC0, -0x180 }, {   0xC0, -0x180 },
            {  -0xA0, -0x140 }, {   0xA0, -0x140 },
            {  -0x80, -0x100 }, {   0x80, -0x100 },
            {  -0x60,  -0xC0 }, {   0x60,  -0xC0 },
    };

    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private final int mappingFrame;

    public AizRockFragmentChild(ObjectSpawn spawn, int xVel, int yVel, int mappingFrame) {
        super(spawn, "RockFragment");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = xVel;
        this.yVel = yVel;
        this.mappingFrame = mappingFrame;
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
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        int xTotal = (xSub & 0xFF) + (xVel & 0xFF);
        currentX += (xVel >> 8) + (xTotal >> 8);
        xSub = xTotal & 0xFF;

        // MoveSprite order: move with old y_vel, then apply gravity.
        int oldYVel = yVel;
        yVel += GRAVITY;
        int yTotal = (ySub & 0xFF) + (oldYVel & 0xFF);
        currentY += (oldYVel >> 8) + (yTotal >> 8);
        ySub = yTotal & 0xFF;

        // Delete when offscreen
        if (!isOnScreen()) {
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getCorkFloorRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }
}
