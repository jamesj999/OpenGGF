package com.openggf.game.sonic2.objects.bosses;

import com.openggf.level.objects.ObjectAnimationState;
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
 * CPZ Boss Container Floor - Floor/bottom of the container.
 * ROM Reference: s2.asm Obj5D (ROUTINE_CONTAINER routineSecondary 4/8)
 * Follows container position and shows floor animations.
 */
public class CPZBossContainerFloor extends AbstractObjectInstance {

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;
    private final CPZBossContainer container;
    private final boolean isFloor2;

    private int x;
    private int y;
    private int renderFlags;
    private int anim;
    private int mappingFrame;
    private int timer2;

    private ObjectAnimationState animationState;

    public CPZBossContainerFloor(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss,
                                  CPZBossContainer container, boolean isFloor2) {
        super(spawn, "CPZ Boss Floor");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.container = container;
        this.isFloor2 = isFloor2;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.anim = isFloor2 ? 0x0B : 9;
        this.mappingFrame = 0;
        this.timer2 = isFloor2 ? 0x24 : 0;
        this.animationState = new ObjectAnimationState(CPZBossAnimations.getDripperAnimations(), anim, mappingFrame);
        animate();  // Initialize mappingFrame to correct first frame for this anim
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

        if (isFloor2) {
            updateFloor2();
        } else {
            updateFloor();
        }
    }

    private void updateFloor() {
        if (container == null || container.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        // Check if dumping - switch animation
        if (mainBoss != null && mainBoss.isContainerDumping() && anim == 9) {
            anim = 0x0A;
        }

        x = container.getContainerX();
        y = container.getContainerY();
        renderFlags = container.getSpawn().renderFlags();
        animate();
    }

    private void updateFloor2() {
        timer2--;
        if (timer2 == 0) {
            setDestroyed(true);
            return;
        }

        if (container == null || container.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        x = container.getContainerX();
        y = container.getContainerY();
        renderFlags = container.getSpawn().renderFlags();
        animate();
    }

    private void animate() {
        if (animationState == null) {
            return;
        }
        animationState.setAnimId(anim);
        animationState.update();
        anim = animationState.getAnimId();  // Sync back after SWITCH transitions (0x0A -> 9)
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
        return isFloor2 ? 5 : 4;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
