package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.graphics.RenderPriority;
import com.openggf.physics.Direction;
import com.openggf.physics.SensorResult;
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
    public void forestFrontPhaseAlsoForcesCpuSidekickInFrontOfForestMask() {
        RuntimeManager.createGameplay();
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        sidekick.setCpuControlled(true);
        GameServices.sprites().addSprite(sidekick, "tails");

        FakeAizEvents aizEvents = new FakeAizEvents();
        aizEvents.setForestFrontPhaseActive(true);
        provider.setAizEvents(aizEvents);

        provider.update(player, 0x44D0, Sonic3kZoneIds.ZONE_AIZ);

        assertTrue(sidekick.isHighPriority(),
                "AIZ forest handoff should force CPU Tails in front of the forest mask");
        assertEquals(RenderPriority.MIN, sidekick.getPriorityBucket(),
                "AIZ forest handoff should also move CPU Tails into the front display bucket");
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

    @Test
    public void aiz1AllowsZeroDistanceAirLandingOnlyForRollingFlatFloorContact() {
        TestZoneFeatureProvider provider = new TestZoneFeatureProvider();
        provider.setFeatureZoneId(Sonic3kZoneIds.ZONE_AIZ);
        provider.setFeatureActId(0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setAir(true);
        player.setRolling(true);
        player.setYSpeed((short) 0x0568);

        assertTrue(provider.shouldTreatZeroDistanceAirLandingAsGround(
                player, new SensorResult((byte) 0xFF, (byte) 0, 0x95, Direction.DOWN)));

        player.setRolling(false);
        assertFalse(provider.shouldTreatZeroDistanceAirLandingAsGround(
                player, new SensorResult((byte) 0xFF, (byte) 0, 0x95, Direction.DOWN)));

        provider.setFeatureZoneId(Sonic3kZoneIds.ZONE_HCZ);
        player.setRolling(true);
        assertFalse(provider.shouldTreatZeroDistanceAirLandingAsGround(
                player, new SensorResult((byte) 0xFF, (byte) 0, 0x95, Direction.DOWN)));
    }

    private static final class TestZoneFeatureProvider extends Sonic3kZoneFeatureProvider {
        private AizZoneRuntimeState aizState;
        private int featureZoneId = Sonic3kZoneIds.ZONE_AIZ;
        private int featureActId = 1;

        void setAizEvents(Sonic3kAIZEvents aizEvents) {
            this.aizState = aizEvents != null
                    ? new AizZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS, aizEvents)
                    : null;
        }

        void setFeatureActId(int featureActId) {
            this.featureActId = featureActId;
        }

        void setFeatureZoneId(int featureZoneId) {
            this.featureZoneId = featureZoneId;
        }

        @Override
        protected AizZoneRuntimeState getAizState() {
            return aizState;
        }

        @Override
        protected int getFeatureZoneId() {
            return featureZoneId;
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

