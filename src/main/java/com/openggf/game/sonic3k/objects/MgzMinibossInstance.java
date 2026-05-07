package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.SwScrlMgz;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.SwingMotion;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $9F — Marble Garden Act 1 miniboss.
 *
 * <p>Ports {@code Obj_MGZMiniboss} from the S3K disassembly and follows the
 * shared Tunnelbot routines for swing motion, drill animation, ceiling contact,
 * debris, hit flash, and defeat flow.
 */
public final class MgzMinibossInstance extends AbstractBossInstance {

    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_WAIT_CAMERA = 2;
    private static final int ROUTINE_DRILL = 4;
    private static final int ROUTINE_TUNNEL_UP = 6;
    private static final int ROUTINE_SHAKE_CEILING = 8;
    private static final int ROUTINE_WAIT = 10;
    private static final int ROUTINE_DROP_SHAKE = 12;
    private static final int ROUTINE_FALL = 14;
    private static final int ROUTINE_RISE = 16;
    private static final int ROUTINE_RETURN_SWING = 18;
    private static final int ROUTINE_DEFEATED = 20;

    private static final int HIT_COUNT = 6;
    private static final int BODY_COLLISION_FLAGS = 0x10;
    private static final int ARM_COLLISION_FLAGS = 0x9E;
    private static final int COLLISION_SIZE = 0x10;
    private static final int INVULNERABILITY_TIME = 0x20;
    private static final int PRIORITY_BUCKET = 5;
    private static final int OBJECT_PATTERN_BASE = 0x20000;

    private static final int BODY_Y_RADIUS = 0x28;
    private static final int ARENA_LOCK_X = 0x2E00;
    private static final int ARENA_TARGET_MAX_Y = 0x0E10;

    private static final int DRILL_ANIM_INITIAL_DELAY = 5;
    private static final int DRILL_ANIM_LOOP_COUNT = 4;
    private static final int[] DRILL_ANIM_FRAMES = {0, 1, 2};
    private static final int[] TUNNEL_ANIM_FRAMES = {0, 1, 2};
    private static final int TUNNEL_ANIM_DELAY = 0;

    private static final int SWING_MAX_VELOCITY = 0xC0;
    private static final int SWING_ACCELERATION = 0x10;
    private static final int PLATFORM_SWING_MAX_VELOCITY = 0x100;

    private static final int SHAKE_TIME = 0x7F;
    private static final int WAIT_TIME = 0x3F;
    private static final int FALL_TIME = 0x2F;
    private static final int KNUCKLES_FALL_TIME = 0x17;
    private static final int RISE_TIME = 0x3F;
    private static final int RETURN_SWING_TIME = 0x3F;
    private static final int DEFEAT_WAIT_TIME = 0x3F;
    private static final int LEVEL_MUSIC_FADE_TIME = 2 * 60;
    private static final int MINIBOSS_MUSIC_FADE_TIME = 90;

    private static final int[] DROP_X_OFFSETS = {0x30, 0x48, 0x60, 0x78, 0xC8, 0xE0, 0xF8, 0x110};
    private static final int[] DEFEAT_FRAMES = {4, 3, 5, 6, 6};
    private static final int[] DEFEAT_X_OFFSETS = {0, -0x1C, 0x1C, -0x1C, 0x1C};
    private static final int[] DEFEAT_Y_OFFSETS = {0, 0, 0, -0x16, -0x16};
    private static final int[] DEFEAT_X_VELS = {-0x180, -0x100, 0x100, -0x80, 0x80};
    private static final int[] DEFEAT_Y_VELS = {-0x340, -0x300, -0x300, -0x280, -0x280};

    private static final int[] FLASH_INDICES = {12, 13, 14};
    private static final int[] FLASH_DARK = {0x0CAA, 0x0866, 0x0644};
    private static final int[] FLASH_BRIGHT = {0x0EEE, 0x0EEE, 0x0EEE};

    private static final int[] SCREEN_SHAKE_CONTINUOUS = {
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3,
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3
    };

