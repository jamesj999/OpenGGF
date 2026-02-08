package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.*;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.rings.RingSpawn;
import uk.co.jamesj999.sonic.level.rings.RingSpriteSheet;
import uk.co.jamesj999.sonic.tools.EnigmaReader;
import uk.co.jamesj999.sonic.tools.KosinskiReader;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Level implementation for Sonic the Hedgehog 1.
 *
 * <p>Key differences from Sonic 2:
 * <ul>
 *   <li>Blocks are 256x256 pixels (16x16 grid of 16x16 chunks), not 128x128</li>
 *   <li>Patterns are Nemesis compressed (not Kosinski)</li>
 *   <li>16x16 chunk mappings are Enigma compressed</li>
 *   <li>256x256 block mappings are Kosinski compressed</li>
 *   <li>Level layouts are uncompressed with a 2-byte header (width, height)</li>
 *   <li>Collision indices are uncompressed binary (not Kosinski)</li>
 *   <li>Sonic 1 has no separate ring placement - rings are objects</li>
 *   <li>Block word format differs: 0SSY X0II IIII IIII</li>
 * </ul>
 */
public class Sonic1Level implements Level {
    private static final int PALETTE_COUNT = 4;
    private static final int S1_CHUNKS_PER_BLOCK = 256; // 16x16 grid
    private static final int S1_BLOCK_SIZE_IN_ROM = S1_CHUNKS_PER_BLOCK * LevelConstants.BYTES_PER_CHUNK; // 512 bytes
    private static final int S1_GRID_SIDE = 16;
    private static final int COLLISION_INDEX_MAX_SIZE = 0x300;

    private static final Logger LOG = Logger.getLogger(Sonic1Level.class.getName());

    /**
     * A pattern load cue entry from the ArtLoadCues table.
     * Each entry specifies a Nemesis-compressed art source and its destination tile offset.
     *
     * @param romAddr    ROM address of Nemesis-compressed data
     * @param tileOffset destination tile index (VRAM byte offset / 0x20)
     */
    public record PatternLoadCue(int romAddr, int tileOffset) {}

    private final int zoneIndex;
    private Palette[] palettes;
    private Pattern[] patterns;
    private Chunk[] chunks;
    private Block[] blocks;
    private SolidTile[] solidTiles;
    private Map map;
    private final List<ObjectSpawn> objects;
    private final List<RingSpawn> rings;
    private final RingSpriteSheet ringSpriteSheet;

    private int patternCount;
    private int chunkCount;
    private int blockCount;
    private int solidTileCount;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;

    public Sonic1Level(Rom rom,
                       int zoneIndex,
                       int sonicPaletteAddr,
                       int levelPaletteAddr,
                       List<PatternLoadCue> patternCues,
                       int chunksAddr,
                       int blocksAddr,
                       int fgLayoutAddr,
                       int bgLayoutAddr,
                       int collisionIndexAddr,
                       int solidTileHeightsAddr,
                       int solidTileWidthsAddr,
                       int solidTileAnglesAddr,
                       List<ObjectSpawn> objectSpawns,
                       List<RingSpawn> ringSpawns,
                       RingSpriteSheet ringSpriteSheet,
                       int[] boundaries) throws IOException {
        this.zoneIndex = zoneIndex;
        this.objects = List.copyOf(objectSpawns);
        this.rings = List.copyOf(ringSpawns);
        this.ringSpriteSheet = ringSpriteSheet;

        loadPalettes(rom, sonicPaletteAddr, levelPaletteAddr);
        loadPatterns(rom, patternCues);
        loadSolidTiles(rom, solidTileHeightsAddr, solidTileWidthsAddr, solidTileAnglesAddr);
        loadChunks(rom, chunksAddr, collisionIndexAddr);
        loadBlocks(rom, blocksAddr);
        loadMap(rom, fgLayoutAddr, bgLayoutAddr);
        verifyChunkPatternReferences();

        this.minX = boundaries[0];
        this.maxX = boundaries[1];
        this.minY = boundaries[2];
        this.maxY = boundaries[3];
    }

    // ===== Level interface =====

    @Override
    public int getBlockPixelSize() {
        return 256;
    }

    @Override
    public int getChunksPerBlockSide() {
        return 16;
    }

    @Override
    public int getPaletteCount() {
        return PALETTE_COUNT;
    }

    @Override
    public Palette getPalette(int index) {
        if (index >= PALETTE_COUNT) {
            throw new IllegalArgumentException("Invalid palette index: " + index);
        }
        return palettes[index];
    }

