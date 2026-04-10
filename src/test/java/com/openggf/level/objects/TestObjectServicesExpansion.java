package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.GameStateManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the expanded ObjectServices methods delegate to the correct singletons.
 */
class TestObjectServicesExpansion {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @Test
    void defaultObjectServices_camera_returnsSingleton() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        assertSame(GameServices.camera(), services.camera(),
                "camera() should delegate to GameServices.camera()");
    }

    @Test
    void defaultObjectServices_levelManager_returnsRuntimeLevelManager() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        assertSame(GameServices.level(), services.levelManager(),
                "levelManager() should delegate to the runtime-owned level manager");
    }

    @Test
    void defaultObjectServices_gameState_returnsSingleton() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        assertSame(GameServices.gameState(), services.gameState(),
                "gameState() should delegate to GameServices.gameState()");
    }

    @Test
    void defaultObjectServices_worldSession_returnsRuntimeWorldSession() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        assertSame(GameServices.worldSession(), services.worldSession(),
                "worldSession() should delegate to the runtime-owned world session");
    }

    @Test
    void defaultObjectServices_gameModule_returnsRuntimeModule() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        assertSame(GameServices.module(), services.gameModule(),
                "gameModule() should delegate to the runtime-owned module");
    }

    @Test
    void defaultObjectServices_processServices_returnRuntimeEngineServicesMembers() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        EngineServices engineServices = RuntimeManager.getCurrent().getEngineServices();

        assertSame(engineServices, services.engineServices());
        assertSame(engineServices.configuration(), services.configuration());
        assertSame(engineServices.debugOverlay(), services.debugOverlay());
        assertSame(engineServices.roms(), services.romManager());
        assertSame(engineServices.crossGameFeatures(), services.crossGameFeatures());
    }

    @Test
    void defaultObjectServices_sidekicks_returnsUnmodifiableList() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        var sidekicks = services.sidekicks();
        assertNotNull(sidekicks);
        assertThrows(UnsupportedOperationException.class, () -> sidekicks.add(null));
    }

    @Test
    void defaultObjectServices_requiresRuntime() {
        assertThrows(NullPointerException.class, () -> new DefaultObjectServices(null));
    }

    @Test
    void defaultObjectServices_bootstrapConstructor_worldSessionAndGameModuleAreNullWithoutRuntime() {
        DefaultObjectServices services = bootstrapConstructorServicesWithoutRuntime();

        assertNull(services.worldSession(),
                "bootstrap constructor should not require an active runtime world session");
        assertNull(services.gameModule(),
                "bootstrap constructor should return null game module when unavailable");
    }

    @Test
    void defaultObjectServices_bootstrapConstructor_processServicesUseLegacyEngineServices() {
        DefaultObjectServices services = bootstrapConstructorServicesWithoutRuntime();

        assertSame(SonicConfigurationService.getInstance(), services.configuration());
        assertSame(DebugOverlayManager.getInstance(), services.debugOverlay());
        assertSame(RomManager.getInstance(), services.romManager());
        assertSame(CrossGameFeatureProvider.getInstance(), services.crossGameFeatures());
        assertNotNull(services.engineServices());
    }

    @Test
    void bootstrapObjectServices_delegatesExpandedApiToGameServices() {
        BootstrapObjectServices services = new BootstrapObjectServices();

        assertSame(GameServices.level(), services.levelManager());
        assertSame(GameServices.camera(), services.camera());
        assertSame(GameServices.gameState(), services.gameState());
        assertSame(SonicConfigurationService.getInstance(), services.configuration());
        assertSame(DebugOverlayManager.getInstance(), services.debugOverlay());
        assertSame(RomManager.getInstance(), services.romManager());
        assertSame(CrossGameFeatureProvider.getInstance(), services.crossGameFeatures());
        assertNotNull(services.engineServices());
    }

    private DefaultObjectServices bootstrapConstructorServicesWithoutRuntime() {
        LevelManager levelManager = RuntimeManager.getCurrent().getLevelManager();
        Camera camera = RuntimeManager.getCurrent().getCamera();
        GameStateManager gameState = RuntimeManager.getCurrent().getGameState();
        SpriteManager spriteManager = RuntimeManager.getCurrent().getSpriteManager();
        FadeManager fadeManager = RuntimeManager.getCurrent().getFadeManager();
        WaterSystem waterSystem = RuntimeManager.getCurrent().getWaterSystem();
        ParallaxManager parallaxManager = RuntimeManager.getCurrent().getParallaxManager();

        RuntimeManager.setCurrent(null);

        DefaultObjectServices services = new DefaultObjectServices(
                levelManager,
                camera,
                gameState,
                spriteManager,
                fadeManager,
                waterSystem,
                parallaxManager);
        RuntimeManager.createGameplay();
        return services;
    }
}
