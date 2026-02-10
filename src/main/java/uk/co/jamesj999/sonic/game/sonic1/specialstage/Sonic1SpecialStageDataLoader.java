package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.tools.EnigmaReader;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.logging.Logger;

import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.*;

/**
 * Loads and caches Sonic 1 Special Stage data from the ROM.
 * All data is loaded lazily on first access.
 *
 * Layout format: Enigma-compressed layouts decompress to big-endian 16-bit words.
 * The low byte of each word is the block ID. The decompressed data is 0x40 bytes
 * wide; SS_Load copies it into v_ssblockbuffer, which starts at offset 0x1020 inside
 * the 0x4000-byte v_ssbuffer1 RAM window.
 */
public class Sonic1SpecialStageDataLoader {
    private static final Logger LOGGER = Logger.getLogger(Sonic1SpecialStageDataLoader.class.getName());

    private final Rom rom;

    private byte[][] stageLayouts;
    private int[][] startPositions;
    private byte[] ssPalette;

    // Art pattern caches
    private Pattern[] wallPatterns;
    private Pattern[] bumperPatterns;
    private Pattern[] goalPatterns;
    private Pattern[] upDownPatterns;
    private Pattern[] rBlockPatterns;
    private Pattern[] oneUpPatterns;
    private Pattern[] emStarsPatterns;
    private Pattern[] redWhitePatterns;
    private Pattern[] ghostPatterns;
    private Pattern[] wBlockPatterns;
    private Pattern[] glassPatterns;
    private Pattern[] emeraldPatterns;
    private Pattern[][] zonePatterns; // [0..5]
    private Pattern[] bgCloudPatterns;
    private Pattern[] bgFishPatterns;
    private Pattern[] resultEmPatterns;

    public Sonic1SpecialStageDataLoader(Rom rom) {
        this.rom = rom;
    }

    /**
     * Gets the layout for a stage in special-stage RAM layout format.
     */
    public byte[] getStageLayout(int stageIndex) throws IOException {
        if (stageLayouts == null) {
            stageLayouts = new byte[SS_STAGE_COUNT][];
        }
        if (stageIndex < 0 || stageIndex >= SS_STAGE_COUNT) {
            throw new IllegalArgumentException("Stage index out of range: " + stageIndex);
        }
        if (stageLayouts[stageIndex] == null) {
            stageLayouts[stageIndex] = loadStageLayout(stageIndex);
        }
        return stageLayouts[stageIndex];
    }

    /**
     * Gets the start position for a stage.
     * @return int[]{x, y} in pixels
     */
    public int[] getStartPosition(int stageIndex) throws IOException {
        if (startPositions == null) {
            startPositions = new int[SS_STAGE_COUNT][];
            for (int i = 0; i < SS_STAGE_COUNT; i++) {
                int addr = SS_START_LOC_ADDR + i * 4;
                int x = rom.read16BitAddr(addr);
                int y = rom.read16BitAddr(addr + 2);
                startPositions[i] = new int[]{x, y};
            }
        }
        return startPositions[stageIndex];
    }

    /**
     * Gets the special stage palette (128 bytes = 4 palette lines).
     */
    public byte[] getSSPalette() throws IOException {
        if (ssPalette == null) {
            ssPalette = rom.readBytes(PAL_SS_ADDR, PAL_SS_SIZE);
            LOGGER.fine("Loaded SS palette: " + ssPalette.length + " bytes");
        }
        return ssPalette;
    }

    // ---- Art pattern loading ----

    public Pattern[] getWallPatterns() throws IOException {
        if (wallPatterns == null) {
            wallPatterns = loadNemesisPatterns(ART_NEM_SS_WALLS_ADDR, ART_NEM_SS_WALLS_SIZE);
            LOGGER.fine("Loaded SS wall art: " + wallPatterns.length + " patterns");
        }
        return wallPatterns;
    }

    public Pattern[] getBumperPatterns() throws IOException {
        if (bumperPatterns == null) {
            bumperPatterns = loadNemesisPatterns(ART_NEM_SS_BUMPER_ADDR, ART_NEM_SS_BUMPER_SIZE);
            LOGGER.fine("Loaded SS bumper art: " + bumperPatterns.length + " patterns");
        }
        return bumperPatterns;
    }

    public Pattern[] getGoalPatterns() throws IOException {
        if (goalPatterns == null) {
            goalPatterns = loadNemesisPatterns(ART_NEM_SS_GOAL_ADDR, ART_NEM_SS_GOAL_SIZE);
            LOGGER.fine("Loaded SS GOAL art: " + goalPatterns.length + " patterns");
        }
        return goalPatterns;
    }