    @Override
    public void setPalette(int index, Palette palette) {
        if (index >= 0 && index < PALETTE_COUNT && palette != null) {
            palettes[index] = palette;
        }
    }

    @Override
    public int getPatternCount() {
        return patternCount;
    }

    @Override
    public Pattern getPattern(int index) {
        if (index >= patternCount) {
            throw new IllegalArgumentException("Invalid pattern index: " + index);
        }
        return patterns[index];
    }

    @Override
    public void ensurePatternCapacity(int minCount) {
        if (minCount <= patternCount) {
            return;
        }
        patterns = Arrays.copyOf(patterns, minCount);
        GraphicsManager graphicsMan = GraphicsManager.getInstance();
        for (int i = patternCount; i < minCount; i++) {
            patterns[i] = new Pattern();
            if (graphicsMan.isGlInitialized()) {
                graphicsMan.cachePatternTexture(patterns[i], i);
            }
        }
        patternCount = minCount;
    }

    @Override
    public int getChunkCount() {
        return chunkCount;
    }

    @Override
    public Chunk getChunk(int index) {
        if (index >= chunkCount) {
            throw new IllegalArgumentException("Invalid chunk index: " + index);
        }
        return chunks[index];
    }

    @Override
    public int getBlockCount() {
        return blockCount;
    }

    @Override
    public Block getBlock(int index) {
        if (index >= blockCount) {
            throw new IllegalArgumentException("Invalid block index: " + index);
        }
        return blocks[index];
    }

    @Override
    public SolidTile getSolidTile(int index) {
        if (index >= solidTileCount) {
            throw new IllegalArgumentException("Invalid solid tile index: " + index);
        }
        return solidTiles[index];
    }

    @Override
    public Map getMap() {
        return map;
    }

    @Override
    public List<ObjectSpawn> getObjects() {
        return objects;
    }

    @Override
    public List<RingSpawn> getRings() {
        return rings;
    }

    @Override
    public RingSpriteSheet getRingSpriteSheet() {
        return ringSpriteSheet;
    }

    @Override
    public int getMinX() {
        return minX;
    }

