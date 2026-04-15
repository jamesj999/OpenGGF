package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression for S3K CPU sidekick bounds during dynamic camera resize.
 * AIZ2 updates camera bounds throughout the act; sidekicks must not keep a
 * stale copy from level load or they can despawn as soon as normal physics
 * resumes after a fly-in.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAiz2SidekickBoundsSync {
    private static final int ZONE_AIZ = 0;
    private static final int ACT_2 = 1;

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;

    @BeforeAll
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_2);
    }

    @AfterAll
    public static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @BeforeEach
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        createSidekick();
    }

    @Test
    public void levelEventsRefreshSidekickBoundsFromLiveCamera() {
        AbstractPlayableSprite sidekick = GameServices.sprites().getSidekicks().getFirst();
        SidekickCpuController controller = sidekick.getCpuController();
        assertNotNull(controller, "CPU sidekick should have a controller");

        controller.setLevelBounds(0x1111, 0x2222, 0x3333);
        GameServices.module().getLevelEventProvider().update();

        assertEquals(fixture.camera().getMinX(), controller.getMinXBound(Integer.MIN_VALUE),
                "S3K level events should refresh sidekick minX bounds from camera");
        assertEquals(fixture.camera().getMaxX(), controller.getMaxXBound(Integer.MIN_VALUE),
                "S3K level events should refresh sidekick maxX bounds from camera");
        assertEquals(Math.max(fixture.camera().getMaxY(), fixture.camera().getMaxYTarget()),
                controller.getMaxYBound(Integer.MIN_VALUE),
                "S3K level events should refresh sidekick maxY bounds from camera");
    }

    private void createSidekick() {
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        SidekickCpuController controller = new SidekickCpuController(tails, fixture.sprite());
        tails.setCpuController(controller);
        GameServices.sprites().addSprite(tails);
    }
}
