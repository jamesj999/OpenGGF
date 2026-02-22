package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.ObjectAnimationState;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * CPZ Boss Container Extend - Extending liquid part of the container.
 * ROM Reference: s2.asm Obj5D (ROUTINE_CONTAINER routineSecondary 6)
 * Extends from container and eventually becomes gunk.
 */
public class CPZBossContainerExtend extends AbstractObjectInstance {

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;
    private final CPZBossContainer container;

    private int x;
    private int y;
    private int renderFlags;
    private int anim;
    private int mappingFrame;

    private ObjectAnimationState animationState;

    public CPZBossContainerExtend(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss,
                                   CPZBossContainer container) {
        super(spawn, "CPZ Boss Extend");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.container = container;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.anim = 0;
        this.mappingFrame = -1; // Don't render until animation starts
        this.animationState = new ObjectAnimationState(CPZBossAnimations.getDripperAnimations(), anim, mappingFrame);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (mainBoss != null && mainBoss.isBossDefeated()) {
            setDestroyed(true);
            return;
        }

        if (container == null || container.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        // Check if should become gunk
        if (mainBoss != null && mainBoss.shouldSpawnGunk()) {
            mainBoss.clearSpawnGunkFlag();
            spawnGunk();
            setDestroyed(true);
            return;
        }

        // ROM: bclr #1,Obj5D_status2(a1) / bne.s + / tst.b anim(a0) / bne.s Floor_End / rts
        // When ACTION1 is NOT set:
        //   - If anim == 0: return without rendering (wait state)
        //   - If anim != 0: render but DON'T increment
        // When ACTION1 IS set:
        //   - Clear ACTION1, set anim to 0x0B if it was 0, then increment
        if (!mainBoss.shouldAdvanceExtend()) {
            if (anim == 0) {
                // Wait state - don't render
                mappingFrame = -1;
                return;
            }
            // anim != 0 but ACTION1 not set - render but don't increment
            updatePosition();
            return;
        }

        // ACTION1 was set - clear it and advance animation
        mainBoss.clearAdvanceExtendFlag();
        if (anim == 0) {
            anim = 0x0B;
        }
        anim += 1;
        if (anim >= 0x17) {
            mainBoss.onExtendComplete();
        }
        updatePosition();
    }

    private void updatePosition() {
        if (container != null) {
            x = container.getContainerX();
            y = container.getContainerY();
            renderFlags = container.getSpawn().renderFlags();
        }
        animate();
    }

    private void spawnGunk() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn gunkSpawn = new ObjectSpawn(x, y, Sonic2ObjectIds.CPZ_BOSS, 0, renderFlags, false, 0);
        CPZBossGunk gunk = new CPZBossGunk(gunkSpawn, levelManager, mainBoss, false);
        levelManager.getObjectManager().addDynamicObject(gunk);
    }

    private void animate() {
        if (animationState == null) {
            return;
        }
        animationState.setAnimId(anim);
        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_PARTS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (mappingFrame < 0) {
            return;
        }

        boolean flipped = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, flipped, false, 3);
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
        return 5;  // Render behind container body (bucket 4) so liquid appears inside
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
