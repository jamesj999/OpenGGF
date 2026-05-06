package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused round-trip coverage for {@link SidekickCpuController}'s mutable
 * scalar state through {@link AbstractPlayableSprite#captureRewindState()}.
 */
class TestSidekickCpuControllerRewindCapture {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void roundTripCoversSidekickCpuScalarSurface() throws Exception {
        Sonic leader = new Sonic("sonic", (short) 0x100, (short) 0x200);
        Tails tails = new Tails("tails_p2", (short) 0x80, (short) 0x220);
        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        tails.setCpuControlled(true);
        tails.setCpuController(controller);

        Map<String, Object> sentinels = scalarSentinels();
        for (var entry : sentinels.entrySet()) {
            writeField(controller, entry.getKey(), entry.getValue());
        }

        PerObjectRewindSnapshot snap1 = tails.captureRewindState();
        Object cpuExtra1 = readRecordComponent(snap1.playerExtra(), "sidekickCpuExtra");
        assertNotNull(cpuExtra1, "PlayerRewindExtra must include SidekickCpuController state");

        assertNoRecordComponent(cpuExtra1, "leader");
        assertNoRecordComponent(cpuExtra1, "respawnStrategy");
        assertNoRecordComponent(cpuExtra1, "carryTrigger");
        for (var entry : sentinels.entrySet()) {
            assertEquals(entry.getValue(), readRecordComponent(cpuExtra1, entry.getKey()),
                    entry.getKey() + " mismatch in snapshot");
        }

        tails.setCpuController(new SidekickCpuController(tails, leader));
        tails.restoreRewindState(snap1);

        SidekickCpuController restored = tails.getCpuController();
        PerObjectRewindSnapshot snap2 = tails.captureRewindState();
        Object cpuExtra2 = readRecordComponent(snap2.playerExtra(), "sidekickCpuExtra");
        assertEquals(cpuExtra1, cpuExtra2, "Sidekick CPU snapshot did not round-trip");
        for (var entry : sentinels.entrySet()) {
            assertEquals(entry.getValue(), readField(restored, entry.getKey()),
                    entry.getKey() + " not restored to controller");
        }
    }

    private static Map<String, Object> scalarSentinels() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("state", SidekickCpuController.State.CARRYING);
        values.put("despawnCounter", 11);
        values.put("frameCounter", 1234);
        values.put("controlCounter", 22);
        values.put("controller2Held", 0x72);
        values.put("controller2Logical", 0x20);
        values.put("inputUp", true);
        values.put("inputDown", true);
        values.put("inputLeft", true);
        values.put("inputRight", false);
        values.put("inputJump", true);
        values.put("inputJumpPress", true);
        values.put("jumpingFlag", true);
        values.put("minXBound", -0x120);
        values.put("maxXBound", 0x2345);
        values.put("maxYBound", 0x456);
        values.put("lastInteractObjectId", 0xA7);
        values.put("normalFrameCount", 17);
        values.put("sidekickCount", 3);
        values.put("normalPushingGraceFrames", 8);
        values.put("suppressNextAirbornePushFollowSteering", true);
        values.put("aizObjectOrderGracePushBypassThisFrame", true);
        values.put("pendingGroundedFollowNudge", -1);
        values.put("pendingGroundedFollowNudgeFrame", 321);
        values.put("aizIntroDormantMarkerPrimed", true);
        values.put("suppressNextAizIntroNormalMovement", true);
        values.put("skipPhysicsThisFrame", true);
        values.put("cpuFrameCounterFromStoredLevelFrame", true);
        values.put("latestNormalStepDiagnostics", diagnosticsSentinel());
        values.put("carryLatchX", (short) 0x0123);
        values.put("carryLatchY", (short) -0x0456);
        values.put("flyingCarryingFlag", true);
        values.put("carryParentagePending", true);
        values.put("releaseCooldown", 19);
        values.put("mgzCarryIntroAscend", true);
        values.put("mgzCarryFlapTimer", 5);
        values.put("mgzReleasedChaseLatched", true);
        values.put("mgzReleasedChaseXAccel", (short) 0x33);
        values.put("mgzReleasedChaseYAccel", (short) -0x44);
        values.put("flightTimer", 77);
        values.put("catchUpTargetX", 0x3456);
        values.put("catchUpTargetY", 0x0789);
        return values;
    }

    private static SidekickCpuController.NormalStepDiagnostics diagnosticsSentinel() {
        return new SidekickCpuController.NormalStepDiagnostics(
                1234, SidekickCpuController.State.NORMAL, "sentinel",
                1, 2, (short) 3, (short) 4,
                5, 6, 7, 8, 9,
                10, 11, 12, 13, 14,
                (short) 15, (short) 16,
                17, 18, (short) 19, (short) 20,
                true, true);
    }

    private static Object readRecordComponent(Object record, String name) throws Exception {
        assertTrue(record.getClass().isRecord(), "Expected record: " + record.getClass());
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component.getAccessor().invoke(record);
            }
        }
        fail("Missing record component: " + name);
        return null;
    }

    private static void assertNoRecordComponent(Object record, String name) {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            assertNotEquals(name, component.getName(), "Structural reference must not be snapshotted");
        }
    }

    private static void writeField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
