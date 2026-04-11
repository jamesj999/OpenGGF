package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic3kAIZEvents {

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
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
        GameServices.level().resetState();
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
        assertFalse(request.showInLevelTitleCard());
        assertEquals(S3kSeamlessMutationExecutor.MUTATION_AIZ1_POST_RELOAD_ACT2, request.mutationKey());
        assertTrue(request.musicOverrideId() >= 0);
    }

    @Test
    public void fireTransitionAppliesMutationBeforeActReload() {
        LevelManager levelManager = GameServices.level();
        levelManager.resetState();

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
    public void postFireHazeOnlyEnablesAfterBurnHandoff() {
        LevelManager levelManager = GameServices.level();
        levelManager.resetState();

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
        LevelManager levelManager = GameServices.level();
        levelManager.resetState();

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
        LevelManager levelManager = GameServices.level();
        levelManager.resetState();

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
        GameServices.level().resetState();
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
        GameServices.level().resetState();
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
        GameServices.level().resetState();
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
        GameServices.level().resetState();
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
        GameServices.level().resetState();
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
        GameServices.level().resetState();
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
        GameServices.level().resetState();
        Camera camera = GameServices.camera();

        // Direct entry: no pending fire sequence
        Sonic3kAIZEvents.resetGlobalState();
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
}


