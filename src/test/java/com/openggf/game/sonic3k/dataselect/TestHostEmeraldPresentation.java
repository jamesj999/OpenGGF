package com.openggf.game.sonic3k.dataselect;

import com.openggf.data.Rom;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_1)
class TestHostEmeraldPresentation {

    @Test
    void s1HostEmeraldsRetintNativeRampInsteadOfCopyingRawSlots() throws Exception {
        Rom rom = TestEnvironment.currentRom();

        HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s1", rom);
        byte[] expected = HostEmeraldPaletteBuilder.composeRetintedPaletteBytes(
                HostEmeraldPaletteBuilder.extractS1HostTargets(rom),
                HostEmeraldPaletteBuilder.nativeRamp());

        assertEquals(6, result.activeEmeraldCount());
        assertEquals(7 * 4 + 2, result.paletteBytes().length);
        assertTrue(result.usesRetintedRamp());
        assertArrayEquals(expected, result.paletteBytes());
    }

    @Test
    void s2HostEmeraldsRetintNativeRampInsteadOfCopyingRawSlots() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 2 ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 2 ROM");

            HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s2", rom);
            byte[] expected = HostEmeraldPaletteBuilder.composeRetintedPaletteBytes(
                    HostEmeraldPaletteBuilder.extractS2HostTargets(rom),
                    HostEmeraldPaletteBuilder.nativeRamp());

            assertEquals(7, result.activeEmeraldCount());
            assertEquals(7 * 4 + 2, result.paletteBytes().length);
            assertTrue(result.usesRetintedRamp());
            assertArrayEquals(expected, result.paletteBytes());
        }
    }

    @Test
    void invalidHostRomFallsBackToEmptyPaletteBytes() {
        HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s1", null);

        assertEquals(0, result.paletteBytes().length);
        assertEquals(7, result.layout().positions().size());
    }

    @Test
    void invalidHostGameFallsBackToEmptyPaletteBytes() {
        Rom rom = TestEnvironment.currentRom();

        HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("nope", rom);

        assertEquals(0, result.paletteBytes().length);
        assertEquals(7, result.layout().positions().size());
    }

    @Test
    void s1PresentationPaletteDiffersFromLegacyDirectBuilderEntryPoint() throws Exception {
        Rom rom = TestEnvironment.currentRom();

        HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s1", rom);
        byte[] legacy = HostEmeraldPaletteBuilder.buildForHostGame("s1", rom);

        assertEquals(6, result.activeEmeraldCount());
        assertEquals(7 * 4 + 2, result.paletteBytes().length);
        assertEquals(7 * 4 + 2, legacy.length);
        assertFalse(java.util.Arrays.equals(result.paletteBytes(), legacy));
    }

    @Test
    void s1LayoutActiveCountMatchesPresentationResult() {
        Rom rom = TestEnvironment.currentRom();

        HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s1", rom);

        assertEquals(6, result.activeEmeraldCount());
        assertEquals(result.activeEmeraldCount(), result.layout().activeEmeraldCount());
        assertEquals(6, result.layout().positions().size());
    }

    @Test
    void s1SixRingUsesSixBalancedPositions() {
        HostEmeraldLayoutProfile layout = HostEmeraldLayoutProfile.s1SixRing();

        assertEquals(6, layout.activeEmeraldCount());
        assertEquals(6, layout.positions().size());
        assertEquals(-layout.positions().get(2).x(), layout.positions().get(0).x());
        assertEquals(layout.positions().get(2).y(), layout.positions().get(0).y());
        assertEquals(-layout.positions().get(3).x(), layout.positions().get(5).x());
        assertEquals(layout.positions().get(3).y(), layout.positions().get(5).y());
        assertEquals(0, layout.positions().get(1).x());
        assertEquals(0, layout.positions().get(4).x());
        assertTrue(layout.positions().get(1).y() < layout.positions().get(4).y());
    }

    @Test
    void composeRetintedPaletteBytes_preservesNativeBrightnessOrderingAndChangesHue() {
        List<HostEmeraldPaletteBuilder.GenesisColour> nativeRamp = List.of(
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0EEE),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0444),
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x0000));
        List<HostEmeraldPaletteBuilder.GenesisColour> hostTargets = List.of(
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(0x000E));

        byte[] paletteBytes = HostEmeraldPaletteBuilder.composeRetintedPaletteBytes(hostTargets, nativeRamp);
        HostEmeraldPaletteBuilder.GenesisColour highlight =
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(readGenesisWord(paletteBytes, 0));
        HostEmeraldPaletteBuilder.GenesisColour shadow =
                HostEmeraldPaletteBuilder.GenesisColour.fromGenesisWord(readGenesisWord(paletteBytes, 2));

        assertTrue(highlight.brightness() > shadow.brightness());
        assertNotEquals(nativeRamp.get(0).toGenesisWord(), highlight.toGenesisWord());
        assertEquals(nativeRamp.get(nativeRamp.size() - 1).toGenesisWord(),
                readGenesisWord(paletteBytes, paletteBytes.length - 2));
    }

    private static int readGenesisWord(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
