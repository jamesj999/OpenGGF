package com.openggf.game.sonic2;

import com.openggf.camera.Camera;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2SpecialRenderEffectRegistration {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void cnzRegistersSlotOverlayOnlyAtAfterForeground() {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_CNZ, 0);

        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_BACKGROUND));
        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
    }

    @Test
    void nonCnzZonesDoNotRegisterSlotOverlay() {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_CPZ, 0);

        assertTrue(registry.isEmpty());
    }

    @Test
    void cpzAct2RegistersWaterSurfaceEffect() {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_CPZ, 1);

        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_BACKGROUND));
        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
    }

    @Test
    void arzRegistersWaterSurfaceEffect() {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_ARZ, 0);

        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
    }

    @Test
    void arzAct2RegistersWaterSurfaceEffect() {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_ARZ, 1);

        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
    }

    @Test
    void cpzAct1DoesNotRegisterWaterSurfaceEffect() {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_CPZ, 0);

        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
    }

    @Test
    void dispatchClearsDeferredSlotRequestsEvenWhenRendererIsUnavailable() throws Exception {
        GameRuntime runtime = RuntimeManager.createGameplay();
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_CNZ, 0);
        provider.requestSlotRender(0x100, 0x120, 8, 12);
        assertEquals(1, pendingSlotRenderCount(provider));

        Camera camera = runtime.getCamera();
        LevelManager levelManager = runtime.getLevelManager();
        GraphicsManager graphicsManager = GameServices.graphics();
        registry.dispatch(
                SpecialRenderEffectStage.AFTER_FOREGROUND,
                new SpecialRenderEffectContext(camera, 7, levelManager, graphicsManager));

        assertEquals(0, pendingSlotRenderCount(provider));
    }

    @Test
    void stagedWaterSurfaceDispatchIsSafeWhenManagerIsAbsent() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic2ZoneConstants.ROM_ZONE_ARZ, 0);

        Camera camera = runtime.getCamera();
        LevelManager levelManager = runtime.getLevelManager();
        GraphicsManager graphicsManager = GameServices.graphics();

        assertNull(waterSurfaceManager(provider));
        assertDoesNotThrow(() -> registry.dispatch(
                SpecialRenderEffectStage.AFTER_SPRITES,
                new SpecialRenderEffectContext(camera, 11, levelManager, graphicsManager)));
    }

    @SuppressWarnings("unchecked")
    private static int pendingSlotRenderCount(Sonic2ZoneFeatureProvider provider) throws Exception {
        Field field = Sonic2ZoneFeatureProvider.class.getDeclaredField("pendingSlotRenders");
        field.setAccessible(true);
        return ((java.util.List<int[]>) field.get(provider)).size();
    }

    private static Object waterSurfaceManager(Sonic2ZoneFeatureProvider provider) {
        try {
            Field field = Sonic2ZoneFeatureProvider.class.getDeclaredField("waterSurfaceManager");
            field.setAccessible(true);
            return field.get(provider);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Expected Sonic2ZoneFeatureProvider to expose waterSurfaceManager", e);
        }
    }
}
