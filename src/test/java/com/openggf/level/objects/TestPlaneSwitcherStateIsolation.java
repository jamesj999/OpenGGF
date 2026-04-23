package com.openggf.level.objects;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlaneSwitcherStateIsolation {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        SessionManager.clear();
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void sonicAndTailsKeepIndependentPlaneSwitcherSideState() {
        ObjectSpawn switcher = new ObjectSpawn(0x06A8, 0x02C8, 0x03, 0x11, 0x00, false, 0);
        ObjectManager objectManager = new ObjectManager(
                List.of(switcher),
                new ObjectRegistry() {
                    @Override
                    public ObjectInstance create(ObjectSpawn spawn) {
                        return null;
                    }

                    @Override
                    public void reportCoverage(List<ObjectSpawn> spawns) {
                    }

                    @Override
                    public String getPrimaryName(int objectId) {
                        return "Test";
                    }
                },
                0x03,
                new PlaneSwitcherConfig((byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F),
                null,
                null,
                null,
                null);
        objectManager.reset(0x060A);

        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setCentreX((short) 0x069C);
        sonic.setCentreY((short) 0x02D0);
        sonic.setAir(false);
        sonic.setTopSolidBit((byte) 0x0C);
        sonic.setLrbSolidBit((byte) 0x0D);

        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCentreX((short) 0x06A8);
        tails.setCentreY((short) 0x02D0);
        tails.setAir(false);
        tails.setTopSolidBit((byte) 0x0C);
        tails.setLrbSolidBit((byte) 0x0D);

        // Seed Tails on the right side of the switcher, matching the pre-crossing trace state.
        objectManager.applyPlaneSwitchers(tails);

        // Sonic is already on the left side and updates first in the frame.
        objectManager.applyPlaneSwitchers(sonic);

        // Tails then crosses to the left side in the same frame.
        tails.setCentreX((short) 0x06A2);
        objectManager.applyPlaneSwitchers(tails);

        assertEquals(1, tails.getLayer(), "Tails should switch to path 1 when crossing left of the switcher");
        assertEquals(0x0E, tails.getTopSolidBit() & 0xFF);
        assertEquals(0x0F, tails.getLrbSolidBit() & 0xFF);
    }

    static class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) {
            super(code, (short) 0, (short) 0);
        }

        @Override
        public void draw() {
        }

        @Override
        public void defineSpeeds() {
        }

        @Override
        protected void createSensorLines() {
        }
    }
}
