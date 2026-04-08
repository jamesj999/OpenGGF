package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.*;

/**
 * A mutable level that deep-copies all data from a ROM-loaded level,
 * provides mutation methods, and tracks dirty regions via BitSet.
 * <p>
 * Used by the level editor (Phase 4) to modify tiles, blocks, chunks,
 * collision, object placements, and ring placements. Subsystems consume
 * dirty regions each frame via {@code LevelManager.processDirtyRegions()}.
 */
public class MutableLevel extends AbstractLevel {

    // Dirty tracking
    private final BitSet dirtyPatterns;
    private final BitSet dirtyChunks;
    private final BitSet dirtyBlocks;
    private final BitSet dirtyMapCells;
    private final BitSet dirtySolidTiles;
    private boolean objectsDirty;
    private boolean ringsDirty;

    // Reverse lookup tables for transitive dirtying
    private final java.util.Map<Integer, Set<Integer>> chunkToBlocks;
    private final java.util.Map<Integer, Set<Integer>> blockToMapCells;

    // Mutable spawn lists (override the immutable ones from AbstractLevel)
    private final ArrayList<ObjectSpawn> mutableObjects;
    private final ArrayList<RingSpawn> mutableRings;

    // Game-specific overrides captured from source level
    private final int blockPixelSize;
    private final int chunksPerBlockSide;
    private final Level sourceLevel;  // retained for resolveCollisionBlockIndex delegation

    private MutableLevel(Level sourceLevel, int zoneIndex,
                         Pattern[] patterns, int patternCount,
                         Chunk[] chunks, int chunkCount,
                         Block[] blocks, int blockCount,
                         SolidTile[] solidTiles, int solidTileCount,
                         Map map,
                         Palette[] palettes,
                         ArrayList<ObjectSpawn> mutableObjects,
                         ArrayList<RingSpawn> mutableRings,
                         int minX, int maxX, int minY, int maxY,
                         java.util.Map<Integer, Set<Integer>> chunkToBlocks,
                         java.util.Map<Integer, Set<Integer>> blockToMapCells) {
        super(zoneIndex);
        this.sourceLevel = sourceLevel;
        this.blockPixelSize = sourceLevel.getBlockPixelSize();
        this.chunksPerBlockSide = sourceLevel.getChunksPerBlockSide();
        this.ringSpriteSheet = sourceLevel.getRingSpriteSheet();
        this.patterns = patterns;
        this.patternCount = patternCount;
        this.chunks = chunks;
        this.chunkCount = chunkCount;
        this.blocks = blocks;
        this.blockCount = blockCount;
        this.solidTiles = solidTiles;
        this.solidTileCount = solidTileCount;
        this.map = map;
        this.palettes = palettes;
        this.mutableObjects = mutableObjects;
        this.mutableRings = mutableRings;
        this.objects = mutableObjects;
        this.rings = mutableRings;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.chunkToBlocks = chunkToBlocks;
        this.blockToMapCells = blockToMapCells;

        // Init dirty tracking
        this.dirtyPatterns = new BitSet(patternCount);
        this.dirtyChunks = new BitSet(chunkCount);
        this.dirtyBlocks = new BitSet(blockCount);
        this.dirtyMapCells = new BitSet(
                map.getLayerCount() * map.getWidth() * map.getHeight());
        this.dirtySolidTiles = new BitSet(solidTileCount);
    }

