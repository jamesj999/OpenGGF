package com.openggf.game.render;

import com.openggf.camera.Camera;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSpecialRenderEffectRegistry {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void registryDispatchesEffectsInRegistrationOrderWithinStage() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        SpecialRenderEffectRegistry registry = runtime.getSpecialRenderEffectRegistry();
        List<String> calls = new ArrayList<>();
        List<Integer> frameCounters = new ArrayList<>();

        registry.register(new RecordingEffect("bg-1", SpecialRenderEffectStage.AFTER_BACKGROUND, calls, frameCounters));
        registry.register(new RecordingEffect("bg-2", SpecialRenderEffectStage.AFTER_BACKGROUND, calls, frameCounters));
        registry.register(new RecordingEffect("fg-1", SpecialRenderEffectStage.AFTER_FOREGROUND, calls, frameCounters));

        SpecialRenderEffectContext context = contextFor(runtime);
        registry.dispatch(SpecialRenderEffectStage.AFTER_BACKGROUND, context);
        registry.dispatch(SpecialRenderEffectStage.AFTER_FOREGROUND, context);

        assertEquals(List.of("bg-1", "bg-2", "fg-1"), calls);
        assertEquals(List.of(42, 42, 42), frameCounters);
    }

    @Test
    void clearRemovesRegisteredEffects() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        SpecialRenderEffectRegistry registry = runtime.getSpecialRenderEffectRegistry();
        List<String> calls = new ArrayList<>();

        registry.register(new RecordingEffect("bg", SpecialRenderEffectStage.AFTER_BACKGROUND, calls, new ArrayList<>()));
        assertFalse(registry.isEmpty());

        registry.clear();
        registry.dispatch(SpecialRenderEffectStage.AFTER_BACKGROUND, contextFor(runtime));

        assertTrue(registry.isEmpty());
        assertTrue(calls.isEmpty());
    }

    @Test
    void runtimeExposesAndClearsRegistryThroughServices() {
        GameRuntime runtime = RuntimeManager.createGameplay();

        assertSame(runtime.getSpecialRenderEffectRegistry(), GameServices.specialRenderEffectRegistry());
        assertSame(runtime.getSpecialRenderEffectRegistry(), GameServices.specialRenderEffectRegistryOrNull());

        runtime.getSpecialRenderEffectRegistry().register(new RecordingEffect(
                "bg",
                SpecialRenderEffectStage.AFTER_BACKGROUND,
                new ArrayList<>(),
                new ArrayList<>()));
        assertFalse(runtime.getSpecialRenderEffectRegistry().isEmpty());

        RuntimeManager.destroyCurrent();

        assertNull(GameServices.specialRenderEffectRegistryOrNull());
        assertThrows(IllegalStateException.class, GameServices::specialRenderEffectRegistry);
        assertTrue(runtime.getSpecialRenderEffectRegistry().isEmpty());
    }

    @Test
    void contextCarriesFrameLocalRenderState() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        SpecialRenderEffectContext context = contextFor(runtime);

        assertSame(GameServices.camera(), context.camera());
        assertSame(GameServices.level(), context.levelManager());
        assertSame(GameServices.graphics(), context.graphicsManager());
        assertEquals(42, context.frameCounter());
    }

    private static SpecialRenderEffectContext contextFor(GameRuntime runtime) {
        Camera camera = runtime.getCamera();
        LevelManager levelManager = runtime.getLevelManager();
        GraphicsManager graphicsManager = GameServices.graphics();
        return new SpecialRenderEffectContext(camera, 42, levelManager, graphicsManager);
    }

    private static final class RecordingEffect implements SpecialRenderEffect {
        private final String name;
        private final SpecialRenderEffectStage stage;
        private final List<String> calls;
        private final List<Integer> frameCounters;

        private RecordingEffect(
                String name,
                SpecialRenderEffectStage stage,
                List<String> calls,
                List<Integer> frameCounters) {
            this.name = name;
            this.stage = stage;
            this.calls = calls;
            this.frameCounters = frameCounters;
        }

        @Override
        public SpecialRenderEffectStage stage() {
            return stage;
        }

        @Override
        public String debugName() {
            return name;
        }

        @Override
        public void render(SpecialRenderEffectContext context) {
            calls.add(name);
            frameCounters.add(context.frameCounter());
        }
    }
}
