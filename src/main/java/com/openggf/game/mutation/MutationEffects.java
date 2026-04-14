package com.openggf.game.mutation;

import java.util.BitSet;

public record MutationEffects(
        BitSet dirtyPatterns,
        boolean dirtyRegionProcessingRequired,
        boolean foregroundRedrawRequired,
        boolean allTilemapsRedrawRequired,
        boolean objectResyncRequired,
        boolean ringResyncRequired) {

    public static final MutationEffects NONE =
            new MutationEffects(new BitSet(), false, false, false, false, false);

    public MutationEffects {
        dirtyPatterns = dirtyPatterns == null ? new BitSet() : (BitSet) dirtyPatterns.clone();
    }

    public static MutationEffects dirtyRegionProcessing() {
        return new MutationEffects(new BitSet(), true, false, false, false, false);
    }

    public static MutationEffects foregroundRedraw() {
        return new MutationEffects(new BitSet(), false, true, false, false, false);
    }

    public static MutationEffects redrawAllTilemaps() {
        return new MutationEffects(new BitSet(), false, false, true, false, false);
    }

    public static MutationEffects redraw() {
        return foregroundRedraw();
    }

    public static MutationEffects objectResync() {
        return new MutationEffects(new BitSet(), false, false, false, true, false);
    }

    public static MutationEffects ringResync() {
        return new MutationEffects(new BitSet(), false, false, false, false, true);
    }

    public static MutationEffects reuploadPattern(int patternIndex) {
        BitSet dirtyPatterns = new BitSet();
        dirtyPatterns.set(patternIndex);
        return new MutationEffects(dirtyPatterns, false, false, false, false, false);
    }

    public boolean hasDirtyPatterns() {
        return !dirtyPatterns.isEmpty();
    }

    public boolean isEmpty() {
        return dirtyPatterns.isEmpty()
                && !dirtyRegionProcessingRequired
                && !foregroundRedrawRequired
                && !allTilemapsRedrawRequired
                && !objectResyncRequired
                && !ringResyncRequired;
    }
}
