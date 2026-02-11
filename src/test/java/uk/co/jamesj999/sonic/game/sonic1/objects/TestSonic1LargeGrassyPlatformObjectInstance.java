package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

    private static Sonic1LargeGrassyPlatformObjectInstance create(int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x2F, subtype, 0, false, 0);
        return new Sonic1LargeGrassyPlatformObjectInstance(spawn, null);
    }
}