    /**
     * Creates a MutableLevel by deep-copying all data from the source level.
     * The source level is not modified.
     */
    public static MutableLevel snapshot(Level source) {
        // Deep copy patterns
        int patCount = source.getPatternCount();
        Pattern[] patterns = new Pattern[patCount];
        for (int i = 0; i < patCount; i++) {
            patterns[i] = new Pattern();
            patterns[i].copyFrom(source.getPattern(i));
        }

        // Deep copy chunks via saveState/restoreState
        int chkCount = source.getChunkCount();
        Chunk[] chunks = new Chunk[chkCount];
        for (int i = 0; i < chkCount; i++) {
            chunks[i] = new Chunk();
            chunks[i].restoreState(source.getChunk(i).saveState());
        }

        // Deep copy blocks via saveState/restoreState
        int blkCount = source.getBlockCount();
        Block[] blocks = new Block[blkCount];
        for (int i = 0; i < blkCount; i++) {
            Block src = source.getBlock(i);
            blocks[i] = new Block(src.getGridSide());
            blocks[i].restoreState(src.saveState());
        }

        // Deep copy solid tiles (copy height/width arrays + angle)
        int stCount = source.getSolidTileCount();
        SolidTile[] solidTiles = new SolidTile[stCount];
        for (int i = 0; i < stCount; i++) {
            SolidTile src = source.getSolidTile(i);
            solidTiles[i] = new SolidTile(
                    i,
                    Arrays.copyOf(src.heights, src.heights.length),
                    Arrays.copyOf(src.widths, src.widths.length),
                    src.getAngle());
        }

        // Deep copy map (Map constructor copies the data array)
        Map srcMap = source.getMap();
        Map map = new Map(srcMap.getLayerCount(), srcMap.getWidth(),
                srcMap.getHeight(), srcMap.getData());

        // Deep copy palettes
        int palCount = source.getPaletteCount();
        Palette[] palettes = new Palette[palCount];
        for (int i = 0; i < palCount; i++) {
            palettes[i] = source.getPalette(i).deepCopy();
        }

        // Mutable spawn lists
        ArrayList<ObjectSpawn> mutableObjects = new ArrayList<>(source.getObjects());
        ArrayList<RingSpawn> mutableRings = new ArrayList<>(source.getRings());

        // Build reverse lookups
        java.util.Map<Integer, Set<Integer>> chunkToBlocks = buildChunkToBlocksMap(blocks);
        java.util.Map<Integer, Set<Integer>> blockToMapCells = buildBlockToMapCellsMap(map);

        return new MutableLevel(
                source, source.getZoneIndex(),
                patterns, patCount,
                chunks, chkCount,
                blocks, blkCount,
                solidTiles, stCount,
                map, palettes,
                mutableObjects, mutableRings,
                source.getMinX(), source.getMaxX(),
                source.getMinY(), source.getMaxY(),
                chunkToBlocks, blockToMapCells);
    }

    // ===== Game-specific overrides =====

    @Override
    public int getBlockPixelSize() {
        return blockPixelSize;
    }

    @Override
    public int getChunksPerBlockSide() {
        return chunksPerBlockSide;
    }

    @Override
    public int resolveCollisionBlockIndex(int blockIndex, int mapX, int mapY) {
        return sourceLevel.resolveCollisionBlockIndex(blockIndex, mapX, mapY);
    }

    // ===== Mutation methods (each marks dirty) =====

    public void setPattern(int index, Pattern pattern) {
        patterns[index] = pattern;
        dirtyPatterns.set(index);
    }

    public void setPatternDescInChunk(int chunkIndex, int px, int py, PatternDesc desc) {
        chunks[chunkIndex].setPatternDesc(px, py, desc);
        dirtyChunks.set(chunkIndex);
        // Transitive: dirty all blocks referencing this chunk
        Set<Integer> affectedBlocks = chunkToBlocks.getOrDefault(chunkIndex, Set.of());
        for (int blockIdx : affectedBlocks) {
            dirtyBlocks.set(blockIdx);
            dirtyTransitiveMapCells(blockIdx);
        }
    }

    public void setChunkInBlock(int blockIndex, int cx, int cy, ChunkDesc desc) {
        int oldChunkIndex = blocks[blockIndex].getChunkDesc(cx, cy).getChunkIndex();
        blocks[blockIndex].setChunkDesc(cx, cy, desc);
        updateChunkToBlocksLookup(blockIndex, oldChunkIndex, desc.getChunkIndex());
        dirtyBlocks.set(blockIndex);
        dirtyTransitiveMapCells(blockIndex);
    }

