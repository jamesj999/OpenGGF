package com.openggf.game.sonic1.objects;

import org.junit.Test;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1SmashBlockObjectInstance {

    @Test
    public void breaksWhenLandingFromCachedRollAnimation() {
        GameServices.gameState().resetSession();
        Sonic1SmashBlockObjectInstance block = new Sonic1SmashBlockObjectInstance(
                new ObjectSpawn(160, 112, 0x51, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite(28, 38);

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
        TestPlayableSprite player = new TestPlayableSprite(28, 38);

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
        TestPlayableSprite player = new TestPlayableSprite(28, 38);
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
        TestPlayableSprite player = new TestPlayableSprite(28, 38);
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

        TestPlayableSprite player = new TestPlayableSprite(28, 38);
        player.setAir(true);
        player.setAir(false);

        assertEquals(0, GameServices.gameState().getItemBonus());
    }

}
