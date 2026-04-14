package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizEndBossInstance {
    private GameRuntime runtime;
    private Camera camera;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        runtime = RuntimeManager.getCurrent();
        camera = runtime.getCamera();
        camera.resetState();
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
        constructionContext().remove();
        RuntimeManager.destroyCurrent();
    }

    @Test
    void initLocksCameraAndLoadsPaletteIntoOwnershipRegistryLineOne() throws Exception {
        camera.setX((short) 0x48A0);

        byte[] paletteLine = new byte[32];
        paletteLine[0] = 0x0E;
        paletteLine[1] = (byte) 0xEE;
        paletteLine[2] = 0x08;
        paletteLine[3] = (byte) 0x88;

        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        services.withRom(new FixedReadRom(paletteLine));
        services.registry.beginFrame();

        AizEndBossInstance boss = buildBoss(services);
        boss.update(0, null);
        services.registry.resolveInto(services.level.palettes(), null, null, null);

        assertEquals(S3kPaletteOwners.AIZ_END_BOSS, services.registry.ownerAt(PaletteSurface.NORMAL, 1, 0));
        assertEquals(S3kPaletteOwners.AIZ_END_BOSS, services.registry.ownerAt(PaletteSurface.NORMAL, 1, 1));
        assertColorWord(services.level.getPalette(1), 0, 0x0EEE);
        assertColorWord(services.level.getPalette(1), 1, 0x0888);
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
        if (services instanceof TestObjectServices testServices && testServices.configuration() == null) {
            testServices.withConfiguration(SonicConfigurationService.getInstance());
        }
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

    private static void assertColorWord(Palette palette, int colorIndex, int segaWord) {
        byte highByte = (byte) ((segaWord >> 8) & 0xFF);
        byte lowByte = (byte) (segaWord & 0xFF);
        int r3 = (lowByte >> 1) & 0x07;
        int g3 = (lowByte >> 5) & 0x07;
        int b3 = (highByte >> 1) & 0x07;
        int expectedR = (r3 * 255 + 3) / 7;
        int expectedG = (g3 * 255 + 3) / 7;
        int expectedB = (b3 * 255 + 3) / 7;
        assertEquals(expectedR, palette.getColor(colorIndex).r & 0xFF);
        assertEquals(expectedG, palette.getColor(colorIndex).g & 0xFF);
        assertEquals(expectedB, palette.getColor(colorIndex).b & 0xFF);
    }

    private static final class RecordingServices extends TestObjectServices {
        private final StubLevel level = new StubLevel();
        private final PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();

        @Override
        public Level currentLevel() {
            return level;
        }

        @Override
        public PaletteOwnershipRegistry paletteOwnershipRegistryOrNull() {
            return registry;
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

    private static final class StubLevel implements Level {
        private final Palette[] palettes = new Palette[] {
                new Palette(), new Palette(), new Palette(), new Palette()
        };

        Palette[] palettes() {
            return palettes;
        }

        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { throw new UnsupportedOperationException(); }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int index) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { return null; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0; }
    }
}


