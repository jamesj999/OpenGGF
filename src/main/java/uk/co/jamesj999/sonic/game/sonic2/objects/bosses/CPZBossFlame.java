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
 * CPZ Boss Flame - Jet flame decoration under the Eggpod.
 * ROM Reference: s2.asm Obj5D (ROUTINE_FLAME = 0x18)
 * Animates through flame frames while attached to the boss.
 */
public class CPZBossFlame extends AbstractObjectInstance {

    private static final int[] FLAME_FRAMES = {0, -1, 1};

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;

    private int x;
    private int y;
    private int renderFlags;
    private int mappingFrame;
    private int animFrameDuration;
    private int frameIndex;
    private int routineSecondary;

    public CPZBossFlame(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss) {
        super(spawn, "CPZ Boss Flame");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.mappingFrame = 0;
        this.animFrameDuration = 1;
        this.frameIndex = 0;
        this.routineSecondary = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (mappingFrame < 0) {
            mappingFrame = 0;
        }

        if (mainBoss != null && mainBoss.isBossDefeated()) {
            if (mainBoss.isInRetreatPhase()) {
                routineSecondary += 2;
            }
            return;
        }

        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        x = mainBoss.getX();
        y = mainBoss.getY();
        renderFlags = mainBoss.getRenderFlags();

        animFrameDuration--;
        if (animFrameDuration < 0) {
            animFrameDuration = 1;
            frameIndex++;
            if (frameIndex > 2) {
                frameIndex = 0;
            }
            mappingFrame = FLAME_FRAMES[frameIndex];
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_JETS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (mappingFrame < 0) {
            return;
        }

        boolean flipped = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, flipped, false);
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
        return 3;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
