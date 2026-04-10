package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZConveyorBeltObjectInstance {

    private final Camera camera = Camera.getInstance();

    @AfterEach
    void tearDown() throws Exception {
        HCZConveyorBeltObjectInstance.resetLoadArray();
        constructionContext().remove();
        camera.resetState();
    }

    @Test
    void pairedTopAndBottomSubtypesDoNotDeduplicate() throws Exception {
        camera.setX((short) 0x0C00);
        TestObjectServices services = new TestObjectServices().withCamera(camera);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x0800);

        HCZConveyorBeltObjectInstance top = buildBelt(services, 0x0B28, 0x0200, 0x00, 0);
        HCZConveyorBeltObjectInstance bottom = buildBelt(services, 0x0B28, 0x022A, 0x10, 1);

        top.update(1, player);
        bottom.update(1, player);

        assertFalse(top.isDestroyed());
        assertFalse(bottom.isDestroyed());
    }

    @Test
    void duplicateRawSubtypeStillDeduplicates() throws Exception {
        camera.setX((short) 0x0C00);
        TestObjectServices services = new TestObjectServices().withCamera(camera);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x0800);

        HCZConveyorBeltObjectInstance first = buildBelt(services, 0x0B28, 0x0200, 0x00, 0);
        HCZConveyorBeltObjectInstance duplicate = buildBelt(services, 0x0B28, 0x0200, 0x00, 0);

        first.update(1, player);
        duplicate.update(1, player);

        assertFalse(first.isDestroyed());
        assertTrue(duplicate.isDestroyed());
    }

    @Test
    void topReleaseCooldownDoesNotBlockBottomPartnerCapture() throws Exception {
        camera.setX((short) 0x0C00);
        TestObjectServices services = new TestObjectServices().withCamera(camera);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x0C00);
        player.setCentreY((short) 0x0221);
        player.setYSpeed((short) 0);

        HCZConveyorBeltObjectInstance top = buildBelt(services, 0x0B28, 0x0200, 0x00, 0);
        HCZConveyorBeltObjectInstance bottom = buildBelt(services, 0x0B28, 0x022A, 0x10, 1);

        top.update(50, player);
        bottom.update(50, player);
        assertTrue(player.isObjectControlled());
        assertEquals(0x63, player.getMappingFrame());
        assertEquals(0x0214, player.getCentreY() & 0xFFFF);

        player.setJumpInputPressed(true);
        top.update(51, player);
        assertFalse(player.isObjectControlled());
        assertEquals(-0x500, player.getYSpeed());

        player.setJumpInputPressed(false);
        player.setCentreY((short) 0x021B);
        player.setGSpeed((short) 1);

        top.update(52, player);
        bottom.update(52, player);

        assertTrue(player.isObjectControlled());
        assertEquals(0x65, player.getMappingFrame());
        assertEquals(0x0216, player.getCentreY() & 0xFFFF);
    }

    private static HCZConveyorBeltObjectInstance buildBelt(
            ObjectServices services, int x, int y, int subtype, int renderFlags) throws Exception {
        ThreadLocal<ObjectServices> context = constructionContext();
        context.set(services);
        try {
            HCZConveyorBeltObjectInstance belt = new HCZConveyorBeltObjectInstance(
                    new ObjectSpawn(x, y, 0x3E, subtype, renderFlags, false, 0));
            belt.setServices(services);
            return belt;
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
}
