package com.openggf.game.sonic3k.dataselect;

import com.openggf.data.Rom;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_1)
class TestHostEmeraldPresentation {

    @Test
    void s1HostEmeraldsRetintNativeRampInsteadOfCopyingRawSlots() {
        Rom rom = TestEnvironment.currentRom();

        HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s1", rom);

        assertEquals(6, result.activeEmeraldCount());
        assertEquals(7 * 4 + 2, result.paletteBytes().length);
        assertTrue(result.usesRetintedRamp());
    }

    @Test
    void s2HostEmeraldsRetintNativeRampInsteadOfCopyingRawSlots() {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 2 ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 2 ROM");

            HostEmeraldPresentation.Result result = HostEmeraldPresentation.forHost("s2", rom);

            assertEquals(7, result.activeEmeraldCount());
            assertEquals(7 * 4 + 2, result.paletteBytes().length);
            assertTrue(result.usesRetintedRamp());
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
}
