package com.openggf.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSpecialStageModuleConfig {

    @Before
    public void setUp() {
        RuntimeManager.createGameplay();
    }

    @After
    public void tearDown() {
        GameModuleRegistry.reset();
        GameServices.gameState().resetSession();
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void sonic1ModuleConfiguresSixStagesAndEmeralds() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        GameStateManager gameState = GameServices.gameState();

        assertEquals(6, gameState.getSpecialStageCount());
        assertEquals(6, gameState.getChaosEmeraldCount());

        for (int i = 0; i < 6; i++) {
            assertEquals(i, gameState.consumeCurrentSpecialStageIndexAndAdvance());
            gameState.markEmeraldCollected(i);
        }

        assertEquals(0, gameState.consumeCurrentSpecialStageIndexAndAdvance());
        assertTrue(gameState.hasAllEmeralds());
    }

    @Test
    public void switchingBackToSonic2RestoresSevenStageConfig() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        GameStateManager gameState = GameServices.gameState();
        for (int i = 0; i < 6; i++) {
            gameState.markEmeraldCollected(i);
        }
        assertTrue(gameState.hasAllEmeralds());

        GameModuleRegistry.setCurrent(new Sonic2GameModule());

        assertEquals(7, gameState.getSpecialStageCount());
        assertEquals(7, gameState.getChaosEmeraldCount());
        assertFalse(gameState.hasAllEmeralds());
        assertEquals(0, gameState.consumeCurrentSpecialStageIndexAndAdvance());
    }

    @Test
    public void resetRestoresSonic2StageConfigThroughCompatibilityPath() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        GameStateManager gameState = GameServices.gameState();
        for (int i = 0; i < 6; i++) {
            gameState.markEmeraldCollected(i);
        }
        assertTrue(gameState.hasAllEmeralds());

        GameModuleRegistry.reset();

        assertEquals(7, gameState.getSpecialStageCount());
        assertEquals(7, gameState.getChaosEmeraldCount());
        assertFalse(gameState.hasAllEmeralds());
        assertEquals(0, gameState.consumeCurrentSpecialStageIndexAndAdvance());
    }
}
