package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestRespawnStrategies {

    @BeforeEach
    void setUp() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        SessionManager.clear();
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    static class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) { super(code, (short) 0, (short) 0); }
        @Override public void draw() {}
        @Override public void defineSpeeds() {}
        @Override protected void createSensorLines() {}
    }

    @Test
    void tailsIsDefaultStrategy() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        assertInstanceOf(TailsRespawnStrategy.class, ctrl.getRespawnStrategy());
    }

    @Test
    void knucklesStrategyAlwaysBegins() {
        TestableSprite sk = new TestableSprite("knux_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        KnucklesRespawnStrategy strategy = new KnucklesRespawnStrategy(ctrl);
        // beginApproach always returns true for Knuckles
        assertTrue(strategy.beginApproach(sk, main));
    }

    @Test
    void sonicStrategyReturnsFalseWithoutLevel() {
        TestableSprite sk = new TestableSprite("sonic_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        SonicRespawnStrategy strategy = new SonicRespawnStrategy(ctrl);
        // No level loaded = no terrain = beginApproach returns false
        assertFalse(strategy.beginApproach(sk, main));
    }

    @Test
    void knucklesDropsAfterTimeout() {
        TestableSprite sk = new TestableSprite("knux_p2");
        sk.setCpuControlled(true);
        TestableSprite main = new TestableSprite("sonic");
        main.setX((short) 160);
        main.setY((short) 400);
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        KnucklesRespawnStrategy strategy = new KnucklesRespawnStrategy(ctrl);
        strategy.beginApproach(sk, main);

        // Run 180 frames (timeout)
        for (int i = 0; i < 180; i++) {
            strategy.updateApproaching(sk, main, i);
        }
        // After timeout, Knuckles should be in dropping phase
        // The next updateApproaching should not move horizontally
        // (We can't fully verify drop without physics, but we can verify
        // the approach doesn't complete before landing)
        boolean complete = strategy.updateApproaching(sk, main, 180);
        // Not complete yet — sidekick is still in air (no physics engine in unit test)
        assertFalse(complete, "Should not complete until landed");
    }
}


