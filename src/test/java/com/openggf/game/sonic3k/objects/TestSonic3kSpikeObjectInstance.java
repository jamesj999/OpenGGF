package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kSpikeObjectInstance {

    @Test
    void spikesUseSolidObjectFullInclusiveRightEdge() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1FF8, 0x0564, Sonic3kObjectIds.SPIKES, 0x00, 0, false, 0));

        assertTrue(spikes.usesInclusiveRightEdge(),
                "Obj_Spikes calls SolidObjectFull; SolidObject_cont rejects relX > width*2, not relX == width*2");
    }
}
