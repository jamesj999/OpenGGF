package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss object (0x91).
 *
 * ROM: Obj_AIZMiniboss (sonic3k.asm):
 * - loc_68A46 init
 * - loc_68A6E trigger
 * - loc_68A94 descend entry
 * - loc_68ACC/loc_68AFE attack cycle
 * - loc_68B34/loc_68B92 movement variants
 */
public class AizMinibossInstance extends AbstractBossInstance {
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_WAIT_TRIGGER = 2;
    private static final int ROUTINE_WAIT = 4;
    private static final int ROUTINE_DESCEND = 6;
    private static final int ROUTINE_SWING = 8;
    private static final int ROUTINE_BREATH = 10;
    private static final int ROUTINE_HOLD = 12;
    private static final int ROUTINE_HORIZONTAL_SWING = 14;
    private static final int ROUTINE_DEFEATED = 16;

    private static final int HIT_COUNT = 6;
    private static final int COLLISION_SIZE = 0x0F;
    private static final int TRIGGER_X = 0x10E0;
    private static final int WAIT_AFTER_TRIGGER = 3 * 60;
    private static final int DESCEND_VEL = 0x100;
    private static final int DESCEND_TIME = 0xAF;
    private static final int SWING_PREP_TIME = 20;
    private static final int FLAME_PREP_TIME = 30;
    private static final int BREATH_SWING_TIME = 0x80;
    private static final int VERTICAL_DRIFT_TIME = 0x5F;
    private static final int HOLD_TIME = 0x10;
    private static final int HORIZONTAL_TIME = 0x60;
    private static final int HORIZONTAL_RECOVERY_TIME = 0x30;
    private static final int DEFEAT_TIME = 0x90;
    private static final int INVULN_TIME = 0x20;

    private static final int FLAG_PARENT_BITS = 0x38;
    private static final int FLAG_PARENT_COUNTER = 0x39;
    private static final int PARENT_BIT_BARREL_ACTIVATE = 1 << 1;
    private static final int PARENT_BIT_ALT_VERTICAL = 1 << 2;
    private static final int PARENT_BIT_ALT_HORIZONTAL = 1 << 3;

    private static final int[] BREATH_FLAME_X_OFFSETS = {-0x64, -0x54, -0x44, -0x2C};
    private static final int[] BREATH_FLAME_Y_OFFSETS = {4, 4, 4, 3};

    private final AizMinibossSwingMotion swingMotion = new AizMinibossSwingMotion();

    private int waitTimer = -1;
    private Runnable waitCallback;
    private int defeatTimer;

