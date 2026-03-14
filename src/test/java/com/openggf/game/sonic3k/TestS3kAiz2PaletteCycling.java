package com.openggf.game.sonic3k;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
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
 * Validates that S3K AIZ Act 2 palette cycling is active and modifies colors over time.
 *
 * <p>The ROM's {@code AnPal_AIZ2} routine cycles fire/torch colors on palette line 4
 * (engine index 3), color 1, every 2 frames via {@code AnPal_PalAIZ2_4/5}. Without this
 * cycling, fire {@code AnimatedStillSprite}s render green (vegetation palette) instead of
 * orange/red fire colors.
 *
 * <p>This test loads AIZ Act 2, ticks the animation manager, and verifies palette colors
 * change over time. Note: in production the palette cycler runs inside
 * {@code LevelManager.drawWithSpritePriority()} (the draw path), so headless tests must
 * manually tick the animation manager since draw is never called.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAiz2PaletteCycling {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_AIZ = 0;
    private static final int ACT_2 = 1;

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_2);
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
        AnimatedPatternManager apm = LevelManager.getInstance().getAnimatedPatternManager();
        if (apm != null) {
            apm.update();
        }
    }

    @Test
    public void torchGlowCycleModifiesPaletteLine4Color1() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Palette pal3 = level.getPalette(3);
        assertNotNull("Palette line 4 (index 3) must exist", pal3);

        // Record initial torch color (palette line 4, color 1)
        Palette.Color color1 = pal3.getColor(1);
        int initialR = color1.r & 0xFF;
        int initialG = color1.g & 0xFF;
        int initialB = color1.b & 0xFF;

        // Step enough frames for the torch cycle to advance.
        // The torch cycle ticks every 2 frames with 26 entries (0x34/2 = 26),
        // so 60 frames covers more than one full cycle.
        boolean colorChanged = false;
        for (int frame = 0; frame < 60; frame++) {
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

        assertTrue("Expected palette[3] color 1 (torch glow) to change over 60 frames, "
                + "proving AnPal_PalAIZ2_4/5 cycling is active. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }

    @Test
    public void waterCycleModifiesPaletteLine4Colors12to15() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Palette pal3 = level.getPalette(3);

        // Record initial water color (palette line 4, color 12)
        Palette.Color color12 = pal3.getColor(12);
        int initialR = color12.r & 0xFF;
        int initialG = color12.g & 0xFF;
        int initialB = color12.b & 0xFF;

        // The water cycle ticks every 6 frames with 4 entries,
        // so 30 frames covers more than one full cycle.
        boolean colorChanged = false;
        for (int frame = 0; frame < 30; frame++) {
            fixture.stepIdleFrames(1);
            tickAnimation();

            int r = color12.r & 0xFF;
            int g = color12.g & 0xFF;
            int b = color12.b & 0xFF;
            if (r != initialR || g != initialG || b != initialB) {
                colorChanged = true;
                break;
            }
        }

        assertTrue("Expected palette[3] color 12 (water cycle) to change over 30 frames, "
                + "proving AnPal_PalAIZ2_1 cycling is active. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }
}
