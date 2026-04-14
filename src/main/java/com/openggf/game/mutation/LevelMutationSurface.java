package com.openggf.game.mutation;

import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.level.Pattern;

import java.util.Objects;

public interface LevelMutationSurface {

    static LevelMutationSurface forLevel(Level level) {
        Objects.requireNonNull(level, "level");
        if (level instanceof MutableLevel mutableLevel) {
            return new MutableLevelMutationSurface(mutableLevel);
        }
        return new DirectLevelMutationSurface(level);
    }

    MutationEffects setPattern(int index, Pattern pattern);

    MutationEffects restoreChunkState(int chunkIndex, int[] state);

    MutationEffects restoreBlockState(int blockIndex, int[] state);

    MutationEffects setBlockInMap(int layer, int blockX, int blockY, int blockIndex);

    default MutationEffects requestRedraw() {
        return MutationEffects.redraw();
    }

    default MutationEffects requestObjectResync() {
        return MutationEffects.objectResync();
    }

    default MutationEffects requestRingResync() {
        return MutationEffects.ringResync();
    }
}
