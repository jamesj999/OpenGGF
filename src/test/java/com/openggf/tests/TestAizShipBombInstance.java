package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.objects.AizBattleshipInstance;
import com.openggf.game.sonic3k.objects.AizBombExplosionInstance;
import com.openggf.game.sonic3k.objects.AizShipBombInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestAizShipBombInstance {

    private static final int BOMB_SCRIPT_X = 0x3F5C;
    private static final int PORT_START_Y = 0x0A60;
    private static final int PORT_READY_Y = 0x0A80;

    @Test
    public void testAttachedBombTracksBattleshipTranslationUntilRelease() {
        Camera camera = new Camera();
        camera.setX((short) 0x4380);
        camera.setY((short) 0x0180);

        ObjectServices services = servicesWithCamera(camera);
        int baseSecondaryY = camera.getY() + 0x08F0;

        AizBattleshipInstance ship = buildWithContext(services,
                () -> new AizBattleshipInstance(
                        new ObjectSpawn(camera.getX(), baseSecondaryY, 0, 0, 0, false, 0),
                        baseSecondaryY));
        AizShipBombInstance bomb = buildWithContext(services,
                () -> new AizShipBombInstance(
                        new ObjectSpawn(camera.getX(), camera.getY(), 0, 0, 0, false, 0),
                        ship,
                        BOMB_SCRIPT_X,
                        camera.getY() + (PORT_START_Y - baseSecondaryY)));

        for (int i = 0; i < 16; i++) {
            ship.update(i, null);
            bomb.update(i, null);
        }

        int expectedX = camera.getX() + (BOMB_SCRIPT_X - ship.getSecondaryCameraX());
        int expectedY = camera.getY() + (PORT_READY_Y - ship.getSecondaryCameraY());

        assertTrue("Bomb should still be rendered in the battleship overlay before release",
                bomb.shouldRenderBehindBattleship());
        assertEquals("Attached bomb X should keep translating from the ship's live camera",
                expectedX, bomb.getX());
        assertEquals("Attached bomb Y should keep following the ship bob while settling in the bay",
                expectedY, bomb.getY());

        for (int i = 16; i < 22; i++) {
            ship.update(i, null);
            bomb.update(i, null);
        }

        assertFalse("Released bomb should no longer be rendered in the behind-ship overlay",
                bomb.shouldRenderBehindBattleship());
        assertEquals("Released bomb X should still use the ship-relative translation",
                camera.getX() + (BOMB_SCRIPT_X - ship.getSecondaryCameraX()), bomb.getX());
    }

    @Test
    public void testBattleshipBombScriptMatchesRomUnderflowCadence() {
        Camera camera = new Camera();
        camera.setX((short) 0x4380);
        camera.setY((short) 0x0180);

        ObjectServices services = servicesWithCamera(camera);
        int baseSecondaryY = camera.getY() + 0x08F0;

        AizBattleshipInstance ship = buildWithContext(services,
                () -> new AizBattleshipInstance(
                        new ObjectSpawn(camera.getX(), baseSecondaryY, 0, 0, 0, false, 0),
                        baseSecondaryY));

        advanceShip(ship, 420);
        assertEquals("The initial $1A4 delay should not spawn a bomb until the counter underflows",
                0, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 1);
        assertEquals("The first bomb should spawn on update 421 after the $1A4 counter underflows",
                1, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 32);
        assertEquals("A $20 script delay should still be waiting after 32 more updates",
                1, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 1);
        assertEquals("A $20 script delay should produce the next bomb after 33 updates",
                2, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 99);
        assertEquals("By this point the first five bombs should have spawned on consecutive $20 gaps",
                5, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 32);
        assertEquals("Bomb 6 should still be pending because bomb 5's entry keeps the gap at $20+1",
                5, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 1);
        assertEquals("Bomb 6 should spawn after bomb 5's $20 delay, not bomb 6's $38 delay",
                6, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 56);
        assertEquals("Bomb 7 should still be waiting because bomb 6's entry sets the long $38 gap",
                6, readIntField(ship, "scriptIndex"));

        advanceShip(ship, 1);
        assertEquals("Bomb 7 should spawn after the full $38+1-frame delay from bomb 6's entry",
                7, readIntField(ship, "scriptIndex"));
    }

    @Test
    public void testExplosionFragmentAppliesWrapOffsetInWorldSpace() {
        AizBombExplosionInstance explosion = new AizBombExplosionInstance(0x4550, 0x02C0, 0, 0);

        explosion.applyWrapOffset(0x0200);

        assertEquals("Explosion fragments should shift back with Level_repeat_offset wraps",
                0x4350, explosion.getX());
    }

    private static ObjectServices servicesWithCamera(Camera camera) {
        return new StubObjectServices() {
            @Override
            public Camera camera() {
                return camera;
            }
        };
    }

    private static <T extends AbstractObjectInstance> T buildWithContext(ObjectServices services,
                                                                         ObjectBuilder<T> builder) {
        setConstructionContext(services);
        try {
            T object = builder.build();
            object.setServices(services);
            return object;
        } finally {
            clearConstructionContext();
        }
    }

    private static void advanceShip(AizBattleshipInstance ship, int updates) {
        for (int i = 0; i < updates; i++) {
            ship.update(i, null);
        }
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices services) {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(services);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface ObjectBuilder<T> {
        T build();
    }
}
