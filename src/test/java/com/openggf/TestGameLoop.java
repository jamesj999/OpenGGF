package com.openggf;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.control.InputHandler;
import com.openggf.game.EngineServices;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.RuntimeManager;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
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

    @Test
    public void testExitTitleCardAppliesDeferredBonusStageSetupWithoutSavedState() throws Exception {
        BonusStageProvider provider = mock(BonusStageProvider.class);

        setPrivateField(gameLoop, "currentGameMode", GameMode.TITLE_CARD);
        setPrivateField(gameLoop, "postTitleCardDestination",
                enumConstant(GameLoop.class, "PostTitleCardDestination", "BONUS_STAGE"));
        setPrivateField(gameLoop, "deferredBonusProvider", provider);
        setPrivateField(gameLoop, "deferredBonusType", BonusStageType.SLOT_MACHINE);
        setPrivateField(gameLoop, "deferredBonusState", null);

        invokePrivateMethod(gameLoop, "exitTitleCard");

        verify(provider).onDeferredSetupComplete();
        assertNull("Deferred provider should be cleared after setup",
                getPrivateField(gameLoop, "deferredBonusProvider"));
        assertNull("Deferred saved state should stay cleared after setup",
                getPrivateField(gameLoop, "deferredBonusState"));
        assertEquals("GameLoop should switch to bonus stage mode",
                GameMode.BONUS_STAGE, gameLoop.getCurrentGameMode());
    }

    @Test
    public void testBonusStageExitFadeIsNotRestartedAfterProviderCompletedFade() {
        BonusStageProvider provider = mock(BonusStageProvider.class);
        when(provider.hasCompletedExitFadeToBlack()).thenReturn(true);

        assertFalse("provider-owned GOAL fade should be reused instead of starting a second generic fade",
                GameLoop.shouldStartBonusStageExitFade(provider));
    }

    private static void invokePrivateMethod(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> ownerClass, String nestedTypeName, String constantName)
            throws Exception {
        Class<?> nestedType = Class.forName(ownerClass.getName() + "$" + nestedTypeName);
        return Enum.valueOf((Class<? extends Enum>) nestedType.asSubclass(Enum.class), constantName);
    }
}
