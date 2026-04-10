package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kActTransitionZoneFeatures {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_AIZ, 0);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @Before
    public void setUp() {
        HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 0x2F10, (short) 0x0200)
                .startPositionIsCentre()
                .build();
    }

    @Test
    public void zoneFeatureProviderPreservedAcrossSonic3kActTransition() throws Exception {
        LevelManager levelManager = GameServices.level();
        ZoneFeatureProvider before = levelManager.getZoneFeatureProvider();
        assertNotNull("ZoneFeatureProvider should exist before transition", before);

        levelManager.executeActTransition(SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_AIZ, 1)
                .preserveMusic(true)
                .build());

        assertSame("S3K act transitions must reinitialize the existing provider so AIZ fire curtain state survives",
                before, levelManager.getZoneFeatureProvider());
    }
}
