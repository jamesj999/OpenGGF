package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.Palette;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

@RequiresRom(SonicGame.SONIC_3K)
public class TestAizPatternVerify {

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
    public void verifyCanopyPatterns() throws Exception {
        Level level = LevelManager.getInstance().getCurrentLevel();
        Rom rom = GameServices.rom().getRom();
        ResourceLoader loader = new ResourceLoader(rom);

        // Get art addresses from LLB entry 26
        int llbAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + 26 * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        int word0 = rom.read32BitAddr(llbAddr);
        int word1 = rom.read32BitAddr(llbAddr + 4);
        int primaryArtAddr = word0 & 0x00FFFFFF;
        int secondaryArtAddr = word1 & 0x00FFFFFF;

        byte[] primaryArt = loader.loadSingle(LoadOp.kosinskiMBase(primaryArtAddr));
        byte[] secondaryArt = loader.loadSingle(LoadOp.kosinskiMBase(secondaryArtAddr));

        int primaryTiles = primaryArt.length / 32;
        int secondaryTiles = secondaryArt.length / 32;

        System.out.println("=== AIZ1 Art Sizes ===");
        System.out.println("Primary art: " + primaryArt.length + " bytes, " + primaryTiles
                + " tiles (0x000-0x" + Integer.toHexString(primaryTiles - 1) + ")");
        System.out.println("Secondary (MainLevel) art: " + secondaryArt.length + " bytes, "
                + secondaryTiles + " tiles (0x" + Integer.toHexString(primaryTiles) + "-0x"
                + Integer.toHexString(primaryTiles + secondaryTiles - 1) + ")");

        // Get chunk sizes too
        int word2 = rom.read32BitAddr(llbAddr + 8);
        int word3 = rom.read32BitAddr(llbAddr + 12);
        int primaryBlocksAddr = word2 & 0x00FFFFFF;
        int secondaryBlocksAddr = word3 & 0x00FFFFFF;
        byte[] primaryBlocks = loader.loadSingle(LoadOp.kosinskiBase(primaryBlocksAddr));
        byte[] secondaryBlocks = loader.loadSingle(LoadOp.kosinskiBase(secondaryBlocksAddr));
        int primaryChunks = primaryBlocks.length / 8;
        int secondaryChunks = secondaryBlocks.length / 8;

        System.out.println("Primary 16x16 blocks: " + primaryBlocks.length + " bytes, "
                + primaryChunks + " chunks (0x000-0x" + Integer.toHexString(primaryChunks - 1) + ")");
        System.out.println("Secondary (MainLevel) 16x16 blocks: " + secondaryBlocks.length + " bytes, "
                + secondaryChunks + " chunks (0x" + Integer.toHexString(primaryChunks) + "-0x"
                + Integer.toHexString(primaryChunks + secondaryChunks - 1) + ")");

        // Dump canopy patterns (used in high-priority FG at tree area)
        int[] canopyPatterns = {0x1B3, 0x1B4, 0x1B5, 0x1B6, 0x1B7, 0x1B8, 0x1B9,
                                0x1BF, 0x1C0, 0x1C1, 0x1C2};

        System.out.println("\n=== Canopy Pattern Pixel Dumps ===");
        for (int patIdx : canopyPatterns) {
            Pattern pattern = level.getPattern(patIdx);
            if (pattern == null) {
                System.out.printf("  Pattern 0x%03X: [NULL]%n", patIdx);
                continue;
            }
            boolean hasIdx15 = false;
            int nonZeroCount = 0;
            StringBuilder rows = new StringBuilder();
            for (int y = 0; y < 8; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < 8; x++) {
                    int idx = pattern.getPixel(x, y) & 0x0F;
                    row.append(String.format("%X", idx));
                    if (idx != 0) nonZeroCount++;
                    if (idx == 15) hasIdx15 = true;
                }
                rows.append("    ").append(row).append("\n");
            }
            // Also verify against raw ROM decompression
            int offsetInSecondary = (patIdx - primaryTiles) * 32;
            boolean matchesRom = true;
            if (offsetInSecondary >= 0 && offsetInSecondary + 32 <= secondaryArt.length) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int romByte = secondaryArt[offsetInSecondary + y * 4 + x / 2] & 0xFF;
                        int romPixel = (x % 2 == 0) ? (romByte >> 4) : (romByte & 0x0F);
                        int enginePixel = pattern.getPixel(x, y) & 0x0F;
                        if (romPixel != enginePixel) {
                            matchesRom = false;
                        }
                    }
                }
            } else {
                matchesRom = false;
            }

            System.out.printf("  Pattern 0x%03X: %d/64 non-zero pixels%s ROM_MATCH=%b%n%s",
                    patIdx, nonZeroCount, hasIdx15 ? " [HAS IDX 15]" : "", matchesRom, rows);
        }

        // Dump Pal_AIZ (index $2A) palette line 2 from ROM
        System.out.println("\n=== Pal_AIZ (PalPointers[$2A]) palette line 1 (engine index 2) ===");
        int palPointersAddr = Sonic3kConstants.PAL_POINTERS_ADDR;
        int palEntryAddr = palPointersAddr + 0x2A * 8;
        int palRomAddr = rom.read32BitAddr(palEntryAddr) & 0x00FFFFFF;
        int palRamDest = rom.read16BitAddr(palEntryAddr + 4) & 0xFFFF;
        int palCountMinus1 = rom.read16BitAddr(palEntryAddr + 6) & 0xFFFF;
        int startLine = (palRamDest & 0xFF) / 32;

        System.out.printf("PalPointers[$2A]: addr=0x%06X ramDest=0x%04X count=%d startLine=%d%n",
                palRomAddr, palRamDest, palCountMinus1 + 1, startLine);

        // Read palette line 1 (engine index 2): offset = 32 bytes (16 colors × 2 bytes)
        for (int line = 0; line <= palCountMinus1; line++) {
            int lineOffset = line * 32; // 16 colors * 2 bytes each
            System.out.printf("  Line %d (engine palette %d):%n", line, startLine + line);
            for (int c = 0; c < 16; c++) {
                int colorAddr = palRomAddr + lineOffset + c * 2;
                int cramWord = rom.read16BitAddr(colorAddr) & 0xFFFF;
                int b = (cramWord >> 9) & 7;
                int g = (cramWord >> 5) & 7;
                int r = (cramWord >> 1) & 7;
                System.out.printf("    color[%2d]: $%04X -> R=%d G=%d B=%d (R=%3d G=%3d B=%3d)%n",
                        c, cramWord, r, g, b, r * 36, g * 36, b * 36);
            }
        }
    }
}
