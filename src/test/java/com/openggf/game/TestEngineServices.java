package com.openggf.game;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestEngineServices {
    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @Test
    void defaultRootCollectsExistingProcessServicesAtCompositionBoundary() {
        EngineServices services = EngineServices.fromLegacySingletonsForBootstrap();

        assertSame(SonicConfigurationService.getInstance(), services.configuration());
        assertSame(GraphicsManager.getInstance(), services.graphics());
        assertSame(AudioManager.getInstance(), services.audio());
        assertSame(RomManager.getInstance(), services.roms());
        assertSame(PerformanceProfiler.getInstance(), services.profiler());
        assertSame(DebugOverlayManager.getInstance(), services.debugOverlay());
        assertSame(PlaybackDebugManager.getInstance(), services.playbackDebug());
        assertSame(RomDetectionService.getInstance(), services.romDetection());
        assertSame(CrossGameFeatureProvider.getInstance(), services.crossGameFeatures());
    }

    @Test
    void engineConfiguresRuntimeRootBeforeGameLoopCanAutoCreateRuntime() {
        EngineServices staleRoot = EngineServices.fromLegacySingletonsForBootstrap();
        EngineServices injectedRoot = EngineServices.fromLegacySingletonsForBootstrap();
        RuntimeManager.configureEngineServices(staleRoot);
        SessionManager.openGameplaySession(new Sonic2GameModule());

        new Engine(injectedRoot);
        // After removing lazy-create-on-getCurrent, the runtime is built
        // explicitly. Engine constructor reconfigures the root before any
        // gameplay runtime exists; verify that downstream creation picks up
        // the injected root.
        com.openggf.game.GameRuntime runtime = RuntimeManager.createGameplay();
        assertSame(injectedRoot, runtime.getEngineServices());
    }

    @Test
    void gameLoopInjectedRootIsUsedWhenConstructorAutoCreatesRuntime() {
        EngineServices staleRoot = EngineServices.fromLegacySingletonsForBootstrap();
        EngineServices injectedRoot = EngineServices.fromLegacySingletonsForBootstrap();
        RuntimeManager.configureEngineServices(staleRoot);
        SessionManager.openGameplaySession(new Sonic2GameModule());

        new GameLoop(injectedRoot);
        // After removing lazy-create-on-getCurrent, runtime build is explicit;
        // the GameLoop constructor reconfigures the root, and the next
        // explicit createGameplay() picks it up.
        com.openggf.game.GameRuntime runtime = RuntimeManager.createGameplay();
        assertSame(injectedRoot, runtime.getEngineServices());
    }

    @Test
    void runtimeRebindsWhenEngineServicesRootChangesWhileActive() {
        EngineServices staleRoot = EngineServices.fromLegacySingletonsForBootstrap();
        EngineServices injectedRoot = EngineServices.fromLegacySingletonsForBootstrap();
        RuntimeManager.configureEngineServices(staleRoot);
        SessionManager.openGameplaySession(new Sonic2GameModule());

        com.openggf.game.GameRuntime runtime = RuntimeManager.createGameplay();
        assertSame(staleRoot, runtime.getEngineServices());

        RuntimeManager.configureEngineServices(injectedRoot);
        // getCurrent with a different EngineServices root drops the stale
        // runtime; an explicit createGameplay() builds a fresh one bound to
        // the new root.
        assertNull(RuntimeManager.getCurrent(injectedRoot),
                "getCurrent should drop the runtime whose EngineServices no longer matches");
        com.openggf.game.GameRuntime rebound = RuntimeManager.createGameplay();

        assertSame(injectedRoot, rebound.getEngineServices());
        assertNotSame(runtime, rebound);
    }

    @Test
    void defaultEngineConstructorUsesCurrentlyConfiguredEngineServicesRoot() {
        EngineServices configuredRoot = EngineServices.fromLegacySingletonsForBootstrap();
        RuntimeManager.configureEngineServices(configuredRoot);

        new Engine();

        assertSame(configuredRoot, RuntimeManager.currentEngineServices());
    }

    @Test
    void defaultGameLoopConstructorsUseCurrentlyConfiguredEngineServicesRoot() {
        EngineServices configuredRoot = EngineServices.fromLegacySingletonsForBootstrap();
        RuntimeManager.configureEngineServices(configuredRoot);

        new GameLoop();
        assertSame(configuredRoot, RuntimeManager.currentEngineServices());

        new GameLoop(new InputHandler());
        assertSame(configuredRoot, RuntimeManager.currentEngineServices());
    }

    @Test
    void scopedEngineLoopCodeDoesNotBypassRootOwnedServices() throws IOException {
        assertNoRootBypass(Path.of("src/main/java/com/openggf/Engine.java"));
        assertNoRootBypass(Path.of("src/main/java/com/openggf/GameLoop.java"));
    }

    private static void assertNoRootBypass(Path path) throws IOException {
        String source = Files.readString(path);
        assertFalse(source.contains("GameServices.debugOverlay()"), path + " should use EngineServices.debugOverlay()");
        assertFalse(source.contains("GameServices.rom()"), path + " should use EngineServices.roms()");
    }
}


