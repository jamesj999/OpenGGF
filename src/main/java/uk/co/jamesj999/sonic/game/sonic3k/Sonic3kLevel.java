package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.*;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.resources.LevelResourcePlan;
import uk.co.jamesj999.sonic.level.resources.ResourceLoader;
import uk.co.jamesj999.sonic.level.rings.RingSpawn;
import uk.co.jamesj999.sonic.level.rings.RingSpriteSheet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Level implementation for Sonic 3 &amp; Knuckles.
 *
 * <p>Key differences from Sonic 2:
 * <ul>
 *   <li>Patterns use Kosinski Moduled (KosM) compression</li>
 *   <li>Layout is uncompressed: 0x1000 bytes with 8-byte header</li>
 *   <li>Collision indices are noninterleaved (primary 0x600 + secondary 0x600)</li>
 *   <li>LevelLoadBlock entries are 24 bytes with embedded PLC and palette indices</li>
 * </ul>
 */
public class Sonic3kLevel implements Level {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevel.class.getName());

    private static final int PALETTE_COUNT = 4;

    private Palette[] palettes;
    private Pattern[] patterns;
    private Chunk[] chunks;
    private Block[] blocks;
    private SolidTile[] solidTiles;
    private Map map;
    private final List<ObjectSpawn> objects;
    private final List<RingSpawn> rings;
    private RingSpriteSheet ringSpriteSheet;
    private final int zoneIndex;

    private int patternCount;
    private int chunkCount;
    private int blockCount;
    private int solidTileCount;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;

    /**
     * Creates an S3K level using a LevelResourcePlan for resource loading.
     *
     * @param rom                    The ROM to load from
     * @param zoneIndex              ROM zone ID (0=AIZ, 1=HCZ, etc.)
     * @param resourcePlan           Resource plan for patterns/blocks/chunks
     * @param primaryCollisionAddr   ROM address of primary collision index data
     * @param secondaryCollisionAddr ROM address of secondary collision index data
     * @param interleavedCollision   true if collision data is interleaved (SK zones),
     *                               false if non-interleaved (S3K zones: primary 0x600 + secondary 0x600)
     * @param layoutAddr             ROM address of uncompressed layout data (0x1000 bytes)
     * @param levelBoundariesAddr    ROM address of level boundaries (8 bytes)
     * @param characterPaletteAddr   ROM address of character palette
     * @param levelPaletteAddr       ROM address of level palette data
     */
    public Sonic3kLevel(Rom rom,
                        int zoneIndex,
                        LevelResourcePlan resourcePlan,
                        int primaryCollisionAddr,
                        int secondaryCollisionAddr,
                        boolean interleavedCollision,
                        int layoutAddr,
                        int levelBoundariesAddr,
                        int characterPaletteAddr,
                        int levelPaletteAddr) throws IOException {
        this.zoneIndex = zoneIndex;
        this.objects = Collections.emptyList();
        this.rings = Collections.emptyList();
        this.ringSpriteSheet = null;

        loadPalettes(rom, characterPaletteAddr, levelPaletteAddr);
        loadPatternsWithPlan(rom, resourcePlan);
        loadSolidTiles(rom);
        loadChunksWithCollision(rom, resourcePlan, primaryCollisionAddr, secondaryCollisionAddr,
                interleavedCollision);
        loadBlocksWithPlan(rom, resourcePlan);
        loadMap(rom, layoutAddr);
        loadBoundaries(rom, levelBoundariesAddr);
    }

    // ===== Level interface =====

    @Override
    public int getPaletteCount() { return PALETTE_COUNT; }

    @Override
    public Palette getPalette(int index) {
        if (index >= PALETTE_COUNT) throw new IllegalArgumentException("Invalid palette index: " + index);
        return palettes[index];
    }

    @Override
    public void setPalette(int index, Palette palette) {
        if (index >= 0 && index < PALETTE_COUNT && palette != null) {
            palettes[index] = palette;
        }
    }

    @Override
    public int getPatternCount() { return patternCount; }

    @Override
    public Pattern getPattern(int index) {
        if (index >= patternCount) throw new IllegalArgumentException("Invalid pattern index: " + index);
        return patterns[index];
    }

    @Override
    public void ensurePatternCapacity(int minCount) {
        if (minCount <= patternCount) return;
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
    public int getChunkCount() { return chunkCount; }

    @Override
    public Chunk getChunk(int index) {
        if (index >= chunkCount) throw new IllegalArgumentException("Invalid chunk index: " + index);
        return chunks[index];
    }

    @Override
    public int getBlockCount() { return blockCount; }

    @Override
    public Block getBlock(int index) {
        if (index >= blockCount) throw new IllegalArgumentException("Invalid block index: " + index);
        return blocks[index];
    }

    @Override
    public SolidTile getSolidTile(int index) {
        if (index >= solidTileCount) throw new IllegalArgumentException("Invalid solid tile index: " + index);
        return solidTiles[index];
    }

    @Override
    public Map getMap() { return map; }

    @Override
    public List<ObjectSpawn> getObjects() { return objects; }

    @Override
    public List<RingSpawn> getRings() { return rings; }

    @Override
    public RingSpriteSheet getRingSpriteSheet() { return ringSpriteSheet; }

    @Override
    public int getMinX() { return minX; }

    @Override
    public int getMaxX() { return maxX; }

    @Override
    public int getMinY() { return minY; }

    @Override
    public int getMaxY() { return maxY; }

    @Override
    public int getZoneIndex() { return zoneIndex; }

    // ===== Loading methods =====

    private void loadPalettes(Rom rom, int characterPaletteAddr, int levelPaletteAddr) throws IOException {
        palettes = new Palette[PALETTE_COUNT];
        GraphicsManager graphicsMan = GraphicsManager.getInstance();

        // Palette 0: character palette (Sonic)
        palettes[0] = new Palette();
        if (characterPaletteAddr > 0) {
            byte[] charPalData = rom.readBytes(characterPaletteAddr, Palette.PALETTE_SIZE_IN_ROM);
            palettes[0].fromSegaFormat(charPalData);
        }

        // Palettes 1-3: level palettes
        // S3K level palettes are 3 palette lines (48 bytes = 3 * 16 colors * 2 bytes)
        if (levelPaletteAddr > 0) {
            int palSize = 3 * Palette.PALETTE_SIZE_IN_ROM;
            byte[] levelPalData = rom.readBytes(levelPaletteAddr, palSize);
            for (int i = 0; i < 3; i++) {
                palettes[i + 1] = new Palette();
                int start = i * Palette.PALETTE_SIZE_IN_ROM;
                int end = start + Palette.PALETTE_SIZE_IN_ROM;
                if (end <= levelPalData.length) {
                    palettes[i + 1].fromSegaFormat(Arrays.copyOfRange(levelPalData, start, end));
                }
            }
        } else {
            for (int i = 1; i < PALETTE_COUNT; i++) {
                palettes[i] = new Palette();
            }
        }

        if (graphicsMan.isGlInitialized()) {
            for (int i = 0; i < palettes.length; i++) {
                graphicsMan.cachePaletteTexture(palettes[i], i);
            }
        }
    }

    private void loadPatternsWithPlan(Rom rom, LevelResourcePlan plan) throws IOException {
        GraphicsManager graphicsMan = GraphicsManager.getInstance();
        ResourceLoader loader = new ResourceLoader(rom);

        byte[] result = loader.loadWithOverlays(plan.getPatternOps(), 0x10000);

        patternCount = result.length / Pattern.PATTERN_SIZE_IN_ROM;
        if (result.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent S3K pattern data");
        }

        patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            byte[] subArray = Arrays.copyOfRange(result, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(subArray);
            if (graphicsMan.isGlInitialized()) {
                graphicsMan.cachePatternTexture(patterns[i], i);
            }
        }

        LOG.info("S3K pattern count: " + patternCount + " (" + result.length + " bytes)");
    }

    private void loadSolidTiles(Rom rom) throws IOException {
        int heightsAddr = Sonic3kConstants.SOLID_TILE_VERTICAL_MAP_ADDR;
        int widthsAddr = Sonic3kConstants.SOLID_TILE_HORIZONTAL_MAP_ADDR;
        int anglesAddr = Sonic3kConstants.SOLID_TILE_ANGLE_ADDR;

        if (heightsAddr == 0 || widthsAddr == 0 || anglesAddr == 0) {
            LOG.warning("S3K collision addresses not set - collision will not work");
            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            return;
        }

        solidTileCount = (Sonic3kConstants.SOLID_TILE_MAP_SIZE + 1) / SolidTile.TILE_SIZE_IN_ROM;
        byte[] heightsBuffer = rom.readBytes(heightsAddr, Sonic3kConstants.SOLID_TILE_MAP_SIZE);
        byte[] widthsBuffer = rom.readBytes(widthsAddr, Sonic3kConstants.SOLID_TILE_MAP_SIZE);

        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) {
            byte angle = rom.readByte(anglesAddr + i);
            byte[] heights = Arrays.copyOfRange(heightsBuffer,
                    i * SolidTile.TILE_SIZE_IN_ROM, (i + 1) * SolidTile.TILE_SIZE_IN_ROM);
            byte[] widths = Arrays.copyOfRange(widthsBuffer,
                    i * SolidTile.TILE_SIZE_IN_ROM, (i + 1) * SolidTile.TILE_SIZE_IN_ROM);
            solidTiles[i] = new SolidTile(i, heights, widths, angle);
        }

        LOG.fine("S3K SolidTiles loaded: " + solidTileCount);
    }

    /**
     * Loads chunks (16x16 tiles) from the resource plan and collision indices from ROM.
     *
     * <p>S3K collision data is uncompressed. Two formats exist:
     * <ul>
     *   <li>Non-interleaved (S3K zones): primary 0x600 bytes at primaryAddr,
     *       secondary 0x600 bytes at secondaryAddr</li>
     *   <li>Interleaved (SK zones): primary and secondary bytes alternate,
     *       primaryAddr points to even bytes, secondaryAddr = primaryAddr + 1</li>
     * </ul>
     */
    private void loadChunksWithCollision(Rom rom, LevelResourcePlan plan,
                                         int primaryCollisionAddr,
                                         int secondaryCollisionAddr,
                                         boolean interleaved) throws IOException {
        ResourceLoader loader = new ResourceLoader(rom);

        byte[] chunkBuffer = loader.loadWithOverlays(plan.getChunkOps(), 0x10000);

        chunkCount = chunkBuffer.length / Chunk.CHUNK_SIZE_IN_ROM;
        if (chunkBuffer.length % Chunk.CHUNK_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent S3K chunk data");
        }

        // Load collision indices directly from ROM (uncompressed)
        byte[] primaryCollision;
        byte[] secondaryCollision;

        if (interleaved && primaryCollisionAddr > 0) {
            // Interleaved: read 0xC00 bytes from base, de-interleave into primary/secondary
            byte[] interleavedData = rom.readBytes(primaryCollisionAddr,
                    Sonic3kConstants.COLLISION_INDEX_SIZE * 2);
            primaryCollision = new byte[Sonic3kConstants.COLLISION_INDEX_SIZE];
            secondaryCollision = new byte[Sonic3kConstants.COLLISION_INDEX_SIZE];
            for (int i = 0; i < Sonic3kConstants.COLLISION_INDEX_SIZE; i++) {
                primaryCollision[i] = interleavedData[i * 2];
                secondaryCollision[i] = interleavedData[i * 2 + 1];
            }
        } else {
            // Non-interleaved: separate blocks
            if (primaryCollisionAddr > 0) {
                primaryCollision = rom.readBytes(primaryCollisionAddr, Sonic3kConstants.COLLISION_INDEX_SIZE);
            } else {
                primaryCollision = new byte[0];
            }
            if (secondaryCollisionAddr > 0) {
                secondaryCollision = rom.readBytes(secondaryCollisionAddr, Sonic3kConstants.COLLISION_INDEX_SIZE);
            } else {
                secondaryCollision = new byte[0];
            }
        }

        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            chunks[i] = new Chunk();
            byte[] subArray = Arrays.copyOfRange(chunkBuffer, i * Chunk.CHUNK_SIZE_IN_ROM,
                    (i + 1) * Chunk.CHUNK_SIZE_IN_ROM);
            int solidIndex = (i < primaryCollision.length) ? Byte.toUnsignedInt(primaryCollision[i]) : 0;
            int altSolidIndex = (i < secondaryCollision.length) ? Byte.toUnsignedInt(secondaryCollision[i]) : 0;
            chunks[i].fromSegaFormat(subArray, solidIndex, altSolidIndex);
        }

        LOG.info("S3K chunk count: " + chunkCount + " (" + chunkBuffer.length + " bytes)");
    }

    private void loadBlocksWithPlan(Rom rom, LevelResourcePlan plan) throws IOException {
        ResourceLoader loader = new ResourceLoader(rom);

        byte[] blockBuffer = loader.loadWithOverlaysAligned(
                plan.getBlockOps(), 0x10000, LevelConstants.BLOCK_SIZE_IN_ROM);

        blockCount = blockBuffer.length / LevelConstants.BLOCK_SIZE_IN_ROM;

        blocks = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) {
            blocks[i] = new Block();
            byte[] subArray = Arrays.copyOfRange(blockBuffer, i * LevelConstants.BLOCK_SIZE_IN_ROM,
                    (i + 1) * LevelConstants.BLOCK_SIZE_IN_ROM);
            blocks[i].fromSegaFormat(subArray);
        }

        // Sanitize Block 0 (empty)
        if (blockCount > 0) {
            blocks[0] = new Block();
        }

        LOG.info("S3K block count: " + blockCount + " (" + blockBuffer.length + " bytes)");
    }

    /**
     * Loads the S3K level layout.
     *
     * <p>S3K layout format (0x1000 bytes):
     * <ul>
     *   <li>Header (8 bytes): FG cols/row (word), BG cols/row (word), FG rows (word), BG rows (word)</li>
     *   <li>Data (0xFF8 bytes): row pointer offsets + chunk index bytes</li>
     * </ul>
     *
     * <p>The layout is loaded into a 2-layer Map matching S2's format (128 wide, 16 tall)
     * for compatibility with the engine's rendering and collision systems.
     */
    private void loadMap(Rom rom, int layoutAddr) throws IOException {
        if (layoutAddr == 0) {
            LOG.warning("S3K layout address is 0 - using empty map");
            map = new Map(Sonic3kConstants.MAP_LAYERS, Sonic3kConstants.MAP_WIDTH, Sonic3kConstants.MAP_HEIGHT,
                    new byte[Sonic3kConstants.MAP_LAYERS * Sonic3kConstants.MAP_WIDTH * Sonic3kConstants.MAP_HEIGHT]);
            return;
        }

        byte[] layoutData = rom.readBytes(layoutAddr, Sonic3kConstants.LEVEL_LAYOUT_TOTAL_SIZE);

        // Parse header
        int fgColsPerRow = ((layoutData[0] & 0xFF) << 8) | (layoutData[1] & 0xFF);
        int bgColsPerRow = ((layoutData[2] & 0xFF) << 8) | (layoutData[3] & 0xFF);
        int fgRows = ((layoutData[4] & 0xFF) << 8) | (layoutData[5] & 0xFF);
        int bgRows = ((layoutData[6] & 0xFF) << 8) | (layoutData[7] & 0xFF);

        LOG.info(String.format("S3K layout header: FG %dx%d, BG %dx%d",
                fgColsPerRow, fgRows, bgColsPerRow, bgRows));

        int mapWidth = Sonic3kConstants.MAP_WIDTH;
        int mapHeight = Sonic3kConstants.MAP_HEIGHT;
        byte[] mapData = new byte[Sonic3kConstants.MAP_LAYERS * mapWidth * mapHeight];

        // Parse FG layout (layer 0)
        parseLayoutLayer(layoutData, Sonic3kConstants.LEVEL_LAYOUT_HEADER_SIZE,
                fgColsPerRow, fgRows, mapData, 0, mapWidth, mapHeight);

        // Parse BG layout (layer 1)
        // BG row pointers start after FG row pointers in the layout data
        int bgOffset = Sonic3kConstants.LEVEL_LAYOUT_HEADER_SIZE + fgRows * 2;
        parseLayoutLayer(layoutData, bgOffset,
                bgColsPerRow, bgRows, mapData, mapWidth * mapHeight, mapWidth, mapHeight);

        map = new Map(Sonic3kConstants.MAP_LAYERS, mapWidth, mapHeight, mapData);
        LOG.info("S3K map loaded successfully");
    }

    /**
     * Parses a single layout layer (FG or BG) from S3K layout data.
     *
     * <p>Each layer has a table of word-sized row pointers (relative to layout start + 8),
     * and each row pointer leads to chunk index bytes for that row.
     */
    private void parseLayoutLayer(byte[] layoutData, int rowPtrOffset,
                                  int colsPerRow, int rows,
                                  byte[] mapData, int mapDataOffset,
                                  int mapWidth, int mapHeight) {
        int dataBase = Sonic3kConstants.LEVEL_LAYOUT_HEADER_SIZE;

        for (int row = 0; row < Math.min(rows, mapHeight); row++) {
            // Read word-sized row pointer
            int ptrPos = rowPtrOffset + row * 2;
            if (ptrPos + 1 >= layoutData.length) break;

            int rowOffset = ((layoutData[ptrPos] & 0xFF) << 8) | (layoutData[ptrPos + 1] & 0xFF);
            int rowDataAddr = dataBase + rowOffset;

            for (int col = 0; col < Math.min(colsPerRow, mapWidth); col++) {
                int srcIdx = rowDataAddr + col;
                if (srcIdx >= layoutData.length) break;
                int destIdx = mapDataOffset + row * mapWidth + col;
                if (destIdx < mapData.length) {
                    mapData[destIdx] = layoutData[srcIdx];
                }
            }
        }
    }

    private void loadBoundaries(Rom rom, int levelBoundariesAddr) throws IOException {
        if (levelBoundariesAddr == 0) {
            minX = 0;
            maxX = 0x6000;
            minY = 0;
            maxY = 0x0800;
            return;
        }
        this.minX = rom.read16BitAddr(levelBoundariesAddr);
        this.maxX = rom.read16BitAddr(levelBoundariesAddr + 2);
        this.minY = (short) rom.read16BitAddr(levelBoundariesAddr + 4);
        this.maxY = (short) rom.read16BitAddr(levelBoundariesAddr + 6);
    }
}
