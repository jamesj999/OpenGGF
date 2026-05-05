package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.sonic3k.objects.AizBattleshipInstance;
import com.openggf.game.sonic3k.objects.AizCollapsingLogBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.AizEndBossInstance;
import com.openggf.game.sonic3k.objects.AizIntroArtLoader;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.LogCaptureHandler;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kAIZEvents {
    private HeadlessTestFixture fixture;

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        RuntimeManager.createGameplay();
        AizIntroArtLoader.reset();
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
    }

    @AfterEach
    public void tearDown() {
        AizIntroArtLoader.reset();
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
    }

    @Test
    public void introArtFallbackDoesNotLogWarningsWhenRomBackedAssetsAreUnavailable() {
        Logger logger = Logger.getLogger(AizIntroArtLoader.class.getName());
        LogCaptureHandler handler = new LogCaptureHandler();
        boolean useParentHandlers = logger.getUseParentHandlers();
        Level previousLevel = logger.getLevel();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        try {
            AizIntroArtLoader.reset();
            AizIntroArtLoader.loadAllIntroArt(new TestObjectServices());
            assertEquals(0, handler.countAtOrAbove(Level.WARNING));
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(useParentHandlers);
            logger.setLevel(previousLevel);
            AizIntroArtLoader.reset();
        }
    }

    @Test
    public void fireTransitionOnRomBackedAizLevelDoesNotLogWarnings() {
        Logger zoneLogger = Logger.getLogger(Sonic3kZoneEvents.class.getName());
        Logger introLogger = Logger.getLogger(AizPlaneIntroInstance.class.getName());
        LogCaptureHandler zoneHandler = new LogCaptureHandler();
        LogCaptureHandler introHandler = new LogCaptureHandler();
        boolean zoneUseParentHandlers = zoneLogger.getUseParentHandlers();
        boolean introUseParentHandlers = introLogger.getUseParentHandlers();
        Level previousZoneLevel = zoneLogger.getLevel();
        Level previousIntroLevel = introLogger.getLevel();
        zoneLogger.addHandler(zoneHandler);
        introLogger.addHandler(introHandler);
        zoneLogger.setUseParentHandlers(false);
        introLogger.setUseParentHandlers(false);
        zoneLogger.setLevel(Level.ALL);
        introLogger.setLevel(Level.ALL);
        try {
            Camera camera = GameServices.camera();
            camera.setX((short) 0x2F10);
            camera.setY((short) 0x0200);

            var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
            events.init(0);
            events.setEventsFg5(true);

            for (int i = 0; i < 320 && !events.isAct2TransitionRequested(); i++) {
                events.update(0, i);
            }

            assertTrue(events.isAct2TransitionRequested());
            assertEquals(0, zoneHandler.countAtOrAbove(Level.WARNING));
            assertEquals(0, introHandler.countAtOrAbove(Level.WARNING));
        } finally {
            zoneLogger.removeHandler(zoneHandler);
            introLogger.removeHandler(introHandler);
            zoneLogger.setUseParentHandlers(zoneUseParentHandlers);
            introLogger.setUseParentHandlers(introUseParentHandlers);
            zoneLogger.setLevel(previousZoneLevel);
            introLogger.setLevel(previousIntroLevel);
        }
    }

    @Test
    public void initWithIntroSkipDoesNotSpawnIntroObject() {
        Camera camera = GameServices.camera();
        var events = new Sonic3kAIZEvents(
                new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null));
        events.init(0);
        assertEquals(0, events.getEventRoutine());
    }

    @Test
    public void initForAct1WithNormalBootstrapRequestsIntro() {
        Camera camera = GameServices.camera();
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        // When bootstrap is NORMAL and act is 0, intro should be requested
        assertTrue(events.shouldSpawnIntro(0));
    }

    @Test
    public void initForAct2DoesNotRequestIntro() {
        Camera camera = GameServices.camera();
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        assertFalse(events.shouldSpawnIntro(1));
    }

    @Test
    public void act1ResizeLocksCameraMinXAtFirePaletteGate() {
        Camera camera = GameServices.camera();
        var events = new Sonic3kAIZEvents(
                new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null));
        events.init(0);
        camera.setX((short) 0x2D80);
        camera.setY((short) 0x02E0);
        camera.setMinX((short) 0x1308);
        camera.setFrozen(true);
        assertTrue(AizPlaneIntroInstance.isMainLevelPhaseActive(), "test precondition: AIZ main-level phase is active");
        assertEquals(0x2D80, camera.getX() & 0xFFFF, "test precondition: camera is at the resize gate");

        events.update(0, 0);

        assertEquals(0x2D80, camera.getMinX() & 0xFFFF,
                "AIZ1_Resize loc_1C594 writes Camera_min_X_pos=$2D80 at the fire palette gate");
    }

    @Test
    public void bossSmallCompletionReleasesBattleshipScrollLockFreeze() throws Exception {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x4640);
        camera.setMinX((short) 0x4640);
        camera.setMaxX((short) 0x4640);
        camera.setFrozen(false);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        setPrivateBoolean(events, "battleshipAutoScrollActive", true);

        events.updatePrePhysics(1);
        assertTrue(camera.getFrozen(), "AIZ2_DoShipLoop Scroll_lock should suppress the normal camera follow step");

        events.onBossSmallComplete();

        assertFalse(camera.getFrozen(),
                "Obj_AIZ2BossSmall clears Scroll_lock before writing Camera_max_X_pos=$6000");
        assertEquals(0x6000, camera.getMaxX() & 0xFFFF,
                "Obj_AIZ2BossSmall loc_50720 writes Camera_max_X_pos=$6000 on exit");
    }

    @Test
    public void introObjectIsReadyBeforeFirstAizGameplayFrame() {
        AizPlaneIntroInstance intro = AizPlaneIntroInstance.getActiveIntroInstance();
        assertNotNull(intro, "ROM SpawnLevelMainSprites installs Obj_AIZPlaneIntro before first Process_Sprites");
        assertFalse(GameServices.camera().isLevelStarted());

        AbstractPlayableSprite sonic = fixture.sprite();
        assertEquals(0x0040, sonic.getCentreX() & 0xFFFF);
        assertEquals(0x0420, sonic.getCentreY() & 0xFFFF);

        List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getRegisteredSidekicks();
        assertFalse(sidekicks.isEmpty(), "AIZ Sonic+Tails intro should spawn Player_2 before first frame");
        AbstractPlayableSprite tails = sidekicks.get(0);

        fixture.stepFrame(false, false, false, false, false);

        assertEquals(0, sonic.getYSpeed() & 0xFFFF,
                "Obj_AIZPlaneIntro routine 0 should clear y_vel before the first strict frame compare");
        assertFalse(sonic.getAir(),
                "Obj_AIZPlaneIntro routine 0 should keep Sonic grounded for the first strict frame compare");
        assertEquals(0x0020, tails.getCentreX() & 0xFFFF,
                "Obj_Tails routine 0 leaves Player_2 at the SpawnLevelMainSprites position for one frame");
        assertEquals(0x0424, tails.getCentreY() & 0xFFFF,
                "Obj_Tails routine 0 leaves Player_2 at the SpawnLevelMainSprites position for one frame");
        assertFalse(tails.getAir());

        fixture.stepFrame(false, false, false, false, false);

        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                "Tails_CPU_Control loc_13A10 parks AIZ intro Tails on the second object tick");
        assertEquals(0, tails.getCentreY() & 0xFFFF);
        assertTrue(tails.getAir());
    }

    @Test
    public void updateFallbackDoesNotDuplicateExistingIntroObject() {
        assertEquals(1, countActiveIntroObjects(),
                "ROM SpawnLevelMainSprites installs exactly one Obj_AIZPlaneIntro object");

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.update(0, 0);

        assertEquals(1, countActiveIntroObjects(),
                "AIZ intro update fallback must reuse the fixed intro object slot");
    }

    @Test
    public void fireCurtainStateIsInactiveOutsideTransition() {
        Camera camera = GameServices.camera();
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        assertFalse(state.active());
        assertEquals(0, state.coverHeightPx());
    }

    @Test
    public void eventsFg5StartsFireTransitionAndRequestsSeamlessFlow() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        events.update(0, 0);
        assertTrue(events.isFireTransitionActive());
        assertFalse(events.isAct2TransitionRequested());

        for (int i = 1; i < 320 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
        }

        assertTrue(events.isAct2TransitionRequested());
        SeamlessLevelTransitionRequest request = GameServices.level().consumeSeamlessTransitionRequest();
        assertNotNull(request);
        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL, request.type());
        assertEquals(0, request.targetZone());
        assertEquals(1, request.targetAct());
        assertFalse(request.preserveMusic());
        assertTrue(request.preserveLevelGamestate());
        assertFalse(request.showInLevelTitleCard());
        assertEquals(S3kSeamlessMutationExecutor.MUTATION_AIZ1_POST_RELOAD_ACT2, request.mutationKey());
        assertTrue(request.musicOverrideId() >= 0);
    }

    @Test
    public void eventsFg5TransitionWritesProgressionSaveForActiveSlot() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        String gameCode = "test_aiz_transition_save";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        GameModule sessionModule = mock(GameModule.class);
        when(sessionModule.getSaveSnapshotProvider()).thenReturn((reason, ctx) -> Map.of("marker", "aiz_transition"));
        when(sessionModule.rngFlavour()).thenReturn(GameRng.Flavour.S3K);

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of("tails")), 0, 0);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(sessionModule, saveContext);
        RuntimeManager.createGameplay(gameplayMode);

        GameServices.level().resetState();
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        for (int i = 1; i < 320 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
        }

        assertTrue(events.isAct2TransitionRequested());
        assertTrue(Files.exists(saveDir.resolve("slot1.json")));
        deleteRecursively(saveDir);
    }

    @Test
    public void fireTransitionAppliesMutationBeforeActReload() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        boolean sawMutationBeforeReload = false;
        for (int i = 0; i < 260 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
            if (events.isFireTransitionActive()
                    && !events.isAct2TransitionRequested()
                    && events.getFireTransitionBgY() >= 0x190) {
                sawMutationBeforeReload = true;
            }
        }

        assertTrue(sawMutationBeforeReload, "Expected mutation applied during fire transition before reload");
    }

    @Test
    public void fireTransitionKeepsLiveTerrainTablesUntilActReload() {
        Sonic3kLevel level = (Sonic3kLevel) GameServices.level().getCurrentLevel();

        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        int[][] blocksBefore = snapshotBlocks(level);
        int[][] chunksBefore = snapshotChunks(level);

        boolean reachedFireMutationHandoff = false;
        for (int i = 0; i < 260 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
            if (events.isFireTransitionActive() && events.getFireTransitionBgY() >= 0x190) {
                reachedFireMutationHandoff = true;
                break;
            }
        }

        assertTrue(reachedFireMutationHandoff, "Expected AIZ1 fire mutation handoff before act reload");
        assert2dArrayEquals(blocksBefore, snapshotBlocks(level),
                "AIZ1 fire handoff must not expose AIZ2 block terrain before Load_Level/LoadSolids");
        assert2dArrayEquals(chunksBefore, snapshotChunks(level),
                "AIZ1 fire handoff must not expose AIZ2 chunk terrain before Load_Level/LoadSolids");
    }

    @Test
    public void postFireHazeOnlyEnablesAfterBurnHandoff() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        assertFalse(events.isPostFireHazeActive());

        events.setEventsFg5(true);
        events.update(0, 0);
        assertFalse(events.isPostFireHazeActive());

        for (int i = 1; i < 320 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
        }

        assertTrue(events.isAct2TransitionRequested());
        assertFalse(events.isPostFireHazeActive());

        var act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act2Events.init(1);
        assertFalse(act2Events.isPostFireHazeActive());

        for (int i = 0; i < 240 && !act2Events.isPostFireHazeActive(); i++) {
            act2Events.update(1, i);
        }
        assertTrue(act2Events.isPostFireHazeActive());
    }

    @Test
    public void fireCurtainRenderStateCarriesAcrossSeamlessReload() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        for (int i = 0; i < 320 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
        }

        assertTrue(events.isAct2TransitionRequested());
        FireCurtainRenderState beforeReload = events.getFireCurtainRenderState(224);
        assertTrue(beforeReload.active());
        assertEquals(224, beforeReload.coverHeightPx());
        // sourceWorldX cycles through 0x1000..0x1060 matching ROM's Camera_X_pos_BG_copy
        assertTrue(beforeReload.sourceWorldX() >= 0x1000 && beforeReload.sourceWorldX() <= 0x1060, "sourceWorldX should be in cycling range");

        var act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act2Events.init(1);

        FireCurtainRenderState afterReload = act2Events.getFireCurtainRenderState(224);
        assertTrue(afterReload.active());
        assertEquals(224, afterReload.coverHeightPx());
        assertEquals(beforeReload.wavePhase(), afterReload.wavePhase());
        // requestAct2Transition() intentionally resets BG Y to 0x1E0 for scroll-off start
        assertEquals(0x01E0, afterReload.sourceWorldY());
        assertEquals(FireCurtainStage.AIZ2_REDRAW, afterReload.stage());
    }

    @Test
    public void fireCurtainIsFullScreenWhenFireMutationStarts() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        FireCurtainRenderState state = FireCurtainRenderState.inactive();
        for (int i = 0; i < 320 && events.getFireTransitionBgY() < 0x190; i++) {
            events.update(0, i);
            state = events.getFireCurtainRenderState(224);
        }

        assertTrue(state.active());
        assertTrue(events.getFireTransitionBgY() >= 0x190);
        assertEquals(224, state.coverHeightPx(), "Curtain should fully cover the screen by the mutation handoff");
        assertEquals(FireCurtainStage.AIZ1_REFRESH, state.stage());
    }

    @Test
    public void fireCurtainCoverHeightIsMonotonicDuringRise() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        int previous = 0;
        for (int i = 0; i < 80 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (!state.active() || (state.stage() != FireCurtainStage.AIZ1_RISING
                    && state.stage() != FireCurtainStage.AIZ1_REFRESH)) {
                continue;
            }
            assertTrue(state.coverHeightPx() >= previous, "cover height regressed at frame " + i);
            previous = state.coverHeightPx();
        }
    }

    @Test
    public void fireCurtainStartsImmediatelyAndReachesFullCoverByMutation() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        events.update(0, 0);
        FireCurtainRenderState initial = events.getFireCurtainRenderState(224);
        assertTrue(initial.active());
        // First frame just starts the transition (bgY=0x20); fire tiles at BG Y >= 0x100
        // are not visible yet.  The rise advances on subsequent frames.
        assertTrue(initial.coverHeightPx() >= 0, "Curtain should be active on first frame");

        // The lerp phase slowly converges bgY toward 0x68 (1/32 per frame).
        // Fire tiles start at BG Y=0x100, so cover = bgY + 224 - 0x100.
        // After ~10 frames bgY reaches ~0x30 and cover exceeds 16.
        FireCurtainRenderState state = initial;
        int i = 1;
        for (; i < 15; i++) {
            events.update(0, i);
            state = events.getFireCurtainRenderState(224);
        }
        assertTrue(state.coverHeightPx() >= 16, "Curtain should begin covering within the lerp phase");

        for (; i < 240 && state.stage() == FireCurtainStage.AIZ1_RISING; i++) {
            events.update(0, i);
            state = events.getFireCurtainRenderState(224);
        }

        assertEquals(224, state.coverHeightPx(), "Curtain should be fully screen-covering by the mutation handoff");
        assertTrue(state.stage() == FireCurtainStage.AIZ1_REFRESH
                || state.stage() == FireCurtainStage.AIZ1_FINISH);
    }

    @Test
    public void fireCurtainStateExposesDeterministicTwentyColumnWaveData() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);
        events.update(0, 8);

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        assertTrue(state.active());
        assertEquals(20, state.columnWaveOffsetsPx().length);

        boolean hasVariation = false;
        int first = state.columnWaveOffsetsPx()[0];
        for (int i = 1; i < state.columnWaveOffsetsPx().length; i++) {
            if (state.columnWaveOffsetsPx()[i] != first) {
                hasVariation = true;
                break;
            }
        }
        assertTrue(hasVariation, "Expected wavy fire-column offsets");
    }

    @Test
    public void fireCurtainHandoffAccessorIsPureWithinTheSameFrame() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        for (int i = 0; i < 320 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
        }

        var act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act2Events.init(1);

        FireCurtainRenderState a = act2Events.getFireCurtainRenderState(224);
        FireCurtainRenderState b = act2Events.getFireCurtainRenderState(224);

        assertEquals(a.coverHeightPx(), b.coverHeightPx());
        assertEquals(a.wavePhase(), b.wavePhase());
        assertEquals(a.frameCounter(), b.frameCounter());
    }

    @Test
    public void act2ContinuationKeepsCurtainUntilWaitFireFinishes() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var act1Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act1Events.init(0);
        act1Events.setEventsFg5(true);
        for (int i = 0; i < 320 && !act1Events.isAct2TransitionRequested(); i++) {
            act1Events.update(0, i);
        }

        var act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act2Events.init(1);

        FireCurtainRenderState state = act2Events.getFireCurtainRenderState(224);
        assertTrue(state.active());
        assertEquals(FireCurtainStage.AIZ2_REDRAW, state.stage());

        boolean sawWaitFire = false;
        boolean sawAiz2SourceStrip = false;
        for (int i = 0; i < 240 && act2Events.getFireCurtainRenderState(224).active(); i++) {
            act2Events.update(1, i);
            state = act2Events.getFireCurtainRenderState(224);
            if (state.stage() == FireCurtainStage.AIZ2_WAIT_FIRE) {
                sawWaitFire = true;
                if (state.sourceWorldX() == 0x0200) {
                    sawAiz2SourceStrip = true;
                }
            }
        }

        assertTrue(sawWaitFire, "Expected to reach AIZ2 WaitFire continuation");
        assertTrue(sawAiz2SourceStrip, "Expected WaitFire to switch to the $200 source strip");
        assertFalse(act2Events.getFireCurtainRenderState(224).active(), "Curtain should eventually clear after AIZ2 WaitFire");
    }

    /**
     * When arriving at AIZ2 through the AIZ1 fire transition, SonicResize1
     * must NOT skip the miniboss path (SonicResize2). The ROM gates this on
     * Apparent_zone_and_act != AIZ2 â€” during the fire transition, the apparent
     * zone is still AIZ1.
     *
     * This was the root cause of the "AIZ1 mid-act transition snapping" bug:
     * unconditionally setting Camera_min_X_pos = $F50 at cameraX >= $2E0
     * snapped the player to the miniboss arena immediately after the spikes.
     */
    @Test
    public void aiz2FromFireTransitionDoesNotSkipMinibossPath() {
        Camera camera = GameServices.camera();

        // Simulate arrival from AIZ1 fire transition: run act 1 fire sequence
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);
        var act1Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act1Events.init(0);
        act1Events.setEventsFg5(true);
        for (int i = 0; i < 320 && !act1Events.isAct2TransitionRequested(); i++) {
            act1Events.update(0, i);
        }
        assertTrue(act1Events.isAct2TransitionRequested(), "Fire transition should have requested act 2");

        // Begin act 2 with pending fire sequence (came from fire transition)
        var act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act2Events.init(1);

        // Simulate player past the first spikes â€” cameraX just past $2E0
        camera.setX((short) 0x0300);
        camera.setY((short) 0x0200);
        camera.setMinX((short) 0);

        // Run update to trigger SonicResize1
        // Wait for the fire curtain to clear first so resize runs
        for (int i = 0; i < 240; i++) {
            act2Events.update(1, i);
        }

        // minX must NOT have been snapped to $F50 (the miniboss lock)
        int minX = camera.getMinX() & 0xFFFF;
        assertTrue(minX < 0x0F50, "Camera minX should NOT be locked to miniboss area ($F50) after fire transition, was 0x"
                + Integer.toHexString(minX));
    }

    /**
     * When entering AIZ2 directly (level select / death restart), SonicResize1
     * SHOULD skip the miniboss path because the miniboss has already been defeated.
     * ROM: Apparent_zone_and_act == AIZ2 â†’ skip to SonicResize3.
     */
    @Test
    public void aiz2DirectEntrySkipsMinibossPath() {
        Camera camera = GameServices.camera();

        // Direct entry: no pending fire sequence
        Sonic3kAIZEvents.resetGlobalState();
        // ROM: LevelSelect_StartZone (sonic3k.asm:10222) and
        // Load_Starpost_Settings (sonic3k.asm:61760) set Apparent_zone_and_act
        // = $0001 for direct AIZ2 entry; the engine mirrors this through
        // LevelManager.setApparentAct.
        GameServices.level().setApparentAct(1);
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(1);  // Act 2 directly, no fire transition

        // Camera past $2E0 (first spikes area)
        camera.setX((short) 0x0300);
        camera.setY((short) 0x0200);
        camera.setMinX((short) 0);

        // Run update to trigger SonicResize1
        events.update(1, 0);

        // minX SHOULD be set to $F50 (skipping miniboss area)
        int minX = camera.getMinX() & 0xFFFF;
        assertEquals(0x0F50, minX, "Camera minX should be locked to $F50 for direct AIZ2 entry");
    }

    /**
     * Reload-resume gating: when AIZ2 is loaded with no pending fire sequence
     * but apparentAct == 0 (e.g., trace reload-resume after the AIZ1 fire
     * transition was committed), SonicResize1 must NOT skip the miniboss
     * path.  This is the regression caught by the AIZ trace at F7171:
     * the old heuristic set enteredAsAct2 = true whenever
     * pendingFireSequence == null, which flipped the engine into the
     * post-miniboss branch even though ROM's Apparent_zone_and_act stayed
     * at 0.  ROM cite: sonic3k.asm:39046-39058 (AIZ2_SonicResize1).
     */
    @Test
    public void aiz2ReloadResumeWithApparentAct0DoesNotSkipMinibossPath() {
        Camera camera = GameServices.camera();

        Sonic3kAIZEvents.resetGlobalState();
        // ROM: AIZ1_AIZ2_Transition (sonic3k.asm:104627) does not write
        // Apparent_zone_and_act; it stays at AIZ1=$0000 across the
        // continuation.  The engine's seamless transition coordinator
        // preserves apparentAct, matching this.
        GameServices.level().setApparentAct(0);
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(1); // Act 2 reload-resume, no pending fire sequence

        camera.setX((short) 0x0300);
        camera.setY((short) 0x0200);
        camera.setMinX((short) 0);

        events.update(1, 0);

        int minX = camera.getMinX() & 0xFFFF;
        assertTrue(minX < 0x0F50,
                "Camera minX must NOT be locked to miniboss area on reload-resume with apparentAct=0, was 0x"
                        + Integer.toHexString(minX));
    }

    @Test
    public void aiz2BattleshipBombingStartsFromEventsFg4Handoff() {
        Camera camera = GameServices.camera();
        Sonic3kAIZEvents.resetGlobalState();
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(1);

        camera.setX((short) 0x3C00);
        camera.setY((short) 0x0200);
        events.setDynamicResizeRoutine(8);
        events.update(1, 0);
        assertEquals(0x0A, events.getDynamicResizeRoutine(), "Stage $08 should only prepare battleship art");
        assertTrue(events.isEventsFg5(), "Stage $08 should raise Events_fg_5 for BG setup");
        assertFalse(events.isEventsFg4(), "Stage $08 must not trigger the bombing screen event");
        assertFalse(events.isBattleshipAutoScrollActive(), "Bombing should not start at the art-load gate");

        camera.setX((short) 0x4000);
        events.update(1, 1);
        assertEquals(0x0C, events.getDynamicResizeRoutine(), "Stage $0A should lock min Y");
        assertFalse(events.isEventsFg4(), "Stage $0A must not trigger the bombing screen event");
        assertFalse(events.isBattleshipAutoScrollActive(), "Bombing should not start at the vertical-lock gate");

        events.update(1, 2);
        assertEquals(0x0E, events.getDynamicResizeRoutine(), "Stage $0C should lock max Y");
        assertFalse(events.isEventsFg4(), "Stage $0C must not trigger the bombing screen event");
        assertFalse(events.isBattleshipAutoScrollActive(), "Bombing should wait for the $4160 gate");

        camera.setX((short) 0x4160);
        events.update(1, 3);
        assertEquals(0x10, events.getDynamicResizeRoutine(), "Stage $0E should advance to terminal state");
        assertTrue(events.isEventsFg4(), "Stage $0E should raise Events_fg_4 for AIZ2_ScreenEvent");
        assertFalse(events.isBattleshipAutoScrollActive(), "Resize should not start bombing directly");

        events.update(1, 4);
        assertFalse(events.isEventsFg4(), "AIZ2_ScreenEvent should consume Events_fg_4");
        assertTrue(events.isBattleshipAutoScrollActive(), "AIZ2_ScreenEvent should start the bombing sequence");
    }

    @Test
    public void aiz2BattleshipRemainsActiveAfterScreenEventSpawnsIt() {
        Camera camera = GameServices.camera();
        Sonic3kAIZEvents.resetGlobalState();
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(1);

        camera.setX((short) 0x4160);
        camera.setY((short) 0x0200);
        events.setDynamicResizeRoutine(0x0E);
        events.update(1, 0);
        events.update(1, 1);

        var objectManager = GameServices.level().getObjectManager();
        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(AizBattleshipInstance.class::isInstance),
                "Screen event should spawn the battleship object");

        objectManager.update(camera.getX(), null, List.of(), 2, false);

        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(object -> object instanceof AizBattleshipInstance ship && !ship.isDestroyed()),
                "Battleship must survive normal object processing while it scrolls in from the sky");
    }

    @Test
    public void aiz2PostBombingShipLoopWrapsCameraAndPlayersByRomDistance() throws Exception {
        Camera camera = GameServices.camera();
        Sonic3kAIZEvents.resetGlobalState();
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(1);
        setPrivateBoolean(events, "battleshipAutoScrollActive", true);
        events.onBattleshipComplete();

        AbstractPlayableSprite sonic = fixture.sprite();
        camera.setFocusedSprite(sonic);
        camera.setX((short) 0x46BC);
        camera.setMinX((short) 0x46BC);
        camera.setMaxX((short) 0x46BC);
        sonic.setCentreXPreserveSubpixel((short) 0x4762);

        events.updatePrePhysics(1);

        assertEquals(0x44C0, camera.getX() & 0xFFFF,
                "AIZ2_DoShipLoop subtracts $200 from Camera_X_pos when Events_bg+$02=$46C0");
        assertEquals(0x4560, sonic.getCentreX() & 0xFFFF,
                "sub_50318 clamps the $200-wrapped player to Camera_X_pos+$A0 before movement");
        assertEquals(0x200, events.getLevelRepeatOffset(),
                "ROM Level_repeat_offset remains $200 on post-bombing AIZ2 ship-loop wraps");
    }

    @Test
    public void aiz2EndBossSpawnsFromEventsAtSonicWaterfallLock() {
        HeadlessTestFixture aiz2 = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 1)
                .startPosition((short) 0x4860, (short) 0x015A)
                .startPositionIsCentre()
                .build();
        Camera camera = aiz2.camera();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x015A);

        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents events = manager.getAizEvents();
        assertNotNull(events, "AIZ event handler should be active for AIZ2");

        events.update(1, 0);

        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .anyMatch(AizEndBossInstance.class::isInstance),
                "AIZ2 end-boss handoff should create the live Robotnik boss object at the waterfall");
    }

    @Test
    public void aiz2EndBossLockKeepsFireLogBridgeLiveForArenaEntry() {
        HeadlessTestFixture aiz2 = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 1)
                .startPosition((short) 0x4860, (short) 0x015A)
                .startPositionIsCentre()
                .build();
        Camera camera = aiz2.camera();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x015A);

        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents events = manager.getAizEvents();
        assertNotNull(events, "AIZ event handler should be active for AIZ2");

        events.update(1, 0);
        GameServices.level().getObjectManager().update(camera.getX(), aiz2.sprite(), List.of(), 1, false);

        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .anyMatch(object -> object instanceof AizCollapsingLogBridgeObjectInstance
                                && object.getX() == 0x48E0
                                && object.getY() == 0x0218),
                "ROM Obj_AIZCollapsingLogBridge loc_2AEE2 stays live at $48E0,$0218 and calls SolidObjectTop");
    }

    @Test
    public void aiz2FireLogBridgeSupportsSonicAtTraceArenaEntryPoint() {
        HeadlessTestFixture aiz2 = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 1)
                .startPosition((short) 0x4880, (short) 0x01FC)
                .startPositionIsCentre()
                .build();
        Camera camera = aiz2.camera();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x015A);
        aiz2.sprite().setXSpeed((short) 0x0600);
        aiz2.sprite().setGSpeed((short) 0x0600);
        aiz2.sprite().setAir(false);
        aiz2.sprite().setOnObject(false);

        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents events = manager.getAizEvents();
        assertNotNull(events, "AIZ event handler should be active for AIZ2");

        events.update(1, 0);
        GameServices.level().getObjectManager().update(camera.getX(), aiz2.sprite(), List.of(), 1,
                false, true, true);
        aiz2.sprite().setCentreXPreserveSubpixel((short) 0x4880);
        aiz2.sprite().setCentreYPreserveSubpixel((short) 0x01FC);
        aiz2.sprite().setXSpeed((short) 0x0600);
        aiz2.sprite().setGSpeed((short) 0x0600);
        aiz2.sprite().setAir(false);
        aiz2.sprite().setOnObject(false);
        GameServices.level().getObjectManager().update(camera.getX(), aiz2.sprite(), List.of(), 1,
                false, true, true);

        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .anyMatch(object -> object instanceof AizCollapsingLogBridgeObjectInstance
                                && object.getX() == 0x48E0
                                && object.getY() == 0x0218),
                "The fire log bridge must be active before checking its SolidObjectTop contact");
        assertTrue(aiz2.sprite().isOnObject(),
                "ROM loc_2AEE2 calls SolidObjectTop and sets Status_OnObj for Sonic at $4880,$01FC");
    }

    @Test
    public void aiz2EndBossEventSpawnUsesLayoutHeightNotArenaBaseHeight() {
        HeadlessTestFixture aiz2 = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 1)
                .startPosition((short) 0x4860, (short) 0x015A)
                .startPositionIsCentre()
                .build();
        Camera camera = aiz2.camera();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x015A);

        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents events = manager.getAizEvents();
        assertNotNull(events, "AIZ event handler should be active for AIZ2");

        events.update(1, 0);

        AizEndBossInstance boss = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(AizEndBossInstance.class::isInstance)
                .map(AizEndBossInstance.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(0x48A0, boss.getX(), "Sonic AIZ2 end boss should use the ROM layout X");
        assertEquals(0x01C0, boss.getY(), "Sonic AIZ2 end boss should use the ROM layout Y, not AIZBossSonicDat base Y");
    }

    @Test
    public void aiz2EndBossActivationKeepsSonicHighPriorityAtWaterfall() {
        HeadlessTestFixture aiz2 = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 1)
                .startPosition((short) 0x4860, (short) 0x015A)
                .startPositionIsCentre()
                .build();
        Camera camera = aiz2.camera();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x015A);

        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents events = manager.getAizEvents();
        assertNotNull(events, "AIZ event handler should be active for AIZ2");

        events.update(1, 0);
        GameServices.level().getObjectManager().update(camera.getX(), aiz2.sprite(), List.of(), 1, false);
        GameServices.level().getZoneFeatureProvider().update(aiz2.sprite(), camera.getX(), 0);

        assertTrue(events.isBossFlag(), "Boss activation should set Boss_flag");
        assertTrue(aiz2.sprite().isHighPriority(),
                "Sonic should render high-priority in front of the AIZ2 waterfall during the boss handoff");
    }

    @Test
    public void bossFlagDefaultsFalse() {
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        assertFalse(events.isBossFlag(), "Boss flag should default to false");
    }

    @Test
    public void bossFlagCanBeSet() {
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setBossFlag(true);
        assertTrue(events.isBossFlag(), "Boss flag should be true after setting");
    }

    @Test
    public void bossFlagResetsOnInit() {
        var events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setBossFlag(true);
        events.init(0);
        assertFalse(events.isBossFlag(), "Boss flag should reset to false on init");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private static long countActiveIntroObjects() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(AizPlaneIntroInstance.class::isInstance)
                .count();
    }

    private static int[][] snapshotBlocks(Sonic3kLevel level) {
        int[][] snapshot = new int[level.getBlockCount()][];
        for (int i = 0; i < level.getBlockCount(); i++) {
            snapshot[i] = level.getBlock(i).saveState();
        }
        return snapshot;
    }

    private static int[][] snapshotChunks(Sonic3kLevel level) {
        int[][] snapshot = new int[level.getChunkCount()][];
        for (int i = 0; i < level.getChunkCount(); i++) {
            snapshot[i] = level.getChunk(i).saveState();
        }
        return snapshot;
    }

    private static void assert2dArrayEquals(int[][] expected, int[][] actual, String message) {
        assertEquals(expected.length, actual.length, message + " length");
        for (int i = 0; i < expected.length; i++) {
            if (!Arrays.equals(expected[i], actual[i])) {
                throw new AssertionError(message + " at index " + i);
            }
        }
    }

    private static void setPrivateBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }
}
