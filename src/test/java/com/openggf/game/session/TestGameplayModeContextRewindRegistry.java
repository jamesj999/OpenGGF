package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link RewindRegistry} integration on
 * {@link GameplayModeContext}.
 *
 * <p>Tests verify that the six always-available atomic adapters are
 * registered automatically when {@link GameplayModeContext#attachGameplayManagers}
 * is called, without requiring a full level load or ROM access.
 */
class TestGameplayModeContextRewindRegistry {

    @BeforeEach
    void configureServices() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
    }

    private static GameplayModeContext buildAttachedContext() {
        WorldSession world = new WorldSession(new Sonic2GameModule());
        GameplayModeContext ctx = new GameplayModeContext(world);

        Camera camera = new Camera();
        TimerManager timers = new TimerManager();
        GameStateManager gameState = new GameStateManager();
        FadeManager fade = new FadeManager();
        GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        DefaultSolidExecutionRegistry solid = new DefaultSolidExecutionRegistry();

        ctx.attachGameplayManagers(camera, timers, gameState, fade, rng, solid);
        return ctx;
    }

    @Test
    void registryIsNonNullAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        assertNotNull(ctx.getRewindRegistry());
    }

    @Test
    void registryHasAtomicAdaptersAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        RewindRegistry registry = ctx.getRewindRegistry();

        CompositeSnapshot snapshot = registry.capture();

        Set<String> expectedKeys = Set.of(
                "camera",
                "gamestate",
                "gamerng",
                "timermanager",
                "fademanager",
                "oscillation");
        assertTrue(snapshot.entries().keySet().containsAll(expectedKeys),
                "Expected all atomic adapter keys to be present, got: " + snapshot.entries().keySet());
    }

    @Test
    void exactlySixAtomicKeysAfterAttach() {
        GameplayModeContext ctx = buildAttachedContext();
        RewindRegistry registry = ctx.getRewindRegistry();
        CompositeSnapshot snapshot = registry.capture();
        assertEquals(6, snapshot.entries().keySet().size(),
                "Expected exactly 6 atomic adapters, got: " + snapshot.entries().keySet());
    }

    @Test
    void registryIsNullBeforeAttach() {
        WorldSession world = new WorldSession(new Sonic2GameModule());
        GameplayModeContext ctx = new GameplayModeContext(world);
        assertNull(ctx.getRewindRegistry(),
                "Registry should be null until attachGameplayManagers is called");
    }

    @Test
    void tearDownClearsRegistry() {
        GameplayModeContext ctx = buildAttachedContext();
        assertNotNull(ctx.getRewindRegistry());
        ctx.tearDownManagers();
        assertNull(ctx.getRewindRegistry(),
                "Registry should be null after tearDownManagers");
    }

    @Test
    void reattachRebuildsRegistry() {
        GameplayModeContext ctx = buildAttachedContext();
        RewindRegistry first = ctx.getRewindRegistry();

        // Tear down and re-attach (simulates a resume-parked path)
        ctx.tearDownManagers();
        Camera camera2 = new Camera();
        TimerManager timers2 = new TimerManager();
        GameStateManager gameState2 = new GameStateManager();
        FadeManager fade2 = new FadeManager();
        GameRng rng2 = new GameRng(GameRng.Flavour.S1_S2);
        DefaultSolidExecutionRegistry solid2 = new DefaultSolidExecutionRegistry();
        ctx.attachGameplayManagers(camera2, timers2, gameState2, fade2, rng2, solid2);

        RewindRegistry second = ctx.getRewindRegistry();
        assertNotNull(second);
        assertNotSame(first, second, "Re-attach should produce a new RewindRegistry instance");
        // New registry should have the same 6 keys
        assertEquals(6, second.capture().entries().keySet().size());
    }
}
