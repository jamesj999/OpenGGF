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
 * Validates that S3K LRZ (Lava Reef Zone, zone 0x09) palette cycling is active
 * and modifies palette colors over time.
 *
 * <p>The ROM's {@code AnPal_LRZ1} routine uses three channels:
 * <ul>
 *   <li>Channel A (shared): {@code AnPal_PalLRZ12_1} → palette[2] colors 1-4, timer period 16.</li>
 *   <li>Channel B (shared): {@code AnPal_PalLRZ12_2} → palette[3] colors 1-2, same shared timer.</li>
 *   <li>Channel C (Act 1 only): {@code AnPal_PalLRZ1_3} → palette[2] color 11, timer period 8.</li>
 * </ul>
 *
 * <p>This test loads LRZ Act 1 (zone 0x09, act 0), ticks the animation manager,
 * and verifies that palette[2] color 1 changes over 100 frames (the timer fires every 16 frames).
 * Note: in production the palette cycler runs inside {@code LevelManager.drawWithSpritePriority()}
 * (the draw path), so headless tests must manually tick the animation manager since draw is never
 * called.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kLrzPaletteCycling {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_LRZ = 0x09;
    private static final int ACT_1 = 0;

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_LRZ, ACT_1);
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
    public void channelACycleModifiesPaletteLine3Color1() {
        Level level = GameServices.level().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Palette pal2 = level.getPalette(2);
        assertNotNull("Palette line 3 (index 2) must exist", pal2);

        // Record initial lava/rock color (palette line 3, color 1)
        Palette.Color color1 = pal2.getColor(1);
        int initialR = color1.r & 0xFF;
        int initialG = color1.g & 0xFF;
        int initialB = color1.b & 0xFF;

        // Channel A (shared timer) fires every 16 frames. After 100 frames (6+ ticks)
        // we expect at least one color change. The table has 16 frames of unique data.
        boolean colorChanged = false;
        for (int frame = 0; frame < 100; frame++) {
            fixture.stepIdleFrames(1);
            tickAnimation();

            int r = color1.r & 0xFF;
            int g = color1.g & 0xFF;
            int b = color1.b & 0xFF;
            if (r != initialR || g != initialG || b != initialB) {
                colorChanged = true;
                break;
            }
        }

        assertTrue("Expected palette[2] color 1 (LRZ lava channel A) to change over 100 frames, "
                + "proving AnPal_PalLRZ12_1 cycling is active. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }
}
