package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * CPZ Boss Pipe Pump - Pump head at the bottom of the extending pipe.
 * ROM Reference: s2.asm Obj5D (ROUTINE_PIPE_PUMP = 0x06)
 * Animates pumping motion as liquid is sucked up.
 */
public class CPZBossPipePump extends AbstractObjectInstance {

    private static final int SUB_ANIMATE = 2;
    private static final int SUB_END = 4;

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;
    private final CPZBossPipe parentPipe;

    private int x;
    private int y;
    private int renderFlags;
    private int routineSecondary;
    private int anim;
    private int mappingFrame;
    private int yOffset;
    private int timer;
    private int timer3;

    private ObjectAnimationState animationState;

    public CPZBossPipePump(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss,
                           CPZBossPipe parentPipe) {
        super(spawn, "CPZ Boss Pipe Pump");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.parentPipe = parentPipe;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.routineSecondary = SUB_ANIMATE;
        this.anim = 2;
        this.mappingFrame = 0;
        this.yOffset = 0x58;
        this.timer = 0x12;
        this.timer3 = 2;
        this.animationState = new ObjectAnimationState(CPZBossAnimations.getDripperAnimations(), anim, mappingFrame);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (parentPipe == null || parentPipe.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (mainBoss != null && mainBoss.isBossDefeated()) {
            setDestroyed(true);
            return;
        }

        switch (routineSecondary) {
            case SUB_ANIMATE -> updatePumpAnimate();
            case SUB_END -> updatePumpEnd();
        }
    }

    private void updatePumpAnimate() {
        x = parentPipe.getPipeX();
        y = parentPipe.getPipeY();
        renderFlags = parentPipe.getSpawn().renderFlags();

        timer--;
        if (timer == 0) {
            timer = 0x12;
            yOffset -= 8;
            if (yOffset < 0) {
                timer = 6;
                routineSecondary = SUB_END;
                return;
            }
            if (yOffset == 0) {
                anim = 3;
                timer = 0x0C;
            }
        }
        y += yOffset;
        animate();
    }

    private void updatePumpEnd() {
        timer--;
        if (timer != 0) {
            return;
        }

        timer3--;
        if (timer3 != 0) {
            anim = 2;
            timer = 0x12;
            routineSecondary = SUB_ANIMATE;
            yOffset = 0x58;
            return;
        }

        setDestroyed(true);
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
        return 4;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
