package com.openggf.game.sonic3k;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validates that S3K HCZ Act 1 palette cycling is active and modifies colors over time.
 *
 * <p>The ROM's {@code AnPal_HCZ1} routine cycles water colors on palette line 3
 * (engine index 2), colors 3-6, every 8 frames via {@code AnPal_PalHCZ1}. The table
 * contains 4 frames of 4 colors each (32 bytes total), cycling indices 0, 8, 16, 24.
 *
 * <p>This test loads HCZ Act 1, ticks the animation manager, and verifies palette colors
 * change over time. In production the palette cycler runs inside
 * {@code LevelManager.drawWithSpritePriority()} (the draw path), so headless tests must
 * manually tick the animation manager since draw is never called.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kHczPaletteCycling {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_HCZ = 1;
    private static final int ACT_1 = 0;

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_HCZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
    }

    /**
     * Ticks the animation manager once. In production this runs inside
     * drawWithSpritePriority(); headless tests must call it explicitly.
     */
    private void tickAnimation() {
        AnimatedPatternManager apm = GameServices.level().getAnimatedPatternManager();
        if (apm != null) {
            apm.update();
        }
    }

    @Test
    public void waterCycleModifiesPaletteLine3Color3() {
        Level level = GameServices.level().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Palette pal2 = level.getPalette(2);
        assertNotNull("Palette line 3 (index 2) must exist", pal2);

        // Record initial water color (palette line 3, color 3)
        Palette.Color color3 = pal2.getColor(3);
        int initialR = color3.r & 0xFF;
        int initialG = color3.g & 0xFF;
        int initialB = color3.b & 0xFF;

        // The HCZ water cycle ticks every 8 frames with 4 entries,
        // so 60 frames covers more than one full cycle (4 frames × 8 ticks = 32 game frames).
        boolean colorChanged = false;
        for (int frame = 0; frame < 60; frame++) {
            fixture.stepIdleFrames(1);
            tickAnimation();

            int r = color3.r & 0xFF;
            int g = color3.g & 0xFF;
            int b = color3.b & 0xFF;
            if (r != initialR || g != initialG || b != initialB) {
                colorChanged = true;
                break;
            }
        }

        assertTrue("Expected palette[2] color 3 (HCZ water cycle) to change over 60 frames, "
                + "proving AnPal_PalHCZ1 cycling is active. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }
}
