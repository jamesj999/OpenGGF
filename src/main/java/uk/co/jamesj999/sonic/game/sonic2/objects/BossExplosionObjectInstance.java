package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Boss Explosion (Obj58).
 * Uses ArtNem_FieryExplosion with mappings from Obj58_MapUnc_2D50A.
 */
public class BossExplosionObjectInstance extends AbstractObjectInstance {
    private static final int FRAME_DELAY = 7;
    private static final int LAST_FRAME = 6;

    private final ObjectRenderManager renderManager;
    private int mappingFrame;
    private int frameTimer;
    private boolean initialized;

    public BossExplosionObjectInstance(int x, int y, ObjectRenderManager renderManager) {
        super(new ObjectSpawn(x, y, Sonic2ObjectIds.BOSS_EXPLOSION, 0, 0, false, 0), "Boss Explosion");
        this.renderManager = renderManager;
        this.mappingFrame = 0;
        this.frameTimer = FRAME_DELAY;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!initialized) {
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_BOSS_EXPLOSION);
            initialized = true;
        }
        frameTimer--;
        if (frameTimer >= 0) {
            return;
        }
        frameTimer = FRAME_DELAY;
        mappingFrame++;
        if (mappingFrame > LAST_FRAME) {
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getBossExplosionRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
    }
}
