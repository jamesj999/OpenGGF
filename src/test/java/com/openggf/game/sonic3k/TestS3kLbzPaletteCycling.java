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
 * Validates that S3K LBZ Act 1 palette cycling is active and modifies colors over time.
 *
 * <p>The ROM's {@code AnPal_LBZ1} / {@code AnPal_LBZ2} routine (sonic3k.asm loc_2516) cycles
 * colors on {@code Normal_palette_line_3+$10} — engine palette[2] colors 8–10 — every 4 frames
 * via {@code AnPal_PalLBZ1} (Act 1) or {@code AnPal_PalLBZ2} (Act 2).
 *
 * <p>This test loads LBZ Act 1 (zone 0x06, act 0), ticks the animation manager manually
 * (palette cycling runs inside the draw path in production), and verifies that palette[2]
 * color 8 changes within 20 frames.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kLbzPaletteCycling {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_LBZ = 6;
    private static final int ACT_1 = 0;

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_LBZ, ACT_1);
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
    public void lbzCycleModifiesPaletteLine3Colors8to10() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Palette pal2 = level.getPalette(2);
        assertNotNull("Palette line 3 (index 2) must exist", pal2);

        // Record initial LBZ cycling color (palette line 3, color 8)
        Palette.Color color8 = pal2.getColor(8);
        int initialR = color8.r & 0xFF;
        int initialG = color8.g & 0xFF;
        int initialB = color8.b & 0xFF;

        // The LBZ cycle ticks every 4 frames with 3 entries (0x12/6 = 3),
        // so 20 frames is more than enough for one full cycle.
        boolean colorChanged = false;
        for (int frame = 0; frame < 20; frame++) {
            fixture.stepIdleFrames(1);
            tickAnimation();

            int r = color8.r & 0xFF;
            int g = color8.g & 0xFF;
            int b = color8.b & 0xFF;
            if (r != initialR || g != initialG || b != initialB) {
                colorChanged = true;
                break;
            }
        }

        assertTrue("Expected palette[2] color 8 (LBZ cycle) to change over 20 frames, "
                + "proving AnPal_PalLBZ1 cycling is active. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }
}
