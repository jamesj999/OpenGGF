package com.openggf;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.control.InputHandler;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Tests for GameLoop class - the core game logic that can run headlessly.
 * These tests verify game state transitions and mode switching
 * without requiring an OpenGL context.
 */
public class TestGameLoop {

    private GameLoop gameLoop;
    private InputHandler mockInputHandler;

    @Before
    public void setUp() {
        RuntimeManager.createGameplay();
        mockInputHandler = mock(InputHandler.class);
        gameLoop = new GameLoop(mockInputHandler);
    }

    @After
    public void tearDown() {
        gameLoop = null;
        RuntimeManager.destroyCurrent();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void testGameLoopStartsInLevelMode() {
        assertEquals("GameLoop should start in LEVEL mode",
                GameMode.LEVEL, gameLoop.getCurrentGameMode());
    }

    @Test
    public void testGameLoopConstructorWithInputHandler() {
        GameLoop loop = new GameLoop(mockInputHandler);
        assertEquals("Input handler should be set via constructor",
                mockInputHandler, loop.getInputHandler());
    }

    @Test
    public void testSetInputHandler() {
        GameLoop loop = new GameLoop();
        assertNull("Input handler should be null initially", loop.getInputHandler());

        loop.setInputHandler(mockInputHandler);
        assertEquals("Input handler should be set", mockInputHandler, loop.getInputHandler());
    }

    @Test(expected = IllegalStateException.class)
    public void testStepWithoutInputHandlerThrows() {
        GameLoop loop = new GameLoop();
        loop.step(); // Should throw IllegalStateException
    }

    // ==================== Game Mode Listener Tests ====================

    @Test
    public void testGameModeChangeListenerCanBeSet() {
        GameLoop.GameModeChangeListener listener = mock(GameLoop.GameModeChangeListener.class);
        gameLoop.setGameModeChangeListener(listener);
        // Verify the listener was accepted (no exception thrown, mode still valid)
        assertNotNull("Game mode should remain valid after setting listener",
                gameLoop.getCurrentGameMode());
    }

    // ==================== Mode Transition Guard Tests ====================

    @Test
    public void testGameModeStartsInLevelMode() {
        // When starting, should be in LEVEL mode
        assertEquals("Should be in LEVEL mode", GameMode.LEVEL, gameLoop.getCurrentGameMode());

        // Verify no results screen is active
        assertNull("Results screen should be null initially", gameLoop.getResultsScreen());
    }

    // ==================== Game Mode Accessor Tests ====================

    @Test
    public void testGetCurrentGameModeReturnsCorrectMode() {
        // Initially should be in LEVEL mode
        GameMode mode = gameLoop.getCurrentGameMode();
        assertNotNull("Game mode should not be null", mode);
        assertEquals("Should be in LEVEL mode", GameMode.LEVEL, mode);
    }

    @Test
    public void testResolveBonusStageDebugShortcutShiftB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.GUMBALL, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutCtrlB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.GLOWING_SPHERE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutAltB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.SLOT_MACHINE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutRequiresExactlyOneModifier() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.NONE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageDebugShortcutIgnoresPlainB() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertEquals(BonusStageType.NONE, GameLoop.resolveBonusStageDebugShortcut(handler));
    }

    @Test
    public void testResolveBonusStageBootstrapSpawnForPachinko() {
        ObjectSpawn spawn = GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.GLOWING_SPHERE);

        assertNotNull(spawn);
        assertEquals(0x78, spawn.x());
        assertEquals(0x0F30, spawn.y());
        assertEquals(Sonic3kObjectIds.PACHINKO_ENERGY_TRAP, spawn.objectId());
    }

    @Test
    public void testResolveBonusStageBootstrapSpawnOnlyForPachinko() {
        assertNull(GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.GUMBALL));
        assertNull(GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.SLOT_MACHINE));
        assertNull(GameLoop.resolveBonusStageBootstrapSpawn(BonusStageType.NONE));
    }
}
