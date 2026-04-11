package com.openggf.game;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for GameStateManager. No ROM or OpenGL dependencies.
 */
public class TestGameStateManager {

    private GameStateManager gsm;

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
        gsm = GameServices.gameState();
        // Reset to defaults before each test
        gsm.configureSpecialStageProgress(7, 7);
        gsm.resetSession();
    }

    @AfterEach
    public void tearDown() {
        // Restore singleton to clean defaults so other tests are not affected
        gsm.configureSpecialStageProgress(7, 7);
        gsm.resetSession();
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void testInitialState() {
        assertEquals(3, gsm.getLives(), "Initial lives should be 3");
        assertEquals(0, gsm.getScore(), "Initial score should be 0");
        assertEquals(0, gsm.getEmeraldCount(), "Initial emerald count should be 0");
    }

    @Test
    public void testMarkEmeraldCollected() {
        gsm.markEmeraldCollected(0);
        assertEquals(1, gsm.getEmeraldCount(), "Emerald count should be 1 after collecting one");
        assertTrue(gsm.hasEmerald(0), "Should have emerald 0");
        assertFalse(gsm.hasEmerald(1), "Should not have emerald 1");
    }

    @Test
    public void testMarkEmeraldCollectedDuplicate() {
        gsm.markEmeraldCollected(2);
        assertEquals(1, gsm.getEmeraldCount());

        gsm.markEmeraldCollected(2);
        assertEquals(1, gsm.getEmeraldCount(), "Collecting same emerald twice should not increment count");
    }

    @Test
    public void testMarkEmeraldCollectedOutOfBounds() {
        int countBefore = gsm.getEmeraldCount();

        // Negative index should not crash or change count
        gsm.markEmeraldCollected(-1);
        assertEquals(countBefore, gsm.getEmeraldCount(), "Negative index should not change count");

        // Overflow index should not crash or change count
        gsm.markEmeraldCollected(100);
        assertEquals(countBefore, gsm.getEmeraldCount(), "Overflow index should not change count");

        // Boundary index (equal to array length) should not crash
        gsm.markEmeraldCollected(7);
        assertEquals(countBefore, gsm.getEmeraldCount(), "Boundary index should not change count");
    }

    @Test
    public void testHasAllEmeralds() {
        assertFalse(gsm.hasAllEmeralds(), "Should not have all emeralds initially");

        for (int i = 0; i < 7; i++) {
            gsm.markEmeraldCollected(i);
        }

        assertEquals(7, gsm.getEmeraldCount(), "Should have 7 emeralds");
        assertTrue(gsm.hasAllEmeralds(), "Should have all emeralds");
    }

    @Test
    public void testConfigureSpecialStageProgress() {
        gsm.configureSpecialStageProgress(8, 6);

        assertEquals(8, gsm.getSpecialStageCount(), "Stage count should be 8");
        assertEquals(6, gsm.getChaosEmeraldCount(), "Emerald target should be 6");
        assertEquals(0, gsm.getEmeraldCount(), "Emerald count should reset to 0");

        // Verify emerald array resized - collecting index 5 should work, index 6 should not
        gsm.markEmeraldCollected(5);
        assertEquals(1, gsm.getEmeraldCount());
        gsm.markEmeraldCollected(6);
        assertEquals(1, gsm.getEmeraldCount(), "Index 6 should be out of bounds for 6-emerald config");

        // All 6 emeralds collected = hasAllEmeralds
        for (int i = 0; i < 6; i++) {
            gsm.markEmeraldCollected(i);
        }
        assertTrue(gsm.hasAllEmeralds(), "6 of 6 emeralds = all");
    }

    @Test
    public void testSessionReset() {
        gsm.addScore(5000);
        gsm.addLife();
        gsm.markEmeraldCollected(0);
        gsm.markEmeraldCollected(3);

        gsm.resetSession();

        assertEquals(0, gsm.getScore(), "Score should reset to 0");
        assertEquals(3, gsm.getLives(), "Lives should reset to 3");
        assertEquals(0, gsm.getEmeraldCount(), "Emerald count should reset to 0");
        assertFalse(gsm.hasEmerald(0), "Emerald 0 should be cleared");
        assertFalse(gsm.hasEmerald(3), "Emerald 3 should be cleared");
    }

    @Test
    public void testAddLife() {
        assertEquals(3, gsm.getLives());
        gsm.addLife();
        assertEquals(4, gsm.getLives(), "Lives should be 4 after addLife");
    }

    @Test
    public void testLoseLife() {
        assertEquals(3, gsm.getLives());
        gsm.loseLife();
        assertEquals(2, gsm.getLives(), "Lives should be 2 after loseLife");

        // Lose all lives
        gsm.loseLife();
        gsm.loseLife();
        assertEquals(0, gsm.getLives(), "Lives should be 0");

        // Should not go below 0
        gsm.loseLife();
        assertEquals(0, gsm.getLives(), "Lives should not go below 0");
    }

    @Test
    public void testAddScore() {
        gsm.addScore(100);
        assertEquals(100, gsm.getScore(), "Score should be 100");

        gsm.addScore(250);
        assertEquals(350, gsm.getScore(), "Score should accumulate to 350");

        // Negative amount should not change score
        gsm.addScore(-50);
        assertEquals(350, gsm.getScore(), "Negative score should be ignored");
    }
}


