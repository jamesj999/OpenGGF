package com.openggf.level.objects;

import com.openggf.camera.Camera;
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
        RuntimeManager.setCurrent(null);

        DefaultObjectServices services = new DefaultObjectServices(
                LevelManager.getInstance(),
                Camera.getInstance(),
                GameStateManager.getInstance(),
                SpriteManager.getInstance(),
                FadeManager.getInstance(),
                WaterSystem.getInstance(),
                ParallaxManager.getInstance());

        assertNull(services.worldSession(),
                "bootstrap constructor should not require an active runtime world session");
        assertNull(services.gameModule(),
                "bootstrap constructor should return null game module when unavailable");
    }
}
