package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ICZ palette cycling (zone 0x05) implemented in {@link Sonic3kPaletteCycler}.
 *
 * <p>ICZ has 4 channels (AnPal_ICZ, sonic3k.asm line 3379):
 * <ul>
 *   <li>Channel 1: timer period 5, counter0 +4, wrap 0x40 → palette[2] colors 14-15</li>
 *   <li>Channel 2: timer period 9, counter2 +4, wrap 0x48 → palette[3] colors 14-15</li>
 *   <li>Channel 3: timer period 7, counter4 +4, wrap 0x18 → palette[3] colors 12-13</li>
 *   <li>Channel 4: shares timer with ch3, counter6 +4, wrap 0x40 → palette[2] colors 12-13</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kIczPaletteCycling {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_ICZ = 5;
    private static final int ACT_1 = 0;

    private Level level;
    private Sonic3kPaletteCycler cycler;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        GraphicsManager.getInstance().initHeadless();

        String mainChar = SonicConfigurationService.getInstance()
                .getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        Sonic temp = new Sonic(mainChar, (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(temp);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(temp);
        camera.setFrozen(false);

        LevelManager lm = LevelManager.getInstance();
        lm.loadZoneAndAct(ZONE_ICZ, ACT_1);
        GroundSensor.setLevelManager(lm);

        level = lm.getCurrentLevel();
        assertNotNull("ICZ1 level should load successfully", level);
        camera.updatePosition(true);

        RomByteReader reader = RomByteReader.fromRom(romRule.rom());
        cycler = new Sonic3kPaletteCycler(reader, level, ZONE_ICZ, ACT_1);
    }

    @After
    public void tearDown() {
        com.openggf.tests.TestEnvironment.resetAll();
    }

    /**
     * Palette[2] color 14 (channel 1) must change within 40 frames.
     * Channel 1 fires on timer period 5, so the first fire is at frame 1 (timer starts at 0,
     * decrements before check). Color change is guaranteed within 7 frames.
     */
    @Test
    public void channel1UpdatesPalette2Color14Within40Frames() {
        Palette.Color initialColor = copyColor(level.getPalette(2).getColor(14));

        for (int i = 0; i < 40; i++) {
            cycler.update();
        }

        Palette.Color finalColor = level.getPalette(2).getColor(14);
        boolean changed = (finalColor.r != initialColor.r)
                || (finalColor.g != initialColor.g)
                || (finalColor.b != initialColor.b);
        assertTrue("palette[2] color 14 should change after 40 ICZ frames (channel 1 period=5)",
                changed);
    }

    /**
     * Palette[2] color 15 (also channel 1, written as a longword with color 14) must change.
     */
    @Test
    public void channel1UpdatesPalette2Color15Within40Frames() {
        Palette.Color initialColor = copyColor(level.getPalette(2).getColor(15));

        for (int i = 0; i < 40; i++) {
            cycler.update();
        }

        Palette.Color finalColor = level.getPalette(2).getColor(15);
        boolean changed = (finalColor.r != initialColor.r)
                || (finalColor.g != initialColor.g)
                || (finalColor.b != initialColor.b);
        assertTrue("palette[2] color 15 should change after 40 ICZ frames (channel 1 period=5)",
                changed);
    }

    /**
     * Palette[3] colors 14-15 (channel 2, period 9) should change within 40 frames.
     */
    @Test
    public void channel2UpdatesPalette3Colors14And15Within40Frames() {
        Palette.Color initial14 = copyColor(level.getPalette(3).getColor(14));
        Palette.Color initial15 = copyColor(level.getPalette(3).getColor(15));

        for (int i = 0; i < 40; i++) {
            cycler.update();
        }

        Palette.Color final14 = level.getPalette(3).getColor(14);
        Palette.Color final15 = level.getPalette(3).getColor(15);

        boolean changed14 = (final14.r != initial14.r) || (final14.g != initial14.g) || (final14.b != initial14.b);
        boolean changed15 = (final15.r != initial15.r) || (final15.g != initial15.g) || (final15.b != initial15.b);
        assertTrue("palette[3] color 14 should change after 40 ICZ frames (channel 2 period=9)",
                changed14);
        assertTrue("palette[3] color 15 should change after 40 ICZ frames (channel 2 period=9)",
                changed15);
    }

    /**
     * Palette[2] colors 12-13 (channel 4, shares timer with channel 3, period 7) should change.
     * Channel 4 has 16 frames (step +4, wrap 0x40) with varying color values.
     * We verify that at least two distinct color12 values are observed over 100 frames
     * (timer period 7 → ~12 fires, cycling through frames 0-11 and wrapping).
     */
    @Test
    public void channel4UpdatesPalette2Colors12And13Within100Frames() {
        Palette.Color firstSeen12 = copyColor(level.getPalette(2).getColor(12));
        boolean seenDifferent12 = false;
        boolean seenDifferent13 = false;
        Palette.Color firstSeen13 = copyColor(level.getPalette(2).getColor(13));

        for (int i = 0; i < 100; i++) {
            cycler.update();
            Palette.Color cur12 = level.getPalette(2).getColor(12);
            Palette.Color cur13 = level.getPalette(2).getColor(13);
            if ((cur12.r != firstSeen12.r) || (cur12.g != firstSeen12.g) || (cur12.b != firstSeen12.b)) {
                seenDifferent12 = true;
            }
            if ((cur13.r != firstSeen13.r) || (cur13.g != firstSeen13.g) || (cur13.b != firstSeen13.b)) {
                seenDifferent13 = true;
            }
        }

        assertTrue("palette[2] color 12 should cycle through multiple values in 100 ICZ frames (channel 4 period=7)",
                seenDifferent12);
        assertTrue("palette[2] color 13 should cycle through multiple values in 100 ICZ frames (channel 4 period=7)",
                seenDifferent13);
    }

    /** Snapshot a color value so the original is preserved for comparison. */
    private static Palette.Color copyColor(Palette.Color src) {
        return new Palette.Color(src.r, src.g, src.b);
    }
}
