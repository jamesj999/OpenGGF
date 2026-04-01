package com.openggf.tests;

import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.TouchResponseProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSonic1RingInstance {

    @Test
    public void testRingCollisionFlagsBeforeCollection() {
        assertEquals(0x47, Sonic1RingInstance.RING_COLLISION_FLAGS);
    }

    @Test
    public void testImplementsTouchResponseProvider() {
        assertTrue(TouchResponseProvider.class.isAssignableFrom(Sonic1RingInstance.class));
    }
}
