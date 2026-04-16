package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCnzTraversalRegistry {

    @Test
    public void s3klTraversalSlotsResolveToConcreteCarnivalNightObjects() throws Exception {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        assertObjectType(registry.create(new ObjectSpawn(0x1200, 0x0580,
                        0x41, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzBalloonInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x1600, 0x0680,
                        0x42, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzCannonInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x1800, 0x05A0,
                        0x43, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzRisingPlatformInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x1A00, 0x05C0,
                        0x44, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzTrapDoorInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x1C00, 0x05E0,
                        0x46, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzHoverFanInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x1E00, 0x0600,
                        0x47, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzCylinderInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x2000, 0x0620,
                        0x48, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzVacuumTubeInstance");
        assertObjectType(registry.create(new ObjectSpawn(0x2200, 0x0640,
                        0x4C, 0, 0, false, 0)),
                "com.openggf.game.sonic3k.objects.CnzSpiralTubeInstance");
    }

    private static void assertObjectType(ObjectInstance instance, String expectedClassName)
            throws ClassNotFoundException {
        Class<?> expected = Class.forName(expectedClassName);
        assertTrue(expected.isInstance(instance),
                "Expected " + expectedClassName + " but found "
                        + (instance != null ? instance.getClass().getName() : "null"));
        assertEquals(expected, instance.getClass());
    }
}
