package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kPachinkoPaletteCycling {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private HeadlessTestFixture fixture;

    @BeforeClass
    public static void configure() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @Before
    public void setUp() {
        fixture = null;
    }

    @Test
    public void pachinkoLine4GlowColorsCycle() {
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x14, 0)
                .build();

        Level level = GameServices.level().getCurrentLevel();
        assertNotNull(level);
        AnimatedPaletteManager cycler = GameServices.level().getAnimatedPaletteManager();
        assertNotNull(cycler);

        Palette.Color color = level.getPalette(3).getColor(8);
        int initialR = color.r & 0xFF;
        int initialG = color.g & 0xFF;
        int initialB = color.b & 0xFF;

        boolean changed = false;
        for (int frame = 0; frame < 40; frame++) {
            fixture.stepIdleFrames(1);
            cycler.update();
            if ((color.r & 0xFF) != initialR
                    || (color.g & 0xFF) != initialG
                    || (color.b & 0xFF) != initialB) {
                changed = true;
                break;
            }
        }

        assertTrue("Expected Pachinko palette[3] color 8 to cycle", changed);
    }

    @Test
    public void pachinkoLine3BackgroundColorsCycle() {
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x14, 0)
                .build();

        Level level = GameServices.level().getCurrentLevel();
        assertNotNull(level);
        AnimatedPaletteManager cycler = GameServices.level().getAnimatedPaletteManager();
        assertNotNull(cycler);

        Palette.Color color = level.getPalette(2).getColor(1);
        int initialR = color.r & 0xFF;
        int initialG = color.g & 0xFF;
        int initialB = color.b & 0xFF;

        boolean changed = false;
        for (int frame = 0; frame < 80; frame++) {
            fixture.stepIdleFrames(1);
            cycler.update();
            if ((color.r & 0xFF) != initialR
                    || (color.g & 0xFF) != initialG
                    || (color.b & 0xFF) != initialB) {
                changed = true;
                break;
            }
        }

        assertTrue("Expected Pachinko palette[2] color 1 to cycle", changed);
    }
}
