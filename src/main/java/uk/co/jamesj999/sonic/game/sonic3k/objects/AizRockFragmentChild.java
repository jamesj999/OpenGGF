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
 *
 * ROM: BreakObjectToPieces (sonic3k.asm:45772) gives each fragment a unique
 * mapping piece from the parent's broken frame. The cork floor's frame 1 has
 * 12 pieces, one per fragment. Each fragment renders only its assigned piece
 * via {@code drawFramePieceByIndex}.
 *
 * ROM also sets high_priority on fragment art tiles (ori.w #high_priority),
 * so fragments render in front of high-priority FG tiles.
 */
public class AizRockFragmentChild extends AbstractObjectInstance {

    /**
     * Fragment-specific gravity: 0x18 subpixels/frame.
     * ROM (sonic3k.asm:58588): loc_2A5F8 uses MoveSprite2 (no gravity) then
     * {@code addi.w #$18,y_vel(a0)} — much lighter than standard S3K gravity (0x38).
     */
    private static final int GRAVITY = 0x18;

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
    private final int pieceIndex;

    /** Shared state object for SubpixelMotion calls. */
    private final SubpixelMotion.State motionState = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    public AizRockFragmentChild(ObjectSpawn spawn, int xVel, int yVel, int mappingFrame, int pieceIndex) {
        super(spawn, "RockFragment");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = xVel;
        this.yVel = yVel;
        this.mappingFrame = mappingFrame;
        this.pieceIndex = pieceIndex;
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
    public boolean isHighPriority() {
        return true; // ROM: ori.w #high_priority,art_tile(a1)
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        motionState.x = currentX; motionState.y = currentY;
        motionState.xSub = xSub;  motionState.ySub = ySub;
        motionState.xVel = xVel;  motionState.yVel = yVel;
        SubpixelMotion.moveSprite(motionState, GRAVITY);
        currentX = motionState.x; currentY = motionState.y;
        xSub = motionState.xSub;  ySub = motionState.ySub;
        yVel = motionState.yVel;

        // Delete when offscreen
        if (!isOnScreen()) {
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getCorkFloorRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFramePieceByIndex(mappingFrame, pieceIndex, currentX, currentY, false, false);
    }
}
