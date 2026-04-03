package com.openggf.tests;

import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.RenderPriority;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic3kZoneFeatureProvider {

    @Test
    public void forestFrontPhaseForcesPlayerInFrontOfForestMask() {
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        FakeAizEvents aizEvents = new FakeAizEvents();
        aizEvents.setForestFrontPhaseActive(true);
        provider.setAizEvents(aizEvents);

        provider.update(player, 0x44D0, Sonic3kZoneIds.ZONE_AIZ);

        assertTrue("AIZ forest handoff should force Sonic in front of the forest mask",
                player.isHighPriority());
        org.junit.Assert.assertEquals("AIZ forest handoff should also move Sonic into the front display bucket",
                RenderPriority.MIN, player.getPriorityBucket());
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
        assertTrue("Forced forest priority should not clear while hurt/death priority is still valid",
                player.isHighPriority());

        player.setHurt(false);
        provider.update(player, 0x4670, Sonic3kZoneIds.ZONE_AIZ);
        assertFalse("Forced forest priority should clear once the protected state ends",
                player.isHighPriority());
        org.junit.Assert.assertEquals("Forced forest display bucket should reset once the protected state ends",
                RenderPriority.PLAYER_DEFAULT, player.getPriorityBucket());
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

        assertFalse("AIZ1 should not inherit the AIZ2 forest-front priority override",
                player.isHighPriority());
        org.junit.Assert.assertEquals("AIZ1 should keep the normal player display bucket",
                RenderPriority.PLAYER_DEFAULT, player.getPriorityBucket());
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