    public AizMinibossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "AIZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        state.routine = ROUTINE_INIT;
        state.hitCount = HIT_COUNT;
        waitTimer = -1;
        waitCallback = null;
        defeatTimer = 0;
    }

    @Override
    protected int getInitialHitCount() {
        return HIT_COUNT;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Behavior changes are driven by the state machine and parent flags.
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_TIME;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return 1;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (state.invulnerable || state.defeated) {
            return;
        }

        state.hitCount--;
        state.invulnerabilityTimer = INVULN_TIME;
        state.invulnerable = true;
        paletteFlasher.startFlash();
        AudioManager.getInstance().playSfx(Sonic3kSfx.BOSS_HIT.id);
        onHitTaken(state.hitCount);

        if (state.hitCount <= 0) {
            state.hitCount = 0;
            state.defeated = true;
            GameServices.gameState().addScore(1000);
            onDefeatStarted();
        }
    }

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEATED;
        state.xVel = 0;
        state.yVel = 0;
        waitTimer = -1;
        waitCallback = null;
        defeatTimer = DEFEAT_TIME;
        AudioManager.getInstance().fadeOutMusic();
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT_TRIGGER -> updateWaitTrigger();
            case ROUTINE_WAIT -> updateWaitOnly();
            case ROUTINE_DESCEND -> updateMoveAndWait(false);
            case ROUTINE_SWING -> updateMoveAndWait(true);
            case ROUTINE_BREATH -> updateMoveAndWait(true);
            case ROUTINE_HOLD -> updateWaitOnly();
            case ROUTINE_HORIZONTAL_SWING -> updateMoveAndWait(true);
            case ROUTINE_DEFEATED -> updateDefeated(frameCounter);
            default -> {
            }
        }
    }

    private void updateInit() {
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(true);
        }
        loadBossPalette();
        state.routine = ROUTINE_WAIT_TRIGGER;
    }

    private void updateWaitTrigger() {
        Camera camera = Camera.getInstance();
        if (camera.getX() < TRIGGER_X) {
            return;
        }

        camera.setMinX((short) TRIGGER_X);
        camera.setMaxX((short) TRIGGER_X);
        AudioManager.getInstance().fadeOutMusic();

        state.routine = ROUTINE_WAIT;
        setWait(WAIT_AFTER_TRIGGER, this::onInitialDelayComplete);
    }

    private void onInitialDelayComplete() {
        state.routine = ROUTINE_DESCEND;
        state.yVel = DESCEND_VEL;
        setWait(DESCEND_TIME, this::onDescendComplete);

        var objectManager = levelManager.getObjectManager();
        spawnChild(new AizMinibossBodyChild(this), objectManager);
        spawnChild(new AizMinibossArmChild(this), objectManager);
        for (int i = 0; i < 3; i++) {
            spawnChild(new AizMinibossFlameBarrelChild(this, i, false), objectManager);
        }

        AudioManager.getInstance().playMusic(Sonic3kMusic.MINIBOSS.id);
    }

    private void onDescendComplete() {
        state.routine = ROUTINE_SWING;
        setCustomFlag(FLAG_PARENT_COUNTER, 3);
        setCustomFlag(FLAG_PARENT_BITS, getCustomFlag(FLAG_PARENT_BITS) | PARENT_BIT_BARREL_ACTIVATE);
        state.yVel = 0;
        swingMotion.setup1(state);
        setWait(SWING_PREP_TIME, this::onSwingPrepComplete);
    }

    private void onSwingPrepComplete() {
        setWait(FLAME_PREP_TIME, this::onFlamePrepComplete);
    }

    private void onFlamePrepComplete() {
        state.routine = ROUTINE_BREATH;
        setCustomFlag(FLAG_PARENT_COUNTER, 8);
        AudioManager.getInstance().playSfx(Sonic3kSfx.FLAMETHROWER_QUIET.id);
        spawnBreathFlames();
        setWait(BREATH_SWING_TIME, this::onBreathCycleComplete);
    }

    private void onBreathCycleComplete() {
        state.routine = ROUTINE_DESCEND;
        int bits = getCustomFlag(FLAG_PARENT_BITS) ^ PARENT_BIT_ALT_VERTICAL;
        setCustomFlag(FLAG_PARENT_BITS, bits);

        if ((bits & PARENT_BIT_ALT_VERTICAL) != 0) {
            state.yVel = -DESCEND_VEL;
            setWait(VERTICAL_DRIFT_TIME, this::onVerticalArcPrep);
        } else {
            state.yVel = DESCEND_VEL;
            setWait(VERTICAL_DRIFT_TIME, this::onDescendComplete);
        }
    }

    private void onVerticalArcPrep() {
        state.routine = ROUTINE_HOLD;
        state.xVel = 0;
        state.yVel = 0;
        setWait(HOLD_TIME, this::onHorizontalArcStart);
    }

    private void onHorizontalArcStart() {
        state.routine = ROUTINE_HORIZONTAL_SWING;
        setCustomFlag(FLAG_PARENT_COUNTER, 4);

        int bits = getCustomFlag(FLAG_PARENT_BITS) ^ PARENT_BIT_ALT_HORIZONTAL;
        setCustomFlag(FLAG_PARENT_BITS, bits);

        int xVel = ((bits & PARENT_BIT_ALT_HORIZONTAL) != 0) ? -DESCEND_VEL : DESCEND_VEL;
        state.xVel = xVel;
        swingMotion.setup1(state);
        setWait(HORIZONTAL_TIME, this::onHorizontalArcPivot);
    }

    private void onHorizontalArcPivot() {
        state.renderFlags ^= 1;
        setCustomFlag(FLAG_PARENT_COUNTER, 4);
        setWait(HORIZONTAL_RECOVERY_TIME, this::onHorizontalArcComplete);
    }

    private void onHorizontalArcComplete() {
        state.routine = ROUTINE_HOLD;
        state.xVel = 0;
        state.yVel = 0;
        setWait(HOLD_TIME, this::onBreathCycleComplete);
    }

    private void updateWaitOnly() {
        tickWait();
    }

    private void updateMoveAndWait(boolean applySwing) {
        if (applySwing) {
            swingMotion.update(state);
        }
        state.applyVelocity();
        tickWait();
    }

    private void updateDefeated(int frameCounter) {
        defeatTimer--;
        if ((defeatTimer & 7) == 0) {
            spawnDefeatExplosion();
        }
        if (defeatTimer > 0) {
            return;
        }

        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(false);
        }
        AudioManager.getInstance().getBackend().restoreMusic();
        setDestroyed(true);
    }

    private void setWait(int frames, Runnable callback) {
        waitTimer = frames;
        waitCallback = callback;
    }

    private void tickWait() {
        if (waitTimer < 0) {
            return;
        }
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        Runnable callback = waitCallback;
        waitCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void spawnChild(AbstractBossChild child,
                            ObjectManager objectManager) {
        childComponents.add(child);
        if (objectManager != null) {
            objectManager.addDynamicObject(child);
        }
    }

    private void spawnBreathFlames() {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }
        for (int i = 0; i < BREATH_FLAME_X_OFFSETS.length; i++) {
            // CreateChild1_Normal sets subtype = d2 (increments by 2): 0, 2, 4, 6
            objectManager.addDynamicObject(new AizMinibossFlameChild(
                    this,
                    BREATH_FLAME_X_OFFSETS[i],
                    BREATH_FLAME_Y_OFFSETS[i],
                    i * 2));
        }
    }

    private void loadBossPalette() {
        try {
            byte[] line = GameServices.rom().getRom().readBytes(
                    Sonic3kConstants.PAL_AIZ_MINIBOSS_ADDR, 32);
            levelManager.updatePalette(1, line);
        } catch (Exception ignored) {
            // Palette load failures should not crash gameplay.
        }
    }

    private Sonic3kAIZEvents getAizEvents() {
        return Sonic3kLevelEventManager.getInstance().getAizEvents();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state.routine < ROUTINE_DESCEND || state.routine == ROUTINE_DEFEATED) {
            return;
        }
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (state.renderFlags & 1) != 0;
        renderer.drawFrameIndex(0, state.x, state.y, hFlip, false);
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,1,1) — priority bit = 1
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return 2;
    }
}
