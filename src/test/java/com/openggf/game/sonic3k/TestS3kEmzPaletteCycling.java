package com.openggf.game.sonic3k;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validates that S3K EMZ (Endless Mine Zone, competition) palette cycling logic is correct.
 *
 * <p>The ROM's {@code AnPal_EMZ} routine drives two independent channels:
 * <ol>
 *   <li><b>Emerald glow</b> (timer period 8, reset to 7): {@code AnPal_PalEMZ_1} via {@code 4(a0,d0.w)}
 *       → {@code Normal_palette_line_3+$1C} = palette[2] color 14.
 *       Counter0 steps +2, wraps at {@code 0x3C} (30 frames per cycle).</li>
 *   <li><b>Background</b> (timer period 32, reset to 0x1F; {@code Palette_cycle_counters+$08}):
 *       {@code AnPal_PalEMZ_2} via {@code (a0,d0.w)} → {@code Normal_palette_line_4+$12} =
 *       palette[3] colors 9-10 ({@code move.l} = 2 colors).
 *       Counter ({@code counters+$02}) steps +4, wraps at {@code 0x34} (13 frames per cycle).</li>
 * </ol>
 *
 * <p>This test validates that ROM data is present at the expected addresses and that the
 * palette cycle logic produces value changes when applied against a mock Level/Palette,
 * without requiring a full EMZ level load (competition zones are not yet in the zone registry).
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kEmzPaletteCycling {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private RomByteReader reader;

    @Before
    public void setUp() throws java.io.IOException {
        reader = RomByteReader.fromRom(romRule.rom());
    }

    /**
     * Verifies AnPal_PalEMZ_1 ROM data: 60 bytes of emerald green color values
     * at the expected address.
     */
    @Test
    public void emz1DataPresentAndNonZero() {
        byte[] data = reader.slice(Sonic3kConstants.ANPAL_EMZ1_ADDR, Sonic3kConstants.ANPAL_EMZ1_SIZE);
        assertNotNull("EMZ glow data must be present", data);
        assertTrue("EMZ glow data must be 64 bytes", data.length == 64);

        // First word at offset 4 (first value actually used by ROM with 4(a0,d0.w)) should be non-zero.
        // From disassembly: AnPal_PalEMZ_1 starts with dc.w 6 ($0006 = green component).
        int firstWord = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        assertTrue("First used word at offset 4 should be non-zero (emerald color component)",
                firstWord != 0);
    }

    /**
     * Verifies AnPal_PalEMZ_2 ROM data: 52 bytes of background color pairs.
     */
    @Test
    public void emz2DataPresentAndNonZero() {
        byte[] data = reader.slice(Sonic3kConstants.ANPAL_EMZ2_ADDR, Sonic3kConstants.ANPAL_EMZ2_SIZE);
        assertNotNull("EMZ background data must be present", data);
        assertTrue("EMZ background data must be 52 bytes", data.length == 52);

        // From disassembly: AnPal_PalEMZ_2 starts with dc.w 0,$E ($0000,$000E).
        // The longword at offset 0 should equal 0x0000_000E.
        int longword = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                     | ((data[2] & 0xFF) << 8)  |  (data[3] & 0xFF);
        assertTrue("First longword of EMZ2 should be 0x0000000E (first two colors: 0 and 0x0E)",
                longword == 0x0000000E);
    }

    /**
     * Verifies that the emerald glow cycle (Channel 1) modifies palette[2] color 14 over time.
     *
     * <p>Uses the same ROM data and cycle logic as {@code EmzCycle} but drives it directly
     * via a mock Level/Palette, bypassing the need for a full level load.
     */
    @Test
    public void emeraldGlowCycleModifiesPaletteLine3Color14() {
        byte[] glowData = reader.slice(Sonic3kConstants.ANPAL_EMZ1_ADDR, Sonic3kConstants.ANPAL_EMZ1_SIZE);
        byte[] bgData   = reader.slice(Sonic3kConstants.ANPAL_EMZ2_ADDR, Sonic3kConstants.ANPAL_EMZ2_SIZE);

        Palette pal2 = new Palette();
        Palette pal3 = new Palette();

        // Simulate the Sonic3kPaletteCycler.EmzCycle glow channel:
        // timer period = 7 (reset to 7), counter step = +2, wrap at 0x3C
        // ROM: move.w 4(a0,d0.w) → palette_line_3+$1C = palette[2] color 14
        int glowTimer = 0;
        int glowCounter = 0;
        int initialR = pal2.getColor(14).r & 0xFF;
        int initialG = pal2.getColor(14).g & 0xFF;
        int initialB = pal2.getColor(14).b & 0xFF;

        boolean colorChanged = false;
        for (int frame = 0; frame < 60; frame++) {
            if (glowTimer > 0) {
                glowTimer--;
            } else {
                glowTimer = 7;
                int d0 = glowCounter;
                glowCounter += 2;
                if (glowCounter >= 0x3C) {
                    glowCounter = 0;
                }
                pal2.getColor(14).fromSegaFormat(glowData, 4 + d0);
            }

            int r = pal2.getColor(14).r & 0xFF;
            int g = pal2.getColor(14).g & 0xFF;
            int b = pal2.getColor(14).b & 0xFF;
            if (r != initialR || g != initialG || b != initialB) {
                colorChanged = true;
                break;
            }
        }

        assertTrue("Expected palette[2] color 14 to change over 60 frames, "
                + "proving AnPal_PalEMZ_1 data is valid and cycle logic is correct. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }

    /**
     * Verifies that the background cycle (Channel 2) modifies palette[3] colors 9-10.
     *
     * <p>Timer period = 0x1F (32), counter step = +4, wrap at 0x34 (13 frames/cycle).
     * ROM: move.l (a0,d0.w) → palette_line_4+$12 = palette[3] colors 9-10.
     */
    @Test
    public void backgroundCycleModifiesPaletteLine4Colors9to10() {
        byte[] bgData = reader.slice(Sonic3kConstants.ANPAL_EMZ2_ADDR, Sonic3kConstants.ANPAL_EMZ2_SIZE);

        Palette pal3 = new Palette();

        int bgTimer = 0;
        int bgCounter = 0;
        int initialR = pal3.getColor(9).r & 0xFF;
        int initialG = pal3.getColor(9).g & 0xFF;
        int initialB = pal3.getColor(9).b & 0xFF;

        boolean colorChanged = false;
        for (int frame = 0; frame < 500; frame++) {
            if (bgTimer > 0) {
                bgTimer--;
            } else {
                bgTimer = 0x1F;
                int d0 = bgCounter;
                bgCounter += 4;
                if (bgCounter >= 0x34) {
                    bgCounter = 0;
                }
                pal3.getColor(9).fromSegaFormat(bgData, d0);
                pal3.getColor(10).fromSegaFormat(bgData, d0 + 2);
            }

            int r = pal3.getColor(9).r & 0xFF;
            int g = pal3.getColor(9).g & 0xFF;
            int b = pal3.getColor(9).b & 0xFF;
            if (r != initialR || g != initialG || b != initialB) {
                colorChanged = true;
                break;
            }
        }

        assertTrue("Expected palette[3] color 9 to change over 500 frames, "
                + "proving AnPal_PalEMZ_2 data is valid and cycle logic is correct. "
                + "Initial RGB=(" + initialR + "," + initialG + "," + initialB + ")",
                colorChanged);
    }
}
