package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGumballItemPriority {

    @Test
    void staticGumballItem_usesLowBucket4Priority() {
        GumballItemObjectInstance item =
                new GumballItemObjectInstance(new ObjectSpawn(0x100, 0x180, 0xEB, 0x00, 0, false, 0));

        assertEquals(4, item.getPriorityBucket());
        assertFalse(item.isHighPriority());
    }

    @Test
    void machineEjectedGumballItem_usesHighBucket3Priority() {
        GumballItemObjectInstance item =
                new GumballItemObjectInstance(new ObjectSpawn(0x100, 0x180, 0xEB, 0x00, 0, false, 0), 0, true);

        assertEquals(3, item.getPriorityBucket());
        assertTrue(item.isHighPriority());
    }

    @Test
    void pachinkoFloatItem_keepsRegularLowPriorityAttributes() {
        GumballItemObjectInstance item =
                GumballItemObjectInstance.createPachinkoItem(
                        new ObjectSpawn(0x100, 0x180, 0xEB, 0x00, 0, false, 0));

        assertEquals(4, item.getPriorityBucket());
        assertFalse(item.isHighPriority());
    }
}
