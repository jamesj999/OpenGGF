package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.GameStateManager;
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
}
