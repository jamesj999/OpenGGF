package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * CPZ Boss Dripper - Dripping liquid effect from the pump.
 * ROM Reference: s2.asm Obj5D (ROUTINE_DRIPPER = 0x0A)
 * Shows liquid dripping animation as pump operates.
 */
public class CPZBossDripper extends AbstractObjectInstance {

    private static final int SUB_INIT = 0;
    private static final int SUB_MAIN = 2;
    private static final int SUB_END = 4;
    private final Sonic2CPZBossInstance mainBoss;
    private final CPZBossPipe parentPipe;

    private int x;
    private int y;
    private int renderFlags;
    private int routineSecondary;
    private int anim;
    private int mappingFrame;
    private int timer;
    private int timer4;

    private ObjectAnimationState animationState;

    public CPZBossDripper(ObjectSpawn spawn, Sonic2CPZBossInstance mainBoss,
                          CPZBossPipe parentPipe) {
        super(spawn, "CPZ Boss Dripper");
        this.mainBoss = mainBoss;
        this.parentPipe = parentPipe;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.routineSecondary = SUB_INIT;
        this.anim = 4;
        this.mappingFrame = -1;
        this.timer = 0x0F;
        this.timer4 = 0;
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

        switch (routineSecondary) {
            case SUB_INIT -> updateInit();
            case SUB_MAIN -> updateMain();
            case SUB_END -> updateEnd();
        }
    }

    private void updateInit() {
        routineSecondary = SUB_MAIN;
        anim = 4;
        timer = 0x0F;

        if (parentPipe != null) {
            x = parentPipe.getPipeX();
            y = parentPipe.getPipeY();
        }

        updateMain();
    }

    private void updateMain() {
        timer--;
        if (timer == 0) {
            anim = 5;
            timer = 4;
            routineSecondary = SUB_END;
            if (parentPipe != null) {
                x = parentPipe.getPipeX() - 2;
                y = parentPipe.getPipeY() - 0x24;
            }
            animate();  // Sync mappingFrame with new anim before returning
            return;
        }

        if (parentPipe == null || parentPipe.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        x = parentPipe.getPipeX();
        y = parentPipe.getPipeY();
        renderFlags = parentPipe.getSpawn().renderFlags();
        animate();
    }

    private void updateEnd() {
        timer--;
        if (timer == 0) {
            routineSecondary = SUB_INIT;

            // Signal to boss that dripper cycle complete
            if (mainBoss != null) {
                mainBoss.onDripperCycleComplete();
            }

            timer4++;
            if (timer4 >= 0x0C) {
                setDestroyed(true);
            }
            return;
        }

        if (parentPipe == null || parentPipe.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        x = parentPipe.getPipeX() - 2;
        y = parentPipe.getPipeY() - 0x24;
        if ((renderFlags & 1) != 0) {
            x += 4;
        }
        animate();
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
        ObjectRenderManager renderManager = GameServices.level() != null ? services().renderManager() : null;
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
        return 4;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
