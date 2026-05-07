package com.openggf.game.rewind.snapshot;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.util.Arrays;
import java.util.List;

/**
 * Composite snapshot of ObjectManager state — slot inventory + per-slot captured
 * object state, dynamic children, placement cursors, and
 * scalar counters.
 *
 * <p>v1 stores live {@link ObjectSpawn} refs (in-memory {@code KeyframeStore});
 * v2 will need a stable ID scheme for disk-spill serialization, because live
 * Java references will not survive process boundaries or save-game persistence.
 *
 * <p>The snapshot covers:
 * <ul>
 *   <li>The live {@code usedSlots} BitSet as a {@code long[]} (serialization-friendly).
 *       Includes bits for non-codec transient dynamics so the allocator's view at restore
 *       matches the reference run at the rewind point; subsequent slot leaks for those
 *       phantom occupants are addressed by adding rewind codecs for the transient classes.</li>
 *   <li>Per-active-slot entries: spawn identity + captured per-instance state.</li>
 *   <li>Scalar counters: {@code frameCounter}, {@code vblaCounter},
 *       {@code currentExecSlot}, {@code peakSlotCount}.</li>
 *   <li>Render-cache dirty flag ({@code bucketsDirty}).</li>
 *   <li>Reserved child-slot mapping entries ({@code childSpawns}).</li>
 *   <li>Restorable dynamic object entries such as badnik projectiles and Buzzer flame children.</li>
 *   <li>Placement cursor/window state needed by the next replayed frame.</li>
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
        List<ChildSpawnEntry> childSpawns,
        List<DynamicObjectEntry> dynamicObjects,
        PlacementSnapshot placement,
        List<SolidContactRidingEntry> solidContactRiding
) {
    public ObjectManagerSnapshot {
        usedSlotsBits = usedSlotsBits == null ? new long[0] : Arrays.copyOf(usedSlotsBits, usedSlotsBits.length);
        slots = List.copyOf(slots);
        childSpawns = List.copyOf(childSpawns);
        dynamicObjects = List.copyOf(dynamicObjects);
        solidContactRiding = solidContactRiding == null ? List.of() : List.copyOf(solidContactRiding);
    }

    public ObjectManagerSnapshot(
            long[] usedSlotsBits,
            List<PerSlotEntry> slots,
            int frameCounter,
            int vblaCounter,
            int currentExecSlot,
            int peakSlotCount,
            boolean bucketsDirty,
            List<ChildSpawnEntry> childSpawns,
            List<DynamicObjectEntry> dynamicObjects,
            PlacementSnapshot placement
    ) {
        this(
                usedSlotsBits, slots,
                frameCounter, vblaCounter, currentExecSlot, peakSlotCount,
                bucketsDirty, childSpawns, dynamicObjects, placement,
                List.of()
        );
    }

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
    ) {
        public ChildSpawnEntry {
            reservedSlots = reservedSlots == null ? new int[0] : Arrays.copyOf(reservedSlots, reservedSlots.length);
        }
    }

    public record DynamicObjectEntry(
            String className,
            ObjectSpawn spawn,
            int slotIndex,
            PerObjectRewindSnapshot state
    ) {}

    public record SolidContactRidingEntry(
            PlayableEntity player,
            ObjectSpawn objectSpawn,
            int objectSlotIndex,
            int x,
            int y,
            int pieceIndex
    ) {}

    /**
     * Snapshot of {@code ObjectManager.Placement}'s cursor/window state.
     *
     * <p>Active object instances alone are not enough for deterministic rewind:
     * the next replayed frame runs placement update/load against cursor state,
     * dormant/remembered latches, and active spawn membership. Leaving those at
     * the later live frame makes seek+replay diverge immediately.
     */
    public record PlacementSnapshot(
            int[] activeSpawnIndices,
            long[] rememberedBits,
            long[] stayActiveBits,
            long[] destroyedInWindowBits,
            long[] dormantBits,
            int cursorIndex,
            int lastCameraX,
            int lastCameraChunk,
            boolean counterBasedRespawn,
            boolean execThenLoadPlacement,
            boolean permanentDestroyLatch,
            int maxDynamicSlots,
            boolean lastScrollBackward,
            int leftCursorIndex,
            int fwdCounter,
            int bwdCounter,
            byte[] objState,
            List<SpawnCounterEntry> spawnCounters
    ) {
        public PlacementSnapshot {
            activeSpawnIndices = activeSpawnIndices == null
                    ? new int[0] : Arrays.copyOf(activeSpawnIndices, activeSpawnIndices.length);
            rememberedBits = rememberedBits == null
                    ? new long[0] : Arrays.copyOf(rememberedBits, rememberedBits.length);
            stayActiveBits = stayActiveBits == null
                    ? new long[0] : Arrays.copyOf(stayActiveBits, stayActiveBits.length);
            destroyedInWindowBits = destroyedInWindowBits == null
                    ? new long[0] : Arrays.copyOf(destroyedInWindowBits, destroyedInWindowBits.length);
            dormantBits = dormantBits == null
                    ? new long[0] : Arrays.copyOf(dormantBits, dormantBits.length);
            objState = objState == null ? new byte[0] : Arrays.copyOf(objState, objState.length);
            spawnCounters = List.copyOf(spawnCounters);
        }
    }

    public record SpawnCounterEntry(int spawnIndex, int counter) {}
}
