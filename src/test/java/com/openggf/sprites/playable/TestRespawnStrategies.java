package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestRespawnStrategies {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
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
    @Test
    void tailsDoesNotCompleteFlyInWhenOnlyVerticalStepReachesTarget() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0100);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x01FF);

        assertFalse(strategy.updateApproaching(sk, main, 0),
                "ROM keeps Tails in fly-in mode when the vertical +/-1 step reaches the target this frame");
        assertEquals(0x0200, sk.getCentreY() & 0xFFFF);

        assertTrue(strategy.updateApproaching(sk, main, 1),
                "ROM exits fly-in once the pre-move vertical delta is already zero");
    }

    @Test
    void tailsCompletesFlyInWhenHorizontalCatchUpClosesRemainingGap() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0105);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        main.setXSpeed((short) 0x0500);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x0200);

        assertTrue(strategy.updateApproaching(sk, main, 0),
                "ROM fly-in completion uses the post-horizontal residual, so closing the X gap this frame exits approach");
        assertEquals(0x0105, sk.getCentreX() & 0xFFFF);
        assertEquals(0x0200, sk.getCentreY() & 0xFFFF);
    }

    @Test
    void tailsFlyInCompletionCopiesLeaderCollisionBitsAndPriority() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0100);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        main.setTopSolidBit((byte) 0x0E);
        main.setLrbSolidBit((byte) 0x0F);
        main.setHighPriority(true);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x0200);
        sk.setTopSolidBit((byte) 0x0C);
        sk.setLrbSolidBit((byte) 0x0D);
        sk.setHighPriority(false);
        sk.setMoveLockTimer(13);

        ctrl.setInitialState(SidekickCpuController.State.APPROACHING);
        ctrl.update(0);

        assertEquals(SidekickCpuController.State.NORMAL, ctrl.getState());
        assertEquals(0x0E, sk.getTopSolidBit() & 0xFF);
        assertEquals(0x0F, sk.getLrbSolidBit() & 0xFF);
        assertTrue(sk.isHighPriority());
        assertEquals(0, sk.getMoveLockTimer(),
                "Tails_Catch_Up_Flying exit clears move_lock before normal CPU control resumes");
    }

    @Test
    void tailsFlyInUsesSignedHighByteOfLeaderVelocityForHorizontalBonus() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x010B);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x0200);
        main.setXSpeed((short) 0xFF82);

        assertFalse(strategy.updateApproaching(sk, main, 0));
        assertEquals(0x0102, sk.getCentreX() & 0xFFFF,
                "ROM uses the signed high byte of Sonic's x_vel during Tails fly-in, so 0xFF82 adds a +1 speed bonus");
    }
}

