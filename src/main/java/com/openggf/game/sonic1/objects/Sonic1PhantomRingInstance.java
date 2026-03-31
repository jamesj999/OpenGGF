package com.openggf.game.sonic1.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.Sonic1RingPlacement;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.rings.RingManager;

import java.util.List;

/**
 * ROM parity: phantom slot placeholder for S1 ring layout entries.
 * <p>
 * In the ROM, ring objects (obj25) are loaded by ObjPosLoad into dynamic slots.
 * Each ring layout entry creates a parent obj25 whose Ring_Main routine converts
 * the parent into the first ring and spawns {@code subtype & 7} additional child
 * ring objects via FindFreeObj. The parent persists as a regular ring — it is NOT
 * deleted.
 * <p>
 * The engine's ring system ({@code RingManager}) handles ring collection, rendering,
 * and sparkle animation. This phantom reserves the parent slot and instructs
 * {@code ObjectManager} to allocate the additional child ring slots.
 * <p>
 * ROM slot allocation for a ring entry with subtype=0x12 (countField=2):
 * <pre>
 *   Slot P:  parent obj25 → becomes first ring (persists)
 *   Slot C1: child ring at parent.x + spacing      (via FindFreeObj)
 *   Slot C2: child ring at parent.x + 2 * spacing  (via FindFreeObj)
 * </pre>
 * Total: 1 parent + countField children = countField + 1 slots.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>Created by {@code syncActiveSpawns}: parent occupies slot P</li>
 *   <li>ObjectManager allocates {@link #getChildRingCount()} child slots</li>
 *   <li>Child slots stored in ObjectManager's phantomRingChildSlots map</li>
 *   <li>When a ring is collected, a countdown starts matching the ROM's sparkle
 *       animation duration. When the countdown expires, the slot is freed
 *       (matching ROM's DeleteObject at end of Ring_Delete)</li>
 *   <li>When ALL rings have been collected+freed, phantom self-destructs</li>
 *   <li>Otherwise, remaining slots freed when spawn entry leaves placement window</li>
 * </ol>
 * <p>
 * Sparkle countdown derivation (matches ROM Ring_Sparkle → Ring_Delete timing):
 * <pre>
 *   Frame N:    Touch_Rings detects collection; Ring_Collect runs (same frame)
 *   Frame N+1:  Ring_Sparkle first run, AnimateSprite tick 0 (anim speed=5)
 *   Frame N+25: AnimateSprite tick 24, $FC command → routine advances to Ring_Delete
 *   Frame N+26: Ring_Delete → DeleteObject → slot freed
 * </pre>
 * PhantomRing detects collection at step 2 of frame N+1 (one frame after collection,
 * since collection happens at step 3). From detection to ROM slot free = 25 updates.
 * Countdown is set to 25 on detection; first decrement on next update; free at 0.
 */
public class Sonic1PhantomRingInstance extends AbstractObjectInstance implements TouchResponseProvider {

    /**
     * ROM parity: number of PhantomRing update() calls from collection detection
     * to slot release. Matches the ring's lifecycle from Ring_Sparkle first run
     * (N+1) through Ring_Delete (N+26) = 25 ExecuteObjects frames.
     * <p>
     * Derivation: sparkle animation = 4 frames × 6 ticks/frame = 24 ticks.
     * Plus 1 Ring_Delete frame = 25 total from sparkle start to deletion.
     */
    private static final int SPARKLE_SLOT_COUNTDOWN = 25;

    private final int childCount;
    private final int dx;
    private final int dy;

    /**
     * Countdown per ring slot. Values:
     * <ul>
     *   <li>{@code -1}: ring not yet collected (slot occupied)</li>
     *   <li>{@code > 0}: ring collected, sparkle countdown in progress (slot occupied)</li>
     *   <li>{@code 0}: countdown expired, slot freed</li>
     * </ul>
     * Index 0 = parent ring, indices 1..childCount = children.
     */
    private final int[] slotCountdown;

    /** True once child slots have been allocated (second update, matching Ring_Main). */
    private boolean childSlotsAllocated;

    /**
     * ROM parity: deferred parent slot release flag.
     * Set when the parent ring's sparkle countdown expires. Processed at the end
     * of update() to avoid mid-loop slot release issues. The parent slot is
     * released via ObjectManager.releaseSlot(), moving the PhantomRing to the
     * fallback loop for continued child countdown management.
     */
    private boolean parentSlotPendingRelease;

    /**
     * Guard against double-update in the frame when the parent slot is released.
     * releaseSlot() removes the PhantomRing from execOrder and sets slotIndex=-1,
     * causing the fallback loop to pick it up and call update() again in the same
     * frame. This field tracks the last frame's vbla to prevent double-processing.
     */
    private int lastUpdateFrame = -1;