    private int anchorX;
    private int anchorY;
    private int routineTimer;
    private int swingVelocity;
    private int ySubpixel;
    private boolean swingDirectionDown;
    private int animFrameIndex;
    private int animTimer;
    private int drillDelay;
    private int drillLoopCounter;
    private int mappingFrame;
    private boolean facingRight;
    private boolean upsideDown;
    private boolean arenaEngaged;
    private boolean knucklesRecovery;
    private boolean flashColorsSaved;
    private boolean defeatHandoffQueued;
    private final Palette.Color[] savedFlashColors = new Palette.Color[FLASH_INDICES.length];
    @com.openggf.game.rewind.RewindDeferred(reason = "explosion controller has mutable queued state needing explicit value codec")
    private S3kBossExplosionController defeatExplosionController;
    @RewindTransient(reason = "parent/child object relationship; restored by live object graph")
    private DrillArmChild leftArm;
    @RewindTransient(reason = "parent/child object relationship; restored by live object graph")
    private DrillArmChild rightArm;

    public MgzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "MGZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        anchorX = spawn.x();
        anchorY = spawn.y();
        state.x = anchorX;
        state.y = anchorY;
        state.xFixed = anchorX << 16;
        state.yFixed = anchorY << 16;
        state.routine = ROUTINE_INIT;
        state.hitCount = HIT_COUNT;
        routineTimer = -1;
        swingVelocity = SWING_MAX_VELOCITY;
        ySubpixel = 0;
        swingDirectionDown = false;
        animFrameIndex = 0;
        animTimer = 0;
        drillDelay = DRILL_ANIM_INITIAL_DELAY;
        drillLoopCounter = 0;
        mappingFrame = 0;
        facingRight = false;
        upsideDown = false;
        arenaEngaged = false;
        knucklesRecovery = false;
        flashColorsSaved = false;
        defeatHandoffQueued = false;
        defeatExplosionController = null;
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        updateHitFlash();

        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT_CAMERA -> updateWaitCamera();
            case ROUTINE_DRILL -> updateDrill();
            case ROUTINE_TUNNEL_UP -> updateTunnelUp();
            case ROUTINE_SHAKE_CEILING -> updateCeilingShake(frameCounter);
            case ROUTINE_WAIT -> updateWait(playerEntity);
            case ROUTINE_DROP_SHAKE -> updateDropShake(frameCounter, playerEntity);
            case ROUTINE_FALL -> updateFall();
            case ROUTINE_RISE -> updateRise();
            case ROUTINE_RETURN_SWING -> updateReturnSwing();
            case ROUTINE_DEFEATED -> updateDefeated(frameCounter);
            default -> {
            }
        }

        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    private void updateInit() {
        services().gameState().setCurrentBossId(Sonic3kObjectIds.MGZ_MINIBOSS);
        services().camera().setMaxX((short) ARENA_LOCK_X);
        services().camera().setMaxYTarget((short) ARENA_TARGET_MAX_Y);
        ensureSupportArtReady();
        spawnMinibossMusicTransition();
        spawnArmChildren();
        state.routine = ROUTINE_WAIT_CAMERA;
    }

    private void updateWaitCamera() {
        updateSwing();
        applySwingVelocity();
        services().camera().setMinX((short) services().camera().getX());
        services().camera().setMaxX((short) ARENA_LOCK_X);
        services().camera().setMaxYTarget((short) ARENA_TARGET_MAX_Y);
        if (services().camera().getX() < ARENA_LOCK_X) {
            return;
        }

        arenaEngaged = true;
        services().camera().setMinX((short) ARENA_LOCK_X);
        services().camera().setMaxX((short) ARENA_LOCK_X);
        enterDrill();
    }

    private void updateDrill() {
        enforceArenaLock();
        updateSwing();
        applySwingVelocity();
        animateRawGetFaster();
    }

    private void updateTunnelUp() {
        enforceArenaLock();
        animateRaw();
        state.y--;

        TerrainCheckResult ceiling = ObjectTerrainUtils.checkCeilingDist(state.x, state.y, BODY_Y_RADIUS);
        if (ceiling.distance() < 0) {
            enterCeilingShake();
        }
    }

    private void updateCeilingShake(int frameCounter) {
        enforceArenaLock();
        animateRaw();
        state.y += ((frameCounter & 1) == 0) ? -2 : 1;
        applyContinuousShake(frameCounter);
        if ((frameCounter & 7) == 0) {
            services().playSfx(Sonic3kSfx.RUMBLE_2.id);
            spawnCeilingDebris();
        }
        if (--routineTimer < 0) {
            clearScreenShake();
            state.routine = ROUTINE_WAIT;
            routineTimer = WAIT_TIME;
        }
    }

    private void updateWait(PlayableEntity playerEntity) {
        enforceArenaLock();
        if (--routineTimer >= 0) {
            return;
        }

        state.routine = ROUTINE_DROP_SHAKE;
        facingRight = true;
        upsideDown = true;
        routineTimer = SHAKE_TIME;
        setScreenShakeActive(true);
        int randomIndex = ((services().rng().nextWord() & 0xFFFF) & 0x0E) >> 1;
        state.x = services().camera().getX() + DROP_X_OFFSETS[randomIndex];
        state.y -= 0x40;
        if (isKnuckles(playerEntity)) {
            boolean mirrored = randomIndex < 4;
            spawnChild(() -> new KnucklesSpikePlatformChild(this, mirrored));
        }
    }

    private void updateDropShake(int frameCounter, PlayableEntity playerEntity) {
        enforceArenaLock();
        animateRaw();
        state.y += ((frameCounter & 1) == 0) ? 2 : -1;
        applyContinuousShake(frameCounter);
        if ((frameCounter & 7) == 0) {
            services().playSfx(Sonic3kSfx.RUMBLE_2.id);
            spawnCeilingDebris();
        }
        if (--routineTimer >= 0) {
            return;
        }

        clearScreenShake();
        state.routine = ROUTINE_FALL;
        knucklesRecovery = isKnuckles(playerEntity);
        routineTimer = knucklesRecovery ? KNUCKLES_FALL_TIME : FALL_TIME;
    }

    private void updateFall() {
        enforceArenaLock();
        animateRaw();
        state.y += 4;
        if (--routineTimer >= 0) {
            return;
        }
        if (knucklesRecovery) {
            enterReturnSwing();
        } else {
            state.routine = ROUTINE_RISE;
            routineTimer = RISE_TIME;
        }
    }

    private void updateRise() {
        enforceArenaLock();
        animateRaw();
        state.y--;
        if (--routineTimer < 0) {
            enterReturnSwing();
        }
    }

    private void updateReturnSwing() {
        enforceArenaLock();
        updateSwing();
        applySwingVelocity();
        animateRaw();
        if (--routineTimer < 0) {
            facingRight = false;
            upsideDown = false;
            enterTunnelUp();
        }
    }

    private void updateDefeated(int frameCounter) {
        if (defeatExplosionController != null && !defeatExplosionController.isFinished()) {
            defeatExplosionController.tick();
            ObjectManager objectManager = services().objectManager();
            if (objectManager != null) {
                for (var pending : defeatExplosionController.drainPendingExplosions()) {
                    if (pending.playSfx()) {
                        services().playSfx(Sonic3kSfx.EXPLODE.id);
                    }
                    objectManager.addDynamicObjectAfterCurrent(new S3kBossExplosionChild(pending.x(), pending.y()));
                }
            }
        }

        if (!defeatHandoffQueued && --routineTimer < 0) {
            queuePostDefeatFlow();
        }

        if (defeatHandoffQueued && (defeatExplosionController == null || defeatExplosionController.isFinished())) {
            setDestroyed(true);
        }
    }

    private void enterDrill() {
        state.routine = ROUTINE_DRILL;
        animFrameIndex = 0;
        animTimer = 0;
        drillDelay = DRILL_ANIM_INITIAL_DELAY;
        drillLoopCounter = 0;
        mappingFrame = DRILL_ANIM_FRAMES[0];
    }

    private void enterTunnelUp() {
        state.routine = ROUTINE_TUNNEL_UP;
        animFrameIndex = 0;
        animTimer = 0;
    }

    private void enterCeilingShake() {
        state.routine = ROUTINE_SHAKE_CEILING;
        routineTimer = SHAKE_TIME;
        setScreenShakeActive(true);
    }

    private void enterReturnSwing() {
        state.routine = ROUTINE_RETURN_SWING;
        routineTimer = RETURN_SWING_TIME;
        swingVelocity = SWING_MAX_VELOCITY;
        ySubpixel = 0;
        swingDirectionDown = false;
    }

    private void animateRawGetFaster() {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }

        animFrameIndex++;
        if (animFrameIndex >= DRILL_ANIM_FRAMES.length) {
            animFrameIndex = 0;
            if (drillDelay > 0) {
                drillDelay--;
            } else {
                drillLoopCounter++;
                if (drillLoopCounter >= DRILL_ANIM_LOOP_COUNT) {
                    enterTunnelUp();
                    return;
                }
            }
        }

        mappingFrame = DRILL_ANIM_FRAMES[animFrameIndex];
        animTimer = drillDelay;
    }

    private void animateRaw() {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }

        animFrameIndex++;
        if (animFrameIndex >= TUNNEL_ANIM_FRAMES.length) {
            animFrameIndex = 0;
        }
        mappingFrame = TUNNEL_ANIM_FRAMES[animFrameIndex];
        animTimer = TUNNEL_ANIM_DELAY;
    }

    private void updateSwing() {
        SwingMotion.Result result = SwingMotion.update(
                SWING_ACCELERATION, swingVelocity, SWING_MAX_VELOCITY, swingDirectionDown);
        swingVelocity = result.velocity();
        swingDirectionDown = result.directionDown();
    }

    private void applySwingVelocity() {
        int fixedY = (state.y << 8) | (ySubpixel & 0xFF);
        fixedY += swingVelocity;
        state.y = fixedY >> 8;
        ySubpixel = fixedY & 0xFF;
    }

    private void enforceArenaLock() {
        if (!arenaEngaged) {
            return;
        }
        services().camera().setMinX((short) ARENA_LOCK_X);
        services().camera().setMaxX((short) ARENA_LOCK_X);
        services().camera().setMaxYTarget((short) ARENA_TARGET_MAX_Y);
    }

    private void spawnMinibossMusicTransition() {
        spawnChild(() -> new SongFadeTransitionInstance(MINIBOSS_MUSIC_FADE_TIME, Sonic3kMusic.MINIBOSS.id));
    }

    private void ensureSupportArtReady() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        boolean needsCache = rendererMissingOrUnready(renderManager, Sonic3kObjectArtKeys.MGZ_MINIBOSS)
                || rendererMissingOrUnready(renderManager, Sonic3kObjectArtKeys.MGZ_MINIBOSS_SPIRE)
                || rendererMissingOrUnready(renderManager, Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS);
        boolean needsBossExplosion = rendererMissingOrUnready(renderManager, com.openggf.level.objects.ObjectArtKeys.BOSS_EXPLOSION);
        if (!needsCache && !needsBossExplosion) {
            return;
        }

        if (renderManager.getArtProvider() instanceof Sonic3kObjectArtProvider s3kProvider) {
            s3kProvider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.MGZ_MINIBOSS);
            s3kProvider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.MGZ_MINIBOSS_SPIRE);
            s3kProvider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS);
            s3kProvider.ensureBossExplosionArtLoaded();
        }

        if (services().graphicsManager() != null) {
            renderManager.ensurePatternsCached(services().graphicsManager(), OBJECT_PATTERN_BASE);
        }
    }

    private static boolean rendererMissingOrUnready(ObjectRenderManager renderManager, String key) {
        PatternSpriteRenderer renderer = renderManager.getRenderer(key);
        return renderer == null || !renderer.isReady();
    }

    private void spawnArmChildren() {
        if (leftArm == null || leftArm.isDestroyed()) {
            leftArm = spawnChild(() -> new DrillArmChild(this, -0x1C, -0x16));
        }
        if (rightArm == null || rightArm.isDestroyed()) {
            rightArm = spawnChild(() -> new DrillArmChild(this, 0x1C, -0x16));
        }
    }

    private void destroyArms() {
        if (leftArm != null) {
            leftArm.setDestroyed(true);
        }
        if (rightArm != null) {
            rightArm.setDestroyed(true);
        }
    }

    private void spawnCeilingDebris() {
        var camera = services().camera();
        if (camera == null) {
            return;
        }

        int random = services().rng().nextWord() & 0xFFFF;
        int x = camera.getX() - 0x40 + (random & 0x1FF);
        int y = camera.getY() - 0x20;
        int frame = (services().rng().nextWord() & 0xFFFF) & 0x03;
        boolean spire = frame == 0;
        if (spire) {
            spawnChild(() -> new CeilingSpireChild(x, y, frame));
        } else {
            spawnChild(() -> new CeilingDebrisChild(x, y, frame, false));
        }
    }

    private void spawnDefeatFragments() {
        for (int i = 0; i < DEFEAT_FRAMES.length; i++) {
            int index = i;
            spawnChild(() -> new DefeatFragmentChild(
                    state.x + DEFEAT_X_OFFSETS[index],
                    state.y + DEFEAT_Y_OFFSETS[index],
                    DEFEAT_FRAMES[index],
                    DEFEAT_X_VELS[index],
                    DEFEAT_Y_VELS[index]));
        }
    }

    private void updateHitFlash() {
        if (!state.invulnerable) {
            restoreFlashColors();
            return;
        }

        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            state.invulnerabilityTimer--;
            if (state.invulnerabilityTimer <= 0) {
                state.invulnerable = false;
            }
            return;
        }

        Palette palette = level.getPalette(1);
        if (!flashColorsSaved) {
            for (int i = 0; i < FLASH_INDICES.length; i++) {
                Palette.Color existing = palette.getColor(FLASH_INDICES[i]);
                savedFlashColors[i] = new Palette.Color(existing.r, existing.g, existing.b);
            }
            flashColorsSaved = true;
        }

        int[] colors = ((state.invulnerabilityTimer & 1) != 0) ? FLASH_DARK : FLASH_BRIGHT;
        for (int i = 0; i < FLASH_INDICES.length; i++) {
            palette.setColor(FLASH_INDICES[i], colorFromGenesisWord(colors[i]));
        }
        if (services().graphicsManager() != null && services().graphicsManager().isGlInitialized()) {
            services().graphicsManager().cachePaletteTexture(palette, 1);
        }

        state.invulnerabilityTimer--;
        if (state.invulnerabilityTimer <= 0) {
            state.invulnerable = false;
            restoreFlashColors();
        }
    }

    private void restoreFlashColors() {
        if (!flashColorsSaved) {
            return;
        }
        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            flashColorsSaved = false;
            return;
        }
        Palette palette = level.getPalette(1);
        for (int i = 0; i < FLASH_INDICES.length; i++) {
            palette.setColor(FLASH_INDICES[i], savedFlashColors[i]);
        }
        if (services().graphicsManager() != null && services().graphicsManager().isGlInitialized()) {
            services().graphicsManager().cachePaletteTexture(palette, 1);
        }
        flashColorsSaved = false;
    }

    private void applyContinuousShake(int frameCounter) {
        SwScrlMgz mgz = resolveMgzScrollHandler();
        if (mgz != null) {
            mgz.setScreenShakeOffset(SCREEN_SHAKE_CONTINUOUS[frameCounter & 0x3F]);
        }
    }

    private void clearScreenShake() {
        SwScrlMgz mgz = resolveMgzScrollHandler();
        if (mgz != null) {
            mgz.setScreenShakeOffset(0);
        }
        setScreenShakeActive(false);
    }

    private void setScreenShakeActive(boolean active) {
        if (services().gameState() != null) {
            services().gameState().setScreenShakeActive(active);
        }
    }

    private SwScrlMgz resolveMgzScrollHandler() {
        if (services().parallaxManager() == null) {
            return null;
        }
        ZoneScrollHandler handler = services().parallaxManager().getHandler(Sonic3kZoneIds.ZONE_MGZ);
        return (handler instanceof SwScrlMgz mgz) ? mgz : null;
    }

    private boolean isKnuckles(PlayableEntity playerEntity) {
        return playerEntity instanceof AbstractPlayableSprite player
                && "knuckles".equalsIgnoreCase(player.getCode());
    }

    private void queuePostDefeatFlow() {
        defeatHandoffQueued = true;
        clearScreenShake();
        if (services().levelGamestate() != null) {
            services().levelGamestate().pauseTimer();
        }

        int levelMusicId = services().getCurrentLevelMusicId();
        if (levelMusicId > 0) {
            spawnChild(() -> new SongFadeTransitionInstance(LEVEL_MUSIC_FADE_TIME, levelMusicId));
        }
        spawnChild(() -> new MgzBossCameraScrollHelper(ARENA_LOCK_X));
        // ROM: Obj_EndSignControlDoSign spawns the signpost via CreateChild6_Simple,
        // inheriting the control object's x_pos — the miniboss's X at the moment of
        // defeat, not the camera lock point.
        int signpostX = state.x;
        spawnChild(() -> new S3kBossDefeatSignpostFlow(
                signpostX, services().currentAct(), S3kBossDefeatSignpostFlow.CleanupAction.NONE));
    }

    private static Palette.Color colorFromGenesisWord(int word) {
        Palette.Color color = new Palette.Color();
        byte[] bytes = {(byte) ((word >> 8) & 0xFF), (byte) (word & 0xFF)};
        color.fromSegaFormat(bytes, 0);
        return color;
    }

    @Override
    protected int getInitialHitCount() {
        return HIT_COUNT;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // sub_88A62 only drives flash and collision restore.
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    public int getCollisionFlags() {
        if (state.invulnerable || state.defeated) {
            return 0;
        }
        return BODY_COLLISION_FLAGS;
    }

    @Override
    protected boolean usesBaseHitHandler() {
        return false;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULNERABILITY_TIME;
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        if (state.invulnerable || state.defeated) {
            return;
        }

        state.hitCount--;
        if (state.hitCount <= 0) {
            state.hitCount = 0;
            state.defeated = true;
            services().gameState().addScore(1000);
            onDefeatStarted();
            return;
        }

        state.invulnerable = true;
        state.invulnerabilityTimer = INVULNERABILITY_TIME;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        onHitTaken(state.hitCount);
    }

    @Override
    protected void onDefeatStarted() {
        restoreFlashColors();
        clearScreenShake();
        destroyArms();
        state.invulnerable = false;
        state.invulnerabilityTimer = 0;
        state.routine = ROUTINE_DEFEATED;
        upsideDown = false;
        routineTimer = DEFEAT_WAIT_TIME;
        defeatExplosionController = new S3kBossExplosionController(state.x, state.y, 0, services().rng());
        spawnDefeatFragments();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_MINIBOSS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, state.x, state.y, facingRight, upsideDown);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }

    private static final class DrillArmChild extends AbstractObjectInstance implements TouchResponseProvider {
        private static final int PRIORITY_BUCKET = 5;

        @RewindTransient(reason = "parent/child object relationship; restored by live object graph")
        private final MgzMinibossInstance parent;
        private final int xOffset;
        private final int yOffset;
        private int currentX;
        private int currentY;

        private DrillArmChild(MgzMinibossInstance parent, int xOffset, int yOffset) {
            super(new ObjectSpawn(parent.state.x + adjustedOffset(xOffset, parent.facingRight),
                    parent.state.y + adjustedOffset(yOffset, parent.upsideDown), 0, 0, 0, false, 0),
                    "MGZMinibossArm");
            this.parent = parent;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.currentX = parent.state.x + adjustedXOffset();
            this.currentY = parent.state.y + adjustedYOffset();
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (parent.isDestroyed() || parent.state.defeated) {
                setDestroyed(true);
                return;
            }
            currentX = parent.state.x + adjustedXOffset();
            currentY = parent.state.y + adjustedYOffset();
            updateDynamicSpawn(currentX, currentY);
        }

        private int adjustedXOffset() {
            return adjustedOffset(xOffset, parent.facingRight);
        }

        private int adjustedYOffset() {
            return adjustedOffset(yOffset, parent.upsideDown);
        }

        private static int adjustedOffset(int offset, boolean flipped) {
            return flipped ? -offset : offset;
        }

        @Override
        public int getCollisionFlags() {
            return (parent.state.defeated || parent.state.invulnerable) ? 0 : ARM_COLLISION_FLAGS;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }
    }

    private static class CeilingDebrisChild extends AbstractObjectInstance {
        private static final int PRIORITY_BUCKET = 4;
        private static final int GRAVITY = 0x18;
        private static final int LIFE = 0x5F;

        private final int mappingFrame;
        private final boolean spire;
        private int xFixed;
        private int yFixed;
        private int yVel;
        private int life;

        private CeilingDebrisChild(int x, int y, int mappingFrame, boolean spire) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "MGZMinibossDebris");
            this.mappingFrame = mappingFrame;
            this.spire = spire;
            this.xFixed = x << 8;
            this.yFixed = y << 8;
            this.yVel = 0;
            this.life = LIFE;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            yFixed += yVel;
            yVel += GRAVITY;
            updateDynamicSpawn(xFixed >> 8, yFixed >> 8);
            if (--life < 0) {
                setDestroyed(true);
            }
        }

        @Override
        public int getX() {
            return xFixed >> 8;
        }

        @Override
        public int getY() {
            return yFixed >> 8;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(
                    spire ? Sonic3kObjectArtKeys.MGZ_MINIBOSS_SPIRE : Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS);
            if (renderer == null && spire) {
                renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS);
            }
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }
    }

    private static final class CeilingSpireChild extends CeilingDebrisChild implements TouchResponseProvider {
        private CeilingSpireChild(int x, int y, int mappingFrame) {
            super(x, y, mappingFrame, true);
        }

        @Override
        public int getCollisionFlags() {
            return 0x84;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }
    }

    private static final class DefeatFragmentChild extends AbstractObjectInstance {
        private static final int PRIORITY_BUCKET = 5;
        private static final int GRAVITY = 0x38;

        private final int mappingFrame;
        private int xFixed;
        private int yFixed;
        private int xVel;
        private int yVel;
        private int life = 0x5F;

        private DefeatFragmentChild(int x, int y, int mappingFrame, int xVel, int yVel) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "MGZMinibossFragment");
            this.mappingFrame = mappingFrame;
            this.xFixed = x << 8;
            this.yFixed = y << 8;
            this.xVel = xVel;
            this.yVel = yVel;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            xFixed += xVel;
            yFixed += yVel;
            yVel += GRAVITY;
            updateDynamicSpawn(xFixed >> 8, yFixed >> 8);
            if (--life < 0) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, xFixed >> 8, yFixed >> 8, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }
    }

    private static final class KnucklesSpikePlatformChild extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {
        private static final int PRIORITY_BUCKET = 5;
        private static final int HALF_WIDTH = 0x18;
        private static final int HALF_HEIGHT = 0x30;
        private static final int SIDE_PADDING = 0x0B;

        private static final int ROUTINE_RISE = 0;
        private static final int ROUTINE_WAIT = 2;
        private static final int ROUTINE_SWING = 4;
        private static final int ROUTINE_WAIT_END = 6;
        private static final int ROUTINE_DESCEND = 8;

        @RewindTransient(reason = "parent/child object relationship; restored by live object graph")
        private final MgzMinibossInstance parent;
        private final boolean mirrored;
        private final int baseY;
        private int currentX;
        private int currentY;
        private int routine;
        private int timer;
        private int xVel;
        private int yVel;
        private int xSubpixel;
        private int ySubpixel;
        private boolean swingDirectionDown;
        private int mappingFrame;
        private int animTimer;

        private KnucklesSpikePlatformChild(MgzMinibossInstance parent, boolean mirrored) {
            super(new ObjectSpawn(parent.state.x, parent.state.y, 0, 0, mirrored ? 1 : 0, false, 0),
                    "MgzKnucklesSpikePlatform");
            this.parent = parent;
            this.mirrored = mirrored;
            int cameraX = parent.services().camera() != null ? parent.services().camera().getX() : parent.state.x;
            int cameraY = parent.services().camera() != null ? parent.services().camera().getY() : parent.state.y;
            this.currentX = cameraX + 0x30 + (mirrored ? 0xE0 : 0);
            this.currentY = cameraY + 0xF0;
            this.baseY = currentY;
            this.routine = ROUTINE_RISE;
            this.timer = 0x0F;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (parent.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            switch (routine) {
                case ROUTINE_RISE -> updateRise();
                case ROUTINE_WAIT -> updateWait();
                case ROUTINE_SWING -> updateSwing();
                case ROUTINE_WAIT_END -> updateEndWait();
                case ROUTINE_DESCEND -> updateDescend();
                default -> {
                }
            }

            animTimer--;
            if (animTimer < 0) {
                animTimer = 7;
                mappingFrame = (mappingFrame + 1) & 0x03;
            }

            updateDynamicSpawn(currentX, currentY);
        }

        private void updateRise() {
            currentY -= 4;
            if (--timer < 0) {
                routine = ROUTINE_WAIT;
                timer = 0x3F;
            }
        }

        private void updateWait() {
            if (--timer >= 0) {
                return;
            }
            routine = ROUTINE_SWING;
            xVel = mirrored ? -0x100 : 0x100;
            yVel = PLATFORM_SWING_MAX_VELOCITY;
            swingDirectionDown = false;
            timer = 0xDF;
        }

        private void updateSwing() {
            SwingMotion.Result result = SwingMotion.update(
                    SWING_ACCELERATION, yVel, PLATFORM_SWING_MAX_VELOCITY, swingDirectionDown);
            yVel = result.velocity();
            swingDirectionDown = result.directionDown();

            int xFixed = (currentX << 8) | (xSubpixel & 0xFF);
            int yFixed = (currentY << 8) | (ySubpixel & 0xFF);
            xFixed += xVel;
            yFixed += yVel;
            currentX = xFixed >> 8;
            currentY = yFixed >> 8;
            xSubpixel = xFixed & 0xFF;
            ySubpixel = yFixed & 0xFF;

            if (--timer < 0) {
                routine = ROUTINE_WAIT_END;
                timer = 0x3F;
            }
        }

        private void updateEndWait() {
            if (--timer < 0) {
                routine = ROUTINE_DESCEND;
            }
        }

        private void updateDescend() {
            currentY += 2;
            if (currentY >= baseY) {
                setDestroyed(true);
            }
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(HALF_WIDTH + SIDE_PADDING, HALF_HEIGHT, HALF_HEIGHT + 1);
        }

        @Override
        public int getTopLandingHalfWidth(PlayableEntity playerEntity, int collisionHalfWidth) {
            return HALF_WIDTH;
        }

        @Override
        public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
            if (playerEntity == null || playerEntity.getInvulnerable()) {
                return;
            }
            int playerY = playerEntity.getCentreY();
            if (playerY - currentY + 0x28 < 0) {
                return;
            }
            playerEntity.applyHurt(currentX);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_MOVING_SPIKE_PLATFORM);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, mirrored, false);
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }
    }

    static final class MgzBossCameraScrollHelper extends AbstractObjectInstance {
        private final int targetX;

        MgzBossCameraScrollHelper(int targetX) {
            super(new ObjectSpawn(targetX, 0, 0, 0, 0, false, 0), "MgzBossCameraScrollHelper");
            this.targetX = targetX;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public int getX() {
            return targetX;
        }

        @Override
        public int getY() {
            return 0;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (services().camera() == null) {
                setDestroyed(true);
                return;
            }
            int nextX = services().camera().getX() + 1;
            services().camera().setX((short) nextX);
            services().camera().setMinX((short) nextX);
            services().camera().setMaxX((short) nextX);
            if (nextX >= targetX) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
