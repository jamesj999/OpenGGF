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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CPZ Boss Pipe - Extending pipe mechanism that sucks up liquid.
 * ROM Reference: s2.asm Obj5D (ROUTINE_PIPE, ROUTINE_PIPE_PUMP, ROUTINE_PIPE_RETRACT)
 * Extends down from the boss, pumps, then retracts.
 */
public class CPZBossPipe extends AbstractObjectInstance {

    private static final int SUB_WAIT = 0;
    private static final int SUB_EXTEND = 2;
    private static final int SUB_PUMP_INIT = 0;
    private static final int SUB_PUMP_ANIMATE = 2;
    private static final int SUB_PUMP_END = 4;
    private static final int SUB_RETRACT = 0;

    private static final int ROUTINE_PIPE = 0;
    private static final int ROUTINE_PUMP = 1;
    private static final int ROUTINE_RETRACT = 2;
    private static final int ROUTINE_SEGMENT = 3;

    private static final int PIPE_SEGMENT_COUNT = 0x0C;

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;

    private int x;
    private int y;
    private int renderFlags;
    private int routine;
    private int routineSecondary;
    private int anim;
    private int mappingFrame;
    private int yOffset;
    private int pipeSegments;
    private int timer;
    private int timer3;
    private boolean retractFlag;

    private final List<CPZBossPipeSegment> segments;
    private ObjectAnimationState animationState;

    public CPZBossPipe(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss) {
        super(spawn, "CPZ Boss Pipe");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.routine = ROUTINE_PIPE;
        this.routineSecondary = SUB_WAIT;
        this.anim = 1;
        this.mappingFrame = 0;
        this.yOffset = 0;
        this.pipeSegments = PIPE_SEGMENT_COUNT;
        this.timer = 0;
        this.timer3 = 2;
        this.retractFlag = false;
        this.segments = new ArrayList<>();
        this.animationState = new ObjectAnimationState(CPZBossAnimations.getDripperAnimations(), anim, mappingFrame);
        animate();  // Initialize mappingFrame to correct first frame for this anim
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        switch (routine) {
            case ROUTINE_PIPE -> updatePipe();
            case ROUTINE_PUMP -> updatePump();
            case ROUTINE_RETRACT -> updateRetract();
            case ROUTINE_SEGMENT -> updateSegment();
        }
    }

    private void updatePipe() {
        switch (routineSecondary) {
            case SUB_WAIT -> updatePipeWait();
            case SUB_EXTEND -> updatePipeExtend();
        }
    }

    private void updatePipeWait() {
        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (!mainBoss.isPipeActive()) {
            return;
        }

        x = mainBoss.getX();
        y = mainBoss.getY() + 0x18;
        renderFlags = mainBoss.getRenderFlags();
        pipeSegments = PIPE_SEGMENT_COUNT;
        routineSecondary = SUB_EXTEND;
        pipeSegments--;
        int segmentIndex = (PIPE_SEGMENT_COUNT - 1) - pipeSegments;
        yOffset = segmentIndex * 8;
        anim = 1;
        updateSegment();
    }

    private void updatePipeExtend() {
        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (levelManager == null || levelManager.getObjectManager() == null) {
            updateSegment();
            return;
        }

        int nextSegments = pipeSegments - 1;
        if (nextSegments < 0) {
            routineSecondary = SUB_PUMP_INIT;
            routine = ROUTINE_PUMP;
            updateSegment();
            return;
        }

        int segmentIndex = (PIPE_SEGMENT_COUNT - 1) - nextSegments;
        int offset = segmentIndex * 8;
        spawnPipeSegment(offset);
        pipeSegments = nextSegments;
        updateSegment();
    }

    private void updatePump() {
        switch (routineSecondary) {
            case SUB_PUMP_INIT -> updatePumpInit();
            case SUB_PUMP_ANIMATE -> updatePumpAnimate();
            case SUB_PUMP_END -> updatePumpEnd();
        }
    }