    @Override
    public int getMaxX() {
        return maxX;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    @Override
    public int getZoneIndex() {
        return zoneIndex;
    }

    // ===== Loading methods =====

    /**
     * Loads palettes. Sonic 1 has a character palette and a level palette.
     * We load the Sonic palette as palette 0 and the level palette data
     * as palettes 1-3 (3 palette lines, 32 bytes each).
     */
    private void loadPalettes(Rom rom, int sonicPaletteAddr, int levelPaletteAddr) throws IOException {
        palettes = new Palette[PALETTE_COUNT];
        GraphicsManager graphicsMan = GraphicsManager.getInstance();

        // Palette 0: Sonic/character palette (32 bytes = 16 colors)
        byte[] buffer = rom.readBytes(sonicPaletteAddr, Palette.PALETTE_SIZE_IN_ROM);
        palettes[0] = new Palette();
        palettes[0].fromSegaFormat(buffer);

        // Palettes 1-3: Level palettes (3 lines of 32 bytes each = 96 bytes)
        int levelPaletteSize = 3 * Palette.PALETTE_SIZE_IN_ROM;
        buffer = rom.readBytes(levelPaletteAddr, levelPaletteSize);

        for (int i = 0; i < 3; i++) {
            palettes[i + 1] = new Palette();
            int start = i * Palette.PALETTE_SIZE_IN_ROM;
            int end = start + Palette.PALETTE_SIZE_IN_ROM;
            if (end <= buffer.length) {
                byte[] subArray = Arrays.copyOfRange(buffer, start, end);
                palettes[i + 1].fromSegaFormat(subArray);
            }
        }

        if (graphicsMan.isGlInitialized()) {
            for (int i = 0; i < palettes.length; i++) {
                graphicsMan.cachePaletteTexture(palettes[i], i);
            }
        }

        // Log first few colors of each palette line for debugging
        for (int p = 0; p < PALETTE_COUNT; p++) {
            Palette pal = palettes[p];
            StringBuilder sb = new StringBuilder();
            sb.append("Palette ").append(p).append(": ");
            for (int c = 0; c < Math.min(4, pal.getColorCount()); c++) {
                Palette.Color col = pal.getColor(c);
                sb.append(String.format("(%d,%d,%d) ", col.r & 0xFF, col.g & 0xFF, col.b & 0xFF));
            }
            sb.append("...");
            LOG.info(sb.toString());
        }
    }

    /**
     * Loads patterns from multiple PLC (Pattern Load Cue) entries using Nemesis decompression.
     *
     * <p>Each PLC entry specifies a ROM address and a destination tile offset. All entries
     * are loaded at their specified offsets, allowing non-contiguous placement. This handles
     * zones like GHZ which require two files: Nem_GHZ_1st at tile 0 and Nem_GHZ_2nd at 0x1CD.
     *
     * <p>Any gaps between entries are filled with empty patterns.
     */
    private void loadPatterns(Rom rom, List<PatternLoadCue> cues) throws IOException {
        GraphicsManager graphicsMan = GraphicsManager.getInstance();
        FileChannel channel = rom.getFileChannel();

        // Sort cues by tile offset
        List<PatternLoadCue> sorted = new ArrayList<>(cues);
        sorted.sort(Comparator.comparingInt(PatternLoadCue::tileOffset));

        // Decompress all PLC entries and place at their specified tile offsets
        List<byte[]> decompressedData = new ArrayList<>();
        List<PatternLoadCue> usedCues = new ArrayList<>();
        int maxTileIndex = 0;

        for (PatternLoadCue cue : sorted) {
            channel.position(cue.romAddr());
            byte[] data = NemesisReader.decompress(channel);
            decompressedData.add(data);
            usedCues.add(cue);

            int tileCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;
            int endTile = cue.tileOffset() + tileCount;
            if (endTile > maxTileIndex) {
                maxTileIndex = endTile;
            }

            LOG.info("PLC entry: romAddr=0x" + Integer.toHexString(cue.romAddr()) +
                    " tileOffset=0x" + Integer.toHexString(cue.tileOffset()) +
                    " tiles=" + tileCount + " endTile=0x" + Integer.toHexString(endTile));
        }

        // Build pattern array covering all loaded entries
        patternCount = maxTileIndex;
        patterns = new Pattern[patternCount];

        for (int i = 0; i < usedCues.size(); i++) {
            PatternLoadCue cue = usedCues.get(i);
            byte[] data = decompressedData.get(i);
            int tileCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;

            for (int t = 0; t < tileCount; t++) {
                int patIdx = cue.tileOffset() + t;
                if (patIdx >= patternCount) break;
                patterns[patIdx] = new Pattern();
                byte[] subArray = Arrays.copyOfRange(data, t * Pattern.PATTERN_SIZE_IN_ROM,
                        (t + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                patterns[patIdx].fromSegaFormat(subArray);

                if (graphicsMan.isGlInitialized()) {
                    graphicsMan.cachePatternTexture(patterns[patIdx], patIdx);
                }
            }
        }

        // Fill any gaps with empty patterns
        for (int i = 0; i < patternCount; i++) {
            if (patterns[i] == null) {
                patterns[i] = new Pattern();
                if (graphicsMan.isGlInitialized()) {
                    graphicsMan.cachePatternTexture(patterns[i], i);
                }
            }
        }

        LOG.info("Total pattern count: 0x" + Integer.toHexString(patternCount) +
                " (" + patternCount + ") from " + usedCues.size() + " PLC entries [Nemesis]");
    }

    /**
     * Loads 16x16 chunk mappings using Enigma decompression and raw collision index.
     *
     * <p>Sonic 1 collision indices are uncompressed binary data (not Kosinski like Sonic 2).
     * Both primary and secondary collision paths use the same index in Sonic 1.
     */
    private void loadChunks(Rom rom, int chunksAddr, int collisionIndexAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(chunksAddr);

        byte[] chunkBuffer = EnigmaReader.decompress(channel, 0);

        chunkCount = chunkBuffer.length / Chunk.CHUNK_SIZE_IN_ROM;
        if (chunkBuffer.length % Chunk.CHUNK_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent chunk data");
        }

        // Collision index is raw binary in Sonic 1 (not Kosinski compressed)
        int collisionSize = Math.min(COLLISION_INDEX_MAX_SIZE, chunkCount);
        byte[] collisionIndex = rom.readBytes(collisionIndexAddr, collisionSize);

        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            chunks[i] = new Chunk();
            byte[] subArray = Arrays.copyOfRange(chunkBuffer, i * Chunk.CHUNK_SIZE_IN_ROM,
                    (i + 1) * Chunk.CHUNK_SIZE_IN_ROM);
            int solidTileIndex = 0;
            if (i < collisionIndex.length) {
                solidTileIndex = Byte.toUnsignedInt(collisionIndex[i]);
            }
            // Sonic 1 uses same collision for both layers
            chunks[i].fromSegaFormat(subArray, solidTileIndex, solidTileIndex);
        }

        LOG.fine("Chunk count: " + chunkCount + " (" + chunkBuffer.length + " bytes) [Enigma]");
    }

    /**
     * Loads 256x256 block mappings using Kosinski decompression.
     *
     * <p>Sonic 1 block word format: {@code 0SSY X0II IIII IIII}
     * <ul>
     *   <li>Bits 0-9: Chunk index (10 bits)</li>
     *   <li>Bit 11: X flip</li>
     *   <li>Bit 12: Y flip</li>
     *   <li>Bits 13-14: Solidity</li>
     * </ul>
     *
     * <p>This must be converted to Sonic 2 ChunkDesc format:
     * {@code SSTT YXII IIII IIII}
     */
    private void loadBlocks(Rom rom, int blocksAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(blocksAddr);

        byte[] blockBuffer = KosinskiReader.decompress(channel, false);

        int dataBlockCount = blockBuffer.length / S1_BLOCK_SIZE_IN_ROM;
        if (blockBuffer.length % S1_BLOCK_SIZE_IN_ROM != 0) {
            LOG.warning("Block data not aligned to block size: " + blockBuffer.length +
                    " bytes, expected multiple of " + S1_BLOCK_SIZE_IN_ROM);
        }

        // Sonic 1 block IDs are 1-based in the layout: ID 0 = empty, ID 1 = first
        // block in the Kosinski data. The disassembly's GetBlockData does subq.b #1
        // before computing the offset. So we allocate one extra slot and store data
        // blocks starting at index 1, keeping index 0 as the empty block.
        blockCount = dataBlockCount + 1;
        blocks = new Block[blockCount];
        blocks[0] = new Block(S1_GRID_SIDE); // Empty block for ID 0

        for (int i = 0; i < dataBlockCount; i++) {
            blocks[i + 1] = new Block(S1_GRID_SIDE);
            byte[] subArray = Arrays.copyOfRange(blockBuffer, i * S1_BLOCK_SIZE_IN_ROM,
                    (i + 1) * S1_BLOCK_SIZE_IN_ROM);
            byte[] converted = convertS1BlockData(subArray);
            blocks[i + 1].fromSegaFormat(converted, S1_CHUNKS_PER_BLOCK);
        }

        LOG.fine("Block count: " + blockCount + " (data=" + dataBlockCount +
                ", " + blockBuffer.length + " bytes) [Kosinski]");
    }

    /**
     * Converts Sonic 1 block word data to Sonic 2 ChunkDesc format.
     *
     * <p>S1 format: {@code 0SSY X0II IIII IIII}
     * <br>S2 format: {@code SSTT YXII IIII IIII}
     *
     * <p>Sonic 1 has a single solidity field; we duplicate it for both
     * primary and secondary collision.
     */
    private byte[] convertS1BlockData(byte[] s1Data) {
        byte[] result = new byte[s1Data.length];
        for (int i = 0; i < s1Data.length; i += 2) {
            int s1Word = ((s1Data[i] & 0xFF) << 8) | (s1Data[i + 1] & 0xFF);

            // S1: 0SSY X0II IIII IIII
            int chunkIndex = s1Word & 0x03FF;            // bits 0-9
            boolean xFlip = (s1Word & 0x0800) != 0;      // bit 11
            boolean yFlip = (s1Word & 0x1000) != 0;       // bit 12
            int solidity = (s1Word >> 13) & 0x3;           // bits 13-14

            // S2: SSTT YXII IIII IIII
            int s2Word = chunkIndex
                    | (xFlip ? 0x0400 : 0)       // bit 10
                    | (yFlip ? 0x0800 : 0)       // bit 11
                    | ((solidity & 0x3) << 12)   // bits 12-13 (primary)
                    | ((solidity & 0x3) << 14);  // bits 14-15 (secondary = same in S1)

            result[i] = (byte) ((s2Word >> 8) & 0xFF);
            result[i + 1] = (byte) (s2Word & 0xFF);
        }
        return result;
    }

    /**
     * Loads level layouts. Sonic 1 layouts are uncompressed with a 2-byte header:
     * byte 0 = (width - 1), byte 1 = (height - 1).
     */
    private void loadMap(Rom rom, int fgAddr, int bgAddr) throws IOException {
        // FG layout
        byte[] fgHeader = rom.readBytes(fgAddr, 2);
        int fgWidth = (fgHeader[0] & 0xFF) + 1;
        int fgHeight = (fgHeader[1] & 0xFF) + 1;
        byte[] fgData = rom.readBytes(fgAddr + 2, fgWidth * fgHeight);

        // BG layout
        byte[] bgHeader = rom.readBytes(bgAddr, 2);
        int bgWidth = (bgHeader[0] & 0xFF) + 1;
        int bgHeight = (bgHeader[1] & 0xFF) + 1;
        byte[] bgData = rom.readBytes(bgAddr + 2, bgWidth * bgHeight);

        // Build Map with 2 layers
        int mapWidth = Math.max(fgWidth, bgWidth);
        int mapHeight = Math.max(fgHeight, bgHeight);
        byte[] mapBuffer = new byte[2 * mapWidth * mapHeight];

        // Copy FG (layer 0) - strip loop flag (bit 7)
        for (int y = 0; y < fgHeight; y++) {
            for (int x = 0; x < fgWidth; x++) {
                mapBuffer[y * mapWidth * 2 + x] = (byte) (fgData[y * fgWidth + x] & 0x7F);
            }
        }

        // Copy BG (layer 1) - strip loop flag (bit 7).
        // Tile BG data to fill the full unified map dimensions, since the engine's
        // Map wraps at mapWidth/mapHeight, not the BG's original dimensions.
        // GHZ BG is 32x1 but needs to tile across the full 48x5 FG area.
        for (int y = 0; y < mapHeight; y++) {
            int srcY = y % bgHeight;
            for (int x = 0; x < mapWidth; x++) {
                int srcX = x % bgWidth;
                mapBuffer[y * mapWidth * 2 + mapWidth + x] =
                        (byte) (bgData[srcY * bgWidth + srcX] & 0x7F);
            }
        }

        map = new Map(2, mapWidth, mapHeight, mapBuffer);

        LOG.fine("Map loaded: FG=" + fgWidth + "x" + fgHeight + ", BG=" + bgWidth + "x" + bgHeight);
    }

    /**
     * Loads solid tile height/width arrays and angle map.
     * Same format as Sonic 2.
     */
    private void loadSolidTiles(Rom rom, int heightsAddr, int widthsAddr, int anglesAddr) throws IOException {
        solidTileCount = (Sonic1Constants.SOLID_TILE_MAP_SIZE + 1) / SolidTile.TILE_SIZE_IN_ROM;

        byte[] heights = rom.readBytes(heightsAddr, Sonic1Constants.SOLID_TILE_MAP_SIZE);
        byte[] widths = rom.readBytes(widthsAddr, Sonic1Constants.SOLID_TILE_MAP_SIZE);

        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) {
            byte angle = rom.readByte(anglesAddr + i);
            byte[] h = Arrays.copyOfRange(heights, i * SolidTile.TILE_SIZE_IN_ROM,
                    (i + 1) * SolidTile.TILE_SIZE_IN_ROM);
            byte[] w = Arrays.copyOfRange(widths, i * SolidTile.TILE_SIZE_IN_ROM,
                    (i + 1) * SolidTile.TILE_SIZE_IN_ROM);
            solidTiles[i] = new SolidTile(i, h, w, angle);
        }

        LOG.fine("SolidTiles loaded: " + solidTileCount);
    }

    /**
     * Verifies that all chunk pattern references are within the loaded pattern count.
     * Logs warnings for any out-of-range references that would appear as blank tiles.
     */
    private void verifyChunkPatternReferences() {
        int outOfRange = 0;
        int maxRefIndex = 0;
        for (int i = 0; i < chunkCount; i++) {
            Chunk chunk = chunks[i];
            for (int py = 0; py < 2; py++) {
                for (int px = 0; px < 2; px++) {
                    PatternDesc pd = chunk.getPatternDesc(px, py);
                    int pidx = pd.getPatternIndex();
                    if (pidx > maxRefIndex) {
                        maxRefIndex = pidx;
                    }
                    if (pidx >= patternCount && pidx != 0) {
                        outOfRange++;
                    }
                }
            }
        }
        LOG.info("Chunk pattern verification: maxRefIndex=0x" + Integer.toHexString(maxRefIndex) +
                " patternCount=0x" + Integer.toHexString(patternCount) +
                " outOfRange=" + outOfRange);
        if (outOfRange > 0) {
            LOG.warning(outOfRange + " chunk pattern references exceed loaded pattern count!");
        }
    }
}
