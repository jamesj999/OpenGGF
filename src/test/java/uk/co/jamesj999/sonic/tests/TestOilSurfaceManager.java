package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic2.OilSurfaceManager;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestOilSurfaceManager {

    private static final int OIL_SURFACE_Y = Sonic2Constants.OIL_SURFACE_Y - Sonic2Constants.OIL_SUBMERSION_MAX;

    private OilSurfaceManager manager;
    private TestOilSprite sprite;

    @Before
    public void setUp() {
        manager = new OilSurfaceManager();
        sprite = new TestOilSprite("test", (short) 0, (short) 0);
    }

    @Test
    public void keepsOilSupportWhenAirFlagTemporarilySet() {
        landOnOilSurface();
        assertTrue(manager.isStandingOnOil());

        // Simulate movement step clearing support before oil update.
        sprite.setAir(true);
        sprite.setOnObject(false);
        sprite.setYSpeed((short) 0x20);
        int before = manager.getSubmersion();

        manager.update(sprite);

        assertTrue(manager.isStandingOnOil());
        assertFalse(sprite.getAir());
        assertTrue(sprite.isOnObject());
        assertEquals(before - 1, manager.getSubmersion());
    }

    @Test
    public void jumpReleaseClearsOilSupport() {
        landOnOilSurface();
        assertTrue(manager.isStandingOnOil());

        sprite.setAir(true);
        sprite.setJumping(true);
        sprite.setYSpeed((short) -0x200);

        manager.update(sprite);

        assertFalse(manager.isStandingOnOil());
        assertFalse(sprite.isOnObject());
    }

    @Test
    public void suffocatesAfterSubmersionCountdownExpires() {
        landOnOilSurface();
        assertTrue(manager.isStandingOnOil());

        for (int i = 0; i < Sonic2Constants.OIL_SUBMERSION_MAX; i++) {
            manager.update(sprite);
            assertFalse("Should not be dead while submersion is decrementing", sprite.getDead());
        }

        assertEquals(0, manager.getSubmersion());
        manager.update(sprite);

        assertTrue("Should die when submersion reaches zero on standing frame", sprite.getDead());
        assertFalse(manager.isStandingOnOil());
        assertFalse(sprite.isOnObject());
    }

    private void landOnOilSurface() {
        int centreY = OIL_SURFACE_Y + 1 - sprite.getYRadius();
        sprite.setCentreY((short) centreY);
        sprite.setAir(true);
        sprite.setOnObject(false);
        sprite.setJumping(false);
        sprite.setYSpeed((short) 0x200);
        manager.update(sprite);
    }

    private static class TestOilSprite extends AbstractPlayableSprite {
        TestOilSprite(String code, short x, short y) {
            super(code, x, y);
        }

        @Override
        public void draw() {
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 12;
            runDecel = 128;
            friction = 12;
            max = 1536;
            jump = 1664;
        }

        @Override
        protected void createSensorLines() {
        }
    }
}
