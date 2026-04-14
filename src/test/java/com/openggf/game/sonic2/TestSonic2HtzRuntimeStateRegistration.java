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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2HtzRuntimeStateRegistration {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void htzInstallsTypedRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);

        Object state = currentRegistry(runtime).currentAs(htzRuntimeStateType()).orElseThrow();
        assertEquals("s2", invokeString(state, "gameId"));
        assertEquals(Sonic2LevelEventManager.ZONE_HTZ, invokeInt(state, "zoneIndex"));
        assertEquals(0, invokeInt(state, "actIndex"));
    }

    @Test
    void nonHtzDoesNotInstallHtzRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 0);

        assertTrue(currentRegistry(runtime).currentAs(htzRuntimeStateType()).isEmpty());
    }

    @Test
    void htzStateClearsWhenLeavingHtz() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);
        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 0);

        assertTrue(currentRegistry(runtime).currentAs(htzRuntimeStateType()).isEmpty());
    }

    @Test
    void nonHtzInitDoesNotClearUnrelatedRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();
        ZoneRuntimeState sentinel = new ZoneRuntimeState() {
            @Override
            public String gameId() {
                return "test";
            }

            @Override
            public int zoneIndex() {
                return 99;
            }

            @Override
            public int actIndex() {
                return 7;
            }
        };
        currentRegistry(runtime).install(sentinel);

        manager.initLevel(Sonic2LevelEventManager.ZONE_CNZ, 0);

        assertSame(sentinel, currentRegistry(runtime).current());
    }

    @Test
    void adapterReflectsUnderlyingEventValues() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);

        Object state = currentRegistry(runtime)
                .currentAs(htzRuntimeStateType())
                .orElseThrow();

        assertFalse(invokeBoolean(state, "earthquakeActive"));
        assertEquals(0, invokeInt(state, "cameraBgYOffset"));
        assertEquals(0, invokeInt(state, "cameraBgXOffset"));
        assertEquals(0, invokeInt(state, "bgVerticalShift"));
    }

    @Test
    void htzActSwitchRefreshesInstalledMetadata() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);
        manager.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 1);

        Object state = currentRegistry(runtime).currentAs(htzRuntimeStateType()).orElseThrow();
        assertEquals(1, invokeInt(state, "actIndex"));
    }

    @Test
    void resetStateClearsInstalledHtzRuntimeState() {
        GameRuntime runtime = currentRuntime();
        Sonic2LevelEventManager manager = new Sonic2LevelEventManager();

        manager.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);
        manager.resetState();

        assertTrue(currentRegistry(runtime).currentAs(htzRuntimeStateType()).isEmpty());
    }

    private static GameRuntime currentRuntime() {
        GameRuntime runtime = RuntimeManager.getActiveRuntime();
        assertNotNull(runtime, "Expected TestEnvironment.resetAll() to create a gameplay runtime");
        return runtime;
    }

    private static ZoneRuntimeRegistry currentRegistry(GameRuntime runtime) {
        return runtime.getZoneRuntimeRegistry();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends ZoneRuntimeState> htzRuntimeStateType() {
        try {
            return (Class<? extends ZoneRuntimeState>) Class
                    .forName("com.openggf.game.sonic2.runtime.HtzRuntimeState")
                    .asSubclass(ZoneRuntimeState.class);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Expected com.openggf.game.sonic2.runtime.HtzRuntimeState to exist", e);
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        Object result = invoke(target, methodName);
        assertTrue(result instanceof Boolean, methodName + " should return a boolean");
        return (Boolean) result;
    }

    private static int invokeInt(Object target, String methodName) {
        Object result = invoke(target, methodName);
        assertTrue(result instanceof Integer, methodName + " should return an int");
        return (Integer) result;
    }

    private static String invokeString(Object target, String methodName) {
        Object result = invoke(target, methodName);
        assertTrue(result instanceof String, methodName + " should return a string");
        return (String) result;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Expected HTZ runtime state method " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("HTZ runtime state method " + methodName + " threw", e.getCause());
        }
    }
}
