package com.openggf.game.rewind.snapshot;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.util.List;

/**
 * Composite snapshot of ObjectManager state — slot inventory + per-slot captured
 * object state + scalar counters.
 *
 * <p>v1 stores live {@link ObjectSpawn} refs (in-memory {@code KeyframeStore});
 * v2 will need a stable ID scheme for disk-spill serialization, because live
 * Java references will not survive process boundaries or save-game persistence.
 *
 * <p>The snapshot covers:
 * <ul>
 *   <li>The full {@code usedSlots} BitSet as a {@code long[]} (serialization-friendly).</li>
 *   <li>Per-active-slot entries: spawn identity + captured per-instance state.</li>
 *   <li>Scalar counters: {@code frameCounter}, {@code vblaCounter},
 *       {@code currentExecSlot}, {@code peakSlotCount}.</li>
 *   <li>Render-cache dirty flag ({@code bucketsDirty}).</li>
 *   <li>Reserved child-slot mapping entries ({@code reservedChildSlots}).</li>
 * </ul>
 */
public record ObjectManagerSnapshot(
        long[] usedSlotsBits,
        List<PerSlotEntry> slots,
        int frameCounter,
        int vblaCounter,
        int currentExecSlot,
        int peakSlotCount,
        boolean bucketsDirty,
        List<ChildSpawnEntry> childSpawns
) {

    /**
     * Snapshot of one active slot.
     *
     * @param slotIndex slot index in the Object Status Table (0-based, game-specific range)
     * @param spawn     the {@link ObjectSpawn} that produced this instance; live ref, stable
     *                  in-memory across rewind (v1 in-memory KeyframeStore only)
     * @param state     captured per-instance state from
     *                  {@link com.openggf.level.objects.AbstractObjectInstance#captureRewindState()}
     */
    public record PerSlotEntry(
            int slotIndex,
            ObjectSpawn spawn,
            PerObjectRewindSnapshot state
    ) {}

    /**
     * One entry in the reserved-child-slot mapping.
     *
     * <p>The ObjectManager keeps a {@code Map<ObjectSpawn, int[]>} of pre-allocated
     * child slots (used for objects like S1 rings that allocate sibling SST slots before
     * ObjPosLoad). This record captures one parent→slot-array pair.
     *
     * @param parentSpawn  the parent object's spawn (live ref, stable in-memory)
     * @param reservedSlots the slot indices that were pre-allocated for this parent
     */
    public record ChildSpawnEntry(
            ObjectSpawn parentSpawn,
            int[] reservedSlots
    ) {}
}
