package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectManager;

import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * AIZ Act 1 miniboss cutscene object (0x90).
 *
 * ROM: Obj_AIZMinibossCutscene (sonic3k.asm):
 * - loc_68508 init
 * - loc_6852C trigger
 * - loc_68574 Obj_Wait
 * - loc_685B8 MoveWaitTouch
 * - loc_685FC Swing_UpAndDown + MoveWaitTouch
 * - loc_6862E pre-exit (explosion + wait)
 * - loc_68646/loc_68690 exit + cleanup
 */
public class AizMinibossCutsceneInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(AizMinibossCutsceneInstance.class.getName());
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_WAIT_TRIGGER = 2;
    private static final int ROUTINE_WAIT = 4;
    private static final int ROUTINE_DESCEND = 6;
    private static final int ROUTINE_SWING = 8;
    private static final int ROUTINE_EXIT = 10;

    private static final int COLLISION_PROPERTY_UNKILLABLE = 0x60;
    private static final int COLLISION_SIZE = 0x0F;
    private static final int TRIGGER_X = 0x2F10;
    private static final int WAIT_AFTER_TRIGGER = 3 * 60;
    private static final int DESCEND_VEL = 0x100;
    private static final int DESCEND_TIME = 0xAF;
    private static final int SWING_TIME = 0x7F;
    private static final int PRE_EXIT_TIME = 0x10;
    private static final int EXIT_VEL = 0x400;
    private static final int EXIT_TIME_AIZ1 = 0x120;
    private static final int EXIT_TIME_OTHER = 0x40;
    private static final int CUTSCENE_BOSS_ID = 1;

    private static final int FLAG_PARENT_BITS = 0x38;
    private static final int PARENT_BIT_BARREL_ACTIVATE = 1 << 1;
    private static final int FLAG_PARENT_COUNTER = 0x39;

    private static final int[] DEBRIS_X_OFFSETS = {-0x20, -0x68, -0x10, -0x58, -8, -0x50};
    private static final int[] DEBRIS_Y_POSITIONS = {0x310, 0x310, 0x31C, 0x31C, 0x328, 0x328};
    private static final int[] DEBRIS_X_VELOCITIES = {0x200, 0x200, 0x180, 0x180, 0x100, 0x100};
    private static final int[] DEBRIS_FRAMES = {0, 0, 1, 1, 2, 2};

    /** ROM: loc_68F62 custom flash — palette line 2 color indices (byte offsets $0E,$14,$16,$1C). */
    private static final int[] CUSTOM_FLASH_INDICES = {7, 10, 11, 14};
    /** ROM: loc_68F62 dark color set (bit 0 of timer set). */
    private static final int[] CUSTOM_FLASH_DARK = {0x0644, 0x0240, 0x0020, 0x0644};
    /** ROM: loc_68F62 bright color set (bit 0 of timer clear). */
    private static final int[] CUSTOM_FLASH_BRIGHT = {0x0888, 0x0AAA, 0x0EEE, 0x0AAA};

    private final AizMinibossSwingMotion swingMotion = new AizMinibossSwingMotion();

    private int waitTimer = -1;
    private S3kBossExplosionController explosionController;
    private Runnable waitCallback;
    private int savedCameraMaxX;

    public AizMinibossCutsceneInstance(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossCutscene");
    }

    @Override
    protected void initializeBossState() {
        state.routine = ROUTINE_INIT;
        state.hitCount = COLLISION_PROPERTY_UNKILLABLE;
        waitTimer = -1;
        waitCallback = null;
        savedCameraMaxX = 0;
    }

    @Override
    protected int getInitialHitCount() {
        return COLLISION_PROPERTY_UNKILLABLE;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Cutscene variant is effectively unkillable in normal play.
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return -1; // Disable standard flash — custom flash via updateCustomFlash()
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT_TRIGGER -> updateWaitTrigger();
            case ROUTINE_WAIT -> updateWaitOnly();
            case ROUTINE_DESCEND -> updateMoveAndWait(false);
            case ROUTINE_SWING -> updateMoveAndWait(true);
            case ROUTINE_EXIT -> updateExit();
            default -> {
            }
        }
        tickExplosionController();
        updateCustomFlash();
    }

    /**
     * ROM: loc_68F62 custom AIZ miniboss flash.
     * Writes 4 specific palette entries on palette line 1 (engine index 1),
     * alternating between dark and bright color sets every frame.
     */
    private void updateCustomFlash() {
        if (!state.invulnerable) {
            return;
        }
        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            return;
        }
        Palette pal = level.getPalette(1);
        // ROM: bit 0 of $20(a0) determines which color set
        boolean useDark = (state.invulnerabilityTimer & 1) != 0;
        int[] colors = useDark ? CUSTOM_FLASH_DARK : CUSTOM_FLASH_BRIGHT;
        for (int i = 0; i < CUSTOM_FLASH_INDICES.length; i++) {
            byte[] bytes = {(byte) ((colors[i] >> 8) & 0xFF), (byte) (colors[i] & 0xFF)};
            pal.getColor(CUSTOM_FLASH_INDICES[i]).fromSegaFormat(bytes, 0);
        }
        var gm = services().graphicsManager();
        if (gm.isGlInitialized()) {
            gm.cachePaletteTexture(pal, 1);
        }
    }

    private void updateInit() {
        var camera = services().camera();
        savedCameraMaxX = camera.getMaxX();

        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(true);
        }

        java.util.logging.Logger.getLogger("AIZMinibossCutscene")
                .info("Cutscene INIT -> WAIT_TRIGGER at x=" + state.x + " y=" + state.y);
        state.routine = ROUTINE_WAIT_TRIGGER;
    }

    private void updateWaitTrigger() {
        var camera = services().camera();
        if (camera.getX() < TRIGGER_X) {
            return;
        }

        spawnDebris();
        loadBossPalette();

        camera.setMinX((short) TRIGGER_X);
        camera.setMaxX((short) TRIGGER_X);
        services().gameState().setCurrentBossId(CUTSCENE_BOSS_ID);
        services().fadeOutMusic();

        state.routine = ROUTINE_WAIT;
        setWait(WAIT_AFTER_TRIGGER, this::onInitialDelayComplete);
    }

    private void onInitialDelayComplete() {
        state.routine = ROUTINE_DESCEND;
        state.yVel = DESCEND_VEL;
        setWait(DESCEND_TIME, this::onDescendComplete);

        var objectManager = services().objectManager();
        spawnChild(new AizMinibossBodyChild(this), objectManager);
        spawnChild(new AizMinibossArmChild(this), objectManager);
        for (int i = 0; i < 3; i++) {
            spawnChild(new AizMinibossFlameBarrelChild(this, i, true), objectManager);
        }

        services().playMusic(Sonic3kMusic.MINIBOSS.id);
    }

    private void onDescendComplete() {
        setCustomFlag(FLAG_PARENT_COUNTER, 3);
        setCustomFlag(FLAG_PARENT_BITS, getCustomFlag(FLAG_PARENT_BITS) | PARENT_BIT_BARREL_ACTIVATE);
        state.routine = ROUTINE_SWING;
        state.yVel = 0;
        swingMotion.setup1(state);
        setWait(SWING_TIME, this::onSwingComplete);
    }

    private void onSwingComplete() {
        // ROM: loc_6862E — directly after swing, spawn explosion and set pre-exit wait
        setWait(PRE_EXIT_TIME, this::onPreExitComplete);
        // ROM: Obj_BossExplosionSpecial positions at screen center (overrides child offset)
        var camera = services().camera();
        explosionController = new S3kBossExplosionController(
                camera.getX() + 160, camera.getY() + 112, 2);
    }

    private void tickExplosionController() {
        if (explosionController == null || explosionController.isFinished()) {
            return;
        }
        explosionController.tick();
        var objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }
        for (var pending : explosionController.drainPendingExplosions()) {
            // ROM: sub_52850 plays sfx_Explode when spawning each child
            if (pending.playSfx()) {
                services().playSfx(Sonic3kSfx.EXPLODE.id);
            }
            objectManager.addDynamicObject(new S3kBossExplosionChild(pending.x(), pending.y()));
        }
    }

    private void onPreExitComplete() {
        state.routine = ROUTINE_EXIT;
        state.xVel = EXIT_VEL;
        state.yVel = 0;

        loadBossPalette();
        services().fadeOutMusic();

        int exitFrames = isAiz1() ? EXIT_TIME_AIZ1 : EXIT_TIME_OTHER;
        setWait(exitFrames, this::onExitComplete);

        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setEventsFg5(true);
            java.util.logging.Logger.getLogger("AIZMinibossCutscene")
                    .info("PRE_EXIT complete: setEventsFg5(true), exitFrames=" + exitFrames);
        } else {
            java.util.logging.Logger.getLogger("AIZMinibossCutscene")
                    .warning("PRE_EXIT complete but getAizEvents() returned null! Fire transition NOT triggered.");
        }
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

    private void updateExit() {
        state.applyVelocity();
        tickWait();
    }

    private void onExitComplete() {
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(false);
        }
        boolean transitionInProgress = events != null
                && (events.isFireTransitionActive() || events.isAct2TransitionRequested());

        // During the unwinnable AIZ1 cutscene transition, BG events own camera/music flow.
        // Only restore defaults when no transition handoff is active.
        if (!transitionInProgress) {
            services().audioManager().getBackend().restoreMusic();
            var camera = services().camera();
            camera.setMinX((short) 0);
            camera.setMaxX((short) savedCameraMaxX);
            services().gameState().setCurrentBossId(0);
        }

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

    private void spawnDebris() {
        var objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }
        int cameraX = services().camera().getX();
        for (int i = 0; i < DEBRIS_X_OFFSETS.length; i++) {
            int x = cameraX + DEBRIS_X_OFFSETS[i];
            int y = DEBRIS_Y_POSITIONS[i];
            objectManager.addDynamicObject(new AizMinibossDebrisChild(
                    x, y, DEBRIS_X_VELOCITIES[i], DEBRIS_FRAMES[i]));
        }
    }

    private void loadBossPalette() {
        try {
            byte[] line = services().rom().readBytes(
                    Sonic3kConstants.PAL_AIZ_MINIBOSS_ADDR, 32);
            services().updatePalette(1, line);
        } catch (Exception e) {
            LOG.fine(() -> "AizMinibossCutsceneInstance.loadBossPalette: " + e.getMessage());
        }
    }

    private boolean isAiz1() {
        return services().romZoneId() == 0 && services().currentAct() == 0;
    }

    private Sonic3kAIZEvents getAizEvents() {
        return ((Sonic3kLevelEventManager) services().levelEventProvider()).getAizEvents();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (state.routine < ROUTINE_DESCEND || isDestroyed()) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
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
        // ROM: ObjDat_AIZMiniboss priority $0200 → $200/$80 = bucket 4
        return 4;
    }
}
