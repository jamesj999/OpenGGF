package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

/**
 * Compares secondary art data between LLB entry 0 (normal AIZ1) and
 * entry 26 (intro AIZ1) to determine if patterns with pixel index 15
 * are present in both or only one.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestAizSecondaryArtCompare {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
    }

    @AfterClass
    public static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @Test
    public void compareSecondaryArt() throws Exception {
        Rom rom = GameServices.rom().getRom();
        ResourceLoader loader = new ResourceLoader(rom);

        // Read LLB entry 0 (normal AIZ1)
        int entry0Addr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR;
        int entry0Word0 = rom.read32BitAddr(entry0Addr);
        int entry0Word1 = rom.read32BitAddr(entry0Addr + 4);
        int entry0PrimaryArtAddr = entry0Word0 & 0x00FFFFFF;
        int entry0SecondaryArtAddr = entry0Word1 & 0x00FFFFFF;

        // Read LLB entry 26 (intro AIZ1)
        int entry26Addr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + 26 * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        int entry26Word0 = rom.read32BitAddr(entry26Addr);
        int entry26Word1 = rom.read32BitAddr(entry26Addr + 4);
        int entry26PrimaryArtAddr = entry26Word0 & 0x00FFFFFF;
        int entry26SecondaryArtAddr = entry26Word1 & 0x00FFFFFF;

        System.out.println("=== LLB Entry Comparison ===");
        System.out.printf("Entry 0:  primaryArt=0x%06X secondaryArt=0x%06X%n",
                entry0PrimaryArtAddr, entry0SecondaryArtAddr);
        System.out.printf("Entry 26: primaryArt=0x%06X secondaryArt=0x%06X%n",
                entry26PrimaryArtAddr, entry26SecondaryArtAddr);
        System.out.println("Same primary: " + (entry0PrimaryArtAddr == entry26PrimaryArtAddr));
        System.out.println("Same secondary: " + (entry0SecondaryArtAddr == entry26SecondaryArtAddr));

        // Decompress both secondary art sets
        byte[] primaryArt = loader.loadSingle(LoadOp.kosinskiMBase(entry0PrimaryArtAddr));
        byte[] entry0Secondary = loader.loadSingle(LoadOp.kosinskiMBase(entry0SecondaryArtAddr));
        byte[] entry26Secondary = loader.loadSingle(LoadOp.kosinskiMBase(entry26SecondaryArtAddr));

        int primaryTiles = primaryArt.length / 32;
        int entry0SecTiles = entry0Secondary.length / 32;
        int entry26SecTiles = entry26Secondary.length / 32;

        System.out.printf("%nPrimary: %d bytes, %d tiles (0x000-0x%03X)%n",
                primaryArt.length, primaryTiles, primaryTiles - 1);
        System.out.printf("Entry 0 secondary: %d bytes, %d tiles (0x%03X-0x%03X)%n",
                entry0Secondary.length, entry0SecTiles, primaryTiles, primaryTiles + entry0SecTiles - 1);
        System.out.printf("Entry 26 secondary: %d bytes, %d tiles (0x%03X-0x%03X)%n",
                entry26Secondary.length, entry26SecTiles, primaryTiles, primaryTiles + entry26SecTiles - 1);

        // Compare problematic patterns with pixel 15
        // Patterns at tree positions that use palette 2 (from chunk dump):
        int[] checkPatterns = {0x002, 0x01B, 0x016, 0x040, 0x052, 0x053, 0x058, 0x059, 0x05F,
                               0x067, 0x068, 0x069, 0x06A, 0x071, 0x075, 0x07B, 0x07C, 0x07D,
                               0x08E, 0x090, 0x091, 0x094,
                               0x1B3, 0x1B4, 0x1B5, 0x1B6, 0x1B7, 0x1B8, 0x1B9,
                               0x1BA, 0x1BB, 0x1BC, 0x1BD, 0x1BE,
                               0x1BF, 0x1C0, 0x1C1, 0x1C2, 0x1C3, 0x1C4, 0x1C5};

        System.out.println("\n=== Pattern Comparison (pixel 15 patterns) ===");
        for (int patIdx : checkPatterns) {
            int secOffset = (patIdx - primaryTiles) * 32;
            boolean inEntry0 = secOffset >= 0 && secOffset + 32 <= entry0Secondary.length;
            boolean inEntry26 = secOffset >= 0 && secOffset + 32 <= entry26Secondary.length;

            System.out.printf("\nPattern 0x%03X (secOffset=0x%04X):%n", patIdx, secOffset);
            if (!inEntry0 && !inEntry26) {
                System.out.println("  OUT OF RANGE in both");
                continue;
            }

            // Dump both versions' pixel data
            for (String label : new String[]{"Entry0", "Entry26"}) {
                byte[] data = label.equals("Entry0") ? entry0Secondary : entry26Secondary;
                boolean inRange = label.equals("Entry0") ? inEntry0 : inEntry26;
                if (!inRange) {
                    System.out.printf("  %s: OUT OF RANGE%n", label);
                    continue;
                }
                boolean hasF = false;
                int nonZero = 0;
                StringBuilder rows = new StringBuilder();
                for (int y = 0; y < 8; y++) {
                    StringBuilder row = new StringBuilder();
                    for (int x = 0; x < 8; x++) {
                        int romByte = data[secOffset + y * 4 + x / 2] & 0xFF;
                        int pixel = (x % 2 == 0) ? (romByte >> 4) : (romByte & 0x0F);
                        row.append(String.format("%X", pixel));
                        if (pixel != 0) nonZero++;
                        if (pixel == 0xF) hasF = true;
                    }
                    rows.append("    ").append(row).append("\n");
                }
                System.out.printf("  %s: %d/64 nonzero, hasF=%b%n%s",
                        label, nonZero, hasF, rows);
            }

            // Check if data is identical
            if (inEntry0 && inEntry26) {
                boolean same = true;
                for (int i = 0; i < 32; i++) {
                    if (entry0Secondary[secOffset + i] != entry26Secondary[secOffset + i]) {
                        same = false;
                        break;
                    }
                }
                System.out.println("  IDENTICAL: " + same);
            }
        }

        // Also check Pal_AIZIntro palette 2 color 15
        System.out.println("\n=== Pal_AIZIntro ($A) palette line 2 colors 11-15 ===");
        int palEntryAddr = Sonic3kConstants.PAL_POINTERS_ADDR
                + Sonic3kConstants.PAL_AIZ_INTRO_INDEX * 8;
        int palRomAddr = rom.read32BitAddr(palEntryAddr) & 0x00FFFFFF;
        int palRamDest = rom.read16BitAddr(palEntryAddr + 4) & 0xFFFF;
        int startLine = (palRamDest & 0xFF) / 32;
        System.out.printf("PalPointers[$A]: addr=0x%06X ramDest=0x%04X startLine=%d%n",
                palRomAddr, palRamDest, startLine);
        // Line 1 of Pal_AIZIntro (CRAM line 2 = engine palette 2)
        int line1Offset = 32; // second line of palette data
        for (int c = 11; c <= 15; c++) {
            int colorAddr = palRomAddr + line1Offset + c * 2;
            int cramWord = rom.read16BitAddr(colorAddr) & 0xFFFF;
            int b = (cramWord >> 9) & 7;
            int g = (cramWord >> 5) & 7;
            int r = (cramWord >> 1) & 7;
            System.out.printf("  color[%2d]: $%04X -> R=%d G=%d B=%d (R=%3d G=%3d B=%3d)%n",
                    c, cramWord, r, g, b, r * 36, g * 36, b * 36);
        }

        // Also check Pal_AIZ palette 2 color 15 for reference
        System.out.println("\n=== Pal_AIZ ($2A) palette line 2 colors 11-15 ===");
        int palAizEntryAddr = Sonic3kConstants.PAL_POINTERS_ADDR + 0x2A * 8;
        int palAizRomAddr = rom.read32BitAddr(palAizEntryAddr) & 0x00FFFFFF;
        int palAizLine1Offset = 32;
        for (int c = 11; c <= 15; c++) {
            int colorAddr = palAizRomAddr + palAizLine1Offset + c * 2;
            int cramWord = rom.read16BitAddr(colorAddr) & 0xFFFF;
            int b = (cramWord >> 9) & 7;
            int g = (cramWord >> 5) & 7;
            int r = (cramWord >> 1) & 7;
            System.out.printf("  color[%2d]: $%04X -> R=%d G=%d B=%d (R=%3d G=%3d B=%3d)%n",
                    c, cramWord, r, g, b, r * 36, g * 36, b * 36);
        }
    }
}
