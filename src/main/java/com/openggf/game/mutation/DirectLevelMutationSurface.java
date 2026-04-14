package com.openggf.game.mutation;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Pattern;

import java.util.Arrays;
import java.util.Objects;

public final class DirectLevelMutationSurface implements LevelMutationSurface {

    private final Level level;

    public DirectLevelMutationSurface(Level level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override
    public MutationEffects setPattern(int index, Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        level.ensurePatternCapacity(index + 1);
        Pattern target = level.getPattern(index);
        target.copyFrom(pattern);
        return MutationEffects.reuploadPattern(index);
    }

    @Override
    public MutationEffects restoreChunkState(int chunkIndex, int[] state) {
        Chunk chunk = level.getChunk(chunkIndex);
        chunk.restoreState(Arrays.copyOf(Objects.requireNonNull(state, "state"), state.length));
        return MutationEffects.redrawAllTilemaps();
    }

    @Override
    public MutationEffects restoreBlockState(int blockIndex, int[] state) {
        Block block = level.getBlock(blockIndex);
        block.restoreState(Arrays.copyOf(Objects.requireNonNull(state, "state"), state.length));
        return MutationEffects.redrawAllTilemaps();
    }

    @Override
    public MutationEffects setBlockInMap(int layer, int blockX, int blockY, int blockIndex) {
        Map map = Objects.requireNonNull(level.getMap(), "level.map");
        map.setValue(layer, blockX, blockY, (byte) blockIndex);
        return layer == 0 ? MutationEffects.foregroundRedraw() : MutationEffects.redrawAllTilemaps();
    }
}
