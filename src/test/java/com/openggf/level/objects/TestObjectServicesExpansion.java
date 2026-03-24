package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the expanded ObjectServices methods delegate to the correct singletons.
 */
class TestObjectServicesExpansion {

    @Test
    void defaultObjectServices_camera_returnsSingleton() {
        DefaultObjectServices services = new DefaultObjectServices();
        assertSame(GameServices.camera(), services.camera(),
                "camera() should delegate to GameServices.camera()");
    }

    @Test
    void defaultObjectServices_gameState_returnsSingleton() {
        DefaultObjectServices services = new DefaultObjectServices();
        assertSame(GameServices.gameState(), services.gameState(),
                "gameState() should delegate to GameServices.gameState()");
    }

    @Test
    void defaultObjectServices_sidekicks_returnsUnmodifiableList() {
        DefaultObjectServices services = new DefaultObjectServices();
        var sidekicks = services.sidekicks();
        assertNotNull(sidekicks);
        assertThrows(UnsupportedOperationException.class, () -> sidekicks.add(null));
    }
}