    public Sonic1PhantomRingInstance(ObjectSpawn spawn) {
        super(spawn, "Ring");
        this.childCount = spawn.subtype() & 0x07;
        int[] spacing = Sonic1RingPlacement.getRingSpacing(spawn.subtype());
        this.dx = spacing[0];
        this.dy = spacing[1];
        this.slotCountdown = new int[1 + childCount];
        java.util.Arrays.fill(slotCountdown, -1); // all uncollected
    }

    /**
     * Returns the number of ADDITIONAL child ring slots to allocate.
     * <p>
     * In the ROM, Ring_Main converts the parent into the first ring, then
     * creates {@code subtype & 7} additional child ring objects via FindFreeObj.
     * The parent itself occupies 1 slot (handled by the standard slot allocation),
     * so only the additional children need to be allocated here.
     */
    public int getChildRingCount() {
        return childCount;
    }

    @Override
    public int getReservedChildSlotCount() {
        return childCount;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Guard: prevent double-update in the frame when parent slot is released.
        // releaseSlot() moves PhantomRing from exec loop to fallback loop mid-frame,
        // which would call update() again. Skip if already updated this frame.
        if (lastUpdateFrame == frameCounter) return;
        lastUpdateFrame = frameCounter;

        // ROM parity: Ring_Main allocates child ring slots via FindFreeObj during
        // ExecuteObjects. For S1 counter-based placement, the placement window
        // updates at the end of frame N (step 5b), syncActiveSpawnsLoad creates
        // the PhantomRing at the start of frame N+1, and the exec loop runs it
        // in the same frame. This matches the ROM where ObjPosLoad loads the
        // parent at end of frame N and Ring_Main runs during frame N+1's
        // ExecuteObjects. Allocate children on the first update.
        if (!childSlotsAllocated && childCount > 0) {
            childSlotsAllocated = true;
            services().objectManager().allocateChildSlots(spawn, childCount);
        }

        RingManager ringManager = services().ringManager();
        if (ringManager == null) {
            return;
        }

        int baseX = spawn.x();
        int baseY = spawn.y();

        // Process parent ring (index 0)
        processRingSlot(ringManager, 0, baseX, baseY);

        // Process child rings (indices 1..childCount)
        for (int i = 0; i < childCount; i++) {
            int childX = baseX + (i + 1) * dx;
            int childY = baseY + (i + 1) * dy;
            processRingSlot(ringManager, i + 1, childX, childY);
        }

        // ROM parity: If ALL rings' slots are freed, the phantom can self-destruct.
        // Check BEFORE releasing parent slot so the exec loop handles normal
        // destruction (with full cleanup including reservedChildSlots and instanceToSpawn).
        if (allSlotsFreed()) {
            setDestroyed(true);
            return;
        }

        // ROM parity: release parent slot independently when its sparkle completes.
        // In the ROM, each ring (parent and children) independently calls DeleteObject
        // when its sparkle animation finishes. The PhantomRing continues running in
        // the fallback loop to manage remaining child countdowns.
        if (parentSlotPendingRelease) {
            parentSlotPendingRelease = false;
            services().objectManager().releaseSlot(this);
        }
    }

    /**
     * ROM parity: processes a single ring slot's lifecycle.
     * <p>
     * When a ring is first detected as collected, a countdown starts.
     * The countdown decrements once per update() call (matching ExecuteObjects).
     * When it reaches 0, the slot is freed (matching ROM's Ring_Delete → DeleteObject).
     */
    private void processRingSlot(RingManager ringManager, int index, int ringX, int ringY) {
        if (slotCountdown[index] == 0) {
            // Already freed
            return;
        }

        if (slotCountdown[index] > 0) {
            // Countdown in progress: decrement
            slotCountdown[index]--;
            if (slotCountdown[index] == 0) {
                // Countdown expired — free the slot
                int vbla = services().objectManager().getVblaCounter();
                System.err.printf("[DIAG_RING_FREE] vbla=%d spawnX=0x%04X idx=%d slot=%d%n",
                        vbla, spawn.x(), index, getSlotIndex());
                freeRingSlot(index);
            }
            return;
        }

        // slotCountdown == -1: not yet collected. Check collection state.
        if (ringManager.isRingCollected(ringX, ringY)) {
            // DIAG: detect false-positive ring collection
            int vbla = services().objectManager().getVblaCounter();
            System.err.printf("[DIAG_RING_COLLECT] vbla=%d x=0x%04X y=0x%04X spawnX=0x%04X idx=%d children=%d slot=%d%n",
                    vbla, ringX, ringY, spawn.x(), index, childCount, getSlotIndex());
            if (SPARKLE_SLOT_COUNTDOWN <= 0) {
                // Immediate release: free slot on detection frame
                slotCountdown[index] = 0;
                freeRingSlot(index);
            } else {
                // Start countdown; first decrement on next update
                slotCountdown[index] = SPARKLE_SLOT_COUNTDOWN;
            }
        }
    }

