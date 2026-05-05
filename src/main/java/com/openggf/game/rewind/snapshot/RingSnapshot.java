package com.openggf.game.rewind.snapshot;

import java.util.BitSet;

/**
 * Snapshot of {@link com.openggf.level.rings.RingManager} per-frame dynamic state.
 *
 * <p>Covers:
 * <ul>
 *   <li>Ring placement: which rings have been collected, sparkle timers, and the
 *       camera-window cursor so the window can be restored without a full rescan.</li>
 *   <li>Lost-ring pool: per-ring physics state and the shared spill animation
 *       counters.</li>
 *   <li>Attracted rings: the short-lived attractor array used when a shield draws
 *       rings toward the player.</li>
 * </ul>
 *
 * <p>Excluded: spawn list, sprite-sheet/pattern references, and any rendering-only
 * data — all are invariant after level load.
 */
public record RingSnapshot(
        // --- RingPlacement ---
        BitSet collected,
        int[] sparkleStartFrames,
        int placementCursorIndex,
        int placementLastCameraX,

        // --- LostRingPool ---
        int lostRingActiveCount,
        int spillAnimCounter,
        int spillAnimAccum,
        int spillAnimFrame,
        int lostRingFrameCounter,
        LostRingEntry[] lostRings,

        // --- AttractedRings ---
        AttractedRingEntry[] attractedRings
) {
    /**
     * Snapshot of a single {@link com.openggf.level.rings.LostRing} instance.
     * All 32 pool slots are captured; inactive entries have {@code active=false}.
     */
    public record LostRingEntry(
            boolean active,
            int xSubpixel,
            int ySubpixel,
            int xVel,
            int yVel,
            int lifetime,
            boolean collected,
            int sparkleStartFrame,
            int phaseOffset,
            int slotIndex
    ) {}

    /**
     * Snapshot of one attracted-ring slot in the attractor array.
     */
    public record AttractedRingEntry(
            boolean active,
            int sourceIndex,
            int x,
            int y,
            int xSub,
            int ySub,
            int xVel,
            int yVel
    ) {}
}