    public void restoreBlockState(int blockIndex, int[] state) {
        Block block = blocks[blockIndex];
        if (state.length != block.saveState().length) {
            throw new IllegalArgumentException("Invalid block state length for block " + blockIndex);
        }

        int side = block.getGridSide();
        for (int i = 0; i < state.length; i++) {
            int x = i % side;
            int y = i / side;
            if (block.getChunkDesc(x, y).get() != state[i]) {
                setChunkInBlock(blockIndex, x, y, new ChunkDesc(state[i]));
            }
        }
    }

    public void setBlockInMap(int layer, int bx, int by, int blockIndex) {
        int oldBlockIndex = map.getValue(layer, bx, by) & 0xFF;
        map.setValue(layer, bx, by, (byte) blockIndex);
        int cellIdx = linearizeMapCell(layer, bx, by);
        updateBlockToMapCellsLookup(cellIdx, oldBlockIndex, blockIndex);
        dirtyMapCells.set(cellIdx);
    }

    public void restoreChunkState(int chunkIndex, int[] state) {
        if (!Arrays.equals(chunks[chunkIndex].saveState(), state)) {
            chunks[chunkIndex].restoreState(Arrays.copyOf(state, state.length));
            dirtyChunks.set(chunkIndex);
            Set<Integer> affectedBlocks = chunkToBlocks.getOrDefault(chunkIndex, Set.of());
            for (int blockIdx : affectedBlocks) {
                dirtyBlocks.set(blockIdx);
                dirtyTransitiveMapCells(blockIdx);
            }
        }
    }

    public void setSolidTile(int index, SolidTile tile) {
        solidTiles[index] = tile;
        dirtySolidTiles.set(index);
    }

    public void addObjectSpawn(ObjectSpawn spawn) {
        mutableObjects.add(spawn);
        objectsDirty = true;
    }

    public void removeObjectSpawn(ObjectSpawn spawn) {
        mutableObjects.remove(spawn);
        objectsDirty = true;
    }

    public void moveObjectSpawn(ObjectSpawn oldSpawn, ObjectSpawn newSpawn) {
        int idx = mutableObjects.indexOf(oldSpawn);
        if (idx >= 0) {
            mutableObjects.set(idx, newSpawn);
            objectsDirty = true;
        }
    }

    public void addRingSpawn(RingSpawn spawn) {
        mutableRings.add(spawn);
        ringsDirty = true;
    }

    public void removeRingSpawn(RingSpawn spawn) {
        mutableRings.remove(spawn);
        ringsDirty = true;
    }

    // ===== Dirty consumption (read-once) =====

    public BitSet consumeDirtyPatterns() {
        BitSet copy = (BitSet) dirtyPatterns.clone();
        dirtyPatterns.clear();
        return copy;
    }

    public BitSet consumeDirtyChunks() {
        BitSet copy = (BitSet) dirtyChunks.clone();
        dirtyChunks.clear();
        return copy;
    }

    public BitSet consumeDirtyBlocks() {
        BitSet copy = (BitSet) dirtyBlocks.clone();
        dirtyBlocks.clear();
        return copy;
    }

    /**
     * Returns and clears dirty map cells. Cell indices are linearized as:
     * {@code layer * width * height + y * width + x}.
     * Use {@link #delinearizeMapCell(int)} to recover (layer, x, y).
     */
    public BitSet consumeDirtyMapCells() {
        BitSet copy = (BitSet) dirtyMapCells.clone();
        dirtyMapCells.clear();
        return copy;
    }

    public BitSet consumeDirtySolidTiles() {
        BitSet copy = (BitSet) dirtySolidTiles.clone();
        dirtySolidTiles.clear();
        return copy;
    }

    public boolean consumeObjectsDirty() {
        boolean was = objectsDirty;
        objectsDirty = false;
        return was;
    }

    public boolean consumeRingsDirty() {
        boolean was = ringsDirty;
        ringsDirty = false;
        return was;
    }

    // ===== Helpers =====

