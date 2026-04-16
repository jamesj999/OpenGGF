package com.openggf.game.sonic2;

import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2CnzRuntimeStateRegistration {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void cnzInstallsTypedRuntimeStateWithMetadata() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);

        Object state = currentRegistry(runtime).currentAs(cnzRuntimeStateType()).orElseThrow();
        assertEquals("s2", invokeString(state, "gameId"));
        assertEquals(Sonic2LevelEventManager.ZONE_CNZ, invokeInt(state, "zoneIndex"));
        assertEquals(1, invokeInt(state, "actIndex"));
        assertFalse(invokeBoolean(state, "bossArenaActive"));
        assertFalse(invokeBoolean(state, "bossSpawnPending"));
        assertFalse(invokeBoolean(state, "bossSpawned"));
        assertFalse(invokeBoolean(state, "leftArenaWallPlaced"));
        assertFalse(invokeBoolean(state, "rightArenaWallPlaced"));
        assertEquals(0, invokeInt(state, "eventRoutine"));
    }

    @Test
    void nonCnzDoesNotInstallCnzRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_EHZ, 0);

        assertTrue(currentRegistry(runtime).currentAs(cnzRuntimeStateType()).isEmpty());
    }

    @Test
    void cnzStateClearsWhenLeavingCnz() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);
        manager.initLevel(Sonic2LevelEventManager.ZONE_EHZ, 0);

        assertTrue(currentRegistry(runtime).currentAs(cnzRuntimeStateType()).isEmpty());
    }

    @Test
    void nonCnzInitDoesNotClearUnrelatedRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();
        ZoneRuntimeState sentinel = new ZoneRuntimeState() {
            @Override
            public String gameId() {
                return "test";
            }

            @Override
            public int zoneIndex() {
                return 77;
            }

            @Override
            public int actIndex() {
                return 2;
            }
        };
        currentRegistry(runtime).install(sentinel);

        manager.initLevel(Sonic2LevelEventManager.ZONE_EHZ, 0);

        assertSame(sentinel, currentRegistry(runtime).current());
    }

    @Test
    void cnzInitDoesNotReplaceUnrelatedRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();
        ZoneRuntimeState sentinel = new ZoneRuntimeState() {
            @Override
            public String gameId() {
                return "test";
            }

            @Override
            public int zoneIndex() {
                return 78;
            }

            @Override
            public int actIndex() {
                return 3;
            }
        };
        currentRegistry(runtime).install(sentinel);

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);

        assertSame(sentinel, currentRegistry(runtime).current());
    }

    @Test
    void cnzActSwitchRefreshesInstalledMetadata() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 0);
        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);

        Object state = currentRegistry(runtime).currentAs(cnzRuntimeStateType()).orElseThrow();
        assertEquals(1, invokeInt(state, "actIndex"));
    }

    @Test
    void resetStateClearsInstalledCnzRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 1);
        manager.resetState();

        assertTrue(currentRegistry(runtime).currentAs(cnzRuntimeStateType()).isEmpty());
    }

    private static GameRuntime currentRuntime() {
        GameRuntime runtime = RuntimeManager.getActiveRuntime();
        assertNotNull(runtime, "Expected gameplay runtime");
        return runtime;
    }

    private static ZoneRuntimeRegistry currentRegistry(GameRuntime runtime) {
        return runtime.getZoneRuntimeRegistry();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends ZoneRuntimeState> cnzRuntimeStateType() {
        try {
            return (Class<? extends ZoneRuntimeState>) Class
                    .forName("com.openggf.game.sonic2.runtime.CnzRuntimeState")
                    .asSubclass(ZoneRuntimeState.class);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Expected CNZ runtime state type", e);
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        Object result = invoke(target, methodName);
        assertTrue(result instanceof Boolean, methodName + " should return boolean");
        return (Boolean) result;
    }

    private static int invokeInt(Object target, String methodName) {
        Object result = invoke(target, methodName);
        assertTrue(result instanceof Integer, methodName + " should return int");
        return (Integer) result;
    }

    private static String invokeString(Object target, String methodName) {
        Object result = invoke(target, methodName);
        assertTrue(result instanceof String, methodName + " should return string");
        return (String) result;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Missing method: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Method threw: " + methodName, e.getCause());
        }
    }
}
