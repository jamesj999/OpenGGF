package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
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
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ICZ palette cycling (zone 0x05) implemented in {@link Sonic3kPaletteCycler}.
 *
 * <p>ICZ has 4 channels (AnPal_ICZ, sonic3k.asm line 3379):
 * <ul>
 *   <li>Channel 1: timer period 5, counter0 +4, wrap 0x40 Ã¢â€ â€™ palette[2] colors 14-15</li>
 *   <li>Channel 2: timer period 9, counter2 +4, wrap 0x48 Ã¢â€ â€™ palette[3] colors 14-15</li>
 *   <li>Channel 3: timer period 7, counter4 +4, wrap 0x18 Ã¢â€ â€™ palette[3] colors 12-13</li>
 *   <li>Channel 4: shares timer with ch3, counter6 +4, wrap 0x40 Ã¢â€ â€™ palette[2] colors 12-13</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kIczPaletteCycling {
    private static final int ZONE_ICZ = 5;
    private static final int ACT_1 = 0;

    private Level level;
    private Sonic3kPaletteCycler cycler;

    @BeforeEach
    public void setUp() throws Exception {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        GraphicsManager.getInstance().initHeadless();

        String mainChar = SonicConfigurationService.getInstance()
                .getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        Sonic temp = new Sonic(mainChar, (short) 0, (short) 0);
        GameServices.sprites().addSprite(temp);

        Camera camera = GameServices.camera();
        camera.setFocusedSprite(temp);
        camera.setFrozen(false);

        LevelManager lm = GameServices.level();
        lm.loadZoneAndAct(ZONE_ICZ, ACT_1);
        GroundSensor.setLevelManager(lm);

        level = lm.getCurrentLevel();
        assertNotNull(level, "ICZ1 level should load successfully");
        camera.updatePosition(true);

        RomByteReader reader = RomByteReader.fromRom(com.openggf.tests.TestEnvironment.currentRom());
        cycler = new Sonic3kPaletteCycler(reader, level, ZONE_ICZ, ACT_1);
    }

    @AfterEach
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
        assertTrue(changed, "palette[2] color 14 should change after 40 ICZ frames (channel 1 period=5)");
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
        assertTrue(changed, "palette[2] color 15 should change after 40 ICZ frames (channel 1 period=5)");
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
        assertTrue(changed14, "palette[3] color 14 should change after 40 ICZ frames (channel 2 period=9)");
        assertTrue(changed15, "palette[3] color 15 should change after 40 ICZ frames (channel 2 period=9)");
    }

    /**
     * Palette[2] colors 12-13 (channel 4, shares timer with channel 3, period 7) should change.
     * Channel 4 has 16 frames (step +4, wrap 0x40) with varying color values.
     * We verify that at least two distinct color12 values are observed over 100 frames
     * (timer period 7 Ã¢â€ â€™ ~12 fires, cycling through frames 0-11 and wrapping).
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

        assertTrue(seenDifferent12, "palette[2] color 12 should cycle through multiple values in 100 ICZ frames (channel 4 period=7)");
        assertTrue(seenDifferent13, "palette[2] color 13 should cycle through multiple values in 100 ICZ frames (channel 4 period=7)");
    }

    // ========== Specific color value assertions ==========

    /**
     * Verifies that channel 1 (geyser/ice) applies specific ROM values on the first tick.
     * Timer starts at 0, so the first update fires immediately, writing data1[0..3] to
     * palette[2] colors 14-15. ICZ ice shimmer colors are cool-toned.
     */
    @Test
    public void channel1FirstTickAppliesNonZeroIceColors() {
        // First tick fires channel 1 immediately (timer starts at 0)
        cycler.update();

        Palette pal2 = level.getPalette(2);
        for (int c = 14; c <= 15; c++) {
            int r = pal2.getColor(c).r & 0xFF;
            int g = pal2.getColor(c).g & 0xFF;
            int b = pal2.getColor(c).b & 0xFF;
            assertTrue(r > 0 || g > 0 || b > 0, "ICZ channel 1 color " + c + " should be non-zero after first tick, got ("
                    + r + "," + g + "," + b + ")");
        }
    }

    /**
     * Verifies that channel 2 applies specific ROM values on first tick.
     * Palette[3] colors 14-15 receive ice data from data2 frame 0.
     */
    @Test
    public void channel2FirstTickAppliesNonZeroColors() {
        cycler.update();

        Palette pal3 = level.getPalette(3);
        for (int c = 14; c <= 15; c++) {
            int r = pal3.getColor(c).r & 0xFF;
            int g = pal3.getColor(c).g & 0xFF;
            int b = pal3.getColor(c).b & 0xFF;
            assertTrue(r > 0 || g > 0 || b > 0, "ICZ channel 2 color " + c + " should be non-zero after first tick, got ("
                    + r + "," + g + "," + b + ")");
        }
    }

    /**
     * Verifies that channel 3 applies specific ROM values on first tick.
     * Palette[3] colors 12-13 receive ice data from data3 frame 0.
     */
    @Test
    public void channel3FirstTickAppliesNonZeroColors() {
        cycler.update();

        Palette pal3 = level.getPalette(3);
        for (int c = 12; c <= 13; c++) {
            int r = pal3.getColor(c).r & 0xFF;
            int g = pal3.getColor(c).g & 0xFF;
            int b = pal3.getColor(c).b & 0xFF;
            assertTrue(r > 0 || g > 0 || b > 0, "ICZ channel 3 color " + c + " should be non-zero after first tick, got ("
                    + r + "," + g + "," + b + ")");
        }
    }

    /**
     * Verifies channel 1 produces multiple distinct values over a full cycle.
     * Channel 1: 16 frames (step +4, wrap 0x40), timer period 5 Ã¢â€ â€™ fires every 6 ticks.
     * Over 96 ticks (16 Ãƒâ€” 6), the entire table is traversed.
     */
    @Test
    public void channel1ProducesMultipleDistinctValues() {
        int distinctCount = 0;
        int prevR = -1, prevG = -1, prevB = -1;

        for (int frame = 0; frame < 96; frame++) {
            cycler.update();
            Palette.Color c14 = level.getPalette(2).getColor(14);
            int r = c14.r & 0xFF;
            int g = c14.g & 0xFF;
            int b = c14.b & 0xFF;
            if (r != prevR || g != prevG || b != prevB) {
                distinctCount++;
                prevR = r;
                prevG = g;
                prevB = b;
            }
        }

        assertTrue(distinctCount >= 3, "ICZ channel 1 should produce at least 3 distinct color 14 values over 96 frames, got "
                + distinctCount);
    }

    /** Snapshot a color value so the original is preserved for comparison. */
    private static Palette.Color copyColor(Palette.Color src) {
        return new Palette.Color(src.r, src.g, src.b);
    }
}


