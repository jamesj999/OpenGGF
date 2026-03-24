package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestSonic3kAIZEvents {

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

        assertTrue("Expected mutation applied during fire transition before reload", sawMutationBeforeReload);
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
        assertTrue("sourceWorldX should be in cycling range",
                beforeReload.sourceWorldX() >= 0x1000 && beforeReload.sourceWorldX() <= 0x1060);

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
        assertEquals("Curtain should fully cover the screen by the mutation handoff",
                224, state.coverHeightPx());
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
            assertTrue("cover height regressed at frame " + i, state.coverHeightPx() >= previous);
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
        assertTrue("Curtain should be active on first frame", initial.coverHeightPx() >= 0);

        // The lerp phase slowly converges bgY toward 0x68 (1/32 per frame).
        // Fire tiles start at BG Y=0x100, so cover = bgY + 224 - 0x100.
        // After ~10 frames bgY reaches ~0x30 and cover exceeds 16.
        FireCurtainRenderState state = initial;
        int i = 1;
        for (; i < 15; i++) {
            events.update(0, i);
            state = events.getFireCurtainRenderState(224);
        }
        assertTrue("Curtain should begin covering within the lerp phase", state.coverHeightPx() >= 16);

        for (; i < 240 && state.stage() == FireCurtainStage.AIZ1_RISING; i++) {
            events.update(0, i);
            state = events.getFireCurtainRenderState(224);
        }

        assertEquals("Curtain should be fully screen-covering by the mutation handoff",
                224, state.coverHeightPx());
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
        assertTrue("Expected wavy fire-column offsets", hasVariation);
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

        assertTrue("Expected to reach AIZ2 WaitFire continuation", sawWaitFire);
        assertTrue("Expected WaitFire to switch to the $200 source strip", sawAiz2SourceStrip);
        assertFalse("Curtain should eventually clear after AIZ2 WaitFire", act2Events.getFireCurtainRenderState(224).active());
    }
}
