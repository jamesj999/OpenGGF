package com.openggf.game.render;

import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAdvancedRenderModeController {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void emptyControllerResolvesDisabledFrameState() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        AdvancedRenderModeController controller = new AdvancedRenderModeController();

        AdvancedRenderFrameState state = controller.resolve(new AdvancedRenderModeContext(
                runtime.getCamera(),
                0,
                runtime.getLevelManager(),
                0,
                0,
                runtime.getCamera().getX()));

        assertFalse(state.enableForegroundHeatHaze());
        assertFalse(state.enablePerLineForegroundScroll());
        assertNull(state.foregroundPerColumnVScrollOverride());
    }

    @Test
    void registeredModesMergeFrameStateContributions() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        AdvancedRenderModeController controller = new AdvancedRenderModeController();

        controller.register(new AdvancedRenderMode() {
            @Override
            public String id() {
                return "heat-haze";
            }

            @Override
            public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
                builder.enableForegroundHeatHaze();
            }
        });
        controller.register(new AdvancedRenderMode() {
            @Override
            public String id() {
                return "slot-scroll";
            }

            @Override
            public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
                builder.enablePerLineForegroundScroll();
                builder.setForegroundPerColumnVScrollOverride(new short[]{3, 5, 7});
            }
        });

        AdvancedRenderFrameState state = controller.resolve(new AdvancedRenderModeContext(
                runtime.getCamera(),
                10,
                runtime.getLevelManager(),
                1,
                2,
                runtime.getCamera().getX()));

        assertTrue(state.enableForegroundHeatHaze());
        assertTrue(state.enablePerLineForegroundScroll());
        assertArrayEquals(new short[]{3, 5, 7}, state.foregroundPerColumnVScrollOverride());
    }

    @Test
    void clearRemovesRegisteredModes() {
        AdvancedRenderModeController controller = new AdvancedRenderModeController();
        controller.register(new AdvancedRenderMode() {
            @Override
            public String id() {
                return "one";
            }

            @Override
            public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
                builder.enableForegroundHeatHaze();
            }
        });

        assertFalse(controller.isEmpty());
        assertEquals(1, controller.size());

        controller.clear();

        assertTrue(controller.isEmpty());
        assertEquals(0, controller.size());
        assertSame(AdvancedRenderFrameState.disabled(), controller.resolve(null));
    }
}
