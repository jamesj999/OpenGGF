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
        assertNull(GameServices.levelOrNull());
        assertNull(GameServices.parallaxOrNull());
        assertNull(GameServices.fadeOrNull());
        assertNull(GameServices.bonusStageOrNull());
    }

    @Test
    void nullableAccessorsReturnManagersWhenRuntimeExists() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertTrue(GameServices.hasRuntime());
        assertSame(runtime, GameServices.runtimeOrNull());
        assertSame(runtime.getLevelManager(), GameServices.levelOrNull());
        assertSame(runtime.getParallaxManager(), GameServices.parallaxOrNull());
        assertSame(runtime.getFadeManager(), GameServices.fadeOrNull());
        assertSame(runtime.getActiveBonusStageProvider(), GameServices.bonusStageOrNull());
    }

    @Test
    void strictAccessorsStillThrowWithoutRuntime() {
        RuntimeManager.destroyCurrent();
        assertThrows(IllegalStateException.class, GameServices::level);
        assertThrows(IllegalStateException.class, GameServices::parallax);
        assertThrows(IllegalStateException.class, GameServices::fade);
        assertThrows(IllegalStateException.class, GameServices::bonusStage);
    }
}
