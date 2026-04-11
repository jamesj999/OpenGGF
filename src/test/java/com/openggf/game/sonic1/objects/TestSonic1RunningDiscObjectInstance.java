package com.openggf.game.sonic1.objects;

import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectSpawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic1RunningDiscObjectInstance {

    @Test
    public void attachesAndSetsStickToConvex() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);
        player.setGSpeed((short) 0);

        disc.update(1, player);

        // ROM: move.b #1,stick_to_convex(a1)
        assertTrue(player.isStickToConvex());
        // ROM: move.w #$400,obInertia(a1) (positive angularSpeed -> clamp rightward)
        assertEquals(0x400, player.getGSpeed());
    }

    @Test
    public void leavingDiscClearsStickToConvex() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);

        disc.update(1, player);
        assertTrue(player.isStickToConvex());

        // Outside detection square -> detach
        // ROM: clr.b stick_to_convex(a1) / clr.b disc_sonic_attached(a0)
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);
        disc.update(2, player);

        assertFalse(player.isStickToConvex());
    }

    @Test
    public void airborneInsideRangeClearsAttachmentButKeepsConvex() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);

        disc.update(1, player);
        assertTrue(player.isStickToConvex());

        // In-range but airborne: ROM clears disc_sonic_attached but does NOT
        // clear stick_to_convex (only the .detach path does that).
        player.setAir(true);
        disc.update(2, player);
        assertTrue(player.isStickToConvex());

        // Out of range, but disc_sonic_attached was already cleared by the
        // airborne path, so the .detach branch skips stick_to_convex clearing
        // (ROM: tst.b disc_sonic_attached / beq.s .return). ROM-accurate.
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);
        disc.update(3, player);
        assertTrue(player.isStickToConvex());
    }

    private static Sonic1RunningDiscObjectInstance createDisc(int subtype, int x, int y) {
        return new Sonic1RunningDiscObjectInstance(
                new ObjectSpawn(x, y, 0x67, subtype, 0, false, 0));
    }

}


