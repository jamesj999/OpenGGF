package com.openggf.game.sonic2.objects;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSpiralObjectInstance {
    private GameModule previousModule;

    @BeforeEach
    void setUp() {
        previousModule = GameModuleRegistry.getCurrent();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        SessionManager.clear();
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.setCurrent(previousModule);
    }

    @Test
    void engageUsesRideObjectSetRideSemantics() {
        SpiralObjectInstance spiral = newSpiral();
        TestablePlayableSprite sonic = playerAt("sonic", 0x0200 - 0x00C8, 0x0100 + 0x0020);
        sonic.setAir(false);
        sonic.setOnObject(false);
        sonic.setXSpeed((short) 0x0600);
        sonic.setYSpeed((short) 0x0123);
        sonic.setGSpeed((short) 0x0000);
        sonic.setAngle((byte) 0x20);

        spiral.update(100, sonic);

        assertTrue(sonic.isOnObject(), "Obj06 should set status.player.on_object when it captures Sonic");
        assertFalse(sonic.getAir(), "RideObject_SetRide clears status.player.in_air");
        assertEquals(0, sonic.getAngle() & 0xFF, "RideObject_SetRide zeros angle");
        assertEquals(0, sonic.getYSpeed(), "RideObject_SetRide zeros y_vel");
        assertEquals(0x0600, sonic.getGSpeed() & 0xFFFF,
                "RideObject_SetRide copies x_vel into inertia");
    }

    @Test
    void sonicAndTailsCanLatchIndependentlyInSameUpdate() {
        TestablePlayableSprite tails = playerAt("tails_p2", 0x0200 + 0x00C8, 0x0100 + 0x0020);
        tails.setAir(false);
        tails.setOnObject(false);
        tails.setXSpeed((short) -0x0600);

        SpiralObjectInstance spiral = newSpiral(new TestObjectServices()
                .withSidekicks(List.of(tails)));

        TestablePlayableSprite sonic = playerAt("sonic", 0x0200 - 0x00C8, 0x0100 + 0x0020);
        sonic.setAir(false);
        sonic.setOnObject(false);
        sonic.setXSpeed((short) 0x0600);

        spiral.update(200, sonic);

        assertTrue(sonic.isOnObject(), "Main player should latch onto Obj06");
        assertFalse(sonic.getAir(), "Main player should stay grounded while riding Obj06");
        assertTrue(tails.isOnObject(), "Sidekick should also latch onto Obj06 in the same frame");
        assertFalse(tails.getAir(), "Sidekick should use RideObject_SetRide semantics too");
    }

    @Test
    void latchSurvivesInlineObjectCleanupWhenDrivenThroughObjectManager() {
        ObjectSpawn spawn = new ObjectSpawn(0x0200, 0x0100, Sonic2ObjectIds.SPIRAL, 0x00, 0, false, 0);
        ObjectManager objectManager = newSpiralManager(List.of(spawn));
        objectManager.reset(0);

        TestablePlayableSprite sonic = playerAt("sonic", 0x0200 - 0x00C8, 0x0100 + 0x0020);
        sonic.setAir(false);
        sonic.setOnObject(false);
        sonic.setXSpeed((short) 0x0600);
        sonic.setYSpeed((short) 0x0123);
        sonic.setGSpeed((short) 0x0000);

        objectManager.update(0, sonic, List.of(), 1, false, true, true);

        assertTrue(sonic.isOnObject(),
                "Inline ObjectManager cleanup should not clear Obj06's non-solid on-object latch");
        assertFalse(sonic.getAir(),
                "Obj06 should still leave Sonic grounded after the inline object pass completes");
    }

    @Test
    void transferToSecondSpiralClearsPreviousSpiralOwnership() {
        ObjectSpawn firstSpawn = new ObjectSpawn(0x24C0, 0x0280, Sonic2ObjectIds.SPIRAL, 0x00, 0, false, 0);
        ObjectSpawn secondSpawn = new ObjectSpawn(0x2640, 0x0280, Sonic2ObjectIds.SPIRAL, 0x00, 0, false, 0);
        ObjectManager objectManager = newSpiralManager(List.of(firstSpawn, secondSpawn));
        objectManager.reset(0x2400);
        objectManager.preloadInitialSpawnsForHydration();

        TestablePlayableSprite sonic = playerAt("sonic", 0x2583, 0x02A0);
        sonic.setAir(false);
        sonic.setOnObject(true);
        sonic.setXSpeed((short) 0x08FA);
        sonic.setYSpeed((short) 0x0000);
        sonic.setGSpeed((short) 0x08FA);

        SpiralObjectInstance firstSpiral = spiralAt(objectManager, 0x24C0);
        SpiralObjectInstance secondSpiral = spiralAt(objectManager, 0x2640);
        markRiding(firstSpiral, sonic);

        objectManager.update(0x2400, sonic, List.of(), 1, false, true, true);

        assertFalse(isRiding(firstSpiral, sonic),
                "RideObject_SetRide parity should clear the previous spiral's standing bit");
        assertTrue(isRiding(secondSpiral, sonic),
                "Second spiral should own Sonic after the handoff frame");
    }

    private static SpiralObjectInstance newSpiral() {
        return newSpiral(new TestObjectServices());
    }

    private static SpiralObjectInstance newSpiral(TestObjectServices services) {
        SpiralObjectInstance spiral = new SpiralObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic2ObjectIds.SPIRAL, 0x00, 0, false, 0),
                "Spiral");
        spiral.setServices(services);
        return spiral;
    }

    private static TestablePlayableSprite playerAt(String code, int centreX, int centreY) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0, (short) 0);
        player.setCentreX((short) centreX);
        player.setCentreY((short) centreY);
        return player;
    }

    private static ObjectManager newSpiralManager(List<ObjectSpawn> spawns) {
        AtomicReference<ObjectManager> managerRef = new AtomicReference<>();
        TestObjectServices services = new TestObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return managerRef.get();
            }
        }.withGraphicsManager(GameServices.graphics())
                .withCamera(GameServices.camera())
                .withGameModule(GameModuleRegistry.getCurrent());
        ObjectManager objectManager = new ObjectManager(
                spawns,
                new SpiralRegistry(),
                0,
                null,
                null,
                GameServices.graphics(),
                GameServices.camera(),
                services);
        managerRef.set(objectManager);
        return objectManager;
    }

    private static SpiralObjectInstance spiralAt(ObjectManager objectManager, int spawnX) {
        return objectManager.getActiveObjects().stream()
                .filter(SpiralObjectInstance.class::isInstance)
                .map(SpiralObjectInstance.class::cast)
                .filter(spiral -> spiral.getSpawn().x() == spawnX)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No spiral found at x=" + Integer.toHexString(spawnX)));
    }

    @SuppressWarnings("unchecked")
    private static void markRiding(SpiralObjectInstance spiral, TestablePlayableSprite player) {
        try {
            Field field = SpiralObjectInstance.class.getDeclaredField("ridingPlayers");
            field.setAccessible(true);
            ((java.util.Set<TestablePlayableSprite>) field.get(spiral)).add(player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to seed spiral riding state", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean isRiding(SpiralObjectInstance spiral, TestablePlayableSprite player) {
        try {
            Field field = SpiralObjectInstance.class.getDeclaredField("ridingPlayers");
            field.setAccessible(true);
            return ((java.util.Set<TestablePlayableSprite>) field.get(spiral)).contains(player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect spiral riding state", e);
        }
    }

    private static final class SpiralRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return new SpiralObjectInstance(spawn, "Spiral");
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Spiral";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return ObjectSlotLayout.SONIC_2;
        }
    }
}