    private void updatePumpInit() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            updateSegment();
            return;
        }

        spawnPumpHead();
        spawnDripper();
        routine = ROUTINE_SEGMENT;
        routineSecondary = 0;
        anim = 1;
        updateSegment();
    }

    private void updatePumpAnimate() {
        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (mainBoss.isBossDefeated()) {
            setDestroyed(true);
            return;
        }

        x = mainBoss.getX();
        y = mainBoss.getY();
        renderFlags = mainBoss.getRenderFlags();

        timer--;
        if (timer == 0) {
            timer = 0x12;
            yOffset -= 8;
            if (yOffset < 0) {
                timer = 6;
                routineSecondary = SUB_PUMP_END;
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
            routineSecondary = SUB_PUMP_ANIMATE;
            yOffset = 0x58;
            animate();  // Sync mappingFrame with new anim before returning
            return;
        }

        routine = ROUTINE_RETRACT;
        yOffset = 0x58;
    }

    private void updateRetract() {
        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (!retractFlag) {
            // Signal segments to retract one by one
            for (int i = segments.size() - 1; i >= 0; i--) {
                CPZBossPipeSegment seg = segments.get(i);
                if (!seg.isDestroyed() && !seg.isRetracting()) {
                    seg.startRetract();
                    yOffset -= 8;
                    if (yOffset <= 0) {
                        retractFlag = true;
                    }
                    break;
                }
            }
        }

        if (retractFlag && yOffset <= 0) {
            setDestroyed(true);
            mainBoss.onPipeComplete();
        }

        updateSegment();
    }

    private void updateSegment() {
        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (mainBoss.isBossDefeated()) {
            // Convert to falling part
            spawnFallingPart();
            setDestroyed(true);
            return;
        }

        x = mainBoss.getX();
        y = mainBoss.getY();
        renderFlags = mainBoss.getRenderFlags();

        if (routineSecondary == 4) {
            y += 0x18;
        }

        y += yOffset;
        anim = 1;
        animate();
    }

    private void spawnPipeSegment(int offset) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn segSpawn = new ObjectSpawn(x, y, Sonic2ObjectIds.CPZ_BOSS, 0, renderFlags, false, 0);
        CPZBossPipeSegment segment = new CPZBossPipeSegment(segSpawn, levelManager, mainBoss, this, offset);
        levelManager.getObjectManager().addDynamicObject(segment);
        segments.add(segment);
    }

    private void spawnPumpHead() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn pumpSpawn = new ObjectSpawn(x, y, Sonic2ObjectIds.CPZ_BOSS, 0, renderFlags, false, 0);
        CPZBossPipePump pump = new CPZBossPipePump(pumpSpawn, levelManager, mainBoss, this);
        levelManager.getObjectManager().addDynamicObject(pump);
    }

    private void spawnDripper() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn dripperSpawn = new ObjectSpawn(x, y, Sonic2ObjectIds.CPZ_BOSS, 0, renderFlags, false, 0);
        CPZBossDripper dripper = new CPZBossDripper(dripperSpawn, levelManager, mainBoss, this);
        levelManager.getObjectManager().addDynamicObject(dripper);
    }

    private void spawnFallingPart() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        int xVel = randomPipeVelocity();
        ObjectSpawn partSpawn = new ObjectSpawn(x, y + yOffset, Sonic2ObjectIds.CPZ_BOSS, 0, renderFlags, false, 0);
        CPZBossFallingPart part = new CPZBossFallingPart(partSpawn, levelManager, 1, xVel);
        levelManager.getObjectManager().addDynamicObject(part);
    }

    private int randomPipeVelocity() {
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int result = (short) (random >> 8);
        return result >> 6;
    }

    private void animate() {
        if (animationState == null) {
            return;
        }
        animationState.setAnimId(anim);
        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    public int getPipeX() {
        return x;
    }

    public int getPipeY() {
        return y;
    }

    public int getPipeYOffset() {
        return yOffset;
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
        renderer.drawFrameIndex(mappingFrame, x, y + yOffset, flipped, false);
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
