package com.openggf.game;

import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void parkAndResumeClearsQueuedMutationsButKeepsRegisteredRenderState() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        runtime.getZoneLayoutMutationPipeline().queue(context -> MutationEffects.redrawAllTilemaps());
        runtime.getSpecialRenderEffectRegistry().register(noOpEffect());
        runtime.getAdvancedRenderModeController().register(noOpMode());

        RuntimeManager.parkCurrent();
        GameRuntime resumed = RuntimeManager.resumeParked(runtime.getGameplayModeContext());

        assertSame(runtime, resumed);
        assertTrue(resumed.getZoneLayoutMutationPipeline().isEmpty());
        assertFalse(resumed.getSpecialRenderEffectRegistry().isEmpty());
        assertFalse(resumed.getAdvancedRenderModeController().isEmpty());
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
