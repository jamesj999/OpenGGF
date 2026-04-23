package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CollapsingBridgeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCollapsingBridgeParity {
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic rider;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MGZ, 0);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        rider = (Sonic) fixture.sprite();
    }

    @Test
    void mgzCollapseWave_isNotSolidForUntrackedPlayers() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);
        Sonic bystander = new Sonic("sonic", (short) 0, (short) 0);
        rider.setCentreX((short) 8);
        rider.setOnObject(true);
        bystander.setCentreX((short) 8);

        invokePerformCollapse(bridge, rider);

        assertFalse(bridge.isSolidFor(bystander),
                "ROM collapse-wave logic stops accepting new SolidObjectTop contacts once the bridge shatters");
    }

    @Test
    void mgzCollapseWave_staysSolidForTheRiderWhoTriggeredIt() throws Exception {
        CollapsingBridgeObjectInstance bridge = newMgzBridge(0x00);
        Sonic rider = new Sonic("sonic", (short) 0, (short) 0);
        rider.setCentreX((short) 8);
        rider.setOnObject(true);

        invokePerformCollapse(bridge, rider);

        assertTrue(bridge.isSolidFor(rider),
                "The rider that triggered the collapse should remain supported until the release wave reaches them");
    }

    private static CollapsingBridgeObjectInstance newMgzBridge(int subtype) throws Exception {
        CollapsingBridgeObjectInstance bridge = new CollapsingBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x0F, subtype, 0x00, false, 0));
        Method initMgz = CollapsingBridgeObjectInstance.class.getDeclaredMethod("initMGZ", int.class);
        initMgz.setAccessible(true);
        initMgz.invoke(bridge, subtype);
        return bridge;
    }

    private static void invokePerformCollapse(CollapsingBridgeObjectInstance bridge, Sonic rider) throws Exception {
        Method performCollapse = CollapsingBridgeObjectInstance.class.getDeclaredMethod(
                "performCollapse",
                com.openggf.sprites.playable.AbstractPlayableSprite.class);
        performCollapse.setAccessible(true);
        performCollapse.invoke(bridge, rider);
    }
}
