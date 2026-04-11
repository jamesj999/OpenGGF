package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.sonic2.OilSurfaceManager;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOilSurfaceManager {

    private static final int OIL_SURFACE_Y = Sonic2Constants.OIL_SURFACE_Y - Sonic2Constants.OIL_SUBMERSION_MAX;

    private OilSurfaceManager manager;
    private TestOilSprite sprite;

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
        manager = new OilSurfaceManager();
        sprite = new TestOilSprite("test", (short) 0, (short) 0);
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
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
            assertFalse(sprite.getDead(), "Should not be dead while submersion is decrementing");
        }

        assertEquals(0, manager.getSubmersion());
        manager.update(sprite);

        assertTrue(sprite.getDead(), "Should die when submersion reaches zero on standing frame");
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