    public Pattern[] getUpDownPatterns() throws IOException {
        if (upDownPatterns == null) {
            upDownPatterns = loadNemesisPatterns(ART_NEM_SS_UPDOWN_ADDR, ART_NEM_SS_UPDOWN_SIZE);
            LOGGER.fine("Loaded SS UP/DOWN art: " + upDownPatterns.length + " patterns");
        }
        return upDownPatterns;
    }

    public Pattern[] getRBlockPatterns() throws IOException {
        if (rBlockPatterns == null) {
            rBlockPatterns = loadNemesisPatterns(ART_NEM_SS_RBLOCK_ADDR, ART_NEM_SS_RBLOCK_SIZE);
            LOGGER.fine("Loaded SS R block art: " + rBlockPatterns.length + " patterns");
        }
        return rBlockPatterns;
    }

    public Pattern[] getOneUpPatterns() throws IOException {
        if (oneUpPatterns == null) {
            oneUpPatterns = loadNemesisPatterns(ART_NEM_SS_1UP_ADDR, ART_NEM_SS_1UP_SIZE);
            LOGGER.fine("Loaded SS 1UP art: " + oneUpPatterns.length + " patterns");
        }
        return oneUpPatterns;
    }

    public Pattern[] getEmStarsPatterns() throws IOException {
        if (emStarsPatterns == null) {
            emStarsPatterns = loadNemesisPatterns(ART_NEM_SS_EM_STARS_ADDR, ART_NEM_SS_EM_STARS_SIZE);
            LOGGER.fine("Loaded SS emerald stars art: " + emStarsPatterns.length + " patterns");
        }
        return emStarsPatterns;
    }

    public Pattern[] getRedWhitePatterns() throws IOException {
        if (redWhitePatterns == null) {
            redWhitePatterns = loadNemesisPatterns(ART_NEM_SS_RED_WHITE_ADDR, ART_NEM_SS_RED_WHITE_SIZE);
            LOGGER.fine("Loaded SS red-white art: " + redWhitePatterns.length + " patterns");
        }
        return redWhitePatterns;
    }

    public Pattern[] getGhostPatterns() throws IOException {
        if (ghostPatterns == null) {
            ghostPatterns = loadNemesisPatterns(ART_NEM_SS_GHOST_ADDR, ART_NEM_SS_GHOST_SIZE);
            LOGGER.fine("Loaded SS ghost art: " + ghostPatterns.length + " patterns");
        }
        return ghostPatterns;
    }

    public Pattern[] getWBlockPatterns() throws IOException {
        if (wBlockPatterns == null) {
            wBlockPatterns = loadNemesisPatterns(ART_NEM_SS_WBLOCK_ADDR, ART_NEM_SS_WBLOCK_SIZE);
            LOGGER.fine("Loaded SS W block art: " + wBlockPatterns.length + " patterns");
        }
        return wBlockPatterns;
    }

    public Pattern[] getGlassPatterns() throws IOException {
        if (glassPatterns == null) {
            glassPatterns = loadNemesisPatterns(ART_NEM_SS_GLASS_ADDR, ART_NEM_SS_GLASS_SIZE);
            LOGGER.fine("Loaded SS glass art: " + glassPatterns.length + " patterns");
        }
        return glassPatterns;
    }

    public Pattern[] getEmeraldPatterns() throws IOException {
        if (emeraldPatterns == null) {
            emeraldPatterns = loadNemesisPatterns(ART_NEM_SS_EMERALD_ADDR, ART_NEM_SS_EMERALD_SIZE);
            LOGGER.fine("Loaded SS emerald art: " + emeraldPatterns.length + " patterns");
        }
        return emeraldPatterns;
    }

    public Pattern[] getZonePatterns(int zoneIndex) throws IOException {
        if (zonePatterns == null) {
            zonePatterns = new Pattern[6][];
        }
        if (zoneIndex < 0 || zoneIndex >= 6) {
            throw new IllegalArgumentException("Zone index out of range: " + zoneIndex);
        }
        if (zonePatterns[zoneIndex] == null) {
            int[] addrs = {ART_NEM_SS_ZONE1_ADDR, ART_NEM_SS_ZONE2_ADDR, ART_NEM_SS_ZONE3_ADDR,
                    ART_NEM_SS_ZONE4_ADDR, ART_NEM_SS_ZONE5_ADDR, ART_NEM_SS_ZONE6_ADDR};
            int[] sizes = {ART_NEM_SS_ZONE1_SIZE, ART_NEM_SS_ZONE2_SIZE, ART_NEM_SS_ZONE3_SIZE,
                    ART_NEM_SS_ZONE4_SIZE, ART_NEM_SS_ZONE5_SIZE, ART_NEM_SS_ZONE6_SIZE};
            zonePatterns[zoneIndex] = loadNemesisPatterns(addrs[zoneIndex], sizes[zoneIndex]);
            LOGGER.fine("Loaded SS zone " + (zoneIndex + 1) + " art: " + zonePatterns[zoneIndex].length + " patterns");
        }
        return zonePatterns[zoneIndex];
    }

