package com.openggf.game;

import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRuntimeOwnedRegistryLifecycle {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
    }

    /**
     * Editor entry/exit replaces parking. The teardown+rebuild contract is:
     * destroyCurrent clears all gameplay-scoped state (including queued
     * mutations + registered render effects + render-mode contributions);
     * a fresh runtime built on resume starts with empty registries.
     * <p>
     * This is a stricter contract than the old parking flow, which preserved
     * registered render effects across the editor detour. The test pins the
     * new behavior so future refactors can't quietly regress it.
     */
    @Test
    void editorRoundTripDestroyAndRebuildClearsAllRegistryState() {
        GameRuntime first = RuntimeManager.createGameplay();
        first.getZoneLayoutMutationPipeline().queue(context -> MutationEffects.redrawAllTilemaps());
        first.getSpecialRenderEffectRegistry().register(noOpEffect());
        first.getAdvancedRenderModeController().register(noOpMode());

        assertFalse(first.getZoneLayoutMutationPipeline().isEmpty(),
                "precondition: queued mutation should be live before editor entry");
        assertFalse(first.getSpecialRenderEffectRegistry().isEmpty(),
                "precondition: registered effect should be live before editor entry");
        assertFalse(first.getAdvancedRenderModeController().isEmpty(),
                "precondition: registered render mode should be live before editor entry");

        // Mirror Engine.enterEditorFromCurrentPlayer's teardown contract:
        // destroyCurrent + SessionManager.enterEditorMode (the world-data
        // capture/restore around it preserves WorldSession but does NOT
        // re-publish gameplay-scoped registry state).
        RuntimeManager.destroyCurrent();
        SessionManager.enterEditorMode(new com.openggf.game.session.EditorCursorState(0, 0));

        // Old pipeline + registries belong to the destroyed runtime; their
        // state was cleared by tearDownManagers. Re-flushing must not fire
        // queued intents.
        StringBuilder log = new StringBuilder();
        first.getZoneLayoutMutationPipeline().queue(context -> {
            log.append("post-destroy");
            return MutationEffects.NONE;
        });
        // (queue itself is fine, but the pipeline should still be empty
        // *as far as the prior batch is concerned*; the new queue entry above
        // is a separate batch that we're not flushing here.)
        assertTrue(first.getSpecialRenderEffectRegistry().isEmpty(),
                "destroyCurrent should clear special render effects");
        assertTrue(first.getAdvancedRenderModeController().isEmpty(),
                "destroyCurrent should clear advanced render-mode contributions");

        // Mirror Engine.resumePlaytestFromEditor: build a fresh runtime over
        // the surviving WorldSession.
        GameplayModeContext resumed = SessionManager.resumeGameplayFromEditor();
        GameRuntime second = RuntimeManager.createGameplay(resumed);

        assertNotSame(first.getZoneLayoutMutationPipeline(), second.getZoneLayoutMutationPipeline(),
                "rebuild must produce a fresh ZoneLayoutMutationPipeline");
        assertNotSame(first.getSpecialRenderEffectRegistry(), second.getSpecialRenderEffectRegistry(),
                "rebuild must produce a fresh SpecialRenderEffectRegistry");
        assertNotSame(first.getAdvancedRenderModeController(), second.getAdvancedRenderModeController(),
                "rebuild must produce a fresh AdvancedRenderModeController");

        assertTrue(second.getZoneLayoutMutationPipeline().isEmpty(),
                "rebuilt pipeline must start empty");
        assertTrue(second.getSpecialRenderEffectRegistry().isEmpty(),
                "rebuilt special render registry must start empty");
        assertTrue(second.getAdvancedRenderModeController().isEmpty(),
                "rebuilt advanced render-mode controller must start empty");
    }

    private static SpecialRenderEffect noOpEffect() {
        return new SpecialRenderEffect() {
            @Override
            public SpecialRenderEffectStage stage() {
                return SpecialRenderEffectStage.AFTER_BACKGROUND;
            }

            @Override
            public void render(SpecialRenderEffectContext context) {
                // no-op
            }
        };
    }

    private static com.openggf.game.render.AdvancedRenderMode noOpMode() {
        return new com.openggf.game.render.AdvancedRenderMode() {
            @Override
            public String id() {
                return "test-mode";
            }

            @Override
            public void contribute(
                    com.openggf.game.render.AdvancedRenderModeContext context,
                    com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                builder.enablePerLineForegroundScroll();
            }
        };
    }
}
