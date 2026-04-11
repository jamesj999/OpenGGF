package com.openggf.game;

import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGameServicesNullableAccessors {
    @BeforeEach void setUp() { TestEnvironment.resetAll(); }
    @AfterEach void tearDown() { RuntimeManager.destroyCurrent(); }

    @Test
    void nullableAccessorsReturnNullWithoutRuntime() {
        RuntimeManager.destroyCurrent();
        assertFalse(GameServices.hasRuntime());
        assertNull(GameServices.runtimeOrNull());
        assertNull(GameServices.cameraOrNull());
        assertNull(GameServices.levelOrNull());
        assertNull(GameServices.gameStateOrNull());
        assertNull(GameServices.timersOrNull());
        assertNull(GameServices.rngOrNull());
        assertNull(GameServices.parallaxOrNull());
        assertNull(GameServices.fadeOrNull());
        assertNull(GameServices.spritesOrNull());
        assertNull(GameServices.collisionOrNull());
        assertNull(GameServices.terrainCollisionOrNull());
        assertNull(GameServices.waterOrNull());
        assertNull(GameServices.bonusStageOrNull());
    }

    @Test
    void nullableAccessorsReturnManagersWhenRuntimeExists() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertTrue(GameServices.hasRuntime());
        assertSame(runtime, GameServices.runtimeOrNull());
        assertSame(runtime.getCamera(), GameServices.cameraOrNull());
        assertSame(runtime.getLevelManager(), GameServices.levelOrNull());
        assertSame(runtime.getGameState(), GameServices.gameStateOrNull());
        assertSame(runtime.getTimers(), GameServices.timersOrNull());
        assertSame(runtime.getRng(), GameServices.rngOrNull());
        assertSame(runtime.getParallaxManager(), GameServices.parallaxOrNull());
        assertSame(runtime.getFadeManager(), GameServices.fadeOrNull());
        assertSame(runtime.getSpriteManager(), GameServices.spritesOrNull());
        assertSame(runtime.getCollisionSystem(), GameServices.collisionOrNull());
        assertSame(runtime.getTerrainCollisionManager(), GameServices.terrainCollisionOrNull());
        assertSame(runtime.getWaterSystem(), GameServices.waterOrNull());
        assertSame(runtime.getActiveBonusStageProvider(), GameServices.bonusStageOrNull());
    }

    @Test
    void strictAccessorsStillThrowWithoutRuntime() {
        RuntimeManager.destroyCurrent();
        assertThrows(IllegalStateException.class, GameServices::camera);
        assertThrows(IllegalStateException.class, GameServices::level);
        assertThrows(IllegalStateException.class, GameServices::gameState);
        assertThrows(IllegalStateException.class, GameServices::timers);
        assertThrows(IllegalStateException.class, GameServices::rng);
        assertThrows(IllegalStateException.class, GameServices::parallax);
        assertThrows(IllegalStateException.class, GameServices::fade);
        assertThrows(IllegalStateException.class, GameServices::sprites);
        assertThrows(IllegalStateException.class, GameServices::collision);
        assertThrows(IllegalStateException.class, GameServices::terrainCollision);
        assertThrows(IllegalStateException.class, GameServices::water);
        assertThrows(IllegalStateException.class, GameServices::bonusStage);
    }
}