    public Pattern[] getBgCloudPatterns() throws IOException {
        if (bgCloudPatterns == null) {
            bgCloudPatterns = loadNemesisPatterns(ART_NEM_SS_BG_CLOUD_ADDR, ART_NEM_SS_BG_CLOUD_SIZE);
            LOGGER.fine("Loaded SS BG cloud art: " + bgCloudPatterns.length + " patterns");
        }
        return bgCloudPatterns;
    }

    public Pattern[] getBgFishPatterns() throws IOException {
        if (bgFishPatterns == null) {
            bgFishPatterns = loadNemesisPatterns(ART_NEM_SS_BG_FISH_ADDR, ART_NEM_SS_BG_FISH_SIZE);
            LOGGER.fine("Loaded SS BG fish art: " + bgFishPatterns.length + " patterns");
        }
        return bgFishPatterns;
    }

    // ---- Internal helpers ----

    private byte[] loadStageLayout(int stageIndex) throws IOException {
        // Read layout pointer
        int layoutAddr = rom.read32BitAddr(SS_LAYOUT_INDEX_ADDR + stageIndex * 4) & 0x00FFFFFF;
        LOGGER.fine("Loading SS layout " + (stageIndex + 1) + " from 0x" + Integer.toHexString(layoutAddr));

        // Read compressed data (generous size, Enigma reader stops at terminator)
        byte[] compressed = rom.readBytes(layoutAddr, 0x2000);
        byte[] enigmaOutput;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             ReadableByteChannel channel = Channels.newChannel(bais)) {
            // startingArtTile = 0 for SS layouts (block IDs, not VRAM tiles)
            enigmaOutput = EnigmaReader.decompress(channel, 0);
        }

        // ROM path (SS_Load):
        // 1) clear v_ssbuffer1..v_ssbuffer2 (0x4000 bytes)
        // 2) copy $1000 bytes from v_ssbuffer2 into v_ssblockbuffer
        //    with $80 stride and $40-byte row padding.
        final int sourceBytesToCopy = 0x1000;
        final int rows = SS_BLOCKBUFFER_ROWS;
        byte[] buffer = new byte[SS_LAYOUT_RAM_SIZE];

        int bytesAvailable = Math.min(sourceBytesToCopy, enigmaOutput.length);
        for (int row = 0; row < rows; row++) {
            int srcOff = row * SS_LAYOUT_COLS;
            int dstOff = SS_BLOCKBUFFER_OFFSET + row * SS_LAYOUT_STRIDE;
            if (srcOff >= bytesAvailable) {
                break;
            }
            int colsThisRow = Math.min(SS_LAYOUT_COLS, bytesAvailable - srcOff);
            System.arraycopy(enigmaOutput, srcOff, buffer, dstOff, colsThisRow);
        }

        LOGGER.fine("SS layout " + (stageIndex + 1) + ": decompressed=" + enigmaOutput.length +
                " bytes, copied=" + bytesAvailable +
                " bytes into blockbuffer@" + Integer.toHexString(SS_BLOCKBUFFER_OFFSET) +
                " (" + rows + "x" + SS_LAYOUT_COLS + ", stride " + SS_LAYOUT_STRIDE + ")");
        return buffer;
    }

    private Pattern[] loadNemesisPatterns(long romAddr, int compressedSize) throws IOException {
        // Read with padding for decompressor edge cases
        byte[] compressed = rom.readBytes(romAddr, compressedSize + 16);
        byte[] decompressed;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             ReadableByteChannel channel = Channels.newChannel(bais)) {
            decompressed = NemesisReader.decompress(channel);
        }
        return bytesToPatterns(decompressed);
    }

    private Pattern[] bytesToPatterns(byte[] data) {
        int patternCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            byte[] subArray = Arrays.copyOfRange(data, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(subArray);
        }
        return patterns;
    }
}
