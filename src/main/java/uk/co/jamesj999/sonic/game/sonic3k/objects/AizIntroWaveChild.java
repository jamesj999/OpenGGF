package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Wave splash sprite for AIZ1 intro.
 * ROM: loc_678A0 (sonic3k.asm)
 *
 * Uses Map_AIZIntroWaves / ArtTile_AIZIntroSprites.
 * Spawned every 5 frames during monitoring routines (0x06-0x0B) of the
 * intro orchestrator. Init sets position, priority 0x100, width 0x10.
 * Each frame scrolls left by the parent's scroll speed and animates.
 * Self-deletes when X < 0x60.
 */
public class AizIntroWaveChild extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(AizIntroWaveChild.class.getName());

    /** X threshold below which the wave self-deletes. */
    static final int DELETE_X = 0x60;

    /** Animation frame duration in frames. */
    private static final int ANIM_FRAME_DURATION = 3;
    /** Intro wave mapping frames are 0..5 (byte_67A9B). */
    private static final int WAVE_FRAME_COUNT = 6;

    private final AizPlaneIntroInstance parent;
    private int currentX;
    private int currentY;
    private int animFrame;
    private int animTimer;

    /**
     * @param spawn  spawn position for the wave
     * @param parent parent intro object (provides live scroll speed via $40(a0))
     */
    public AizIntroWaveChild(ObjectSpawn spawn, AizPlaneIntroInstance parent) {
        super(spawn, "AIZIntroWave");
        this.parent = parent;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
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
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // ROM: subtract parent.$40 each frame (speed changes from 8 -> 12 -> 16).
        currentX -= parent.getScrollSpeed();

        // Animate through wave frames
        if (++animTimer >= ANIM_FRAME_DURATION) {
            animTimer = 0;
            animFrame = (animFrame + 1) % WAVE_FRAME_COUNT;
        }

        // Self-delete when scrolled off left side of screen
        if (currentX < DELETE_X) {
            setDestroyed(true);
        }
    }

    public int getAnimFrame() {
        return animFrame;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getIntroSpritesRenderer();
        if (renderer == null || !renderer.isReady()) return;
        // Screen-space coordinates use the ROM +128 sprite-table bias.
        int renderX = currentX;
        int renderY = currentY;
        try {
            Camera camera = Camera.getInstance();
            renderX += camera.getX() - 128;
            renderY += camera.getY() - 128;
        } catch (Exception ignored) {}
        renderer.drawFrameIndex(animFrame, renderX, renderY, false, false);
    }
}
