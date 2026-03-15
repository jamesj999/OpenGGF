package com.openggf.game;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure unit tests for GameStateManager. No ROM or OpenGL dependencies.
 */
public class TestGameStateManager {

    private GameStateManager gsm;

    @Before
    public void setUp() {
        gsm = GameStateManager.getInstance();
        // Reset to defaults before each test
        gsm.configureSpecialStageProgress(7, 7);
        gsm.resetSession();
    }

    @After
    public void tearDown() {
        // Restore singleton to clean defaults so other tests are not affected
        gsm.configureSpecialStageProgress(7, 7);
        gsm.resetSession();
    }

    @Test
    public void testInitialState() {
        assertEquals("Initial lives should be 3", 3, gsm.getLives());
        assertEquals("Initial score should be 0", 0, gsm.getScore());
        assertEquals("Initial emerald count should be 0", 0, gsm.getEmeraldCount());
    }

    @Test
    public void testMarkEmeraldCollected() {
        gsm.markEmeraldCollected(0);
        assertEquals("Emerald count should be 1 after collecting one", 1, gsm.getEmeraldCount());
        assertTrue("Should have emerald 0", gsm.hasEmerald(0));
        assertFalse("Should not have emerald 1", gsm.hasEmerald(1));
    }

    @Test
    public void testMarkEmeraldCollectedDuplicate() {
        gsm.markEmeraldCollected(2);
        assertEquals(1, gsm.getEmeraldCount());

        gsm.markEmeraldCollected(2);
        assertEquals("Collecting same emerald twice should not increment count", 1, gsm.getEmeraldCount());
    }

    @Test
    public void testMarkEmeraldCollectedOutOfBounds() {
        int countBefore = gsm.getEmeraldCount();

        // Negative index should not crash or change count
        gsm.markEmeraldCollected(-1);
        assertEquals("Negative index should not change count", countBefore, gsm.getEmeraldCount());

        // Overflow index should not crash or change count
        gsm.markEmeraldCollected(100);
        assertEquals("Overflow index should not change count", countBefore, gsm.getEmeraldCount());

        // Boundary index (equal to array length) should not crash
        gsm.markEmeraldCollected(7);
        assertEquals("Boundary index should not change count", countBefore, gsm.getEmeraldCount());
    }

    @Test
    public void testHasAllEmeralds() {
        assertFalse("Should not have all emeralds initially", gsm.hasAllEmeralds());

        for (int i = 0; i < 7; i++) {
            gsm.markEmeraldCollected(i);
        }

        assertEquals("Should have 7 emeralds", 7, gsm.getEmeraldCount());
        assertTrue("Should have all emeralds", gsm.hasAllEmeralds());
    }

    @Test
    public void testConfigureSpecialStageProgress() {
        gsm.configureSpecialStageProgress(8, 6);

        assertEquals("Stage count should be 8", 8, gsm.getSpecialStageCount());
        assertEquals("Emerald target should be 6", 6, gsm.getChaosEmeraldCount());
        assertEquals("Emerald count should reset to 0", 0, gsm.getEmeraldCount());

        // Verify emerald array resized - collecting index 5 should work, index 6 should not
        gsm.markEmeraldCollected(5);
        assertEquals(1, gsm.getEmeraldCount());
        gsm.markEmeraldCollected(6);
        assertEquals("Index 6 should be out of bounds for 6-emerald config", 1, gsm.getEmeraldCount());

        // All 6 emeralds collected = hasAllEmeralds
        for (int i = 0; i < 6; i++) {
            gsm.markEmeraldCollected(i);
        }
        assertTrue("6 of 6 emeralds = all", gsm.hasAllEmeralds());
    }

    @Test
    public void testSessionReset() {
        gsm.addScore(5000);
        gsm.addLife();
        gsm.markEmeraldCollected(0);
        gsm.markEmeraldCollected(3);

        gsm.resetSession();

        assertEquals("Score should reset to 0", 0, gsm.getScore());
        assertEquals("Lives should reset to 3", 3, gsm.getLives());
        assertEquals("Emerald count should reset to 0", 0, gsm.getEmeraldCount());
        assertFalse("Emerald 0 should be cleared", gsm.hasEmerald(0));
        assertFalse("Emerald 3 should be cleared", gsm.hasEmerald(3));
    }

    @Test
    public void testAddLife() {
        assertEquals(3, gsm.getLives());
        gsm.addLife();
        assertEquals("Lives should be 4 after addLife", 4, gsm.getLives());
    }

    @Test
    public void testLoseLife() {
        assertEquals(3, gsm.getLives());
        gsm.loseLife();
        assertEquals("Lives should be 2 after loseLife", 2, gsm.getLives());

        // Lose all lives
        gsm.loseLife();
        gsm.loseLife();
        assertEquals("Lives should be 0", 0, gsm.getLives());

        // Should not go below 0
        gsm.loseLife();
        assertEquals("Lives should not go below 0", 0, gsm.getLives());
    }

    @Test
    public void testAddScore() {
        gsm.addScore(100);
        assertEquals("Score should be 100", 100, gsm.getScore());

        gsm.addScore(250);
        assertEquals("Score should accumulate to 350", 350, gsm.getScore());

        // Negative amount should not change score
        gsm.addScore(-50);
        assertEquals("Negative score should be ignored", 350, gsm.getScore());
    }
}
