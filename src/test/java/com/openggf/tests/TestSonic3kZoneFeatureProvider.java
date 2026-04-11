package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.RenderPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.openggf.level.scroll.M68KMath.packScrollWords;

public class TestSonic3kZoneFeatureProvider {

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void forestFrontPhaseForcesPlayerInFrontOfForestMask() {
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        FakeAizEvents aizEvents = new FakeAizEvents();
        aizEvents.setForestFrontPhaseActive(true);
        provider.setAizEvents(aizEvents);

        provider.update(player, 0x44D0, Sonic3kZoneIds.ZONE_AIZ);

        assertTrue(player.isHighPriority(), "AIZ forest handoff should force Sonic in front of the forest mask");
        assertEquals(RenderPriority.MIN, player.getPriorityBucket(),
                "AIZ forest handoff should also move Sonic into the front display bucket");
    }

    @Test
    public void forestFrontPriorityReleaseWaitsUntilHurtStateEnds() {
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        FakeAizEvents aizEvents = new FakeAizEvents();
        provider.setAizEvents(aizEvents);

        aizEvents.setForestFrontPhaseActive(true);
        provider.update(player, 0x44D0, Sonic3kZoneIds.ZONE_AIZ);
        assertTrue(player.isHighPriority());

        player.setHurt(true);
        aizEvents.setForestFrontPhaseActive(false);
        provider.update(player, 0x4670, Sonic3kZoneIds.ZONE_AIZ);
        assertTrue(player.isHighPriority(), "Forced forest priority should not clear while hurt/death priority is still valid");

        player.setHurt(false);
        provider.update(player, 0x4670, Sonic3kZoneIds.ZONE_AIZ);
        assertFalse(player.isHighPriority(), "Forced forest priority should clear once the protected state ends");
        assertEquals(RenderPriority.PLAYER_DEFAULT, player.getPriorityBucket(),
                "Forced forest display bucket should reset once the protected state ends");
    }

    @Test
    public void forestFrontOverrideOnlyAppliesInAct2() {
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        FakeAizEvents aizEvents = new FakeAizEvents();
        aizEvents.setForestFrontPhaseActive(true);
        provider.setAizEvents(aizEvents);
        provider.setFeatureActId(0);

        provider.update(player, 0x44D0, Sonic3kZoneIds.ZONE_AIZ);

        assertFalse(player.isHighPriority(), "AIZ1 should not inherit the AIZ2 forest-front priority override");
        assertEquals(RenderPriority.PLAYER_DEFAULT, player.getPriorityBucket(),
                "AIZ1 should keep the normal player display bucket");
    }

    @Test
    public void slotDisplayOriginUsesForegroundPlaneSpaceForSlotsPanel() throws Exception {
        RuntimeManager.createGameplay();
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();

        GameServices.camera().setX((short) 0x3C0);
        GameServices.camera().setY((short) 0x2F0);
        GameServices.parallax().getHScroll()[0] = packScrollWords((short) -0x360, (short) 0);

        var vscrollField = GameServices.parallax().getClass().getDeclaredField("vscrollFactorFG");
        vscrollField.setAccessible(true);
        vscrollField.setShort(GameServices.parallax(), (short) 0x3E0);

        assertEquals(0x360, provider.slotDisplayOriginX());
        assertEquals(0x3E0, provider.slotDisplayOriginY());
    }

    private static final class TestZoneFeatureProvider extends Sonic3kZoneFeatureProvider {
        private Sonic3kAIZEvents aizEvents;
        private int featureActId = 1;

        void setAizEvents(Sonic3kAIZEvents aizEvents) {
            this.aizEvents = aizEvents;
        }

        void setFeatureActId(int featureActId) {
            this.featureActId = featureActId;
        }

        @Override
        protected Sonic3kAIZEvents getAizEvents() {
            return aizEvents;
        }

        @Override
        protected int getFeatureActId() {
            return featureActId;
        }

        int slotDisplayOriginX() {
            return resolveSlotDisplayOriginX(GameServices.camera());
        }

        int slotDisplayOriginY() {
            return resolveSlotDisplayOriginY(GameServices.camera());
        }
    }

    private static final class FakeAizEvents extends Sonic3kAIZEvents {
        private boolean forestFrontPhaseActive;

        private FakeAizEvents() {
            super(Sonic3kLoadBootstrap.NORMAL);
        }

        void setForestFrontPhaseActive(boolean forestFrontPhaseActive) {
            this.forestFrontPhaseActive = forestFrontPhaseActive;
        }

        @Override
        public boolean isBattleshipForestFrontPhaseActive() {
            return forestFrontPhaseActive;
        }
    }
}


