package com.openggf.game.mutation;

import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.level.Pattern;

import java.util.Objects;

/**
 * Abstraction over writable level data used by the mutation pipeline.
 *
 * <p>The surface hides whether the target level is a {@link MutableLevel} with
 * dirty-region tracking or a direct level structure that requires explicit
 * redraw/reupload effects.
 */
public interface LevelMutationSurface {

    /** Adapts the supplied level to the appropriate mutation surface implementation. */
    static LevelMutationSurface forLevel(Level level) {
        Objects.requireNonNull(level, "level");
        if (level instanceof MutableLevel mutableLevel) {
            return new MutableLevelMutationSurface(mutableLevel);
        }
        return new DirectLevelMutationSurface(level);
    }

    /** Replaces one pattern entry and returns the effects needed to reflect the change. */
    MutationEffects setPattern(int index, Pattern pattern);

    /** Restores one chunk descriptor/state entry from a previously captured snapshot. */
    MutationEffects restoreChunkState(int chunkIndex, int[] state);

    /** Restores one block descriptor/state entry from a previously captured snapshot. */
    MutationEffects restoreBlockState(int blockIndex, int[] state);

    /** Replaces a map block reference on the requested layer. */
    MutationEffects setBlockInMap(int layer, int blockX, int blockY, int blockIndex);

    /** Requests a foreground redraw without performing any direct data change. */
    default MutationEffects requestRedraw() {
        return MutationEffects.redraw();
    }

    /** Requests object spawn list resynchronization after layout changes. */
    default MutationEffects requestObjectResync() {
        return MutationEffects.objectResync();
    }

    /** Requests ring spawn list resynchronization after layout changes. */
    default MutationEffects requestRingResync() {
        return MutationEffects.ringResync();
    }
}