    /**
     * Frees the slot for ring at the given index.
     * Index 0 = parent, indices 1+ = children.
     * <p>
     * ROM parity: In the ROM, each ring (parent and children) is an independent
     * SST entry. When a ring's sparkle completes, Ring_Delete → DeleteObject
     * frees THAT ring's slot independently. The parent ring's slot is freed
     * when the parent's sparkle completes, even if children are still sparkling.
     * <p>
     * The PhantomRing continues running (slotless) to manage remaining child
     * countdowns. It self-destructs when all countdowns reach 0.
     */
    private void freeRingSlot(int index) {
        if (index == 0) {
            // ROM parity: parent ring independently frees its slot when sparkle
            // completes, matching ROM's Ring_Delete → DeleteObject. Deferred to
            // end of update() to avoid mid-loop slot release complications.
            parentSlotPendingRelease = true;
        } else {
            // Child ring: free the reserved child slot (matches ROM timing
            // where each child ring independently calls DeleteObject)
            services().objectManager().freePhantomChildSlot(spawn, index - 1);
        }
    }

    private boolean allSlotsFreed() {
        for (int countdown : slotCountdown) {
            if (countdown != 0) return false;
        }
        return true;
    }

    // ── TouchResponseProvider ─────────────────────────────────────────────
    //
    // ROM parity: In the ROM, each ring object (obj25) has obColType=$47
    // (powerup category $40, size index $07 → width=6, height=6). These
    // ring objects participate in ReactToItem's scan loop just like any
    // other dynamic object. When a ring overlaps Sonic, ReactToItem
    // processes it (advances routine → collection) and EXITS (rts),
    // preventing any subsequent objects in higher slots from being checked.
    //
    // This blocking behavior is critical: a ring can prevent an enemy in
    // a later slot from being detected until the ring is collected and
    // cleared. Without ring participation in the touch response loop,
    // the engine detects enemies 1 frame early whenever a ROM-side ring
    // would have blocked ReactToItem.
    //
    // The PhantomRingInstance provides multi-touch regions (one per
    // uncollected ring) with collision flags $47. RingManager handles
    // actual collection independently — the touch regions only need to
    // block the scan loop at the correct slot position.

    /** S1 ring collision type: $47 = powerup category ($40) + size index 7. */
    private static final int RING_COLLISION_FLAGS = 0x47;

    @Override
    public int getCollisionFlags() {
        // Use multi-region instead of single-region.
        return 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public TouchRegion[] getMultiTouchRegions() {
        // Only include rings that are still uncollected and not yet in sparkle
        // countdown. Collected rings (slotCountdown != -1) have already been
        // processed and should no longer block the touch response loop.
        int baseX = spawn.x();
        int baseY = spawn.y();
        int count = 0;

        // DIAG: check all phantom rings at critical vbla
        int vbla = services() != null && services().objectManager() != null
                ? services().objectManager().getVblaCounter() : -1;
        if (vbla >= 4265 && vbla <= 4270 && baseX >= 0x0A00 && baseX <= 0x0D00) {
            System.err.printf("[DIAG_PHANTOM_ACTIVE] vbla=%d slot=%d spawnX=0x%04X spawnY=0x%04X children=%d countdown=%s%n",
                    vbla, getSlotIndex(), baseX, baseY, childCount, java.util.Arrays.toString(slotCountdown));
        }

        // Count uncollected rings
        for (int i = 0; i <= childCount; i++) {
            if (slotCountdown[i] == -1) {
                count++;
            }
        }
        if (count == 0) {
            return null;
        }

        TouchRegion[] regions = new TouchRegion[count];
        int idx = 0;
        // Parent ring (index 0)
        if (slotCountdown[0] == -1) {
            regions[idx++] = new TouchRegion(baseX, baseY, RING_COLLISION_FLAGS);
        }
        // Child rings (indices 1..childCount)
        for (int i = 0; i < childCount; i++) {
            if (slotCountdown[i + 1] == -1) {
                int childX = baseX + (i + 1) * dx;
                int childY = baseY + (i + 1) * dy;
                regions[idx++] = new TouchRegion(childX, childY, RING_COLLISION_FLAGS);
            }
        }

        return regions;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No rendering: ring rendering handled by RingManager
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }
}
