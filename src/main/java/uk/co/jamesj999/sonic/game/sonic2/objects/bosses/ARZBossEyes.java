package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ARZ Boss Eyes - Animated decoration showing bulging eyes.
 * ROM Reference: s2.asm Obj89 (subtype 8)
 * Simple timed object that displays for ~40 frames then disappears.
 */
public class ARZBossEyes extends AbstractObjectInstance {

    private static final int EYES_DURATION = 0x28; // 40 frames
    private static final int EYES_MAPPING_FRAME = 2;

    private final LevelManager levelManager;

    private int x;
    private int y;
    private int renderFlags;
    private int eyesTimer;
    private int mappingFrame;

    public ARZBossEyes(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "ARZ Boss Eyes");
        this.levelManager = levelManager;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.eyesTimer = EYES_DURATION;
        this.mappingFrame = EYES_MAPPING_FRAME;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (eyesTimer <= 0) {
            setDestroyed(true);
            return;
        }
        eyesTimer--;
        mappingFrame = EYES_MAPPING_FRAME;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_PARTS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return 2;
    }
}
