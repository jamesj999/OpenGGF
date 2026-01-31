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
 * CPZ Boss Smoke Puff - Smoke effect during retreat.
 * ROM Reference: s2.asm Obj5D (ROUTINE_SMOKE_PUFF = 0x1A)
 * Animates through smoke frames following the boss.
 */
public class CPZBossSmokePuff extends AbstractObjectInstance {

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;

    private int x;
    private int y;
    private int mappingFrame;
    private int animFrameDuration;

    public CPZBossSmokePuff(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss) {
        super(spawn, "CPZ Boss Smoke");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.x = spawn.x();
        this.y = spawn.y();
        this.mappingFrame = 0;
        this.animFrameDuration = 5;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        animFrameDuration--;
        if (animFrameDuration < 0) {
            animFrameDuration = 5;
            mappingFrame++;
            if (mappingFrame == 4) {
                mappingFrame = 0;
                if (mainBoss == null || mainBoss.isDestroyed()) {
                    setDestroyed(true);
                    return;
                }
                x = mainBoss.getX() - 0x28;
                y = mainBoss.getY() + 4;
            }
        }

        if (mainBoss != null) {
            x = mainBoss.getX() - 0x28;
            y = mainBoss.getY() + 4;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_SMOKE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (mappingFrame < 0) {
            return;
        }

        renderer.drawFrameIndex(mappingFrame, x, y, false, false);
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
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), 0, spawn.respawnTracked(), spawn.rawYWord());
    }
}
