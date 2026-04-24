package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestCnzRisingPlatformInstance {

    @Test
    void solidTopWidthMatchesRomD1() {
        CnzRisingPlatformInstance platform = new CnzRisingPlatformInstance(
                new ObjectSpawn(0x1A40, 0x0790, 0x43, 0, 0, false, 0));

        assertEquals(0x30, platform.getSolidParams().halfWidth());
        assertEquals(0x10, platform.getSolidParams().airHalfHeight());
        assertEquals(0x11, platform.getSolidParams().groundHalfHeight());
    }

    @Test
    void ridingBoundsDoNotUseEngineStickyBuffer() {
        CnzRisingPlatformInstance platform = new CnzRisingPlatformInstance(
                new ObjectSpawn(0x1A40, 0x0790, 0x43, 0, 0, false, 0));

        assertFalse(platform.usesStickyContactBuffer(),
                "ROM SolidObjectTop_1P exits at the exact d1*2 ride bounds");
    }

    @Test
    void floorSnappedPlatformDoesNotBounceAgainWhenRiderLeaves() throws Exception {
        CnzRisingPlatformInstance platform = new CnzRisingPlatformInstance(
                new ObjectSpawn(0x1A40, 0x0790, 0x43, 0, 0, false, 0));

        setField(platform, "armed", true);
        setField(platform, "floorSettledRoutine", true);
        setField(platform, "displayFrame", 2);

        platform.update(0, null);

        assertEquals(0, platform.getYSpeedForTest(), "Terminal floor-snap state must not create a release bounce");
        assertEquals(2, platform.getRenderFrameForTest());
    }

    @Test
    void animationFrameTwoDoesNotMeanTerminalRoutine() throws Exception {
        CnzRisingPlatformInstance platform = new CnzRisingPlatformInstance(
                new ObjectSpawn(0x1A40, 0x0790, 0x43, 0, 0, false, 0));

        setField(platform, "armed", true);
        setField(platform, "displayFrame", 2);
        setField(platform, "motion.yVel", 0x0100);

        platform.update(0, null);

        assertFalse(platform.isArmedForTest());
        assertEquals(-0x0180, platform.getYSpeedForTest(),
                "ROM loc_31C86 negates y_vel and subtracts $80 when the rider leaves");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        if (name.contains(".")) {
            String[] parts = name.split("\\.", 2);
            Field outer = target.getClass().getDeclaredField(parts[0]);
            outer.setAccessible(true);
            Object nested = outer.get(target);
            setField(nested, parts[1], value);
            return;
        }
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
