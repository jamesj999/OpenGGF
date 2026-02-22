package com.openggf.game.sonic1.objects;

import org.junit.Test;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1SmashBlockObjectInstance {

    @Test
    public void breaksWhenLandingFromCachedRollAnimation() {
        GameServices.gameState().resetSession();
        Sonic1SmashBlockObjectInstance block = new Sonic1SmashBlockObjectInstance(
                new ObjectSpawn(160, 112, 0x51, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();

        // Pre-collision state this frame: Sonic is airborne in roll animation.
        player.setAnimationId(Sonic1AnimationIds.ROLL);
        player.setRolling(true);
        player.setAir(true);
        block.update(1, player);

        // Landing resolution may clear rolling before onSolidContact callback.
        player.setRolling(false);
        player.setAir(false);
        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 1);

        assertTrue(block.isDestroyed());
        assertEquals(100, GameServices.gameState().getScore());
        assertEquals(2, GameServices.gameState().getItemBonus());
    }

    @Test
    public void doesNotBreakWhenNotRollAnimatingOrRolling() {
        GameServices.gameState().resetSession();
        Sonic1SmashBlockObjectInstance block = new Sonic1SmashBlockObjectInstance(
                new ObjectSpawn(160, 112, 0x51, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();

        player.setAnimationId(Sonic1AnimationIds.WALK);
        player.setRolling(false);
        player.setAir(false);
        block.update(1, player);
        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 1);

        assertFalse(block.isDestroyed());
        assertEquals(0, GameServices.gameState().getScore());
    }

    @Test
    public void chainBonusIncreasesAcrossBlocks() {
        GameServices.gameState().resetSession();
        TestPlayableSprite player = new TestPlayableSprite();
        player.setAnimationId(Sonic1AnimationIds.ROLL);
        player.setRolling(true);
        player.setAir(true);

        Sonic1SmashBlockObjectInstance first = new Sonic1SmashBlockObjectInstance(
                new ObjectSpawn(160, 112, 0x51, 0x00, 0, false, 0));
        first.update(1, player);
        player.setRolling(false);
        player.setAir(false);
        first.onSolidContact(player, new SolidContact(true, false, false, true, false), 1);

        assertEquals(100, GameServices.gameState().getScore());
        assertEquals(2, GameServices.gameState().getItemBonus());

        player.setAnimationId(Sonic1AnimationIds.ROLL);
        player.setRolling(true);
        player.setAir(true);
        Sonic1SmashBlockObjectInstance second = new Sonic1SmashBlockObjectInstance(
                new ObjectSpawn(192, 112, 0x51, 0x00, 0, false, 0));
        second.update(2, player);
        player.setRolling(false);
        player.setAir(false);
        second.onSolidContact(player, new SolidContact(true, false, false, true, false), 2);

        assertEquals(300, GameServices.gameState().getScore()); // 100 + 200
        assertEquals(4, GameServices.gameState().getItemBonus());
    }

    @Test
    public void sixteenthChainAwardUsesSpecialBonus() {
        GameServices.gameState().resetSession();
        // One smash away from special threshold.
        GameServices.gameState().setItemBonus(0x1E);

        Sonic1SmashBlockObjectInstance block = new Sonic1SmashBlockObjectInstance(
                new ObjectSpawn(160, 112, 0x51, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setAnimationId(Sonic1AnimationIds.ROLL);
        player.setRolling(true);
        player.setAir(true);
        block.update(1, player);
        player.setRolling(false);
        player.setAir(false);
        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 1);

        assertTrue(block.isDestroyed());
        assertEquals(10000, GameServices.gameState().getScore());
        assertEquals(0x20, GameServices.gameState().getItemBonus());
    }

    @Test
    public void landingResetsItemBonusChain() {
        GameServices.gameState().resetSession();
        GameServices.gameState().setItemBonus(6);

        TestPlayableSprite player = new TestPlayableSprite();
        player.setAir(true);
        player.setAir(false);

        assertEquals(0, GameServices.gameState().getItemBonus());
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 28;
            runHeight = 38;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
            // No-op for tests.
        }
    }
}
