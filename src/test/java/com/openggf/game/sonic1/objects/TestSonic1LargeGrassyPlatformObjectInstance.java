package com.openggf.game.sonic1.objects;

import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic1LargeGrassyPlatformObjectInstance {

    @Test
    public void wideVariantUsesSolidObject2fCompensation() {
        Sonic1LargeGrassyPlatformObjectInstance platform = create(0x00);

        SolidObjectParams params = platform.getSolidParams();
        assertEquals(0x20, params.airHalfHeight());
        assertEquals(0x20, params.groundHalfHeight());
        assertEquals(0x20, platform.getSlopeBaseline());
        assertFalse(platform.isTopSolidOnly());
    }

    @Test
    public void narrowVariantUsesSolidObject2fCompensation() {
        Sonic1LargeGrassyPlatformObjectInstance platform = create(0x20);

        SolidObjectParams params = platform.getSolidParams();
        assertEquals(0x30, params.airHalfHeight());
        assertEquals(0x30, params.groundHalfHeight());
        assertEquals(0x30, platform.getSlopeBaseline());
        assertFalse(platform.isTopSolidOnly());
    }

    @Test
    public void grassyPlatformUsesHighPrioritySpriteBit() {
        Sonic1LargeGrassyPlatformObjectInstance platform = create(0x00);
        assertTrue(platform.isHighPriority());
    }

    private static Sonic1LargeGrassyPlatformObjectInstance create(int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x2F, subtype, 0, false, 0);
        return new Sonic1LargeGrassyPlatformObjectInstance(spawn);
    }
}