    /**
     * Recovers (layer, x, y) from a linearized map cell index.
     * Linearization: {@code layer * width * height + y * width + x}.
     *
     * @return int[3] = {layer, x, y}
     */
    public int[] delinearizeMapCell(int cellIdx) {
        int w = map.getWidth();
        int h = map.getHeight();
        int layerSize = w * h;
        int layer = cellIdx / layerSize;
        int remainder = cellIdx % layerSize;
        int y = remainder / w;
        int x = remainder % w;
        return new int[] { layer, x, y };
    }

    public boolean isChunkReferencedInBlocks(int chunkIndex) {
        return !chunkToBlocks.getOrDefault(chunkIndex, Set.of()).isEmpty();
    }

    public boolean isBlockReferencedInMap(int blockIndex) {
        return !blockToMapCells.getOrDefault(blockIndex, Set.of()).isEmpty();
    }

    private void dirtyTransitiveMapCells(int blockIndex) {
        Set<Integer> cells = blockToMapCells.getOrDefault(blockIndex, Set.of());
        for (int cellIdx : cells) {
            dirtyMapCells.set(cellIdx);
        }
    }

    private int linearizeMapCell(int layer, int x, int y) {
        return layer * map.getWidth() * map.getHeight() + y * map.getWidth() + x;
    }

    private void updateBlockToMapCellsLookup(int cellIdx, int oldBlockIndex, int newBlockIndex) {
        if (oldBlockIndex == newBlockIndex) {
            return;
        }

        removeLookupMember(blockToMapCells, oldBlockIndex, cellIdx);
        blockToMapCells.computeIfAbsent(newBlockIndex, ignored -> new HashSet<>()).add(cellIdx);
    }

    private void updateChunkToBlocksLookup(int blockIndex, int oldChunkIndex, int newChunkIndex) {
        if (oldChunkIndex == newChunkIndex) {
            return;
        }

        if (!blockStillReferencesChunk(blockIndex, oldChunkIndex)) {
            removeLookupMember(chunkToBlocks, oldChunkIndex, blockIndex);
        }
        chunkToBlocks.computeIfAbsent(newChunkIndex, ignored -> new HashSet<>()).add(blockIndex);
    }

    private boolean blockStillReferencesChunk(int blockIndex, int chunkIndex) {
        Block block = blocks[blockIndex];
        int side = block.getGridSide();
        for (int cy = 0; cy < side; cy++) {
            for (int cx = 0; cx < side; cx++) {
                if (block.getChunkDesc(cx, cy).getChunkIndex() == chunkIndex) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeLookupMember(java.util.Map<Integer, Set<Integer>> lookup, int key, int member) {
        Set<Integer> members = lookup.get(key);
        if (members == null) {
            return;
        }
        members.remove(member);
        if (members.isEmpty()) {
            lookup.remove(key);
        }
    }

    /**
     * Builds a reverse lookup: chunk index -> set of block indices that reference it.
     */
    static java.util.Map<Integer, Set<Integer>> buildChunkToBlocksMap(Block[] blocks) {
        java.util.Map<Integer, Set<Integer>> map = new HashMap<>();
        for (int bi = 0; bi < blocks.length; bi++) {
            Block block = blocks[bi];
            int side = block.getGridSide();
            for (int cy = 0; cy < side; cy++) {
                for (int cx = 0; cx < side; cx++) {
                    int chunkIdx = block.getChunkDesc(cx, cy).getChunkIndex();
                    map.computeIfAbsent(chunkIdx, k -> new HashSet<>()).add(bi);
                }
            }
        }
        return map;
    }

    /**
     * Builds a reverse lookup: block index -> set of linearized map cell indices.
     */
    static java.util.Map<Integer, Set<Integer>> buildBlockToMapCellsMap(Map levelMap) {
        java.util.Map<Integer, Set<Integer>> result = new HashMap<>();
        int layers = levelMap.getLayerCount();
        int w = levelMap.getWidth();
        int h = levelMap.getHeight();
        for (int layer = 0; layer < layers; layer++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int blockIdx = levelMap.getValue(layer, x, y) & 0xFF;
                    int cellIdx = layer * w * h + y * w + x;
                    result.computeIfAbsent(blockIdx, k -> new HashSet<>()).add(cellIdx);
                }
            }
        }
        return result;
    }
}
