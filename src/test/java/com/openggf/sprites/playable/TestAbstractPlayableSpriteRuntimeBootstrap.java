package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestAbstractPlayableSpriteRuntimeBootstrap {

    private Sonic2GameModule module;

    @BeforeEach
    void setUp() {
        module = new Sonic2GameModule();
        GameModuleRegistry.setCurrent(module);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void playableConstructorsShouldNotRequireAnActiveRuntime() {
        assertAll(
                "Playable constructors should not require an active GameRuntime",
                () -> assertDoesNotThrow(() -> new Sonic("sonic", (short) 0, (short) 0)),
                () -> assertDoesNotThrow(() -> new Tails("tails", (short) 0, (short) 0)),
                () -> assertDoesNotThrow(() -> new Knuckles("knuckles", (short) 0, (short) 0))
        );
    }

    @Test
    void sonicConstructedBeforeRuntimeCanHydrateAfterGameplaySessionOpens() {
        AtomicReference<Sonic> sonicRef = new AtomicReference<>();

        assertDoesNotThrow(() -> sonicRef.set(new Sonic("sonic", (short) 0, (short) 0)));

        GameplayModeContext gameplay = SessionManager.openGameplaySession(module);
        RuntimeManager.createGameplay(gameplay);

        Sonic sonic = sonicRef.get();
        assertNotNull(sonic);

        // BUBBLE animation resolution is a narrow runtime-bound lookup that should
        // succeed once the gameplay runtime and world session are active.
        assertDoesNotThrow(() -> assertNotEquals(-1, sonic.resolveAnimationId(CanonicalAnimation.BUBBLE)));
    }
}
