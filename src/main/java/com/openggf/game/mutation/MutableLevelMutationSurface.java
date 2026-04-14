package com.openggf.game.mutation;

import com.openggf.level.MutableLevel;
import com.openggf.level.Pattern;

import java.util.Objects;

public final class MutableLevelMutationSurface implements LevelMutationSurface {

    private final MutableLevel level;

    public MutableLevelMutationSurface(MutableLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override
    public MutationEffects setPattern(int index, Pattern pattern) {
        level.setPattern(index, Objects.requireNonNull(pattern, "pattern"));
        return MutationEffects.dirtyRegionProcessing();
    }

    @Override
    public MutationEffects restoreChunkState(int chunkIndex, int[] state) {
        level.restoreChunkState(chunkIndex, Objects.requireNonNull(state, "state"));
        return MutationEffects.dirtyRegionProcessing();
    }

    @Override
    public MutationEffects restoreBlockState(int blockIndex, int[] state) {
        level.restoreBlockState(blockIndex, Objects.requireNonNull(state, "state"));
        return MutationEffects.dirtyRegionProcessing();
    }

    @Override
    public MutationEffects setBlockInMap(int layer, int blockX, int blockY, int blockIndex) {
        level.setBlockInMap(layer, blockX, blockY, blockIndex);
        return MutationEffects.dirtyRegionProcessing();
    }
}
