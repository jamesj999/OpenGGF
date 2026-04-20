package com.openggf.game.sonic1.objects.badniks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBuzzBomberMissileInstance {

    @Test
    public void missileStaysHarmlessForThirtyCreationFrameTicks() {
        Sonic1BuzzBomberMissileInstance missile =
                new Sonic1BuzzBomberMissileInstance(0, 0, 0x200, 0x200, false, null);

        assertEquals(0, missile.getCollisionFlags(), "Fresh missile should start harmless");

        for (int i = 0; i < 30; i++) {
            missile.update(i + 1, null);
        }

        assertEquals(0, missile.getCollisionFlags(),
                "Missile should still be in its flare window after 30 execution ticks");

        missile.update(31, null);

        assertEquals(0x87, missile.getCollisionFlags(),
                "Missile should arm on the 31st execution tick");
    }
}
