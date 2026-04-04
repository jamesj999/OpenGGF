package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizEndBossInstance {
    private final Camera camera = Camera.getInstance();

    @BeforeEach
    void setUp() {
        camera.resetState();
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
        constructionContext().remove();
        camera.resetState();
    }

    @Test
    void initLocksCameraAndLoadsPaletteIntoEngineIndexOne() throws Exception {
        camera.setX((short) 0x48A0);

        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        services.withRom(new FixedReadRom(new byte[32]));

        AizEndBossInstance boss = buildBoss(services);
        boss.update(0, null);

        assertEquals(1, services.lastPaletteIndex);
        assertEquals(0x4880, camera.getMinX() & 0xFFFF);
        assertEquals(0x4880, camera.getMaxX() & 0xFFFF);
    }

    @Test
    void retreatSubmergeAnimationRunsRepositionCallback() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "beginReSubmerge");

        for (int frame = 0; frame < 45; frame++) {
            boss.update(frame, null);
        }

        assertEquals(12, boss.getState().routine);
    }

    @Test
    void fireSignalTriggersBurnBridgeVariant() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);

        AizCollapsingLogBridgeObjectInstance bridge = new AizCollapsingLogBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x2C, 0x80, 0, false, 0));
        bridge.setServices(new TestObjectServices());

        invokeNoArg(boss, "onFireTimerExpired");
        bridge.update(0, null);

        assertTrue(readBoolean(bridge, "segmentsSpawned"));
        assertTrue(bridge.isHighPriority());
    }

    @Test
    void retreatDoesNotClearBridgeBurnLatchBeforeBridgeConsumesIt() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);

        AizCollapsingLogBridgeObjectInstance bridge = new AizCollapsingLogBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x2C, 0x80, 0, false, 0));
        bridge.setServices(new TestObjectServices());

        invokeNoArg(boss, "onFireTimerExpired");
        invokeNoArg(boss, "beginRetreat");
        bridge.update(0, null);

        assertTrue(readBoolean(bridge, "segmentsSpawned"));
    }

    @Test
    void cameraScrollPhaseAdvancesArenaBoundsWithRomParity() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        camera.setX((short) 0x4880);
        camera.setMinX((short) 0x4880);
        camera.setMaxX((short) 0x4880);

        invokeNoArg(boss, "updateCameraScroll");

        assertEquals(0x4882, camera.getMinX() & 0xFFFF);
        assertEquals(0x4882, camera.getMaxX() & 0xFFFF);

        camera.setMinX((short) 0x48E0);
        camera.setMaxX((short) 0x48E0);
        invokeNoArg(boss, "updateCameraScroll");

        assertEquals(0x48E0, camera.getMinX() & 0xFFFF);
        assertEquals(0x48E2, camera.getMaxX() & 0xFFFF);
    }

    @Test
    void cameraScrollPhaseLetsCameraFollowIntoExpandedRightBound() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x49A8);
        player.setCentreY((short) 0x0100);
        camera.setFocusedSprite(player);
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x00A0);
        camera.setMinX((short) 0x48E0);
        camera.setMaxX((short) 0x48E0);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 0x1000);

        invokeNoArg(boss, "updateCameraScroll");
        camera.updatePosition();

        assertEquals(0x48E2, camera.getX() & 0xFFFF);
    }

    @Test
    void hoverSetupDoesNotChangeBossRenderFacing() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);

        invokeNoArg(boss, "doMainInit");
        invokeNoArg(boss, "beginHover");

        assertTrue(boss.isFacingRight());
    }

    private static AizEndBossInstance buildBoss(ObjectServices services) throws Exception {
        ThreadLocal<ObjectServices> context = constructionContext();
        context.set(services);
        try {
            AizEndBossInstance boss = new AizEndBossInstance(
                    new ObjectSpawn(0x48E0, 0x015A, 0x92, 0, 0, false, 0));
            boss.setServices(services);
            return boss;
        } finally {
            context.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<ObjectServices> constructionContext() throws Exception {
        Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
        field.setAccessible(true);
        return (ThreadLocal<ObjectServices>) field.get(null);
    }

    private static void invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static boolean readBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static final class RecordingServices extends TestObjectServices {
        int lastPaletteIndex = -1;

        @Override
        public void updatePalette(int paletteIndex, byte[] paletteData) {
            lastPaletteIndex = paletteIndex;
        }
    }

    private static final class FixedReadRom extends Rom {
        private final byte[] bytes;

        private FixedReadRom(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public byte[] readBytes(long offset, int count) {
            return bytes;
        }
    }
}
