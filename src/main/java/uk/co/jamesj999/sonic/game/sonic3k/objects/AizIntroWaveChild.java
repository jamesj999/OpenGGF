package uk.co.jamesj999.sonic.game.sonic3k.objects;

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

    private final int scrollSpeed;
    private int currentX;
    private int currentY;
    private int animFrame;
    private int animTimer;

    /**
     * @param spawn       spawn position for the wave
     * @param scrollSpeed pixels per frame to scroll left (from parent's SCROLL_SPEED)
     */
    public AizIntroWaveChild(ObjectSpawn spawn, int scrollSpeed) {
        super(spawn, "AIZIntroWave");
        this.scrollSpeed = scrollSpeed;
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

        // Scroll left by parent's scroll speed
        currentX -= scrollSpeed;

        // Animate through wave frames
        if (++animTimer >= ANIM_FRAME_DURATION) {
            animTimer = 0;
            animFrame++;
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
        renderer.drawFrameIndex(animFrame, currentX, currentY, false, false);
    }
}
