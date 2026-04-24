package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestBubblerObjectInstance {

    @Test
    void childBubbleInitializesWobbleAngleFromSharedRomRng() {
        StubObjectServices services = new StubObjectServices();
        long seed = 0x14A7ABBBL;
        services.rng().setSeed(seed);
        GameRng expected = new GameRng(GameRng.Flavour.S3K, seed);

        BubblerObjectInstance bubble =
                new BubblerObjectInstance(new ObjectSpawn(0x1200, 0x0400, 0x54, 0, 0, false, 0));
        bubble.setServices(services);

        bubble.update(0, null);

        assertEquals(expected.nextByte(), bubble.getWobbleAngleForTest() & 0xFF,
                "Obj_Bubbler initializes angle(a0) from Random_Number, sharing the global S3K RNG stream");
        assertEquals(expected.getSeed(), services.rng().getSeed(),
                "Bubbler must advance the same RNG stream later CNZ balloons use");
    }
}
