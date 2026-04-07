package com.openggf.level.objects;

import static org.lwjgl.opengl.GL11.GL_LINES;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.CollisionModel;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.GameStateManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.spawn.AbstractPlacementManager;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.game.PlayableEntity;
import com.openggf.game.DamageCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.game.GroundMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class ObjectManager {
    private static final int BUCKET_COUNT = RenderPriority.MAX - RenderPriority.MIN + 1;

    private final Placement placement;
    private final ObjectRegistry registry;
    private final GraphicsManager graphicsManager;
    private final Camera camera;
    private final Map<ObjectSpawn, ObjectInstance> activeObjects = new IdentityHashMap<>();
    private final List<ObjectInstance> dynamicObjects = new ArrayList<>();
    private final List<GLCommand> renderCommands = new ArrayList<>();
    private int frameCounter;
    private int vblaCounter;
    private boolean updating;

    // ROM parity: slot-ordered execution array for ExecuteObjects emulation.
    // Populated at start of each update pass, iterated slot 0→95 in order.
    // When a child is spawned at a higher slot, it's placed here directly
    // so the ongoing loop reaches it naturally (same-frame execution).
    private final ObjectInstance[] execOrder = new ObjectInstance[DYNAMIC_SLOT_COUNT];
    private int currentExecSlot = -1; // -1 when not in update loop
    private int peakSlotCount = 0;    // Track actual peak usedSlots cardinality

    private final ObjectServices objectServices;

    // Pre-bucketed lists for O(n) rendering instead of O(n*buckets)
    @SuppressWarnings("unchecked")
    private final List<ObjectInstance>[] lowPriorityBuckets = new ArrayList[BUCKET_COUNT];
    @SuppressWarnings("unchecked")
    private final List<ObjectInstance>[] highPriorityBuckets = new ArrayList[BUCKET_COUNT];
    private boolean bucketsDirty = true;

    // Cached combined active objects list to avoid allocation in getActiveObjects()
    private final List<ObjectInstance> cachedActiveObjects = new ArrayList<>();
    private boolean activeObjectsCacheDirty = true;


    // ROM: Dynamic objects occupy SST slots 32-127 (96 slots total).
    // Each slot's d7 = 127 - slotIndex, used in timing gates like
    // (v_vbla_byte + d7) & 7 for Batbrain drop checks. Objects need
    // unique slot indices for correct per-object timing phase offsets.
    private static final int DYNAMIC_SLOT_BASE = 32;
    private static final int DYNAMIC_SLOT_COUNT = 96;
    private final BitSet usedSlots = new BitSet(DYNAMIC_SLOT_COUNT);

    // ROM parity: Tracks child slots reserved by objects with getReservedChildSlotCount() > 0.
    // In S1, ring objects (obj25) allocate child ring slots via FindFreeObj. These slots
    // must be occupied to match the ROM's SST layout and give subsequent objects correct
    // slot numbers (affecting timing gates like (v_vbla_byte + d7) & 7).
    private final Map<ObjectSpawn, int[]> reservedChildSlots = new IdentityHashMap<>();

    private final PlaneSwitchers planeSwitchers;
    private final SolidContacts solidContacts;
    private final TouchResponses touchResponses;

    private static final Comparator<ObjectInstance> RENDER_SLOT_DESCENDING = (a, b) -> {
        int slotA = a instanceof AbstractObjectInstance aoiA ? aoiA.getSlotIndex() : Integer.MAX_VALUE;
        int slotB = b instanceof AbstractObjectInstance aoiB ? aoiB.getSlotIndex() : Integer.MAX_VALUE;
        return Integer.compare(slotB, slotA);
    };

    public ObjectManager(List<ObjectSpawn> spawns, ObjectRegistry registry,
            int planeSwitcherObjectId, PlaneSwitcherConfig planeSwitcherConfig,
            TouchResponseTable touchResponseTable, GraphicsManager graphicsManager,
            Camera camera, ObjectServices objectServices) {
        this.placement = new Placement(spawns);
        this.registry = registry;
        this.graphicsManager = graphicsManager;
        this.camera = camera;
        this.objectServices = objectServices;
        this.planeSwitchers = planeSwitcherConfig != null
                ? new PlaneSwitchers(placement, planeSwitcherObjectId, planeSwitcherConfig)
                : null;
        this.solidContacts = new SolidContacts(this);
        this.touchResponses = touchResponseTable != null
                ? new TouchResponses(this, touchResponseTable)
                : null;
        // Initialize bucket arrays
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i] = new ArrayList<>();
            highPriorityBuckets[i] = new ArrayList<>();
        }
    }

    public ObjectManager(List<ObjectSpawn> spawns, ObjectRegistry registry,
            int planeSwitcherObjectId, PlaneSwitcherConfig planeSwitcherConfig,
            TouchResponseTable touchResponseTable) {
        this(spawns, registry, planeSwitcherObjectId, planeSwitcherConfig, touchResponseTable,
                GraphicsManager.getInstance(),
                Camera.getInstance(),
                defaultServices());
    }

    private static ObjectServices defaultServices() {
        return new DefaultObjectServices(
                LevelManager.getInstance(),
                Camera.getInstance(),
                GameStateManager.getInstance(),
                SpriteManager.getInstance(),
                FadeManager.getInstance(),
                WaterSystem.getInstance(),
                ParallaxManager.getInstance());
    }

    public void reset(int cameraX) {
        activeObjects.clear();
        dynamicObjects.clear();
        reservedChildSlots.clear();
        cachedActiveObjects.clear();
        activeObjectsCacheDirty = true;
        bucketsDirty = true;
        frameCounter = 0;
        placement.reset(cameraX);
        if (registry != null) {
            registry.reportCoverage(placement.getAllSpawns());
        }
        if (planeSwitchers != null) {
            planeSwitchers.reset();
        }
        solidContacts.reset();
        if (touchResponses != null) {
            touchResponses.reset();
        }
    }

    ObjectServices services() {
        return objectServices;
    }

    /**
     * Replaces the spawn list with a new one from the editor.
     * Clears remembered/destroyed state so edited spawns can respawn.
     * Existing active objects are cleared — {@code syncActiveSpawns()} on
     * the next frame will re-instantiate objects in the camera window.
     */
    public void resyncSpawnList(List<ObjectSpawn> newSpawns) {
        activeObjects.clear();
        cachedActiveObjects.clear();
        activeObjectsCacheDirty = true;
        bucketsDirty = true;
        placement.replaceSpawnsAndReset(newSpawns);
    }

    void resetTouchResponses() {
        if (touchResponses != null) {
            touchResponses.reset();
        }
    }

    /**
     * Forces cached render buckets to rebuild on the next draw.
     *
     * Object priority can change during update (or by following another entity's
     * priority), so add/remove-based invalidation alone is not sufficient.
     */
    public void invalidateRenderBuckets() {
        bucketsDirty = true;
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks, int touchFrameCounter) {
        update(cameraX, player, sidekicks, touchFrameCounter, true);
    }

    /**
     * Run touch responses for a single player outside the main update loop.
     * ROM order: ReactToItem runs during each player's slot within ExecuteObjects,
     * after their physics but before other objects' solid checks.
     */
    public void runTouchResponsesForPlayer(PlayableEntity player, int touchFrameCounter) {
        if (touchResponses == null) {
            return;
        }
        touchResponses.debugState.setEnabled(
                DebugOverlayManager.getInstance().isEnabled(DebugOverlayToggle.TOUCH_RESPONSE));
        // ROM: CPU sidekick uses separate overlap tracking and Hurt_Sidekick handler
        // (knockback only, no ring scatter or death). Must dispatch to updateSidekick
        // to avoid routing through the main player's applyHurtOrDeath path.
        if (player.isCpuControlled()) {
            touchResponses.updateSidekick(player, touchFrameCounter);
        } else {
            touchResponses.update(player, touchFrameCounter);
        }
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses) {
        frameCounter++;
        vblaCounter++;
        updateCameraBounds();

        boolean counterBased = placement.isCounterBasedRespawn();

        syncActiveSpawnsUnload();
        // ROM parity: In the ROM, ExecuteObjects runs BEFORE ObjPosLoad.
        // Dynamic children (e.g. GlassBlock reflections) whose parent was just
        // unloaded in syncActiveSpawnsUnload may now be marked destroyed (via
        // the parent's onUnload()). Free their slots before the load phase so
        // FindFreeObj sees the same available slots as the ROM's ObjPosLoad.
        cleanupDestroyedDynamicObjects();
        if (counterBased) {
            preAllocateReservedChildSlots();
        }
        syncActiveSpawnsLoad();
        runExecLoop(cameraX, player);






        // Note: solidContacts.update() is now called during SpriteManager.update(),
        // after movement but before animation. This ensures pushing flag is set correctly
        // for both terrain and solid objects before animation resolves.
        if (enableTouchResponses && touchResponses != null) {
            touchResponses.debugState.setEnabled(
                    DebugOverlayManager.getInstance().isEnabled(DebugOverlayToggle.TOUCH_RESPONSE));
            touchResponses.update(player, touchFrameCounter);
            // ROM: Both players participate in touch responses.
            // Each sidekick uses separate overlap tracking and special hurt handling.
            for (PlayableEntity sk : sidekicks) {
                touchResponses.updateSidekick(sk, touchFrameCounter);
            }
        }

        // Stream object placement for the NEXT frame.
        // ROM parity: S1's ObjPosLoad runs AFTER DeformLayers (camera update).
        // Counter-based respawn depends on seeing the post-camera X to assign
        // the same counter values as the ROM. Defer to postCameraPlacementUpdate().
        if (!counterBased) {
            placement.update(cameraX);
        }
    }

    /**
     * ROM-accurate update flow for S1 counter-based respawn.
     * <p>
     * Matches the ROM's Level_MainLoop order:
     * <ol>
     *   <li><b>ExecuteObjects</b> — runs objects in slot order. Objects that are
     *       out of range call DeleteObject during their own routine, freeing their
     *       slot immediately. Child allocations (Ring_Main FindFreeObj, CStom FindNextFreeObj)
     *       see these freed slots in the same pass.</li>
     *   <li><b>ObjPosLoad</b> — loads new objects using FindFreeObj, which sees the
     *       post-ExecuteObjects slot landscape (all frees and child allocations applied).</li>
     * </ol>
     * <p>
     * The previous approach batch-freed all out-of-range objects before the exec loop,
     * then loaded new objects before exec. New objects could fill freed slots that should
     * have been available for child allocations, causing cumulative slot offset drift.
     */
    private void updateCounterBasedExecThenLoad(int cameraX, PlayableEntity player) {
        // Phase 1: Snapshot positions and build exec order from EXISTING objects.
        for (ObjectInstance inst : activeObjects.values()) {
            inst.snapshotPreUpdatePosition();
        }
        for (ObjectInstance inst : dynamicObjects) {
            inst.snapshotPreUpdatePosition();
        }

        Arrays.fill(execOrder, null);
        Map<ObjectInstance, ObjectSpawn> instanceToSpawn = new IdentityHashMap<>();
        for (Map.Entry<ObjectSpawn, ObjectInstance> e : activeObjects.entrySet()) {
            ObjectInstance inst = e.getValue();
            instanceToSpawn.put(inst, e.getKey());
            if (inst instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                execOrder[aoi.getSlotIndex() - DYNAMIC_SLOT_BASE] = inst;
            }
        }
        for (ObjectInstance inst : dynamicObjects) {
            if (inst instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                execOrder[aoi.getSlotIndex() - DYNAMIC_SLOT_BASE] = inst;
            }
        }
        int currentSlotCount = usedSlots.cardinality();
        if (currentSlotCount > peakSlotCount) peakSlotCount = currentSlotCount;

        // Phase 2: ExecuteObjects — run objects in slot order with inline out_of_range.
        updating = true;
        boolean objectsRemoved = false;
        try {
            for (currentExecSlot = 0; currentExecSlot < DYNAMIC_SLOT_COUNT; currentExecSlot++) {
                ObjectInstance instance = execOrder[currentExecSlot];
                if (instance == null) continue;

                // ROM parity: each object checks out_of_range at the start of its
                // routine during ExecuteObjects. Freeing the slot here (not in a
                // batch pre-pass) ensures child allocations from higher slots see
                // the correct set of available slots.
                ObjectSpawn spawn = instanceToSpawn.get(instance);
                if (spawn != null && !instance.isPersistent()
                        && isOutOfRangeS1(instance.getX(), cameraX)) {
                    int slotIndex = currentExecSlot + DYNAMIC_SLOT_BASE;
                    if (instance instanceof AbstractObjectInstance aoi
                            && aoi.getSlotIndex() == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    freeAllReservedChildSlots(spawn);
                    placement.clearCounterForSpawn(spawn);
                    placement.removeFromActiveForUnload(spawn);
                    instance.onUnload();
                    execOrder[currentExecSlot] = null;
                    instanceToSpawn.remove(instance);
                    activeObjects.remove(spawn);
                    objectsRemoved = true;
                    continue;
                }

                instance.update(vblaCounter, player);

                if (instance.isDestroyed()) {
                    int slotIndex = currentExecSlot + DYNAMIC_SLOT_BASE;
                    if (instance instanceof AbstractObjectInstance aoi3
                            && aoi3.getSlotIndex() == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    instance.onUnload();
                    execOrder[currentExecSlot] = null;

                    if (spawn != null) {
                        instanceToSpawn.remove(instance);
                        freeAllReservedChildSlots(spawn);
                        placement.clearStayActive(spawn);
                        placement.removeFromActive(spawn);
                        activeObjects.remove(spawn);
                    } else {
                        dynamicObjects.remove(instance);
                    }
                    objectsRemoved = true;
                }
            }

            // Fallback: process dynamic objects without valid slots
            for (ObjectInstance inst : new ArrayList<>(dynamicObjects)) {
                if (inst instanceof AbstractObjectInstance aoi2
                        && aoi2.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    dynamicObjects.remove(inst);
                    objectsRemoved = true;
                    continue;
                }
                inst.update(vblaCounter, player);
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    dynamicObjects.remove(inst);
                    objectsRemoved = true;
                }
            }
            // Fallback: process active objects without valid slots
            for (var entry : new ArrayList<>(activeObjects.entrySet())) {
                ObjectInstance inst = entry.getValue();
                if (inst instanceof AbstractObjectInstance aoi2
                        && aoi2.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(entry.getKey());
                    placement.removeFromActive(entry.getKey());
                    activeObjects.remove(entry.getKey());
                    objectsRemoved = true;
                    continue;
                }
                inst.update(vblaCounter, player);
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(entry.getKey());
                    placement.removeFromActive(entry.getKey());
                    activeObjects.remove(entry.getKey());
                    objectsRemoved = true;
                }
            }
        } finally {
            currentExecSlot = -1;
            updating = false;
            if (objectsRemoved) {
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
        }

        // Phase 3: ObjPosLoad — load new objects AFTER ExecuteObjects.
        // Slots freed during the exec loop and child slots allocated during exec
        // are now reflected in usedSlots. New objects get the correct slot numbers.
        syncActiveSpawnsLoad();

        // Phase 4: Run newly loaded objects immediately.
        // ROM parity compensation: In the ROM, objects loaded by ObjPosLoad don't
        // execute until the next frame's ExecuteObjects. But the engine has a
        // 1-frame pipeline delay (spawns added by postCameraPlacementUpdate are
        // instantiated the next frame). Running new objects now compensates for
        // this delay, keeping object behavior in sync with the trace.
        runNewlyLoadedObjects(player);
    }

    /**
     * Runs the standard exec loop for non-counter-based respawn (S2/S3K).
     * This preserves the existing behavior where unload and load happen before exec.
     */
    private void runExecLoop(int cameraX, PlayableEntity player) {
        // ROM parity: Snapshot all objects' positions BEFORE their updates run.
        for (ObjectInstance inst : activeObjects.values()) {
            inst.snapshotPreUpdatePosition();
        }
        for (ObjectInstance inst : dynamicObjects) {
            inst.snapshotPreUpdatePosition();
        }

        // ROM parity: Build slot-ordered execution array.
        Arrays.fill(execOrder, null);
        Map<ObjectInstance, ObjectSpawn> instanceToSpawn = new IdentityHashMap<>();
        for (Map.Entry<ObjectSpawn, ObjectInstance> e : activeObjects.entrySet()) {
            ObjectInstance inst = e.getValue();
            instanceToSpawn.put(inst, e.getKey());
            if (inst instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                execOrder[aoi.getSlotIndex() - DYNAMIC_SLOT_BASE] = inst;
            }
        }
        for (ObjectInstance inst : dynamicObjects) {
            if (inst instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                execOrder[aoi.getSlotIndex() - DYNAMIC_SLOT_BASE] = inst;
            }
        }
        int currentSlotCount = usedSlots.cardinality();
        if (currentSlotCount > peakSlotCount) peakSlotCount = currentSlotCount;

        updating = true;
        boolean objectsRemoved = false;
        boolean counterBased = placement.isCounterBasedRespawn();
        // Track objects processed by the slot-based loop so the fallback loop
        // doesn't double-update objects that lost their slot mid-frame.
        Set<ObjectInstance> processedInExecLoop = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            // ROM parity: Iterate slots in ascending order, matching ExecuteObjects.
            for (currentExecSlot = 0; currentExecSlot < DYNAMIC_SLOT_COUNT; currentExecSlot++) {
                ObjectInstance instance = execOrder[currentExecSlot];
                if (instance == null) continue;
                processedInExecLoop.add(instance);

                instance.update(vblaCounter, player);

                // ROM parity: each object calls RememberState / out_of_range
                // at the END of its routine, AFTER updating position. Check
                // post-update position so objects moving toward the camera
                // survive (matching ROM behavior where obX reflects movement).
                if (counterBased && !instance.isDestroyed()) {
                    ObjectSpawn oorSpawn = instanceToSpawn.get(instance);
                    if (oorSpawn != null && !instance.isPersistent()
                            && isOutOfRangeS1(instance.getX(), cameraX)) {
                        int slotIndex = currentExecSlot + DYNAMIC_SLOT_BASE;
                        if (instance instanceof AbstractObjectInstance aoi
                                && aoi.getSlotIndex() == slotIndex) {
                            releaseSlot(slotIndex);
                        }
                        freeAllReservedChildSlots(oorSpawn);
                        placement.clearCounterForSpawn(oorSpawn);
                        // ROM parity: mark dormant instead of removing from active.
                        // The spawn stays in placement.active but syncActiveSpawnsLoad
                        // skips dormant spawns. Only the cursor system clears dormant
                        // when it naturally re-processes this position.
                        placement.markDormant(oorSpawn);
                        instance.onUnload();
                        execOrder[currentExecSlot] = null;
                        instanceToSpawn.remove(instance);
                        activeObjects.remove(oorSpawn);
                        objectsRemoved = true;
                        continue;
                    }
                }

                if (instance.isDestroyed()) {
                    int slotIndex = currentExecSlot + DYNAMIC_SLOT_BASE;
                    if (instance instanceof AbstractObjectInstance aoi3
                            && aoi3.getSlotIndex() == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    instance.onUnload();
                    execOrder[currentExecSlot] = null;

                    ObjectSpawn spawn = instanceToSpawn.remove(instance);
                    if (spawn != null) {
                        freeAllReservedChildSlots(spawn);
                        placement.clearStayActive(spawn);
                        placement.removeFromActive(spawn);
                        activeObjects.remove(spawn);
                    } else {
                        dynamicObjects.remove(instance);
                    }
                    objectsRemoved = true;
                }
            }

            // Fallback: process objects without valid slots
            for (ObjectInstance inst : new ArrayList<>(dynamicObjects)) {
                if (inst instanceof AbstractObjectInstance aoi2
                        && aoi2.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    dynamicObjects.remove(inst);
                    objectsRemoved = true;
                    continue;
                }
                inst.update(vblaCounter, player);
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    dynamicObjects.remove(inst);
                    objectsRemoved = true;
                }
            }
            for (var entry : new ArrayList<>(activeObjects.entrySet())) {
                ObjectInstance inst = entry.getValue();
                if (inst instanceof AbstractObjectInstance aoi2
                        && aoi2.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
                    continue;
                }
                // Skip objects already processed in the slot-based exec loop.
                // This prevents double-updates when an object releases its slot mid-frame.
                if (processedInExecLoop.contains(inst)) {
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(entry.getKey());
                    placement.removeFromActive(entry.getKey());
                    activeObjects.remove(entry.getKey());
                    objectsRemoved = true;
                    continue;
                }
                inst.update(vblaCounter, player);
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(entry.getKey());
                    placement.removeFromActive(entry.getKey());
                    activeObjects.remove(entry.getKey());
                    objectsRemoved = true;
                }
            }
        } finally {
            currentExecSlot = -1;
            updating = false;
            if (objectsRemoved) {
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
        }
    }

    /**
     * Runs update() on objects that were just created by syncActiveSpawnsLoad.
     * <p>
     * These objects were loaded AFTER the exec loop (matching ROM's ObjPosLoad timing).
     * In the ROM, objects loaded by ObjPosLoad don't execute until the next frame.
     * But the engine compensates for its pipeline delay by running them immediately.
     * They run in slot order for consistency.
     */
    private void runNewlyLoadedObjects(PlayableEntity player) {
        // Build a slot-ordered list of newly loaded objects.
        // These are objects in activeObjects that are NOT in execOrder (they weren't
        // present during the exec loop) and have valid slots.
        boolean objectsRemoved = false;
        for (int slot = 0; slot < DYNAMIC_SLOT_COUNT; slot++) {
            if (execOrder[slot] != null) continue; // Already processed in exec loop
            if (!usedSlots.get(slot)) continue; // No object at this slot

            // Find the object at this slot
            ObjectInstance instance = null;
            ObjectSpawn spawn = null;
            for (Map.Entry<ObjectSpawn, ObjectInstance> e : activeObjects.entrySet()) {
                if (e.getValue() instanceof AbstractObjectInstance aoi
                        && aoi.getSlotIndex() == slot + DYNAMIC_SLOT_BASE) {
                    instance = e.getValue();
                    spawn = e.getKey();
                    break;
                }
            }
            if (instance == null) {
                for (ObjectInstance di : dynamicObjects) {
                    if (di instanceof AbstractObjectInstance aoi
                            && aoi.getSlotIndex() == slot + DYNAMIC_SLOT_BASE) {
                        instance = di;
                        break;
                    }
                }
            }
            if (instance == null) continue;

            instance.snapshotPreUpdatePosition();
            instance.update(vblaCounter, player);

            if (instance.isDestroyed()) {
                int slotIndex = slot + DYNAMIC_SLOT_BASE;
                if (instance instanceof AbstractObjectInstance aoi
                        && aoi.getSlotIndex() == slotIndex) {
                    releaseSlot(slotIndex);
                }
                instance.onUnload();
                if (spawn != null) {
                    freeAllReservedChildSlots(spawn);
                    placement.clearStayActive(spawn);
                    placement.removeFromActive(spawn);
                    activeObjects.remove(spawn);
                } else {
                    dynamicObjects.remove(instance);
                }
                objectsRemoved = true;
            }
        }
        if (objectsRemoved) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
    }

    /**
     * Extend the placement active set using the post-camera X position.
     * <p>
     * ROM parity: {@code ObjPosLoad} runs <b>after</b> {@code DeformLayers}
     * (camera update), so it sees the camera's post-update position. The primary
     * {@code placement.update()} inside {@link #update} uses the pre-camera
     * position. When the camera advance crosses a 128px chunk boundary between
     * those two positions, the post-camera spawn window is wider on the right
     * side. Objects in that gap are delayed by one extra frame, breaking the
     * engine's normal compensation (pipeline delay vs post-move touch check)
     * and creating a 4px collision offset.
     * <p>
     * This method scans the gap region (between the old and new window right
     * edges) and adds any eligible spawns to the active set, WITHOUT updating
     * the placement's internal state (cursor, lastCameraChunk). This ensures
     * that the primary placement pass in the next frame still processes the
     * chunk boundary normally (including left-edge removal), while the gap
     * spawns are already in the active set for {@code syncActiveSpawns()}.
     *
     * @param postCameraX camera X position after the camera update step
     */
    public void postCameraPlacementUpdate(int postCameraX) {
        placement.extendForPostCamera(postCameraX);
    }

    /**
     * Inline creation callback for ROM-accurate ObjPosLoad.
     * Called during placement cursor advancement to create instances immediately.
     */
    private boolean inlineCreateObject(ObjectSpawn spawn, int counterValue) {
        if (activeObjects.containsKey(spawn)) {
            return true; // Already exists
        }
        int preSlot = allocateSlot();
        if (preSlot < 0) {
            return false; // FindFreeObj failure equivalent
        }
        AbstractObjectInstance.PRE_ALLOCATED_SLOT.set(preSlot);
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(objectServices);
        try {
            ObjectInstance instance = registry != null ? registry.create(spawn) : null;
            if (instance != null) {
                if (instance instanceof AbstractObjectInstance aoi) {
                    aoi.setServices(objectServices);
                    if (aoi.getSlotIndex() < 0) {
                        aoi.setSlotIndex(preSlot);
                    }
                    if (counterValue >= 0) {
                        aoi.setRespawnStateIndex(counterValue);
                    }
                } else {
                    releaseSlot(preSlot);
                }
                activeObjects.put(spawn, instance);
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
                return true;
            } else {
                releaseSlot(preSlot);
                return false;
            }
        } finally {
            AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
            AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
        }
    }

    /**
     * Returns placement cursor diagnostics for ROM↔engine comparison.
     * Only meaningful for S1 counter-based respawn mode.
     */
    public int[] getPlacementCursorState() {
        if (!placement.isCounterBasedRespawn()) return null;
        return new int[] {
            placement.getCursorIndex(),
            placement.getLeftCursorIndex(),
            placement.getFwdCounter(),
            placement.getBwdCounter(),
            placement.getLastCameraChunk()
        };
    }

    public void applyPlaneSwitchers(PlayableEntity player) {
        // ROM: CPU Tails does not interact with plane switchers in 1P mode.
        // Only the main player triggers layer/priority changes.
        if (planeSwitchers != null && !player.isCpuControlled()) {
            planeSwitchers.update(player);
        }
    }

    public int getPlaneSwitcherSideState(ObjectSpawn spawn) {
        if (planeSwitchers == null) {
            return -1;
        }
        return planeSwitchers.getSideState(spawn);
    }

    public void drawLowPriority() {
        ensureBucketsPopulated();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            drawPriorityBucket(bucket, false);
        }
    }

    public void drawHighPriority() {
        ensureBucketsPopulated();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            drawPriorityBucket(bucket, true);
        }
    }

    private void ensureBucketsPopulated() {
        if (!bucketsDirty) {
            return;
        }
        bucketsDirty = false;

        // Clear all buckets
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i].clear();
            highPriorityBuckets[i].clear();
        }

        // Bucket active objects
        for (ObjectInstance instance : activeObjects.values()) {
            int bucket = RenderPriority.clamp(instance.getPriorityBucket());
            int idx = bucket - RenderPriority.MIN;
            if (instance.isHighPriority()) {
                highPriorityBuckets[idx].add(instance);
            } else {
                lowPriorityBuckets[idx].add(instance);
            }
        }

        // Bucket dynamic objects
        for (ObjectInstance instance : dynamicObjects) {
            int bucket = RenderPriority.clamp(instance.getPriorityBucket());
            int idx = bucket - RenderPriority.MIN;
            if (instance.isHighPriority()) {
                highPriorityBuckets[idx].add(instance);
            } else {
                lowPriorityBuckets[idx].add(instance);
            }
        }

        // ROM parity: lower sprite-table indices render in front. Objects execute and
        // call Draw_Sprite in slot order, so lower SST slots must be drawn later in
        // painter's-algorithm order. Sort each bucket descending by slot so lower
        // slot indices appear on top.
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i].sort(RENDER_SLOT_DESCENDING);
            highPriorityBuckets[i].sort(RENDER_SLOT_DESCENDING);
        }
    }

    public void drawPriorityBucket(int bucket, boolean highPriority) {
        ensureBucketsPopulated();
        int targetBucket = RenderPriority.clamp(bucket);
        int idx = targetBucket - RenderPriority.MIN;
        List<ObjectInstance>[] buckets = highPriority ? highPriorityBuckets : lowPriorityBuckets;
        List<ObjectInstance> instances = buckets[idx];

        if (instances.isEmpty()) {
            return;
        }

        enableVerticalWrapIfNeeded();
        try {
            renderCommands.clear();
            for (ObjectInstance instance : instances) {
                instance.appendRenderCommands(renderCommands);
            }

            if (renderCommands.isEmpty()) {
                return;
            }
            graphicsManager.enqueueDebugLineState();
            graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
            graphicsManager.enqueueDefaultShaderState();
        } finally {
            graphicsManager.disableVerticalWrapAdjust();
        }
    }

    /**
     * Draw all objects in a single unified bucket, regardless of their isHighPriority flag.
     * Calls the provided callback before drawing each object with its high priority status.
     *
     * This supports ROM-accurate sprite-to-sprite ordering where bucket number determines
     * draw order independently of the sprite-to-tile priority (isHighPriority flag).
     *
     * @param bucket   The priority bucket to draw (0-7)
     * @param callback Called before each object draw with (object, isHighPriority)
     */
    public void drawUnifiedBucket(int bucket, ObjectDrawCallback callback) {
        ensureBucketsPopulated();
        int targetBucket = RenderPriority.clamp(bucket);
        int idx = targetBucket - RenderPriority.MIN;

        // Draw low-priority objects first (they appear behind)
        drawBucketInstances(lowPriorityBuckets[idx], false, callback);

        // Draw high-priority objects second (they appear in front)
        drawBucketInstances(highPriorityBuckets[idx], true, callback);
    }

    private void drawBucketInstances(List<ObjectInstance> instances, boolean highPriority, ObjectDrawCallback callback) {
        if (instances.isEmpty()) {
            return;
        }

        enableVerticalWrapIfNeeded();
        try {
            renderCommands.clear();
            for (ObjectInstance instance : instances) {
                if (callback != null) {
                    callback.beforeDraw(instance, highPriority);
                }
                instance.appendRenderCommands(renderCommands);
            }

            if (!renderCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
                graphicsManager.enqueueDefaultShaderState();
            }
        } finally {
            graphicsManager.disableVerticalWrapAdjust();
        }
    }

    /**
     * Callback interface for unified object drawing.
     * Called before each object is drawn to allow setting up shader uniforms.
     */
    public interface ObjectDrawCallback {
        /**
         * Called before drawing an object.
         *
         * @param instance     The object instance about to be drawn
         * @param highPriority True if object should appear above high-priority tiles
         */
        void beforeDraw(ObjectInstance instance, boolean highPriority);
    }

    /**
     * Draw all objects in a single unified bucket with per-instance priority.
     * Priority is now handled per-instance in the shader, so no batch flushing
     * is needed when switching between low and high priority objects.
     *
     * @param bucket The priority bucket to draw (0-7)
     * @param gfx    The graphics manager to use for priority state
     */
    public void drawUnifiedBucketWithPriority(int bucket, GraphicsManager gfx) {
        ensureBucketsPopulated();
        int idx = RenderPriority.clamp(bucket) - RenderPriority.MIN;

        // The BatchedPatternRenderer uses a single global priority uniform for
        // the entire batch — it cannot vary per-instance. We must flush and
        // restart the batch at each LOW→HIGH transition so that each group
        // gets its own batch with the correct priority.
        // (The InstancedPatternRenderer bakes priority per-instance and doesn't
        // need the flush, but it's harmless — empty flushes are no-ops.)

        if (!lowPriorityBuckets[idx].isEmpty()) {
            gfx.flushPatternBatch();
            gfx.setCurrentSpriteHighPriority(false);
            gfx.beginPatternBatch();
            drawBucketInstancesWithPriority(lowPriorityBuckets[idx]);
        }

        if (!highPriorityBuckets[idx].isEmpty()) {
            gfx.flushPatternBatch();
            gfx.setCurrentSpriteHighPriority(true);
            gfx.beginPatternBatch();
            drawBucketInstancesWithPriority(highPriorityBuckets[idx]);
        }
    }

    private void drawBucketInstancesWithPriority(List<ObjectInstance> instances) {
        if (instances.isEmpty()) {
            return;
        }

        enableVerticalWrapIfNeeded();
        try {
            renderCommands.clear();
            for (ObjectInstance instance : instances) {
                instance.appendRenderCommands(renderCommands);
            }

            if (!renderCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
                graphicsManager.enqueueDefaultShaderState();
            }
        } finally {
            graphicsManager.disableVerticalWrapAdjust();
        }
    }

    public Collection<ObjectInstance> getActiveObjects() {
        if (activeObjectsCacheDirty) {
            cachedActiveObjects.clear();
            cachedActiveObjects.addAll(activeObjects.values());
            cachedActiveObjects.addAll(dynamicObjects);
            // ROM parity: ReactToItem and SolidObject checks scan from lowest
            // slot to highest (v_lvlobjspace → v_lvlobjend). Sort by slot index
            // so the first overlapping object found matches the ROM's scan order.
            // Objects without slots sort last.
            cachedActiveObjects.sort((a, b) -> {
                int slotA = a instanceof AbstractObjectInstance aoiA ? aoiA.getSlotIndex() : Integer.MAX_VALUE;
                int slotB = b instanceof AbstractObjectInstance aoiB ? aoiB.getSlotIndex() : Integer.MAX_VALUE;
                return Integer.compare(slotA, slotB);
            });
            activeObjectsCacheDirty = false;
        }
        return cachedActiveObjects;
    }

    public Collection<ObjectSpawn> getActiveSpawns() {
        return placement.getActiveSpawns();
    }

    public List<ObjectSpawn> getAllSpawns() {
        return placement.getAllSpawns();
    }

    public void addDynamicObject(ObjectInstance object) {
        addDynamicObjectInternal(object, false);
    }

    /**
     * Adds a dynamic object using the slot immediately after the current exec slot when
     * called from an object's update, matching ROM AllocateObjectAfterCurrent behavior.
     */
    public void addDynamicObjectAfterCurrent(ObjectInstance object) {
        addDynamicObjectInternal(object, true);
    }

    private void addDynamicObjectInternal(ObjectInstance object, boolean allocateAfterCurrent) {
        if (object instanceof AbstractObjectInstance aoi) {
            aoi.setServices(objectServices);
            // ROM parity: FindFreeObj allocates an SST slot for EVERY object,
            // including children spawned by other objects (lava balls, projectiles,
            // explosion effects, etc.). Without this, child objects don't consume
            // slots in usedSlots, causing subsequent OPL allocations to get lower
            // slot numbers than the ROM — shifting d7 values and breaking timing
            // gates like (v_vbla_byte + d7) & 7.
            if (aoi.getSlotIndex() < 0) {
                int slot = allocateAfterCurrent && updating && currentExecSlot >= 0
                        ? allocateSlotAfter(currentExecSlot + DYNAMIC_SLOT_BASE)
                        : allocateSlot();
                if (slot >= 0) {
                    aoi.setSlotIndex(slot);
                }
            } else {
                // Pre-assigned slot (e.g. from addDynamicObjectAtSlot for badnik
                // replacement). Ensure the slot is marked as used in the BitSet —
                // it may have been released when the original object was destroyed.
                int slot = aoi.getSlotIndex();
                if (slot >= DYNAMIC_SLOT_BASE && slot < DYNAMIC_SLOT_BASE + DYNAMIC_SLOT_COUNT) {
                    usedSlots.set(slot - DYNAMIC_SLOT_BASE);
                }
            }
        }
        dynamicObjects.add(object);
        if (updating && object instanceof AbstractObjectInstance aoi2
                && aoi2.getSlotIndex() >= DYNAMIC_SLOT_BASE) {
            // ROM parity: FindFreeObj places the child directly into the SST.
            // The ExecuteObjects loop processes slots sequentially, so a child
            // at a HIGHER slot than the parent will be reached and updated in
            // the same frame. A child at a LOWER slot (already processed) won't
            // run until the next frame.
            int execIdx = aoi2.getSlotIndex() - DYNAMIC_SLOT_BASE;
            if (execIdx < DYNAMIC_SLOT_COUNT && execIdx > currentExecSlot) {
                object.snapshotPreUpdatePosition();
                aoi2.setSkipTouchThisFrame(true);
                execOrder[execIdx] = object;
            }
        }
        bucketsDirty = true;
        activeObjectsCacheDirty = true;
    }

    /**
     * Adds a dynamic object at a specific slot index.
     * ROM parity: badnik destruction changes obID in-place, keeping the SST slot.
     */
    public void addDynamicObjectAtSlot(ObjectInstance object, int slotIndex) {
        if (object instanceof AbstractObjectInstance aoi) {
            aoi.setServices(objectServices);
            aoi.setSlotIndex(slotIndex);
        }
        addDynamicObject(object);
    }

    /**
     * Allocates the next available dynamic slot index (32-127).
     * Equivalent to ROM's FindFreeObj — searches from slot 32 forward.
     * Returns -1 if all slots are in use (overflow).
     */
    private int allocateSlot() {
        int bit = usedSlots.nextClearBit(0);
        if (bit >= DYNAMIC_SLOT_COUNT) {
            return -1;
        }
        usedSlots.set(bit);
        return DYNAMIC_SLOT_BASE + bit;
    }

    /**
     * Allocates the next available dynamic slot AFTER the given parent slot.
     * Equivalent to ROM's FindNextFreeObj — used by segmented objects
     * (Caterkiller body, boss sub-parts) that spawn children into slots
     * immediately following the parent's position in the SST.
     *
     * @param parentSlot the parent object's slot index
     * @return the allocated slot index, or -1 if no slot is available
     */
    public int allocateSlotAfter(int parentSlot) {
        int startBit = Math.max(0, parentSlot - DYNAMIC_SLOT_BASE + 1);
        int bit = usedSlots.nextClearBit(startBit);
        if (bit >= DYNAMIC_SLOT_COUNT) {
            return -1;
        }
        usedSlots.set(bit);
        return DYNAMIC_SLOT_BASE + bit;
    }

    /**
     * Releases a previously allocated dynamic slot index.
     */
    private void releaseSlot(int slotIndex) {
        if (slotIndex >= DYNAMIC_SLOT_BASE && slotIndex < DYNAMIC_SLOT_BASE + DYNAMIC_SLOT_COUNT) {
            int bit = slotIndex - DYNAMIC_SLOT_BASE;
            usedSlots.clear(bit);
        }
    }

    /**
     * Enables counter-based respawn tracking (S1 v_objstate system).
     */
    public void enableCounterBasedRespawn() {
        placement.enableCounterBasedRespawn();
    }

    /**
     * Adjusts the placement system's tracking state after a camera wrap-back.
     * <p>
     * ROM parity: when Level_repeat_offset is non-zero, the ROM's ObjPosLoad
     * adjusts its cursor boundaries by the wrap distance so that the forward/backward
     * scan sees a continuous camera motion instead of a discontinuous jump.
     * Without this adjustment, the engine's placement system detects a negative
     * camera delta, triggering a full {@code refreshWindow()} that can re-spawn
     * objects already in the scene (e.g., the AIZ2 end boss during the bombing
     * sequence camera loop).
     *
     * @param wrapDelta the positive distance the camera was moved backward
     */
    public void adjustPlacementTrackingForWrap(int wrapDelta) {
        placement.adjustForWrap(wrapDelta);
    }

    /**
     * Enables slot limit enforcement (ROM FindFreeObj simulation).
     */
    public void enforceSlotLimit() {
        placement.enforceSlotLimit(this::getDynamicObjectCount);
    }

    private int getDynamicObjectCount() {
        return dynamicObjects.size();
    }

    /**
     * Releases this object's own slot back to the pool while keeping the object
     * alive. Used by objects (e.g., ChainedStomper) that need to continue monitoring
     * children after their parent slot should be reusable.
     * <p>
     * ROM parity: In the ROM, each ring calls DeleteObject individually after
     * Ring_Sparkle completes. The parent ring's SST slot is freed when collected,
     * but the obj25 continues monitoring children until they too complete.
     */
    public void releaseSlot(ObjectInstance object) {
        if (object instanceof AbstractObjectInstance aoi) {
            int slot = aoi.getSlotIndex();
            if (slot >= DYNAMIC_SLOT_BASE) {
                releaseSlot(slot);
                // Clear execOrder so the slot can be reused
                int execIdx = slot - DYNAMIC_SLOT_BASE;
                if (execIdx >= 0 && execIdx < DYNAMIC_SLOT_COUNT) {
                    execOrder[execIdx] = null;
                }
                // Clear the object's slot index to prevent double-release.
                // Without this, the object would be placed back into execOrder
                // on the next frame (line ~239), and when eventually destroyed
                // or unloaded, would release the slot AGAIN — potentially
                // corrupting a different object that reused the slot number.
                // With slotIndex=-1, the object falls through to the fallback
                // activeObjects loop for continued updates (slotIndex < DYNAMIC_SLOT_BASE).
                aoi.setSlotIndex(-1);
            }
        }
    }

    /**
     * Releases an object's parent slot independently from any child slots.
     * <p>
     * ROM parity: In S1, Ring_Delete → DeleteObject frees the parent ring's SST
     * slot independently from child rings. The parent and children are separate
     * SST entries with independent lifecycles. This method releases the parent's
     * slot, allowing the object to continue running slotlessly to manage remaining
     * child lifecycles.
     *
     * @param instance the object whose parent slot should be released
     */
    public void releaseParentSlot(AbstractObjectInstance instance) {
        int slot = instance.getSlotIndex();
        if (slot >= DYNAMIC_SLOT_BASE) {
            releaseSlot(slot);
            instance.setSlotIndex(-1);
        }
    }

    /**
     * Frees a reserved child slot for an object that used getReservedChildSlotCount().
     * <p>
     * ROM parity: In S1, each child object calls DeleteObject after its sequence
     * completes, freeing its SST slot. This method replicates that slot release
     * for objects using the reserved child slot mechanism (e.g., ChainedStomper).
     *
     * @param spawn the parent object's spawn (used as key for the child slot array)
     * @param index the child index (0-based) to free
     */
    public void freeReservedChildSlot(ObjectSpawn spawn, int index) {
        int[] childSlots = reservedChildSlots.get(spawn);
        if (childSlots != null && index >= 0 && index < childSlots.length) {
            int slot = childSlots[index];
            if (slot >= DYNAMIC_SLOT_BASE) {
                releaseSlot(slot);
                childSlots[index] = -1; // Mark as freed
            }
        }
    }

    /**
     * ROM parity: pre-allocate reserved child slots for objects that declare them.
     * <p>
     * In the ROM, ExecuteObjects runs BEFORE ObjPosLoad. Objects that allocate
     * child slots via FindFreeObj during ExecuteObjects get lower slot numbers
     * than objects loaded by ObjPosLoad in the same frame. This method restores
     * that ordering by allocating child slots before syncActiveSpawnsLoad runs.
     */
    private void preAllocateReservedChildSlots() {
        for (ObjectInstance inst : activeObjects.values()) {
            if (!inst.needsPreAllocatedChildSlots()) {
                continue;
            }
            int childCount = inst.getReservedChildSlotCount();
            if (childCount > 0 && !reservedChildSlots.containsKey(inst.getSpawn())) {
                allocateChildSlots(inst.getSpawn(), childCount);
            }
        }
    }

    /**
     * Adds a dynamic child object using a pre-allocated reserved slot.
     * <p>
     * ROM parity: when ring parent objects spawn children during ExecuteObjects,
     * those children must occupy the same slot numbers that were pre-allocated
     * via {@link #preAllocateReservedChildSlots()} / {@link #allocateChildSlots}.
     * This method places the child into the pre-allocated slot at {@code childIndex},
     * replacing the phantom reservation with a real object.
     *
     * @param object      the child object to add
     * @param parentSpawn the parent's spawn (key for the reserved slot table)
     * @param childIndex  which reserved child slot to use (0-based)
     */
    public void addDynamicObjectToReservedSlot(ObjectInstance object, ObjectSpawn parentSpawn, int childIndex) {
        int[] childSlots = reservedChildSlots.get(parentSpawn);
        if (childSlots != null && childIndex >= 0 && childIndex < childSlots.length) {
            int reservedSlot = childSlots[childIndex];
            if (reservedSlot >= DYNAMIC_SLOT_BASE) {
                if (object instanceof AbstractObjectInstance aoi) {
                    aoi.setServices(objectServices);
                    aoi.setSlotIndex(reservedSlot);
                    // Slot is already marked used in usedSlots from pre-allocation;
                    // no need to call allocateSlot() again.
                }
                // Mark slot as consumed in the reservation table so freeAllReservedChildSlots
                // won't double-free this slot (the real child object now owns it).
                childSlots[childIndex] = -1;
                dynamicObjects.add(object);
                if (updating) {
                    int execIdx = reservedSlot - DYNAMIC_SLOT_BASE;
                    if (execIdx >= 0 && execIdx < DYNAMIC_SLOT_COUNT && execIdx > currentExecSlot) {
                        object.snapshotPreUpdatePosition();
                        execOrder[execIdx] = object;
                    }
                }
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
                return;
            }
        }
        // Fallback: no pre-allocated slot, use normal allocation.
        // Record a consumed sentinel in the reservation table so that
        // preAllocateReservedChildSlots() won't attempt to allocate phantom
        // slots for this parent in subsequent frames.
        if (childSlots == null) {
            reservedChildSlots.put(parentSpawn, new int[]{-1});
        }
        addDynamicObject(object);
    }

    /**
     * Allocates reserved child slots for an object during the exec loop.
     * <p>
     * ROM parity: In S1, Ring_Main runs during ExecuteObjects and allocates
     * child ring slots via FindFreeObj. This method should be called from
     * the object's first update() to match the ROM's allocation timing.
     * ObjPosLoad allocates parent slots BEFORE ExecuteObjects runs, but
     * child slots are allocated DURING ExecuteObjects.
     *
     * @param spawn the parent object's spawn
     * @param childCount number of child slots to allocate
     * @return the allocated slot indices (may contain -1 for failed allocations)
     */
    public int[] allocateChildSlots(ObjectSpawn spawn, int childCount) {
        // Guard: if already allocated, return existing
        int[] existing = reservedChildSlots.get(spawn);
        if (existing != null) {
            return existing;
        }
        int[] childSlots = new int[childCount];
        for (int c = 0; c < childCount; c++) {
            childSlots[c] = allocateSlot();
        }
        reservedChildSlots.put(spawn, childSlots);
        return childSlots;
    }

    /**
     * Allocates child slots starting from the given parent slot, matching ROM's
     * FindNextFreeObj which scans from the parent's slot forward. Used by objects
     * like CStom_MakeParts that create children in slots adjacent to the parent.
     *
     * @param spawn      the parent's ObjectSpawn (for tracking)
     * @param childCount number of child slots to allocate
     * @param parentSlot the parent object's slot index
     * @return the allocated slot indices (may contain -1 for failed allocations)
     */
    public int[] allocateChildSlotsAfter(ObjectSpawn spawn, int childCount, int parentSlot) {
        int[] childSlots = new int[childCount];
        int lastSlot = parentSlot;
        for (int c = 0; c < childCount; c++) {
            childSlots[c] = allocateSlotAfter(lastSlot);
            if (childSlots[c] >= 0) {
                lastSlot = childSlots[c]; // Next child starts after this one
            }
        }
        reservedChildSlots.put(spawn, childSlots);
        return childSlots;
    }

    /**
     * Returns peak dynamic slot count seen during this level.
     */
    public int getPeakSlotCount() {
        return peakSlotCount;
    }

    /**
     * Frees all reserved child slots for a given spawn, removing the tracking entry.
     * Called when the parent object is destroyed or unloaded.
     */
    private void freeAllReservedChildSlots(ObjectSpawn spawn) {
        int[] childSlots = reservedChildSlots.remove(spawn);
        if (childSlots != null) {
            for (int slot : childSlots) {
                if (slot >= DYNAMIC_SLOT_BASE) {
                    releaseSlot(slot);
                }
            }
        }
    }

    /**
     * Initializes the VBla frame counter to match ROM's v_vbla_byte at trace start.
     */
    public void initVblaCounter(int initialValue) {
        this.vblaCounter = initialValue;
    }

    /**
     * Advances the VBla counter by one, mirroring ROM's v_vbla_byte increment.
     */
    public void advanceVblaCounter() {
        this.vblaCounter++;
    }

    /**
     * Returns the current VBla counter value.
     */
    public int getVblaCounter() {
        return vblaCounter;
    }

    public boolean isRemembered(ObjectSpawn spawn) {
        return placement.isRemembered(spawn);
    }

    public void markRemembered(ObjectSpawn spawn) {
        // Look up the instance to check if it should stay active.
        // activeObjects is an IdentityHashMap so try identity first.
        ObjectInstance instance = activeObjects.get(spawn);
        if (instance == null) {
            // Fallback: scan by equals() in case the caller's spawn reference
            // differs from the canonical key stored in the IdentityHashMap.
            for (Map.Entry<ObjectSpawn, ObjectInstance> entry : activeObjects.entrySet()) {
                if (entry.getKey().equals(spawn)) {
                    instance = entry.getValue();
                    break;
                }
            }
        }
        if (instance != null) {
            placement.markRemembered(spawn, instance);
        } else {
            placement.markRemembered(spawn);
        }
    }

    public void clearRemembered() {
        placement.clearRemembered();
    }

    /**
     * Removes a spawn from the active set without marking it as remembered.
     * The spawn can still respawn when the camera leaves and re-enters the area.
     * Used for badniks which should respawn on camera re-entry but not immediately.
     */
    public void removeFromActiveSpawns(ObjectSpawn spawn) {
        placement.removeFromActive(spawn);
    }

    /** Is this player riding any object? */
    public boolean isRidingObject(PlayableEntity player) {
        return solidContacts.isRidingObject(player);
    }

    /** Is this specific player riding this specific object? */
    public boolean isRidingObject(PlayableEntity player, ObjectInstance instance) {
        return solidContacts.isPlayerRiding(player, instance);
    }

    /** Is ANY player riding anything? */
    public boolean isAnyPlayerRiding() {
        return solidContacts.isAnyPlayerRiding();
    }

    /** Is ANY player riding this specific object? */
    public boolean isAnyPlayerRiding(ObjectInstance instance) {
        return solidContacts.isAnyPlayerRiding(instance);
    }

    /** Clear this player's riding state. */
    public void clearRidingObject(PlayableEntity player) {
        solidContacts.clearRidingObject(player);
    }

    /**
     * Get the object that this player is currently standing on (riding).
     * Used for balance detection at object edges.
     *
     * @param player The player to check
     * @return The object being ridden, or null if not standing on any object
     */
    public ObjectInstance getRidingObject(PlayableEntity player) {
        return solidContacts.getRidingObject(player);
    }

    /**
     * Get the piece index this player is riding on a multi-piece object, or -1.
     * Used for balance detection at piece edges (e.g., CPZ Staircase).
     */
    public int getRidingPieceIndex(PlayableEntity player) {
        return solidContacts.getRidingPieceIndex(player);
    }

    public boolean hasStandingContact(PlayableEntity player) {
        return solidContacts.hasStandingContact(player);
    }

    public int getHeadroomDistance(PlayableEntity player, int hexAngle) {
        return solidContacts.getHeadroomDistance(player, hexAngle);
    }

    /**
     * Run solid contacts resolution for a player sprite.
     * This is called by the CollisionSystem as part of the unified collision pipeline.
     */
    public void updateSolidContacts(PlayableEntity player) {
        solidContacts.deferSideToPostMovement = false;
        solidContacts.update(player, false);
    }

    /**
     * Update solid contacts with an explicit post-movement flag. When {@code postMovement}
     * is true, the velocity classification adjustment is skipped because the player's
     * position already reflects their velocity (movement has already happened).
     * <p>Used by the S1 UNIFIED model where solid objects run AFTER Sonic's movement,
     * unlike S2/S3K where solid contacts run before movement.
     *
     * @param deferSideToPostMovement when true, side collision effects (speed zeroing,
     *     position correction) are skipped in this pass because a post-movement pass will
     *     handle them. This is used for the pre-movement pass in S1 UNIFIED, where the ROM
     *     processes solid objects AFTER Sonic's movement — side collisions at the pre-movement
     *     position are spurious.
     */
    public void updateSolidContacts(PlayableEntity player, boolean postMovement,
                                     boolean deferSideToPostMovement) {
        solidContacts.deferSideToPostMovement = deferSideToPostMovement;
        solidContacts.update(player, postMovement);
    }

    /**
     * Refresh the SolidContacts riding tracking position for an object after it has moved itself.
     * Prevents the delta from that movement being double-applied to riding players.
     */
    public void refreshRidingTrackingPosition(ObjectInstance object) {
        solidContacts.refreshRidingTrackingPosition(object);
    }

    /**
     * Pre-contact player X speed, captured before solid contact resolution zeroes it.
     * ROM: objects save player velocity BEFORE SolidObjectFull (e.g. Obj_AIZLRZEMZRock $30(a0)).
     */
    public short getPreContactXSpeed() { return solidContacts.getPreContactXSpeed(); }

    /** Pre-contact player Y speed. */
    public short getPreContactYSpeed() { return solidContacts.getPreContactYSpeed(); }

    /** Pre-contact player rolling state, before landing clears it. */
    public boolean getPreContactRolling() { return solidContacts.getPreContactRolling(); }

    public TouchResponseDebugState getTouchResponseDebugState() {
        return touchResponses != null ? touchResponses.getDebugState() : null;
    }

    /**
     * Phase 1 of spawn window sync: unload objects that left the placement window.
     * <p>
     * ROM parity: Release slots for departing objects BEFORE the exec loop runs.
     * For counter-based respawn (S1), objects are also checked against the ROM's
     * {@code out_of_range} macro for objects whose spawns are still in the
     * placement active set but whose position is beyond the ROM's 640px threshold.
     */
    private void syncActiveSpawnsUnload() {
        Collection<ObjectSpawn> activeSpawns = placement.getActiveSpawns();
        boolean changed = false;
        boolean counterBased = placement.isCounterBasedRespawn();
        int cameraX = camera.getX();

        Iterator<Map.Entry<ObjectSpawn, ObjectInstance>> iterator = activeObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ObjectSpawn, ObjectInstance> entry = iterator.next();
            ObjectSpawn spawn = entry.getKey();
            ObjectInstance instance = entry.getValue();

            boolean removedFromPlacement = !activeSpawns.contains(spawn);
            // ROM parity: do NOT run a second out-of-range check here for S1.
            // The ROM only checks out_of_range during ExecuteObjects (each
            // object's own RememberState call). The centralized check here
            // was causing 20+ extra unloads during camera backtracking because
            // it runs BEFORE objects execute — objects that would survive the
            // ROM's single check (by having moved closer to Sonic) get killed
            // before they get a chance to update their position this frame.
            boolean outOfRange = false;

            if ((removedFromPlacement || outOfRange) && !instance.isPersistent()) {
                // Release the SST slot so it can be reused by future objects
                if (instance instanceof AbstractObjectInstance aoi) {
                    int slot = aoi.getSlotIndex();
                    if (slot >= 0) {
                        releaseSlot(slot);
                    }
                }
                // Also release any reserved child slots for this spawn
                freeAllReservedChildSlots(spawn);
                // ROM parity: RememberState clears bit 7 when the object
                // actually unloads (goes off-screen). This is the engine's
                // equivalent — the ObjectInstance is being released because
                // it left the screen. Destroyed objects are handled separately
                // (removeFromActive path) and never reach here, so their bits
                // stay set.
                if (counterBased) {
                    placement.clearCounterForSpawn(spawn);
                }
                if (outOfRange) {
                    placement.removeFromActiveForUnload(spawn);
                }
                instance.onUnload();
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
    }

    /**
     * Frees slots of dynamic objects explicitly marked as destroyed.
     * <p>
     * Called after {@link #syncActiveSpawnsUnload()} to release slots held by
     * dynamic children whose parent's {@code onUnload()} set them to destroyed.
     * This is a targeted pass — only objects with {@code isDestroyed() == true}
     * are freed, matching the ROM's ExecuteObjects behavior where these objects
     * would delete themselves before ObjPosLoad runs.
     */
    private void cleanupDestroyedDynamicObjects() {
        boolean changed = false;
        Iterator<ObjectInstance> iter = dynamicObjects.iterator();
        while (iter.hasNext()) {
            ObjectInstance inst = iter.next();
            if (inst.isDestroyed()) {
                if (inst instanceof AbstractObjectInstance aoi) {
                    int slot = aoi.getSlotIndex();
                    if (slot >= 0) {
                        releaseSlot(slot);
                    }
                }
                inst.onUnload();
                iter.remove();
                changed = true;
            }
        }
        if (changed) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
    }

    /**
     * ROM parity: S1 {@code out_of_range} macro (Macros.asm line 261).
     * <p>
     * Computes unsigned 16-bit distance between object and screen position:
     * <pre>
     *   d0 = obX & 0xFF80
     *   d1 = (v_screenposx - 128) & 0xFF80
     *   distance = (d0 - d1) & 0xFFFF   (unsigned 16-bit)
     *   out_of_range when distance > 640  (bhi = unsigned greater)
     * </pre>
     * 640 = 128 + 320 + 192 pixels (behind-camera + screen width + ahead).
     * Catches both left (negative wraps to large unsigned) and right out of range.
     */
    private static boolean isOutOfRangeS1(int objX, int cameraX) {
        int objRounded = objX & 0xFF80;
        int screenRounded = (cameraX - 128) & 0xFF80;
        int distance = (objRounded - screenRounded) & 0xFFFF;
        return distance > 640;
    }

    /**
     * Phase 2 of spawn window sync: load new objects from the placement window.
     * <p>
     * ROM parity: ObjPosLoad runs AFTER ExecuteObjects. By loading new objects
     * after the exec loop, children allocated during the exec loop (Ring_Main,
     * CStom_Main, etc.) get slots BEFORE new placement objects, matching the
     * ROM's slot assignment order. Objects loaded here don't execute until the
     * next frame, matching ROM timing where ObjPosLoad objects first run during
     * the following frame's ExecuteObjects.
     */
    private void syncActiveSpawnsLoad() {
        Collection<ObjectSpawn> activeSpawns = placement.getActiveSpawns();
        boolean changed = false;

        // ROM parity: OPL processes objects in X-sorted order (left-to-right),
        // calling FindFreeObj for each. Sort new spawns by ascending X.
        List<ObjectSpawn> sortedNewSpawns = new ArrayList<>();
        for (ObjectSpawn spawn : activeSpawns) {
            if (!activeObjects.containsKey(spawn)
                    && !(placement.isRemembered(spawn) && !placement.isStayActive(spawn))
                    && !placement.isDormant(spawn)) {
                sortedNewSpawns.add(spawn);
            }
        }
        sortedNewSpawns.sort(Comparator.comparingInt(ObjectSpawn::x));

        // Allocate parent slots for all new objects (matching ObjPosLoad).
        // ROM parity: ObjPosLoad assigns one slot per object in X order.
        // Pre-allocate each parent slot BEFORE running the constructor, so that
        // if the constructor spawns children (e.g., GlassBlock's reflection),
        // getSlotIndex() returns the correct value and allocateSlotAfter()
        // gives the child a HIGHER slot (matching ROM's FindNextFreeObj).
        for (ObjectSpawn spawn : sortedNewSpawns) {
            // Pre-allocate parent slot — consumed by AbstractObjectInstance's
            // constructor via the PRE_ALLOCATED_SLOT ThreadLocal.
            int preSlot = allocateSlot();
            AbstractObjectInstance.PRE_ALLOCATED_SLOT.set(preSlot >= 0 ? preSlot : null);
            AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(objectServices);
            try {
                ObjectInstance instance = registry != null ? registry.create(spawn) : null;
                if (instance != null) {
                    if (instance instanceof AbstractObjectInstance aoi) {
                        aoi.setServices(objectServices);
                        // Slot already set by constructor via PRE_ALLOCATED_SLOT.
                        // Ensure it's set (defensive, in case constructor didn't consume it).
                        if (aoi.getSlotIndex() < 0 && preSlot >= 0) {
                            aoi.setSlotIndex(preSlot);
                        }
                        // ROM: S1 OPL_MakeItem stores the counter value as
                        // obRespawnNo for RememberState to use on unload.
                        if (placement.isCounterBasedRespawn()) {
                            int counter = placement.getCounterForSpawn(spawn);
                            if (counter >= 0) {
                                aoi.setRespawnStateIndex(counter);
                            }
                        }
                    } else {
                        // Non-AbstractObjectInstance: release pre-allocated slot
                        if (preSlot >= 0) {
                            releaseSlot(preSlot);
                        }
                    }
                    activeObjects.put(spawn, instance);
                    changed = true;
                } else {
                    // Creation failed: release pre-allocated slot
                    if (preSlot >= 0) {
                        releaseSlot(preSlot);
                    }
                }
            } finally {
                AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
                AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
            }
        }

        if (changed) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
    }



    /**
     * Enables vertical wrap Y adjustment on GraphicsManager if the camera has
     * vertical wrapping active. Called before object rendering to ensure objects
     * on the "wrong side" of a wrap boundary render at correct screen positions.
     */
    private void enableVerticalWrapIfNeeded() {
        if (camera.isVerticalWrapEnabled()) {
            graphicsManager.enableVerticalWrapAdjust(Camera.VERTICAL_WRAP_RANGE, camera.getY());
        }
    }

    private void updateCameraBounds() {
        int left = camera.getX();
        int top = camera.getY();
        int right = left + camera.getWidth();
        int bottom = top + camera.getHeight();
        int wrapRange = camera.isVerticalWrapEnabled() ? Camera.VERTICAL_WRAP_RANGE : 0;
        AbstractObjectInstance.updateCameraBounds(left, top, right, bottom, wrapRange);
    }

    public static int decodePlaneSwitcherHalfSpan(int subtype) {
        return PlaneSwitchers.decodeHalfSpan(subtype);
    }

    public static boolean isPlaneSwitcherHorizontal(int subtype) {
        return PlaneSwitchers.isHorizontal(subtype);
    }

    public static int decodePlaneSwitcherPath(int subtype, int side) {
        return PlaneSwitchers.decodePath(subtype, side);
    }

    public static boolean decodePlaneSwitcherPriority(int subtype, int side) {
        return PlaneSwitchers.decodePriority(subtype, side);
    }

    public static boolean planeSwitcherGroundedOnly(int subtype) {
        return PlaneSwitchers.onlySwitchWhenGrounded(subtype);
    }

    public static char formatPlaneSwitcherLayer(byte layer) {
        return PlaneSwitchers.formatLayer(layer);
    }

    public static char formatPlaneSwitcherPriority(boolean highPriority) {
        return PlaneSwitchers.formatPriority(highPriority);
    }

    static final class Placement extends AbstractPlacementManager<ObjectSpawn> {
        private static final Logger LOGGER = Logger.getLogger(Placement.class.getName());
        // ROM: ObjectsManager_GoingForward (s2.asm) uses addi.w #$280,d6 for forward load range.
        // Behind-window unload range is one chunk ($80) for forward movement.
        private static final int LOAD_AHEAD = 0x280;
        private static final int UNLOAD_BEHIND = 0x80;
        private static final int CHUNK_MASK = 0xFF80;
        /** ROM: OPL_Next advances v_opl_screen by one chunk (0x80) per frame. */
        private static final int CHUNK_STEP = 0x80;

        private final BitSet remembered = new BitSet();
        /** Tracks spawns that should stay in active even when remembered (e.g. broken monitors). */
        private final BitSet stayActive = new BitSet();
        /** Tracks spawns destroyed while in the window - prevents respawn until they leave the window. */
        private final BitSet destroyedInWindow = new BitSet();
        /**
         * ROM parity: tracks spawns whose instance was deleted via out_of_range
         * during ExecuteObjects but whose cursor position hasn't changed.
         * In the ROM, DeleteObject zeroes the SST slot but ObjPosLoad's cursors
         * are unaware. The spawn stays "between cursors" as a dead slot. It is
         * NOT re-created until the cursor naturally retreats past it and then
         * re-advances (via the backward/forward scan cycle).
         * <p>
         * syncActiveSpawnsLoad must skip dormant spawns — only the cursor system
         * can clear dormant (when it re-processes the spawn position).
         */
        private final BitSet dormant = new BitSet();
        private int cursorIndex = 0;
        private int lastCameraX = Integer.MIN_VALUE;
        private int lastCameraChunk = Integer.MIN_VALUE;

        private boolean counterBasedRespawn;
        private java.util.function.IntSupplier usedSlotCounter;
        private int maxDynamicSlots = 96;

        /**
         * ROM parity: tracks scroll direction from last Placement.update().
         * S1 ObjPosLoad backward scan (camera scrolling left) processes objects
         * in DESCENDING X order (a0 -= 6), while forward scan uses ascending X.
         * syncActiveSpawnsLoad uses this to sort new spawns accordingly.
         */
        private boolean lastScrollBackward;

        /**
         * Callback for inline instance creation during cursor advancement.
         * ROM parity: ObjPosLoad creates objects immediately via FindFreeObj
         * during cursor scans. This callback eliminates the 1-frame pipeline
         * delay between cursor advancement and instance creation.
         */
        @FunctionalInterface
        interface SpawnCallback {
            /**
             * @param spawn the spawn to create
             * @param counterValue the counter assigned (-1 for non-tracked)
             * @return true if created successfully (false = FindFreeObj failure → stop scan)
             */
            boolean tryCreate(ObjectSpawn spawn, int counterValue);
        }

        /** Set during updateAndLoad to enable inline creation. Null = deferred mode. */
        private SpawnCallback inlineCallback;

        // ================================================================
        // S1 counter-based respawn state (ROM: v_objstate system)
        // ================================================================
        // ROM: v_opl_data+4 cursor; engine equivalent is leftCursorIndex.
        private int leftCursorIndex;
        // ROM: v_objstate[0] = forward counter, v_objstate[1] = backward counter.
        // Both start at 1 after OPL_Main init.
        private int fwdCounter;
        private int bwdCounter;
        // ROM: v_objstate[2..255] — per-counter-slot state.
        // Bit 7: set = object loaded or permanently destroyed.
        private final int[] objState = new int[256];
        // Maps active spawn (identity) → counter value assigned during load.
        // Used to clear objState bit when the object is normally unloaded.
        private final IdentityHashMap<ObjectSpawn, Integer> spawnToCounter = new IdentityHashMap<>();

        Placement(List<ObjectSpawn> spawns) {
            super(spawns, LOAD_AHEAD, UNLOAD_BEHIND);
        }

        /**
         * Adjusts tracking state after a camera wrap-back so that the next
         * {@link #update(int)} call sees a small positive delta instead of a
         * large negative one, preventing a spurious {@link #refreshWindow(int)}.
         *
         * @param wrapDelta positive distance the camera X was decreased
         */
        void adjustForWrap(int wrapDelta) {
            if (lastCameraX != Integer.MIN_VALUE) {
                lastCameraX -= wrapDelta;
                lastCameraChunk = toCoarseChunk(lastCameraX);
            }
        }

        void enableCounterBasedRespawn() {
            this.counterBasedRespawn = true;
        }

        void enforceSlotLimit(java.util.function.IntSupplier counter) {
            this.usedSlotCounter = counter;
        }

        /** Replaces spawns and clears all tracking state. */
        void replaceSpawnsAndReset(List<ObjectSpawn> newSpawns) {
            replaceSpawns(newSpawns);
            remembered.clear();
            stayActive.clear();
            destroyedInWindow.clear();
            dormant.clear();
            cursorIndex = 0;
            leftCursorIndex = 0;
            lastCameraX = Integer.MIN_VALUE;
            lastCameraChunk = Integer.MIN_VALUE;
            fwdCounter = 1;
            bwdCounter = 1;
            Arrays.fill(objState, 0);
            spawnToCounter.clear();
        }

        void reset(int cameraX) {
            active.clear();
            remembered.clear();
            stayActive.clear();
            destroyedInWindow.clear();
            dormant.clear();
            spawnToCounter.clear();
            cursorIndex = 0;
            leftCursorIndex = 0;
            fwdCounter = 1;
            bwdCounter = 1;
            Arrays.fill(objState, 0);

            if (counterBasedRespawn) {
                resetCounterBased(cameraX);
            } else {
                lastCameraX = cameraX;
                lastCameraChunk = toCoarseChunk(cameraX);
                refreshWindow(cameraX);
            }
        }

        /**
         * ROM: OPL_Main + first OPL_Next forward pass.
         * <p>
         * Positions both cursors and counters to match the ROM's initialization,
         * then performs the first-frame forward scan to load all objects in the
         * initial camera window.
         */
        private void resetCounterBased(int cameraX) {
            int cameraChunk = cameraX & CHUNK_MASK;
            // ROM: d6 = max(0, cameraX - 0x80) & 0xFF80
            int initD6 = Math.max(0, cameraX - 0x80) & CHUNK_MASK;

            // OPL_Main: Right cursor scan — skip past objects before initD6,
            // counting respawn-tracked entries to build fwdCounter.
            while (cursorIndex < spawns.size() && spawns.get(cursorIndex).x() < initD6) {
                if (spawns.get(cursorIndex).respawnTracked()) {
                    fwdCounter = (fwdCounter + 1) & 0xFF;
                }
                cursorIndex++;
            }

            // OPL_Main: Left cursor scan — skip past objects before (initD6 - 0x80),
            // counting respawn-tracked entries to build bwdCounter.
            int leftD6 = initD6 - 0x80;
            if (leftD6 > 0) {
                while (leftCursorIndex < spawns.size()
                        && spawns.get(leftCursorIndex).x() < leftD6) {
                    if (spawns.get(leftCursorIndex).respawnTracked()) {
                        bwdCounter = (bwdCounter + 1) & 0xFF;
                    }
                    leftCursorIndex++;
                }
            }

            // First OPL_Next forward scan: load objects from right cursor to
            // cameraChunk + LOAD_AHEAD, assigning counter values.
            int windowEnd = cameraChunk + LOAD_AHEAD;
            while (cursorIndex < spawns.size()
                    && spawns.get(cursorIndex).x() < windowEnd) {
                spawnForwardEntry(cursorIndex);
                cursorIndex++;
            }

            // First OPL_Next left cursor trim: advance to cameraChunk - 0x80.
            int leftTrimEdge = cameraChunk - 0x80;
            if (leftTrimEdge > 0) {
                while (leftCursorIndex < spawns.size()
                        && spawns.get(leftCursorIndex).x() < leftTrimEdge) {
                    if (spawns.get(leftCursorIndex).respawnTracked()) {
                        bwdCounter = (bwdCounter + 1) & 0xFF;
                    }
                    leftCursorIndex++;
                }
            }

            lastCameraX = cameraX;
            lastCameraChunk = cameraChunk;
        }

        /**
         * ROM-accurate combined cursor advancement + inline instance creation.
         * Eliminates the 1-frame pipeline delay between cursor scan and instance creation.
         */
        void updateAndLoad(int cameraX, SpawnCallback callback) {
            this.inlineCallback = callback;
            try {
                update(cameraX);
            } finally {
                this.inlineCallback = null;
            }
        }

        void update(int cameraX) {
            if (spawns.isEmpty()) {
                return;
            }
            if (lastCameraX == Integer.MIN_VALUE) {
                reset(cameraX);
                return;
            }

            int cameraChunk = toCoarseChunk(cameraX);
            if (cameraChunk == lastCameraChunk) {
                lastCameraX = cameraX;
                return;
            }

            if (counterBasedRespawn) {
                // S1 mode: two-cursor system with counter tracking.
                // ROM processes exactly one chunk step per frame via v_opl_screen.
                if (cameraChunk > lastCameraChunk) {
                    lastScrollBackward = false;
                    spawnForwardCountered(cameraX);
                    trimLeftCountered(cameraX);
                } else {
                    lastScrollBackward = true;
                    spawnBackwardCountered(cameraX);
                    trimRightCountered(cameraX);
                }
            } else {
                int delta = cameraX - lastCameraX;
                if (delta < 0 || delta > (getLoadAhead() + getUnloadBehind())) {
                    refreshWindow(cameraX);
                } else {
                    spawnForward(cameraX);
                    trimActive(cameraX);
                }
            }

            lastCameraX = cameraX;
            lastCameraChunk = cameraChunk;
        }

        public List<ObjectSpawn> getAllSpawns() {
            return spawns;
        }

        void markRemembered(ObjectSpawn spawn) {
            int index = getSpawnIndex(spawn);
            if (index < 0) {
                LOGGER.warning(() -> "markRemembered: spawn not found in placement list at ("
                        + spawn.x() + "," + spawn.y() + ") id=0x" + Integer.toHexString(spawn.objectId()));
                return;
            }
            remembered.set(index);
        }

        void markRemembered(ObjectSpawn spawn, ObjectInstance instance) {
            // Some objects (monitors, capsules) need to stay active to complete their
            // destruction/animation sequence even after being marked as remembered
            if (!instance.shouldStayActiveWhenRemembered()) {
                active.remove(spawn);
            }

            int index = getSpawnIndex(spawn);
            if (index < 0) {
                LOGGER.warning(() -> "markRemembered: spawn not found in placement list at ("
                        + spawn.x() + "," + spawn.y() + ") id=0x" + Integer.toHexString(spawn.objectId()));
                return;
            }
            remembered.set(index);
            if (instance.shouldStayActiveWhenRemembered()) {
                stayActive.set(index);
            }
        }

        boolean isRemembered(ObjectSpawn spawn) {
            int index = getSpawnIndex(spawn);
            return index >= 0 && remembered.get(index);
        }

        boolean isStayActive(ObjectSpawn spawn) {
            int index = getSpawnIndex(spawn);
            return index >= 0 && stayActive.get(index);
        }

        /**
         * Clears the stayActive flag for a spawn. Called when a stayActive object
         * (e.g. EggPrison) self-destructs, so the remembered flag alone prevents respawn.
         */
        void clearStayActive(ObjectSpawn spawn) {
            int index = getSpawnIndex(spawn);
            if (index >= 0) {
                stayActive.clear(index);
            }
        }

        void clearRemembered() {
            remembered.clear();
        }

        /**
         * Removes a spawn from the active set without marking it as remembered.
         * The spawn won't respawn until it leaves the camera window entirely.
         * Used for badniks which should respawn on camera re-entry but not immediately.
         */
        void removeFromActive(ObjectSpawn spawn) {
            active.remove(spawn);
            int index = getSpawnIndex(spawn);
            if (index >= 0) {
                destroyedInWindow.set(index);
            }
        }

        /**
         * Removes a spawn from the active set for normal out-of-range unloading.
         * <p>
         * Unlike {@link #removeFromActive}, does NOT set {@code destroyedInWindow}.
         * The object left naturally (ROM's out_of_range fired), so it can be
         * respawned when it comes back into the placement window.
         * <p>
         * ROM parity: When an object self-destructs via out_of_range, it calls
         * DeleteObject which zeroes the SST slot. If the object has RememberState,
         * it clears its objState bit before deleting. The cursor system's counters
         * are already correct (trimRightCountered/trimLeftCountered adjusted them).
         * The spawn just needs to be removed from the engine's active set so
         * future placement scans can re-create it.
         */
        void removeFromActiveForUnload(ObjectSpawn spawn) {
            active.remove(spawn);
        }

        /**
         * Marks a spawn as dormant after its instance was deleted via out_of_range.
         * The spawn stays in {@code active} but syncActiveSpawnsLoad will skip it.
         * Only the cursor system clears dormant when it naturally re-processes
         * the spawn position (via forward/backward scan or cursor trim).
         */
        void markDormant(ObjectSpawn spawn) {
            int index = getSpawnIndex(spawn);
            if (index >= 0) {
                dormant.set(index);
            }
        }

        boolean isDormant(ObjectSpawn spawn) {
            int index = getSpawnIndex(spawn);
            return index >= 0 && dormant.get(index);
        }

        private void spawnForward(int cameraX) {
            int spawnLimit = getWindowEnd(cameraX);
            // ROM parity: ObjPosLoad forward scan uses `bls` (branch if lower or same)
            // on the comparison `cmp.w (a0), d6` where d6 = right_edge. This means
            // the loop continues when right_edge > object_x, i.e., object_x < right_edge.
            // Objects exactly AT the right boundary are NOT loaded — strict less-than.
            while (cursorIndex < spawns.size() && spawns.get(cursorIndex).x() < spawnLimit) {
                trySpawn(cursorIndex);
                cursorIndex++;
            }
        }

        private void trimActive(int cameraX) {
            int windowStart = getWindowStart(cameraX);
            int windowEnd = getWindowEnd(cameraX);
            // ROM parity: window is [windowStart, windowEnd) — objects at windowEnd
            // are outside the window (strict < on right boundary).
            Iterator<ObjectSpawn> iterator = active.iterator();
            while (iterator.hasNext()) {
                ObjectSpawn spawn = iterator.next();
                if (spawn.x() < windowStart || spawn.x() >= windowEnd) {
                    iterator.remove();
                }
            }
            clearDestroyedLatchOutsideWindow(windowStart, windowEnd);
        }

        private void refreshWindow(int cameraX) {
            int windowStart = getWindowStart(cameraX);
            int windowEnd = getWindowEnd(cameraX);
            int start = lowerBound(windowStart);
            // ROM parity: objects exactly AT the right edge are excluded (strict <).
            // lowerBound(windowEnd) gives the first index where x >= windowEnd,
            // so indices [start, end) have windowStart <= x < windowEnd.
            int end = lowerBound(windowEnd);
            cursorIndex = end;
            active.clear();
            for (int i = start; i < end; i++) {
                trySpawn(i);
            }
            clearDestroyedLatchOutsideWindow(windowStart, windowEnd);
        }

        private void trySpawn(int index) {
            ObjectSpawn spawn = spawns.get(index);
            if (remembered.get(index) && !stayActive.get(index)) {
                return;
            }
            // Don't respawn if destroyed while still in the window
            if (destroyedInWindow.get(index)) {
                return;
            }
            active.add(spawn);
        }

        // ================================================================
        // S1 counter-based respawn methods
        // ================================================================

        /**
         * Forward scan with counters (ROM: loc_D9F6 forward path, loc_DA02 loop).
         * Advances the right cursor to cameraChunk + LOAD_AHEAD, spawning
         * new objects entering from the right.
         */
        private void spawnForwardCountered(int cameraX) {
            int windowEnd = toCoarseChunk(cameraX) + LOAD_AHEAD;
            while (cursorIndex < spawns.size()
                    && spawns.get(cursorIndex).x() < windowEnd) {
                spawnForwardEntry(cursorIndex);
                cursorIndex++;
            }
        }

        /**
         * Helper: process one entry during forward scan.
         * ROM: assigns d2 = fwdCounter, then fwdCounter++ for respawn-tracked.
         */
        private void spawnForwardEntry(int index) {
            // Clear dormant: the cursor is re-scanning this position, matching
            // ROM behavior where ObjPosLoad re-processes the spawn entry.
            dormant.clear(index);
            ObjectSpawn spawn = spawns.get(index);
            if (spawn.respawnTracked()) {
                int counter = fwdCounter & 0xFF;
                fwdCounter = (fwdCounter + 1) & 0xFF;
                trySpawnCountered(index, counter);
            } else {
                // Non-tracked objects always spawn (ROM: loc_DA3C bpl → OPL_MakeItem)
                if (!destroyedInWindow.get(index)) {
                    active.add(spawn);
                    if (inlineCallback != null) {
                        inlineCallback.tryCreate(spawn, -1);
                    }
                }
            }
        }

        /**
         * Left cursor trim during forward movement (ROM: loc_DA24 loop).
         * Advances left cursor past objects that have left the window from
         * the left side, incrementing bwdCounter for respawn-tracked entries.
         * <p>
         * ROM parity: loc_DA24 ONLY advances the cursor and increments
         * bwdCounter. It does NOT destroy or unload the loaded objects.
         * Objects remain alive in the SST until they self-destruct via
         * their own out_of_range check in ExecuteObjects.
         * <p>
         * This is critical for moving objects (e.g. Batbrains) whose current
         * position (obX) differs from their spawn position. The engine's
         * cursor trim uses the spawn X, but the ROM's out_of_range uses the
         * object's current position. If a Batbrain has flown toward Sonic,
         * its current position may still be in range even though its spawn
         * position has passed the cursor boundary. Removing from active here
         * would prematurely unload the Batbrain.
         */
        private void trimLeftCountered(int cameraX) {
            int leftEdge = toCoarseChunk(cameraX) - UNLOAD_BEHIND;
            if (leftEdge <= 0) return;
            // ROM: `cmp.w (a0),d6; bls.s stop` → continues when leftEdge > entry.x
            while (leftCursorIndex < spawns.size()
                    && spawns.get(leftCursorIndex).x() < leftEdge) {
                ObjectSpawn spawn = spawns.get(leftCursorIndex);
                if (spawn.respawnTracked()) {
                    bwdCounter = (bwdCounter + 1) & 0xFF;
                }
                // ROM: loc_DA24 only advances cursor and bwdCounter.
                // Do NOT remove from active — objects stay alive until out_of_range.
                if (destroyedInWindow.get(leftCursorIndex)) {
                    destroyedInWindow.clear(leftCursorIndex);
                }
                // Spawn leaving the cursor window — clear dormant so it can
                // be normally re-loaded when the cursor re-enters this region.
                dormant.clear(leftCursorIndex);
                leftCursorIndex++;
            }
        }

        /**
         * Backward scan with counters (ROM: loc_D9A6 loop).
         * Retreats the left cursor to spawn objects entering the window
         * from the left as the camera scrolls backward.
         */
        private void spawnBackwardCountered(int cameraX) {
            int leftEdge = toCoarseChunk(cameraX) - UNLOAD_BEHIND;
            // ROM: `cmp.w -6(a0),d6; bge.s stop` → continues when leftEdge < prev.x
            while (leftCursorIndex > 0) {
                ObjectSpawn prev = spawns.get(leftCursorIndex - 1);
                if (leftEdge >= prev.x()) break;
                // Retreat cursor
                leftCursorIndex--;
                dormant.clear(leftCursorIndex); // cursor re-processing this entry
                if (prev.respawnTracked()) {
                    bwdCounter = (bwdCounter - 1) & 0xFF;
                    int counter = bwdCounter & 0xFF;
                    boolean spawned = trySpawnCountered(leftCursorIndex, counter);
                    if (!spawned) {
                        // ROM: loc_D9C6 — if bset blocked or FindFreeObj failed,
                        // undo counter and cursor retreat, then stop.
                        // (bset skip returns d0=0 → NOT treated as failure in ROM,
                        // but FindFreeObj failure IS. We only stop on FindFreeObj
                        // failure equivalent. bset skip continues the loop.)
                        //
                        // In the ROM, bset-skip returns d0=0 (success), so the
                        // loop continues. Only FindFreeObj failure stops the loop.
                        // The engine doesn't have FindFreeObj failure, so always
                        // continue.
                    }
                } else {
                    if (!destroyedInWindow.get(leftCursorIndex)) {
                        active.add(prev);
                    }
                }
            }
        }

        /**
         * Right cursor trim during backward movement (ROM: loc_D9DE loop).
         * Retreats the right cursor past objects that have left the window
         * from the right side, decrementing fwdCounter for respawn-tracked.
         * <p>
         * ROM parity: loc_D9DE ONLY retreats the cursor and adjusts fwdCounter.
         * It does NOT destroy or unload the objects. The loaded ring/object
         * instances remain alive in the SST and continue executing during
         * ExecuteObjects until they self-destruct via their own out_of_range
         * check (e.g., Ring_Animate → out_of_range → Ring_Delete).
         * <p>
         * Previously, the engine removed spawns from the active set here,
         * which caused syncActiveSpawnsUnload to force-unload objects. This
         * freed their SST slots too early, causing downstream slot assignment
         * differences (e.g., Batbrain timing gates firing 1 frame early).
         * Objects are now kept active and unloaded via the separate
         * out_of_range check in syncActiveSpawnsUnload.
         */
        private void trimRightCountered(int cameraX) {
            int rightEdge = toCoarseChunk(cameraX) + LOAD_AHEAD;
            // ROM: `cmp.w -6(a0),d6; bgt.s stop` → continues when rightEdge <= prev.x
            while (cursorIndex > 0) {
                ObjectSpawn prev = spawns.get(cursorIndex - 1);
                if (rightEdge > prev.x()) break;
                // Retreat cursor
                cursorIndex--;
                if (prev.respawnTracked()) {
                    fwdCounter = (fwdCounter - 1) & 0xFF;
                }
                // ROM: loc_D9DE only retreats cursor and fwdCounter.
                // Do NOT remove from active — objects stay alive until out_of_range.
                if (destroyedInWindow.get(cursorIndex)) {
                    destroyedInWindow.clear(cursorIndex);
                }
                // Spawn leaving the cursor window — clear dormant so it can
                // be normally re-loaded when the cursor re-enters this region.
                dormant.clear(cursorIndex);
            }
        }

        /**
         * Counter-aware spawn check (ROM: loc_DA3C with REV01 bset bug).
         * <p>
         * The ROM uses {@code bset #7,2(a2,d2.w)} which both TESTS and SETS
         * bit 7 in a single instruction. This means the first load sets the bit
         * as a side effect. If a later spawn attempt reuses the same counter
         * value (due to counter wrapping or camera backtracking through a
         * different object set), the bit is already set and the spawn is blocked.
         * <p>
         * This is the REV01 "remember sprite" bug documented at:
         * https://info.sonicretro.org/SCHG_How-to:Fix_a_remember_sprite_related_bug
         *
         * @param index   spawn list index
         * @param counter counter value (d2 register in ROM)
         * @return true if the object was added to the active set
         */
        private boolean trySpawnCountered(int index, int counter) {
            ObjectSpawn spawn = spawns.get(index);
            // ROM: bset #7,2(a2,d2.w) — test AND set bit 7
            boolean wasSet = (objState[counter & 0xFF] & 0x80) != 0;
            objState[counter & 0xFF] |= 0x80; // Side effect: always sets bit
            if (wasSet) {
                return false; // Bit was already set → skip
            }
            if (destroyedInWindow.get(index)) {
                return false;
            }
            spawnToCounter.put(spawn, counter & 0xFF);
            active.add(spawn);
            // ROM parity: inline instance creation during cursor scan.
            // This eliminates the 1-frame pipeline delay between cursor
            // advancement (placement.update) and instance creation (syncActiveSpawnsLoad).
            if (inlineCallback != null) {
                return inlineCallback.tryCreate(spawn, counter & 0xFF);
            }
            return true;
        }

        /**
         * Clears the objState bit for a normally-unloaded spawn.
         * ROM equivalent: RememberState's {@code bclr #7,2(a2,d0.w)}.
         * Called when an object leaves the camera window (not when destroyed).
         */
        private void clearCounterForSpawn(ObjectSpawn spawn) {
            Integer counter = spawnToCounter.remove(spawn);
            if (counter != null) {
                objState[counter] &= ~0x80;
            }
        }

        /**
         * Returns the counter value assigned to a spawn during loading,
         * or -1 if not tracked. Used by ObjectManager to set respawnStateIndex.
         */
        int getCounterForSpawn(ObjectSpawn spawn) {
            Integer counter = spawnToCounter.get(spawn);
            return counter != null ? counter : -1;
        }

        boolean isCounterBasedRespawn() {
            return counterBasedRespawn;
        }

        /**
         * ROM parity: true when the last chunk transition was leftward (backward).
         * Used by syncActiveSpawnsLoad to sort new spawns in descending X order,
         * matching S1 ObjPosLoad's backward scan direction (a0 -= 6).
         */
        boolean isLastScrollBackward() {
            return lastScrollBackward;
        }

        // Diagnostic accessors for cursor state comparison
        int getCursorIndex() { return cursorIndex; }
        int getLeftCursorIndex() { return leftCursorIndex; }
        int getFwdCounter() { return fwdCounter; }
        int getBwdCounter() { return bwdCounter; }
        int getLastCameraChunk() { return lastCameraChunk; }

        private int toCoarseChunk(int cameraX) {
            return cameraX & CHUNK_MASK;
        }

        /**
         * Extend the active set for spawns visible with the post-camera position.
         * <p>
         * When the post-camera X is in a higher chunk than the last processed chunk,
         * scans spawns in the gap between the old and new window right edges and
         * adds eligible ones to the active set.
         * <p>
         * In counter mode, this is a full forward scan that advances the cursor
         * and updates lastCameraChunk/lastCameraX, because counter values must
         * not be assigned twice (the next frame's update would see the already-
         * updated cursor and skip re-processing).
         * <p>
         * In non-counter mode, cursor and lastCameraChunk are NOT updated,
         * preserving the primary placement pass's ability to process the chunk
         * boundary normally on the next frame.
         */
        void extendForPostCamera(int postCameraX, SpawnCallback callback) {
            if (counterBasedRespawn) {
                // ROM parity: S1's ObjPosLoad runs AFTER DeformLayers (camera).
                // Inline creation eliminates 1-frame pipeline delay.
                updateAndLoad(postCameraX, callback);
                return;
            }
            extendForPostCamera(postCameraX);
        }

        void extendForPostCamera(int postCameraX) {
            if (counterBasedRespawn) {
                update(postCameraX);
                return;
            }
            int postChunk = toCoarseChunk(postCameraX);
            if (postChunk <= lastCameraChunk) {
                return; // Camera didn't advance to a new chunk
            }
            {
                int oldWindowEnd = getWindowEnd(lastCameraX);
                int newWindowEnd = postChunk + LOAD_AHEAD;
                for (int i = cursorIndex; i < spawns.size(); i++) {
                    int sx = spawns.get(i).x();
                    if (sx >= newWindowEnd) {
                        break;
                    }
                    if (sx >= oldWindowEnd) {
                        trySpawn(i);
                    }
                }
            }
        }

        /**
         * Returns true if the given spawn index has the destroyedInWindow latch set.
         */
        boolean isDestroyedInWindow(int index) {
            return index >= 0 && destroyedInWindow.get(index);
        }


        private void clearDestroyedLatchOutsideWindow(int windowStart, int windowEnd) {
            // Clear destroyed-in-window latch once the spawn fully leaves the current stream window.
            // ROM parity: window is [windowStart, windowEnd) — strict < on right boundary.
            for (int i = destroyedInWindow.nextSetBit(0); i >= 0; i = destroyedInWindow.nextSetBit(i + 1)) {
                ObjectSpawn spawn = spawns.get(i);
                if (spawn.x() < windowStart || spawn.x() >= windowEnd) {
                    destroyedInWindow.clear(i);
                }
            }
        }
    }

    static final class PlaneSwitchers {
        private static final Logger LOGGER = Logger.getLogger(PlaneSwitchers.class.getName());
        private static final int[] HALF_SPANS = new int[]{0x20, 0x40, 0x80, 0x100};
        private static final int MASK_SIZE = 0x03;
        private static final int MASK_HORIZONTAL = 0x04;
        private static final int MASK_PATH_SIDE1 = 0x08;
        private static final int MASK_PATH_SIDE0 = 0x10;
        private static final int MASK_PRIORITY_SIDE1 = 0x20;
        private static final int MASK_PRIORITY_SIDE0 = 0x40;
        private static final int MASK_GROUNDED_ONLY = 0x80;

        private final Placement placement;
        private final int objectId;
        private final PlaneSwitcherConfig config;
        private final Map<ObjectSpawn, PlaneSwitcherState> states = new HashMap<>();

        PlaneSwitchers(Placement placement, int objectId, PlaneSwitcherConfig config) {
            this.placement = placement;
            this.objectId = objectId & 0xFF;
            this.config = config;
        }

        void reset() {
            states.clear();
        }

        void update(PlayableEntity player) {
            if (placement == null || player == null || config == null) {
                return;
            }
            Collection<ObjectSpawn> active = placement.getActiveSpawns();
            if (active.isEmpty()) {
                return;
            }

            int playerX = player.getCentreX();
            int playerY = player.getCentreY();

            for (ObjectSpawn spawn : active) {
                if (spawn.objectId() != objectId) {
                    continue;
                }

                int subtype = spawn.subtype();
                PlaneSwitcherState state = states.computeIfAbsent(spawn,
                        key -> new PlaneSwitcherState(decodeHalfSpan(subtype)));

                boolean horizontal = isHorizontal(subtype);
                int sideNow = horizontal
                        ? (playerY >= spawn.y() ? 1 : 0)
                        : (playerX >= spawn.x() ? 1 : 0);
                if (!state.seeded) {
                    state.sideState = (byte) sideNow;
                    state.seeded = true;
                }

                int half = state.halfSpanPixels;
                boolean inSpan = horizontal
                        ? (playerX >= spawn.x() - half && playerX < spawn.x() + half)
                        : (playerY >= spawn.y() - half && playerY < spawn.y() + half);
                boolean groundedGate = onlySwitchWhenGrounded(subtype) && player.getAir();

                if (inSpan && !groundedGate && sideNow != state.sideState) {
                    boolean skipCollisionChange = (spawn.renderFlags() & 0x1) != 0;
                    if (!skipCollisionChange) {
                        int path = decodePath(subtype, sideNow);
                        player.setLayer((byte) path);
                        if (path == 0) {
                            player.setTopSolidBit(config.getPath0TopSolidBit());
                            player.setLrbSolidBit(config.getPath0LrbSolidBit());
                        } else {
                            player.setTopSolidBit(config.getPath1TopSolidBit());
                            player.setLrbSolidBit(config.getPath1LrbSolidBit());
                        }
                        LOGGER.fine(() -> String.format(
                                "PlaneSwitcher path=%d: player(%d,%d) obj(%d,%d) sub=0x%02X side=%d→%d air=%b mode=%s",
                                path, player.getCentreX(), player.getCentreY(),
                                spawn.x(), spawn.y(), subtype, state.sideState, sideNow,
                                player.getAir(), player.getGroundMode()));
                    }
                    boolean highPriority = decodePriority(subtype, sideNow);
                    player.setHighPriority(highPriority);
                }

                state.sideState = (byte) sideNow;
            }

            states.keySet().removeIf(spawn -> spawn.objectId() == objectId && !active.contains(spawn));
        }

        int getSideState(ObjectSpawn spawn) {
            PlaneSwitcherState state = states.get(spawn);
            if (state == null || !state.seeded) {
                return -1;
            }
            return state.sideState;
        }

        static int decodeHalfSpan(int subtype) {
            int index = subtype & MASK_SIZE;
            if (index < 0 || index >= HALF_SPANS.length) {
                index = 0;
            }
            return HALF_SPANS[index];
        }

        static boolean isHorizontal(int subtype) {
            return (subtype & MASK_HORIZONTAL) != 0;
        }

        static int decodePath(int subtype, int side) {
            int mask = side == 1 ? MASK_PATH_SIDE1 : MASK_PATH_SIDE0;
            return (subtype & mask) != 0 ? 1 : 0;
        }

        static boolean decodePriority(int subtype, int side) {
            int mask = side == 1 ? MASK_PRIORITY_SIDE1 : MASK_PRIORITY_SIDE0;
            return (subtype & mask) != 0;
        }

        static boolean onlySwitchWhenGrounded(int subtype) {
            return (subtype & MASK_GROUNDED_ONLY) != 0;
        }

        static char formatLayer(byte layer) {
            return layer == 0 ? 'A' : 'B';
        }

        static char formatPriority(boolean highPriority) {
            return highPriority ? 'H' : 'L';
        }

        private static final class PlaneSwitcherState {
            private final int halfSpanPixels;
            private byte sideState = 0;
            private boolean seeded = false;

            private PlaneSwitcherState(int halfSpanPixels) {
                this.halfSpanPixels = halfSpanPixels;
            }
        }
    }

    static final class TouchResponses {
        private static final Logger LOGGER = Logger.getLogger(TouchResponses.class.getName());
        private final ObjectManager objectManager;
        private final TouchResponseTable table;
        // Double-buffer pattern: swap buffers instead of allocating new sets each frame
        private final Set<ObjectInstance> bufferA = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<ObjectInstance> bufferB = Collections.newSetFromMap(new IdentityHashMap<>());
        private Set<ObjectInstance> overlapping = bufferA;
        private Set<ObjectInstance> building = bufferB;
        // Per-sidekick overlap tracking (each sidekick needs independent edge detection)
        private static class OverlapBufferPair {
            final Set<ObjectInstance> bufferA = Collections.newSetFromMap(new IdentityHashMap<>());
            final Set<ObjectInstance> bufferB = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<ObjectInstance> overlapping = bufferA;
            Set<ObjectInstance> building = bufferB;

            void swap() {
                Set<ObjectInstance> temp = overlapping;
                overlapping = building;
                building = temp;
            }

            void reset() {
                bufferA.clear();
                bufferB.clear();
                overlapping = bufferA;
                building = bufferB;
            }
        }

        private final Map<PlayableEntity, OverlapBufferPair> sidekickOverlaps = new IdentityHashMap<>();
        private final TouchResponseDebugState debugState = new TouchResponseDebugState();
        private static final int SHIELD_TOUCH_HALF_SIZE = 0x18;
        private static final int SHIELD_TOUCH_SIZE = SHIELD_TOUCH_HALF_SIZE * 2;
        private static final int SHIELD_REACTION_BOUNCE_BIT = 1 << 3;
        private int currentFrameCounter;
        private boolean instaShieldActive;
        private PlayableEntity currentPlayer;

        TouchResponses(ObjectManager objectManager, TouchResponseTable table) {
            this.objectManager = objectManager;
            this.table = table;
        }

        void reset() {
            bufferA.clear();
            bufferB.clear();
            overlapping = bufferA;
            building = bufferB;
            sidekickOverlaps.values().forEach(OverlapBufferPair::reset);
            currentFrameCounter = 0;
        }

        void update(PlayableEntity player, int frameCounter) {
            currentFrameCounter = frameCounter;
            if (player == null || objectManager == null || player.getDead() || table == null) {
                overlapping.clear();
                debugState.clear();
                return;
            }

            if (player.isDebugMode()) {
                overlapping.clear();
                debugState.clear();
                return;
            }

            int playerX = player.getCentreX() - 8;
            int baseYRadius = Math.max(1, player.getYRadius() - 3);
            // ROM: playerY = y_pos - (y_radius - 3). Do NOT subtract 8 from Y (only X).
            int playerY = player.getCentreY() - baseYRadius;
            int playerHeight = baseYRadius * 2;
            boolean crouching = player.getCrouching();
            if (crouching) {
                playerY += 12;
                playerHeight = 20;
            }
            // ROM (sonic3k.asm:20620-20640): Insta-shield expands hitbox to 48x48
            instaShieldActive = false;
            currentPlayer = player;
            int playerWidth = 0x10; // Normal width
            PhysicsFeatureSet fs = player.getPhysicsFeatureSet();
            if (fs != null && fs.instaShieldEnabled()
                    && player.getDoubleJumpFlag() == 1
                    && player.getShieldType() == null
                    && player.getInvincibleFrames() == 0) {
                instaShieldActive = true;
                playerX = player.getCentreX() - 0x18;
                playerY = player.getCentreY() - 0x18;
                playerHeight = 0x30;
                playerWidth = 0x30;
            }
            debugState.setPlayer(playerX, playerY, playerHeight, baseYRadius, crouching);
            debugState.clear();

            // Double-buffer pattern:
            // - 'overlapping' contains last frame's overlapping objects
            // - 'building' will be populated with this frame's overlapping objects
            // Clear building to prepare for this frame's data
            building.clear();

            processCollisionLoop(player, playerX, playerY, playerHeight, playerWidth,
                    building, overlapping, false);

            // Swap buffers: building becomes overlapping for next frame
            Set<ObjectInstance> temp = overlapping;
            overlapping = building;
            building = temp;
            instaShieldActive = false;
            currentPlayer = null;
        }

        /**
         * Touch response check for the CPU sidekick (Tails).
         * Uses separate overlap tracking from the main player.
         * ROM: In 1P mode, CPU Tails interacts with objects but doesn't scatter
         * rings when hurt and can never die from enemy contact.
         */
        void updateSidekick(PlayableEntity sidekick, int frameCounter) {
            currentPlayer = null; // Sidekick doesn't get insta-shield
            currentFrameCounter = frameCounter;
            OverlapBufferPair buffers = sidekickOverlaps.computeIfAbsent(sidekick, k -> new OverlapBufferPair());
            if (sidekick == null || objectManager == null || sidekick.getDead() || table == null) {
                buffers.overlapping.clear();
                return;
            }

            if (sidekick.isDebugMode()) {
                buffers.overlapping.clear();
                return;
            }

            int playerX = sidekick.getCentreX() - 8;
            int baseYRadius = Math.max(1, sidekick.getYRadius() - 3);
            int playerY = sidekick.getCentreY() - baseYRadius;
            int playerHeight = baseYRadius * 2;
            boolean crouching = sidekick.getCrouching();
            if (crouching) {
                playerY += 12;
                playerHeight = 20;
            }

            buffers.building.clear();

            processCollisionLoop(sidekick, playerX, playerY, playerHeight, 0x10,
                    buffers.building, buffers.overlapping, true);

            buffers.swap();
        }

        /**
         * Shared collision loop for both main player and sidekick touch responses.
         * Iterates active objects, checks touch regions and overlap, dispatches to the
         * appropriate response handler. Behavioral differences are parameterized:
         * - Player: records debug hits, breaks after first hit (ROM: bne.w branch)
         * - Sidekick: no debug recording, continues loop after hits
         *
         * @param player         the playable entity to check collisions for
         * @param playerX        hitbox left edge (centreX - 8, or insta-shield adjusted)
         * @param playerY        hitbox top edge (centreY - yRadius adjusted)
         * @param playerHeight   hitbox height
         * @param playerWidth    hitbox width (0x10 normal, 0x30 insta-shield)
         * @param buildingSet    the set being populated with this frame's overlapping objects
         * @param overlappingSet last frame's overlapping objects (for edge-trigger detection)
         * @param isSidekick     true for sidekick (no break-on-hit, sidekick response handler)
         */
        private void processCollisionLoop(PlayableEntity player,
                int playerX, int playerY, int playerHeight, int playerWidth,
                Set<ObjectInstance> buildingSet, Set<ObjectInstance> overlappingSet,
                boolean isSidekick) {
            Collection<ObjectInstance> activeObjects = objectManager.getActiveObjects();

            for (ObjectInstance instance : activeObjects) {
                if (!(instance instanceof TouchResponseProvider provider)) {
                    continue;
                }
                if (instance.isSkipTouchThisFrame()) {
                    continue;
                }

                // Multi-region providers (e.g., spiked pole helix) check each region independently
                TouchResponseProvider.TouchRegion[] regions = provider.getMultiTouchRegions();
                if (regions != null) {
                    boolean hit = processMultiRegionTouch(player, playerX, playerY, playerHeight,
                            instance, provider, regions, playerWidth,
                            buildingSet, overlappingSet, isSidekick);
                    if (!isSidekick && hit) {
                        break;
                    }
                    continue;
                }

                // ROM parity: ReactToItem checks "tst.b obRender(a1) / bpl.s .next"
                // for each object. If obRender bit 7 is clear (object not yet displayed
                // by DisplaySprite), the entire object is skipped. This covers:
                // (a) First-frame objects whose DisplaySprite hasn't run yet
                // (b) Objects that were offscreen on the previous frame
                // (c) Objects created by higher-slot makers that haven't run yet
                // Use isOnScreen() as the engine's equivalent of obRender bit 7.
                if (instance.isSkipSolidContactThisFrame()) {
                    continue;
                }
                if (instance instanceof AbstractObjectInstance aoi && !aoi.isOnScreenForTouch()) {
                    continue;
                }
                int preFlags = instance.getPreUpdateCollisionFlags();
                int flags = (preFlags >= 0) ? preFlags : provider.getCollisionFlags();
                if (flags == 0) {
                    continue; // Skip collision for objects with no collision flags
                }
                int sizeIndex = flags & 0x3F;
                int width = table.getWidthRadius(sizeIndex);
                int height = table.getHeightRadius(sizeIndex);
                TouchCategory category = decodeCategory(flags);
                if (category == TouchCategory.HURT
                        && tryShieldDeflect(player, instance, provider, width, height)) {
                    continue;
                }

                // ROM parity: ReactToItem runs in Sonic's slot (slot 0) BEFORE other
                // objects update. So touch collision sees objects at their pre-update
                // positions. Use getPreUpdateX()/getPreUpdateY() which return the
                // position snapshot taken before the object update loop ran.
                int objX = instance.getPreUpdateX();
                int objY = instance.getPreUpdateY();
                boolean overlap = isOverlapping(playerX, playerY, playerHeight, objX, objY, width, height, playerWidth);

                if (!isSidekick && debugState.isEnabled()) {
                    debugState.addHit(
                            new TouchResponseDebugHit(instance.getSpawn(), flags, sizeIndex, width, height, category, overlap));
                }
                if (!overlap) {
                    continue;
                }
                buildingSet.add(instance);
                // ROM touch checks run every frame for bosses. Most other objects are
                // edge-triggered, but some special objects need per-frame polling behavior.
                // ROM: Touch_ChkHurt runs every frame — no edge-triggering.
                // HURT must be continuous so damage re-applies after i-frames expire
                // while the player is still inside the hazard hitbox.
                boolean shouldTrigger = category == TouchCategory.BOSS
                        || category == TouchCategory.HURT
                        || provider.requiresContinuousTouchCallbacks()
                        || !overlappingSet.contains(instance);
                if (shouldTrigger) {
                    TouchResponseResult result = new TouchResponseResult(sizeIndex, width, height, category);
                    TouchResponseListener listener = instance instanceof TouchResponseListener casted ? casted : null;
                    if (isSidekick) {
                        handleTouchResponseSidekick(player, instance, listener, result);
                    } else {
                        handleTouchResponse(player, instance, listener, result);
                    }
                }
                // ROM parity: ReactToItem ALWAYS exits after the first overlapping
                // object, regardless of category or whether a response was triggered.
                // The ROM's handler returns via rts which exits the entire ReactToItem
                // subroutine. Previously the engine only broke when shouldTrigger was
                // true, allowing already-overlapping objects to be skipped and later
                // objects to be found — a 1-frame-early bounce when a lower-slot
                // non-enemy blocked ReactToItem in the ROM but was skipped here.
                if (!isSidekick) {
                    break;
                }
            }
        }

        /**
         * Unified multi-region touch check for both player and sidekick.
         * Each region is checked separately; if any overlaps, the object is treated as
         * overlapping with the first matching region's collision flags applied.
         * One hit per object per frame is sufficient — returns after first overlapping region.
         */
        private boolean processMultiRegionTouch(PlayableEntity player,
                int playerX, int playerY, int playerHeight,
                ObjectInstance instance, TouchResponseProvider provider,
                TouchResponseProvider.TouchRegion[] regions, int playerWidth,
                Set<ObjectInstance> buildingSet, Set<ObjectInstance> overlappingSet,
                boolean isSidekick) {
            for (TouchResponseProvider.TouchRegion region : regions) {
                int flags = region.collisionFlags();
                if (flags == 0) {
                    continue;
                }
                int sizeIndex = flags & 0x3F;
                int width = table.getWidthRadius(sizeIndex);
                int height = table.getHeightRadius(sizeIndex);
                TouchCategory category = decodeCategory(flags);

                boolean overlap = isOverlappingXY(playerX, playerY, playerHeight,
                        region.x(), region.y(), width, height, playerWidth);
                if (!overlap) {
                    continue;
                }

                buildingSet.add(instance);
                // ROM: HURT is continuous (same as BOSS) — see processCollisionLoop comment
                boolean shouldTrigger = category == TouchCategory.BOSS
                        || category == TouchCategory.HURT
                        || provider.requiresContinuousTouchCallbacks()
                        || !overlappingSet.contains(instance);
                if (shouldTrigger) {
                    TouchResponseResult result = new TouchResponseResult(sizeIndex, width, height, category);
                    TouchResponseListener listener = instance instanceof TouchResponseListener casted ? casted : null;
                    if (isSidekick) {
                        handleTouchResponseSidekick(player, instance, listener, result);
                    } else {
                        handleTouchResponse(player, instance, listener, result);
                    }
                }
                // ROM parity: ReactToItem ALWAYS exits on first overlap, even
                // when the response is edge-trigger suppressed. Match the
                // single-region break-on-first-overlap behaviour.
                return !isSidekick;
            }
            return false; // No region overlapped
        }

        private boolean tryShieldDeflect(PlayableEntity player, ObjectInstance instance,
                TouchResponseProvider provider, int objectWidth, int objectHeight) {
            if (player == null || !player.hasShield()) {
                return false;
            }
            if ((provider.getShieldReactionFlags() & SHIELD_REACTION_BOUNCE_BIT) == 0) {
                return false;
            }

            int shieldLeft = player.getCentreX() - SHIELD_TOUCH_HALF_SIZE;
            int shieldTop = player.getCentreY() - SHIELD_TOUCH_HALF_SIZE;
            boolean overlap = isRectOverlapping(shieldLeft, shieldTop, SHIELD_TOUCH_SIZE, SHIELD_TOUCH_SIZE,
                    instance.getX(), instance.getY(), objectWidth, objectHeight);
            if (!overlap) {
                return false;
            }
            return provider.onShieldDeflect(player);
        }

        /**
         * ROM: In 1P mode, CPU Tails interacts with objects but hurt handling differs:
         * - Can destroy badniks while rolling/invincible (same as Sonic)
         * - Gets knocked back when hurt but does NOT scatter rings or die
         * - Special category objects still interact normally
         */
        private void handleTouchResponseSidekick(PlayableEntity sidekick, ObjectInstance instance,
                TouchResponseListener listener, TouchResponseResult result) {
            if (sidekick == null) {
                return;
            }
            if (listener != null) {
                listener.onTouchResponse(sidekick, result, currentFrameCounter);
            }

            switch (result.category()) {
                case HURT -> applySidekickHurt(sidekick, instance);
                case ENEMY -> {
                    if (isPlayerAttacking(sidekick)) {
                        // ROM: Touch_Enemy_Part2 checks collision_property BEFORE decrementing HP.
                        int hpBeforeHit = 0;
                        if (instance instanceof TouchResponseProvider provider2) {
                            hpBeforeHit = provider2.getCollisionProperty();
                        }
                        if (instance instanceof TouchResponseAttackable attackable) {
                            attackable.onPlayerAttack(sidekick, result);
                        }
                        if (hpBeforeHit > 0) {
                            // Touch_Enemy_Part2: neg.w x_vel / neg.w y_vel
                            sidekick.setXSpeed((short) -sidekick.getXSpeed());
                            sidekick.setYSpeed((short) -sidekick.getYSpeed());
                        } else {
                            // Touch_KillEnemy: position-based bounce
                            applyEnemyBounce(sidekick, instance);
                        }
                    } else {
                        applySidekickHurt(sidekick, instance);
                    }
                }
                case SPECIAL -> {
                    // Listener handles object-specific logic.
                }
                case BOSS -> {
                    if (isPlayerAttacking(sidekick)) {
                        if (instance instanceof TouchResponseAttackable attackable) {
                            attackable.onPlayerAttack(sidekick, result);
                        }
                        applyBossBounce(sidekick);
                    } else {
                        applySidekickHurt(sidekick, instance);
                    }
                }
            }
        }

        /**
         * ROM: Hurt_Sidekick in 1P mode - just knockback, no ring scatter, no death.
         * From s2.asm HurtCharacter: in 1P mode, branches directly to Hurt_Sidekick
         * which applies hurt animation without checking rings.
         */
        private void applySidekickHurt(PlayableEntity sidekick, ObjectInstance instance) {
            if (sidekick.getInvulnerable()) {
                return;
            }
            int sourceX = instance != null ? instance.getX() : sidekick.getCentreX();
            // ROM: Hurt_Sidekick in 1P mode - just apply hurt knockback, no ring scatter
            sidekick.applyHurt(sourceX);
        }

        private boolean isOverlapping(int playerX, int playerY, int playerHeight,
                int objectX, int objectY, int objectWidth, int objectHeight, int playerWidth) {
            int dx = objectX - objectWidth - playerX;
            if (dx < 0) {
                int sum = (dx & 0xFFFF) + ((objectWidth * 2) & 0xFFFF);
                if (sum <= 0xFFFF) {
                    return false;
                }
            } else if (dx > playerWidth) {
                return false;
            }

            int dy = objectY - objectHeight - playerY;
            if (dy < 0) {
                int sum = (dy & 0xFFFF) + ((objectHeight * 2) & 0xFFFF);
                if (sum <= 0xFFFF) {
                    return false;
                }
            } else if (dy > playerHeight) {
                return false;
            }

            return true;
        }

        private boolean isRectOverlapping(int playerLeft, int playerTop, int playerWidth, int playerHeight,
                int objectX, int objectY, int objectWidth, int objectHeight) {
            int playerRight = playerLeft + playerWidth;
            int playerBottom = playerTop + playerHeight;
            int objectLeft = objectX - objectWidth;
            int objectRight = objectX + objectWidth;
            int objectTop = objectY - objectHeight;
            int objectBottom = objectY + objectHeight;
            return playerRight >= objectLeft
                    && playerLeft <= objectRight
                    && playerBottom >= objectTop
                    && playerTop <= objectBottom;
        }

        /**
         * Overlap check using raw x/y coordinates instead of ObjectSpawn.
         * Used by multi-region touch collision.
         */
        private boolean isOverlappingXY(int playerX, int playerY, int playerHeight,
                int objX, int objY, int objectWidth, int objectHeight, int playerWidth) {
            int dx = objX - objectWidth - playerX;
            if (dx < 0) {
                int sum = (dx & 0xFFFF) + ((objectWidth * 2) & 0xFFFF);
                if (sum <= 0xFFFF) {
                    return false;
                }
            } else if (dx > playerWidth) {
                return false;
            }

            int dy = objY - objectHeight - playerY;
            if (dy < 0) {
                int sum = (dy & 0xFFFF) + ((objectHeight * 2) & 0xFFFF);
                if (sum <= 0xFFFF) {
                    return false;
                }
            } else if (dy > playerHeight) {
                return false;
            }

            return true;
        }

        private TouchCategory decodeCategory(int flags) {
            int categoryBits = flags & 0xC0;
            return switch (categoryBits) {
                case 0x00 -> TouchCategory.ENEMY;
                case 0x40 -> TouchCategory.SPECIAL;
                case 0x80 -> TouchCategory.HURT;
                default -> TouchCategory.BOSS;
            };
        }

        private void handleTouchResponse(PlayableEntity player, ObjectInstance instance,
                TouchResponseListener listener, TouchResponseResult result) {
            if (player == null) {
                return;
            }
            if (listener != null) {
                listener.onTouchResponse(player, result, currentFrameCounter);
            }

            switch (result.category()) {
                case HURT -> applyHurt(player, instance);
                case ENEMY -> {
                    if (isPlayerAttacking(player)) {
                        // ROM: Touch_Enemy_Part2 checks collision_property BEFORE decrementing HP.
                        // Capture HP before onPlayerAttack (which may decrement it).
                        int hpBeforeHit = 0;
                        if (instance instanceof TouchResponseProvider provider2) {
                            hpBeforeHit = provider2.getCollisionProperty();
                        }
                        if (instance instanceof TouchResponseAttackable attackable) {
                            attackable.onPlayerAttack(player, result);
                        }
                        if (hpBeforeHit > 0) {
                            // Touch_Enemy_Part2: neg.w x_vel(a0) / neg.w y_vel(a0)
                            // Then clear collision_flags and decrement HP (handled by onPlayerAttack)
                            System.err.printf("[TOUCH_NEG] frame=%d obj=%s x=0x%04X y=0x%04X hp=%d playerX=0x%04X playerY=0x%04X xSpd=%d ySpd=%d%n",
                                    currentFrameCounter,
                                    instance.getClass().getSimpleName(), instance.getX(), instance.getY(),
                                    hpBeforeHit, player.getCentreX(), player.getCentreY(),
                                    player.getXSpeed(), player.getYSpeed());
                            player.setXSpeed((short) -player.getXSpeed());
                            player.setYSpeed((short) -player.getYSpeed());
                        } else {
                            // Touch_KillEnemy: position-based bounce (one-hit kill)
                            applyEnemyBounce(player, instance);
                        }
                    } else {
                        applyHurt(player, instance);
                    }
                }
                case SPECIAL -> {
                    // Listener handles object-specific logic.
                }
                case BOSS -> {
                    if (isPlayerAttacking(player)) {
                        if (instance instanceof TouchResponseAttackable attackable) {
                            attackable.onPlayerAttack(player, result);
                        }
                        applyBossBounce(player);
                    } else {
                        applyHurt(player, instance);
                    }
                }
            }
        }

        private boolean isPlayerAttacking(PlayableEntity player) {
            return player.isSuperSonic()
                    || player.getInvincibleFrames() > 0
                    || player.getRolling()
                    || player.getSpindash()
                    || (instaShieldActive && player == currentPlayer);
        }

        private void applyEnemyBounce(PlayableEntity player, ObjectInstance instance) {
            // ROM-accurate: React_Enemy (s1.asm) only modifies obVelY, it does NOT
            // set the air flag. Letting the collision system handle air state naturally
            // preserves rolling through enemy bounces (ground roll into badnik).
            short ySpeed = player.getYSpeed();
            // TEMPORARY DIAGNOSTIC
            System.err.printf("[ENEMY_BOUNCE] frame=%d vbla=%d obj=%s objX=0x%04X objY=0x%04X playerX=0x%04X playerY=0x%04X ySpd=0x%04X%n",
                currentFrameCounter, objectManager.vblaCounter,
                instance != null ? instance.getClass().getSimpleName() : "null",
                instance != null ? instance.getX() : 0,
                instance != null ? instance.getY() : 0,
                player.getCentreX(), player.getCentreY(),
                ySpeed & 0xFFFF);
            if (ySpeed < 0) {
                player.setYSpeed((short) (ySpeed + 0x100));
                return;
            }
            // Use center coordinates to match ROM y_pos behavior
            int playerY = player.getCentreY();
            // ROM: cmp.w y_pos(a1),d0 — use current position, not spawn
            int enemyY = instance != null ? instance.getY() : playerY;
            if (playerY < enemyY) {
                player.setYSpeed((short) -ySpeed);
            } else {
                player.setYSpeed((short) (ySpeed - 0x100));
            }
        }

        /**
         * ROM-accurate boss bounce: negate both X and Y velocities.
         * From s2.asm Touch_Enemy_Part2 lines 84806-84807.
         * Does not set air flag - ROM only modifies velocities here.
         */
        private void applyBossBounce(PlayableEntity player) {
            player.setXSpeed((short) -player.getXSpeed());
            player.setYSpeed((short) -player.getYSpeed());
        }

        private void applyHurt(PlayableEntity player, ObjectInstance instance) {
            if (player.getInvulnerable()) {
                return;
            }

            if (instance != null) {
                String className = instance.getClass().getSimpleName();
                int objectId = instance.getSpawn().objectId();
                LOGGER.fine(() -> "Touch hurt by: " + className + " (ID: 0x" + Integer.toHexString(objectId) + ")");
            }

            int sourceX = instance != null ? instance.getX() : player.getCentreX();
            boolean spikeHit = instance != null && instance.getSpawn().objectId() == 0x36;

            // S3K shield_reaction bit 4: fire shield blocks fire damage
            boolean fireHit = !spikeHit && instance instanceof TouchResponseProvider trp
                    && (trp.getShieldReactionFlags() & 0x10) != 0;

            DamageCause cause = spikeHit
                    ? DamageCause.SPIKE
                    : fireHit ? DamageCause.FIRE
                    : DamageCause.NORMAL;

            boolean hadRings = player.getRingCount() > 0;
            if (hadRings && !player.hasShield()) {
                // Escape hatch: LevelManager.spawnLostRings needs concrete type for RingManager
                if (player instanceof AbstractPlayableSprite aps) {
                    LevelManager.getInstance().spawnLostRings(aps, currentFrameCounter);
                }
            }
            player.applyHurtOrDeath(sourceX, cause, hadRings);
        }

        TouchResponseDebugState getDebugState() {
            return debugState;
        }
    }

    static final class SolidContacts {
        private static final Logger LOGGER = Logger.getLogger(SolidContacts.class.getName());
        private static final int OBJ85_ID = 0x85;
        private final ObjectManager objectManager;
        private int frameCounter;

        // Per-player riding state (ROM: each player object has its own SST interact field $3E)
        private record RidingState(ObjectInstance object, int x, int y, int pieceIndex) {}
        private final Map<PlayableEntity, RidingState> ridingStates = new IdentityHashMap<>(2);
        private PlayableEntity currentPlayer; // set during update() for internal use

        // ROM: objects like Obj_AIZLRZEMZRock save player velocity/anim BEFORE calling
        // SolidObjectFull, then check the saved values after. Our engine runs contact
        // resolution (which zeroes velocity, clears rolling) before onSolidContact fires.
        // Snapshot the player's pre-contact state so objects can read the "before" values.
        private short preContactXSpeed;
        private short preContactYSpeed;
        private boolean preContactRolling;

        // When true, the velocity classification adjustment in resolveContactInternal is
        // skipped. Set when this pass runs AFTER movement (S1 UNIFIED post-movement pass),
        // since the player's position already reflects their velocity.
        private boolean postMovement;

        // When true, this is a pre-movement pass for a game that will have a post-movement
        // pass (S1 UNIFIED). Side collision effects (speed zeroing, position correction)
        // should be deferred to the post-movement pass, because in the ROM, objects check
        // Sonic's position AFTER he has moved (his slot runs first in ExecuteObjects).
        // Standing/riding contacts still apply in pre-movement for platform delta tracking.
        private boolean deferSideToPostMovement;

        SolidContacts(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }

        void reset() {
            frameCounter = 0;
            ridingStates.clear();
        }

        boolean isRidingObject(PlayableEntity player) {
            if (player == null) return false;
            RidingState state = ridingStates.get(player);
            return state != null && state.object != null;
        }

        boolean isAnyPlayerRiding() {
            for (RidingState state : ridingStates.values()) {
                if (state != null && state.object != null) return true;
            }
            return false;
        }

        boolean isPlayerRiding(PlayableEntity player, ObjectInstance instance) {
            if (player == null) return false;
            RidingState state = ridingStates.get(player);
            return state != null && state.object == instance;
        }

        boolean isAnyPlayerRiding(ObjectInstance instance) {
            for (RidingState state : ridingStates.values()) {
                if (state != null && state.object == instance) return true;
            }
            return false;
        }

        void clearRidingObject(PlayableEntity player) {
            if (player != null) {
                ridingStates.remove(player);
            }
        }

        /**
         * Update the riding tracking position for a specific object without applying any delta.
         * This is used when an object moves itself (e.g. Tornado horizontal follow) AFTER
         * the player is already standing on it, to prevent SolidContacts from double-applying
         * that movement as a riding delta on the next frame.
         *
         * In the ROM, SolidObject runs inline during the object's update (before the horizontal
         * follow), so it never sees the follow delta. In our engine, SolidContacts runs after
         * all objects update, so we need this to synchronize the tracking position.
         */
        void refreshRidingTrackingPosition(ObjectInstance object) {
            for (var entry : ridingStates.entrySet()) {
                RidingState state = entry.getValue();
                if (state != null && state.object == object) {
                    entry.setValue(new RidingState(object, object.getX(), object.getY(), state.pieceIndex));
                }
            }
        }

        ObjectInstance getRidingObject(PlayableEntity player) {
            if (player == null) return null;
            RidingState state = ridingStates.get(player);
            return state != null ? state.object : null;
        }

        /** Get the piece index this player is riding, or -1 if not riding a multi-piece object. */
        int getRidingPieceIndex(PlayableEntity player) {
            if (player == null) return -1;
            RidingState state = ridingStates.get(player);
            return state != null ? state.pieceIndex : -1;
        }

        /** Check if the current player (set during update()) is riding the given object. Used by internal resolve methods. */
        private boolean isRidingCurrentPlayerObject(ObjectInstance instance) {
            if (currentPlayer == null) return false;
            RidingState state = ridingStates.get(currentPlayer);
            return state != null && state.object == instance;
        }

        /** Get the piece index the current player is riding. Used by internal resolve methods. */
        private int getCurrentPlayerRidingPieceIndex() {
            if (currentPlayer == null) return -1;
            RidingState state = ridingStates.get(currentPlayer);
            return state != null ? state.pieceIndex : -1;
        }

        /** Player X speed captured before any solid contact resolution modified it. */
        short getPreContactXSpeed() { return preContactXSpeed; }
        /** Player Y speed captured before any solid contact resolution modified it. */
        short getPreContactYSpeed() { return preContactYSpeed; }
        /** Player rolling state captured before any solid contact resolution modified it. */
        boolean getPreContactRolling() { return preContactRolling; }

        boolean hasStandingContact(PlayableEntity player) {
            if (player == null || objectManager == null || player.getDead()) {
                return false;
            }
            if (player.isDebugMode()) {
                return false;
            }
            if (player.getYSpeed() < 0) {
                return false;
            }
            PlayableEntity savedPlayer = currentPlayer;
            currentPlayer = player;
            try {
                Collection<ObjectInstance> activeObjects = objectManager.getActiveObjects();
                for (ObjectInstance instance : activeObjects) {
                    if (!(instance instanceof SolidObjectProvider provider)) {
                        continue;
                    }
                    if (!provider.isSolidFor(player)) {
                        continue;
                    }

                    if (provider instanceof MultiPieceSolidProvider multiPiece) {
                        if (hasStandingContactMultiPiece(player, multiPiece, instance)) {
                            return true;
                        }
                        continue;
                    }

                    SolidObjectParams params = provider.getSolidParams();
                    int anchorX = instance.getX() + params.offsetX();
                    int anchorY = instance.getY() + params.offsetY();
                    // ROM always uses airHalfHeight (d2) for the overlap test — d3 is
                    // overwritten by playerYRadius before it is read.
                    int halfHeight = params.airHalfHeight();
                    boolean useStickyBuffer = provider.usesStickyContactBuffer();
                    byte[] slopeData = null;
                    if (instance instanceof SlopedSolidProvider sloped) {
                        slopeData = sloped.getSlopeData();
                    }
                    SolidContact contact;
                    if (slopeData != null && instance instanceof SlopedSolidProvider sloped) {
                        int slopeHalfHeight = params.groundHalfHeight();
                        contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(), slopeHalfHeight,
                                slopeData, sloped.isSlopeFlipped(), provider.isTopSolidOnly(),
                                useStickyBuffer, instance, false, sloped);
                    } else {
                        contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                                provider.isTopSolidOnly(), provider.hasMonitorSolidity(),
                                useStickyBuffer, instance, false);
                    }
                    if (contact != null && contact.standing()) {
                        return true;
                    }
                }
                return false;
            } finally {
                currentPlayer = savedPlayer;
            }
        }

        private boolean hasStandingContactMultiPiece(PlayableEntity player,
                MultiPieceSolidProvider multiPiece, ObjectInstance instance) {
            int pieceCount = multiPiece.getPieceCount();
            for (int i = 0; i < pieceCount; i++) {
                SolidObjectParams params = multiPiece.getPieceParams(i);
                int anchorX = multiPiece.getPieceX(i) + params.offsetX();
                int anchorY = multiPiece.getPieceY(i) + params.offsetY();
                int halfHeight = player.getAir() ? params.airHalfHeight() : params.groundHalfHeight();
                boolean useStickyBuffer = multiPiece.usesStickyContactBuffer();

                // Multi-piece solids don't use monitor solidity
                SolidContact contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        multiPiece.isTopSolidOnly(), false, useStickyBuffer, instance, false);
                if (contact != null && contact.standing()) {
                    return true;
                }
            }
            return false;
        }

        int getHeadroomDistance(PlayableEntity player, int hexAngle) {
            if (player == null || objectManager == null || player.getDead()) {
                return Integer.MAX_VALUE;
            }
            if (player.isDebugMode()) {
                return Integer.MAX_VALUE;
            }

            int overheadAngle = (hexAngle + 0x80) & 0xFF;
            int quadrant = (overheadAngle + 0x20) & 0xC0;

            int minDistance = Integer.MAX_VALUE;
            int playerCenterX = player.getCentreX();
            int playerCenterY = player.getCentreY();
            int playerXRadius = player.getXRadius();
            int playerYRadius = player.getYRadius();

            Collection<ObjectInstance> activeObjects = objectManager.getActiveObjects();
            for (ObjectInstance instance : activeObjects) {
                if (!(instance instanceof SolidObjectProvider provider)) {
                    continue;
                }
                if (!provider.isSolidFor(player)) {
                    continue;
                }
                if (provider.isTopSolidOnly()) {
                    continue;
                }
                SolidObjectParams params = provider.getSolidParams();
                int anchorX = instance.getX() + params.offsetX();
                int anchorY = instance.getY() + params.offsetY();
                int halfWidth = params.halfWidth();
                int halfHeight = params.groundHalfHeight();

                int distance = calculateOverheadDistance(quadrant, playerCenterX, playerCenterY,
                        playerXRadius, playerYRadius, anchorX, anchorY, halfWidth, halfHeight);
                if (distance >= 0 && distance < minDistance) {
                    minDistance = distance;
                }
            }
            return minDistance;
        }

        private int calculateOverheadDistance(int quadrant, int playerCenterX, int playerCenterY,
                int playerXRadius, int playerYRadius, int objX, int objY, int objHalfWidth, int objHalfHeight) {
            switch (quadrant) {
                case 0x40 -> {
                    int objRight = objX + objHalfWidth;
                    int playerLeft = playerCenterX - playerXRadius;
                    if (playerLeft < objRight) {
                        return -1;
                    }
                    int objTop = objY - objHalfHeight;
                    int objBottom = objY + objHalfHeight;
                    int playerTop = playerCenterY - playerYRadius;
                    int playerBottom = playerCenterY + playerYRadius;
                    if (playerBottom < objTop || playerTop > objBottom) {
                        return -1;
                    }
                    return playerLeft - objRight;
                }
                case 0x80 -> {
                    int objBottom = objY + objHalfHeight;
                    int playerTop = playerCenterY - playerYRadius;
                    if (playerTop < objBottom) {
                        return -1;
                    }
                    int objLeft = objX - objHalfWidth;
                    int objRight = objX + objHalfWidth;
                    int playerLeft = playerCenterX - playerXRadius;
                    int playerRight = playerCenterX + playerXRadius;
                    if (playerRight < objLeft || playerLeft > objRight) {
                        return -1;
                    }
                    return playerTop - objBottom;
                }
                case 0xC0 -> {
                    int objLeft = objX - objHalfWidth;
                    int playerRight = playerCenterX + playerXRadius;
                    if (playerRight > objLeft) {
                        return -1;
                    }
                    int objTop = objY - objHalfHeight;
                    int objBottom = objY + objHalfHeight;
                    int playerTop = playerCenterY - playerYRadius;
                    int playerBottom = playerCenterY + playerYRadius;
                    if (playerBottom < objTop || playerTop > objBottom) {
                        return -1;
                    }
                    return objLeft - playerRight;
                }
                default -> {
                    return Integer.MAX_VALUE;
                }
            }
        }

        // KNOWN ARCHITECTURAL DIFFERENCE: The ROM processes solid object contacts inline
        // during each object's update routine, so each subsequent object sees the player's
        // position as updated by earlier solid contacts within the same frame. This engine
        // instead batches all solid contacts in a single pass after object updates, meaning
        // every object sees the player's position from the START of the pass. This can cause
        // differences with adjacent or sandwiching solid objects and with crush detection
        // timing, since the cumulative position adjustments don't propagate between objects
        // within the same frame. The sticky buffer and subpixel workarounds partially
        // compensate for this, but a full fix would require per-object inline resolution
        // integrated into the object update loop.
        void update(PlayableEntity player, boolean postMovement) {
            this.postMovement = postMovement;
            frameCounter++;
            if (player == null || objectManager == null || player.getDead()) {
                if (player != null) ridingStates.remove(player);
                return;
            }

            if (player.isDebugMode()) {
                ridingStates.remove(player);
                return;
            }

            // Set currentPlayer so internal resolveContact/resolveSlopedContact can check
            // this player's riding state via isRidingCurrentPlayerObject()
            currentPlayer = player;

            // Snapshot pre-contact state before any resolveContact can modify the player.
            preContactXSpeed = player.getXSpeed();
            preContactYSpeed = player.getYSpeed();
            preContactRolling = player.getRolling();

            // Note: Do NOT clear pushing here. Terrain collision handles pushing for terrain walls,
            // and solid object collision sets pushing when appropriate. Clearing here would
            // override the pushing flag set by terrain collision earlier in the same frame.

            Collection<ObjectInstance> activeObjects = objectManager.getActiveObjects();

            // Extract this player's riding state
            RidingState state = ridingStates.get(player);
            ObjectInstance ridingObject = state != null ? state.object : null;
            int ridingX = state != null ? state.x : 0;
            int ridingY = state != null ? state.y : 0;
            int ridingPieceIndex = state != null ? state.pieceIndex : -1;
            ObjectInstance dropOnFloorExclude = null;

            // ROM: Sonic_Jump does "bclr #sta_onObj,obStatus(a0)" before any
            // platform's SolidObject routine runs.  If the player is airborne,
            // they must not be repositioned by a stale riding record.
            if (ridingObject != null && player.getAir()) {
                ridingStates.remove(player);
                ridingObject = null;
                ridingPieceIndex = -1;
                player.setOnObject(false);
            }
            if (ridingObject != null && ridingObject instanceof SolidObjectProvider provider) {
                int currentX;
                int currentY;
                SolidObjectParams params;

                if (ridingPieceIndex >= 0 && ridingObject instanceof MultiPieceSolidProvider multiPiece) {
                    currentX = multiPiece.getPieceX(ridingPieceIndex);
                    currentY = multiPiece.getPieceY(ridingPieceIndex);
                    params = multiPiece.getPieceParams(ridingPieceIndex);
                } else {
                    currentX = ridingObject.getX();
                    currentY = ridingObject.getY();
                    params = provider.getSolidParams();
                }

                int halfWidth = params.halfWidth();
                // ROM: ExitPlatform ALWAYS uses the full collision halfWidth (obActWid + $B)
                // for continued riding bounds. The narrower obActWid is only used by
                // Solid_Landed for new landings. Do NOT use getTopLandingHalfWidth() here.
                int ridingHalfWidth = halfWidth;

                // ROM: Bounds check uses collision-offset X (anchorX = obX + offsetX),
                // while delta tracking uses raw object X for movement following.
                int boundsX = currentX + params.offsetX();
                int relX = player.getCentreX() - boundsX + ridingHalfWidth;
                // ROM: ExitPlatform uses exact bounds (relX in [0, width*2)).
                // The sticky buffer is for engine-side jitter compensation on moving
                // platforms (S2/S3K), but S1 UNIFIED processes objects after movement
                // so there's no jitter to compensate. Sloped objects also use exact bounds.
                boolean isSloped = (ridingObject instanceof SlopedSolidProvider);
                int stickyX = (!isSloped && !postMovement && provider.usesStickyContactBuffer()
                        && ridingHalfWidth == halfWidth) ? 16 : 0;
                int minRelX = -stickyX;
                int maxRelXExclusive = (ridingHalfWidth * 2) + stickyX;
                boolean inBounds = relX >= minRelX && relX < maxRelXExclusive;

                // ROM: s2.asm:35387 — skip repositioning if obj_control bit 7 set
                if (inBounds && provider.isSolidFor(player) && !player.isObjectControlled()) {
                    // ROM: s2.asm:35377-35401 — X uses delta tracking, Y uses absolute positioning
                    int deltaX = currentX - ridingX;
                    if (deltaX != 0) {
                        player.shiftX(deltaX);
                    }
                    // ROM: Sloped objects use SlopeObject2 for riding updates,
                    // which re-samples the slope heightmap at the player's X each
                    // frame: surfaceY = obY - slopeSample; playerY = surfaceY - yRadius.
                    // Flat objects use MvSonicOnPtfm: y = obY - d3 - yRadius.
                    // d3 is the GROUND half-height (walking), NOT d2 (air/jumping).
                    // ROM: MvSonicOnPtfm uses d3 which was set by the caller before
                    // SolidObject was called. For spikes d3=$11, push blocks d3=$11,
                    // platforms d3=$10, etc.
                    int surfaceOffset;
                    if (ridingObject instanceof SlopedSolidProvider sloped) {
                        int slopeY = sampleSlopeY(player, currentX, params.halfWidth(), sloped);
                        surfaceOffset = (slopeY != Integer.MIN_VALUE) ? slopeY : params.groundHalfHeight();
                    } else {
                        surfaceOffset = params.groundHalfHeight();
                    }
                    int newCentreY = currentY + params.offsetY() - surfaceOffset - player.getYRadius();
                    int newY = newCentreY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    ridingX = currentX;
                    ridingY = currentY;
                    // Update state with new tracking position
                    ridingStates.put(player, new RidingState(ridingObject, ridingX, ridingY, ridingPieceIndex));

                    // ROM: DropOnFloor (s2.asm:35810) — after repositioning the player
                    // on a platform, check if terrain is at or above the player's feet.
                    // If so, detach the player so terrain collision takes over next frame.
                    if (provider.dropOnFloor()) {
                        TerrainCheckResult floorCheck = ObjectTerrainUtils.checkFloorDist(
                                player.getCentreX(), player.getCentreY(), player.getYRadius());
                        if (floorCheck.distance() <= 0) {
                            dropOnFloorExclude = ridingObject;
                            ridingStates.remove(player);
                            ridingObject = null;
                            ridingPieceIndex = -1;
                            player.setOnObject(false);
                            player.setAir(true);
                        }
                    }
                } else {
                    ridingStates.remove(player);
                    ridingObject = null;
                    ridingPieceIndex = -1;
                }
            }

            // S1 UNIFIED: the riding section is the ExitPlatform equivalent. The ROM
            // never re-runs Solid_ChkEnter for the standing object. Track the maintained
            // object so we can skip it and preserve the riding state.
            ObjectInstance ridingMaintained = postMovement ? ridingObject : null;
            ObjectInstance nextRidingObject = null;
            int nextRidingX = 0;
            int nextRidingY = 0;
            int nextRidingPieceIndex = -1;
            for (ObjectInstance instance : activeObjects) {
                // DropOnFloor detached the player from this object — don't re-land on it
                // this frame. Terrain collision will handle the player next frame.
                if (instance == dropOnFloorExclude) {
                    continue;
                }
                // S1 UNIFIED: riding section already handled this via ExitPlatform.
                // ROM returns d4=0; Solid_ChkEnter is never reached. Keep as standing
                // and fire callback, but skip resolveContact which would misclassify as SIDE.
                if (instance == ridingMaintained) {
                    nextRidingObject = instance;
                    nextRidingX = instance.getX();
                    nextRidingY = instance.getY();
                    nextRidingPieceIndex = -1;
                    if (instance instanceof SolidObjectListener listener) {
                        listener.onSolidContact(player, SolidContact.STANDING, frameCounter);
                    }
                    continue;
                }
                if (!(instance instanceof SolidObjectProvider provider)) {
                    continue;
                }
                if (!provider.isSolidFor(player)) {
                    continue;
                }

                // ROM: SolidObject_ChkBounds (s2.asm:35175-35176) — when obj_control bit 7
                // is set, SolidObject returns "no collision". This prevents captured/spring-locked
                // players from interacting with other solid objects (avoids crush death, position
                // shifts, and state corruption while the controlling object manages the player).
                if (player.isObjectControlled()) {
                    continue;
                }

                // ROM parity: Objects skip SolidObject on their first frame because
                // obRender bit 7 hasn't been set yet (DisplaySprite hasn't run).
                // Exception: the currently-ridden object must not be skipped, since
                // the ROM's ExitPlatform path (top of SolidObject) doesn't check
                // obRender — it always processes the standing player.
                if (instance.isSkipSolidContactThisFrame() && instance != ridingObject) {
                    continue;
                }

                if (provider instanceof MultiPieceSolidProvider multiPiece) {
                    MultiPieceContactResult result = processMultiPieceCollision(
                            player, multiPiece, instance, frameCounter, provider.usesStickyContactBuffer());
                    if (result.pushing) {
                        player.setPushing(true);
                        // ROM: s2.asm:35220-35226 — also set pushing bit on the object
                        provider.setPlayerPushing(player, true);
                    }
                    if (result.standing) {
                        nextRidingObject = instance;
                        nextRidingX = result.ridingX;
                        nextRidingY = result.ridingY;
                        nextRidingPieceIndex = result.pieceIndex;
                    }
                    continue;
                }

                SolidObjectParams params = provider.getSolidParams();
                int anchorX = instance.getX() + params.offsetX();
                int anchorY = instance.getY() + params.offsetY();
                // ROM always uses airHalfHeight (d2) for the overlap test — d3 is
                // overwritten by playerYRadius before it is read.
                int halfHeight = params.airHalfHeight();
                boolean useStickyBuffer = provider.usesStickyContactBuffer();

                SolidContact contact;
                byte[] slopeData = null;
                if (instance instanceof SlopedSolidProvider sloped) {
                    slopeData = sloped.getSlopeData();
                }

                if (slopeData != null && instance instanceof SlopedSolidProvider sloped) {
                    // ROM parity: when already riding a sloped object, the ROM does NOT
                    // re-run SolidObject2F. It only runs ExitPlatform + SlopeObject2,
                    // which is handled by the riding update above. Re-running the full
                    // collision check can produce false SIDE contacts when Sonic is near
                    // the platform edge (absDistX <= absDistY), causing premature
                    // detachment. Skip the collision check and preserve the riding state.
                    if (instance == ridingObject) {
                        nextRidingObject = instance;
                        nextRidingX = instance.getX();
                        nextRidingY = instance.getY();
                        nextRidingPieceIndex = -1;
                        if (instance instanceof SolidObjectListener listener) {
                            listener.onSolidContact(player, SolidContact.STANDING, frameCounter);
                        }
                        continue;
                    }
                    int slopeHalfHeight = params.groundHalfHeight();
                    contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(), slopeHalfHeight,
                            slopeData, sloped.isSlopeFlipped(), provider.isTopSolidOnly(),
                            useStickyBuffer, instance, true, sloped);
                } else {
                    contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                            provider.isTopSolidOnly(), provider.hasMonitorSolidity(),
                            useStickyBuffer, instance, true);
                }

                if (contact == null) {
                    continue;
                }
                if (contact.pushing()) {
                    player.setPushing(true);
                    // ROM: s2.asm:35220-35226 — also set pushing bit on the object
                    provider.setPlayerPushing(player, true);
                }
                if (contact.standing()) {
                    nextRidingObject = instance;
                    nextRidingX = instance.getX();
                    nextRidingY = instance.getY();
                    nextRidingPieceIndex = -1;
                }
                if (instance instanceof SolidObjectListener listener) {
                    listener.onSolidContact(player, contact, frameCounter);
                }
            }

            if (nextRidingObject != null) {
                ridingStates.put(player, new RidingState(nextRidingObject, nextRidingX, nextRidingY, nextRidingPieceIndex));
            } else {
                ridingStates.remove(player);
            }

            // ROM: bclr #status.player.on_object when not standing on any object
            // Also clear when player becomes airborne (jumping/falling off) - s2.asm has many instances
            // of this paired with bset #status.player.in_air
            if (nextRidingObject == null) {
                player.setOnObject(false);
            }

            currentPlayer = null;
        }

        private record MultiPieceContactResult(boolean standing, boolean pushing, int ridingX, int ridingY, int pieceIndex) {}

        private MultiPieceContactResult processMultiPieceCollision(PlayableEntity player,
                MultiPieceSolidProvider multiPiece, ObjectInstance instance, int frameCounter,
                boolean useStickyBuffer) {
            int pieceCount = multiPiece.getPieceCount();

            boolean anyStanding = false;
            boolean anyPushing = false;
            boolean anyTouchTop = false;
            boolean anyTouchBottom = false;
            boolean anyTouchSide = false;

            int standingPieceIndex = -1;
            int standingPieceX = 0;
            int standingPieceY = 0;

            for (int i = 0; i < pieceCount; i++) {
                SolidObjectParams params = multiPiece.getPieceParams(i);
                int pieceX = multiPiece.getPieceX(i);
                int pieceY = multiPiece.getPieceY(i);
                int anchorX = pieceX + params.offsetX();
                int anchorY = pieceY + params.offsetY();
                int halfHeight = player.getAir() ? params.airHalfHeight() : params.groundHalfHeight();

                // Multi-piece solids don't use monitor solidity
                // Pass piece index so sticky buffer only applies to the piece being ridden
                SolidContact contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        multiPiece.isTopSolidOnly(), false, useStickyBuffer, instance, i, true);

                if (contact == null) {
                    continue;
                }

                if (contact.standing()) {
                    anyStanding = true;
                    if (standingPieceIndex < 0) {
                        standingPieceIndex = i;
                        standingPieceX = pieceX;
                        standingPieceY = pieceY;
                    }
                }
                if (contact.touchTop()) {
                    anyTouchTop = true;
                }
                if (contact.touchBottom()) {
                    anyTouchBottom = true;
                }
                if (contact.touchSide()) {
                    anyTouchSide = true;
                }
                if (contact.pushing()) {
                    anyPushing = true;
                }

                multiPiece.onPieceContact(i, player, contact, frameCounter);
            }

            if (anyStanding || anyTouchTop || anyTouchBottom || anyTouchSide || anyPushing) {
                if (instance instanceof SolidObjectListener listener) {
                    SolidContact aggregateContact = new SolidContact(
                            anyStanding, anyTouchSide, anyTouchBottom, anyTouchTop, anyPushing);
                    listener.onSolidContact(player, aggregateContact, frameCounter);
                }
            }

            return new MultiPieceContactResult(anyStanding, anyPushing, standingPieceX, standingPieceY, standingPieceIndex);
        }

        /**
         * Resolve contact for single-piece objects (backwards compatibility).
         */
        private SolidContact resolveContact(PlayableEntity player,
                int anchorX, int anchorY, int halfWidth, int halfHeight, boolean topSolidOnly,
                boolean monitorSolidity, boolean useStickyBuffer, ObjectInstance instance, boolean apply) {
            return resolveContact(player, anchorX, anchorY, halfWidth, halfHeight, topSolidOnly,
                    monitorSolidity, useStickyBuffer, instance, -1, apply);
        }

        /**
         * Resolve contact with piece index support for multi-piece objects.
         * @param pieceIndex The piece index being checked, or -1 for single-piece objects
         */
        private SolidContact resolveContact(PlayableEntity player,
                int anchorX, int anchorY, int halfWidth, int halfHeight, boolean topSolidOnly,
                boolean monitorSolidity, boolean useStickyBuffer, ObjectInstance instance, int pieceIndex, boolean apply) {
            int playerCenterX = player.getCentreX();
            int playerCenterY = player.getCentreY();

            int width2 = halfWidth * 2;
            int relXRaw = playerCenterX - anchorX + halfWidth;

            int playerYRadius = player.getYRadius();
            int maxTop = halfHeight + playerYRadius;
            // SPG: Monitors don't add +4 during vertical overlap check
            int verticalOffset = monitorSolidity ? 0 : 4;
            // ROM: s2.asm:35147 — mask with $7FF to handle edge cases near y_pos=0
            int relY = ((playerCenterY - anchorY + verticalOffset + maxTop) & 0x7FF);

            boolean riding = useStickyBuffer && isRidingCurrentPlayerObject(instance);
            // For multi-piece objects, only apply sticky buffer (-16px) when checking
            // the specific piece being ridden, not all pieces of the same object.
            // pieceIndex < 0 means single-piece object (always apply sticky buffer if riding)
            int currentRidingPieceIndex = getCurrentPlayerRidingPieceIndex();
            boolean ridingThisPiece = riding && (pieceIndex < 0 || pieceIndex == currentRidingPieceIndex);
            // Sticky solids approximate SolidObject's old/new X handoff behavior in the
            // original engine by allowing a small horizontal retention window on the
            // ridden piece. Without this, fast moving platforms (ObjB2 Tornado, etc.)
            // can drop contact for one frame at edges.
            int stickyX = ridingThisPiece ? 16 : 0;
            if (relXRaw < -stickyX || relXRaw >= width2 + stickyX) {
                return null;
            }
            // Clamp back into the normal box before side/top resolution so all existing
            // distance math keeps its established behavior.
            int relX = relXRaw;
            if (relX < 0) {
                relX = 0;
            } else if (relX >= width2) {
                relX = width2 - 1;
            }

            int minRelY = ridingThisPiece ? -16 : 0;

            if (relY < minRelY || relY >= maxTop * 2) {
                return null;
            }

            if (monitorSolidity) {
                return resolveMonitorContact(player, relX, relY, halfWidth, maxTop, playerCenterX, playerCenterY,
                        anchorX, riding, apply);
            }
            return resolveContactInternal(player, relX, relY, halfWidth, maxTop, playerCenterX, playerCenterY,
                    topSolidOnly, riding, apply, instance);
        }

        /**
         * Monitor-specific collision resolution (SPG: "Item Monitor").
         * Differences from normal solid objects:
         * - Landing only if playerY - topCombinedBox < 16 AND within monitor X ± (halfWidth + 4)
         * - Never pushes player downward, only to sides
         */
        private SolidContact resolveMonitorContact(PlayableEntity player, int relX, int relY,
                int halfWidth, int maxTop, int playerCenterX, int playerCenterY, int anchorX,
                boolean sticky, boolean apply) {
            // ROM: Mon_Solid / SolidObject_Monitor_Sonic — rolling check runs AFTER
            // Mon_SolidSides geometry detection, not as an external gate.
            // When the player is rolling AND velY >= 0 (not moving upward), skip
            // the solid response entirely so the touch system can break the monitor.
            // When velY < 0 (moving upward), always handle as solid (side push / landing)
            // regardless of rolling state — this is the S1 "always solid when moving up" rule.
            // When standing on the monitor (sticky), always handle as solid (ride mode,
            // ROM: ob2ndRout=2 bypasses Mon_SolidSides entirely).
            if (!sticky && player.getRolling() && player.getYSpeed() >= 0) {
                return null;
            }

            // Calculate distances from center
            int distX;
            int absDistX;
            if (relX >= halfWidth) {
                distX = relX - (halfWidth * 2);
                absDistX = -distX;
            } else {
                distX = relX;
                absDistX = distX;
            }

            int distY;
            if (relY <= maxTop) {
                distY = relY;
            } else {
                // No +4 offset for monitor bottom collision (since we didn't add it in overlap)
                distY = relY - (maxTop * 2);
            }

            // ROM: Mon_SolidSides checks cmpi.w #$10,d3 — relY < 16 means near-top zone
            boolean canLand = distY >= 0 && distY < 16;

            // ROM: Mon_SolidSides narrow top landing zone uses obActWid+4, NOT
            // the full collision halfWidth. obActWid = halfWidth - $B (the $B comes
            // from the "addi.w #$B,d1" that creates the collision box from obActWid).
            // So the landing margin = (halfWidth - $B) + 4 = halfWidth - 7.
            int xFromCenter = relX - halfWidth;
            int landingXMargin = halfWidth - 7;
            boolean withinLandingX = Math.abs(xFromCenter) <= landingXMargin;

            if (canLand && withinLandingX) {
                // Landing on top
                if (player.getYSpeed() < 0) {
                    return null;
                }

                if (apply) {
                    int newCenterY = playerCenterY - distY + 3;
                    int newY = newCenterY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    // ROM: Solid_ResetFloor unconditionally sets these.
                    player.setAngle((byte) 0);
                    player.setYSpeed((short) 0);
                    player.setGSpeed(player.getXSpeed());
                    if (player.getAir()) {
                        LOGGER.fine(() -> "Monitor landing at (" + player.getX() + "," + player.getY() +
                            ") distY=" + distY);
                        player.setAir(false);
                        clearRollingOnLanding(player);
                        player.setGroundMode(GroundMode.GROUND);
                    }
                    // ROM: bset #status.player.on_object (s2.asm:35739)
                    player.setOnObject(true);
                }
                return SolidContact.STANDING;
            }

            // ROM loc_A220: Monitors never push player downward, only to sides.
            // The positional push and velocity zeroing ONLY happen when the player
            // is actively moving INTO the monitor. When d0=0 (centered) or moving
            // away, the ROM skips the push entirely (falls through to loc_A246).
            boolean leftSide = playerCenterX <= anchorX;
            boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
            boolean pushing = !player.getAir() && movingInto;
            boolean skipMonitorSide = deferSideToPostMovement && player.getAir();
            if (apply && movingInto && !skipMonitorSide) {
                player.setXSpeed((short) 0);
                player.setGSpeed((short) 0);
                int pushDist = leftSide ? -absDistX : absDistX;
                if (postMovement) {
                    // ROM: sub.w d0,obX(a1) — pixel-only, subpixel preserved
                    player.move((short) (pushDist * 256), (short) 0);
                } else {
                    player.setCentreX((short) (playerCenterX + pushDist));
                }
            }
            return pushing ? SolidContact.SIDE_PUSH : SolidContact.SIDE_NO_PUSH;
        }

        private SolidContact resolveSlopedContact(PlayableEntity player, int anchorX, int anchorY, int halfWidth,
                int halfHeight, byte[] slopeData, boolean xFlip, boolean topSolidOnly, boolean useStickyBuffer,
                ObjectInstance instance, boolean apply, SlopedSolidProvider slopedProvider) {
            if (slopeData == null || slopeData.length == 0) {
                return null;
            }
            int playerCenterX = player.getCentreX();
            int playerCenterY = player.getCentreY();

            int relX = playerCenterX - anchorX + halfWidth;
            int width2 = halfWidth * 2;
            if (relX < 0 || relX >= width2) {
                return null;
            }

            int sampleX = relX;
            if (xFlip) {
                sampleX = width2 - sampleX;
            }
            sampleX = sampleX >> 1;
            if (sampleX < 0 || sampleX >= slopeData.length) {
                return null;
            }

            int slopeSample = (byte) slopeData[sampleX];
            int slopeBase = slopedProvider.getSlopeBaseline();
            boolean riding = useStickyBuffer && isRidingCurrentPlayerObject(instance);
            int minRelY = riding ? -16 : 0;

            int slopeOffset = slopeSample - slopeBase;
            int baseY = anchorY - slopeOffset;

            int playerYRadius = player.getYRadius();
            int maxTop = halfHeight + playerYRadius;
            int relY = playerCenterY - baseY + 4 + maxTop;

            if (relY < minRelY || relY >= maxTop * 2) {
                return null;
            }

            SolidContact result = resolveContactInternal(player, relX, relY, halfWidth, maxTop,
                    playerCenterX, playerCenterY, topSolidOnly, riding, apply, instance);

            // ROM parity: SlopeObject2 uses absolute Y positioning for continued riding.
            // The generic resolveContactInternal formula (playerCenterY - distY + 3) is a
            // relative adjustment that is 1px too high for sloped contacts when
            // slopeData[0] == halfHeight. Override with the ROM's absolute formula:
            //   playerCentreY = objectY - slopeData[idx] - playerYRadius
            // This only applies to continued riding (sticky=true); initial landing uses
            // Solid_Landed which the generic formula approximates correctly.
            if (result == SolidContact.STANDING && apply && riding) {
                int rawSample = sampleSlopeY(player, anchorX, halfWidth, slopedProvider);
                if (rawSample != Integer.MIN_VALUE) {
                    int targetCentreY = anchorY - (rawSample & 0xFF) - playerYRadius;
                    int newY = targetCentreY - (player.getHeight() / 2);
                    player.setY((short) newY);
                }
            }

            return result;
        }

        private SolidContact resolveContactInternal(PlayableEntity player, int relX, int relY, int halfWidth,
                int maxTop, int playerCenterX, int playerCenterY, boolean topSolidOnly, boolean sticky, boolean apply,
                ObjectInstance instance) {
            int distX;
            int absDistX;
            if (relX >= halfWidth) {
                distX = relX - (halfWidth * 2);
                absDistX = -distX;
            } else {
                distX = relX;
                absDistX = distX;
            }

            int distY;
            int absDistY;
            if (relY <= maxTop) {
                distY = relY;
                absDistY = distY;
            } else {
                distY = relY - 4 - (maxTop * 2);
                absDistY = Math.abs(distY);
            }

            // Sonic 1 top-solid objects use PlatformObject/SlopeObject semantics:
            // top-landing is resolved purely by X-range + top Y-window (no side-priority compare).
            if (topSolidOnly && usesUnifiedCollisionModel(player)) {
                if (player.getYSpeed() < 0) {
                    return null;
                }
                if (distY < 0 || distY >= 0x10) {
                    return null;
                }
                // ROM: Solid_Landed uses narrow obActWid for NEW landings only.
                // When sticky (already riding), ROM uses ExitPlatform's full collision width.
                if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                    return null;
                }
                if (apply) {
                    int newCenterY = playerCenterY - distY + 3;
                    int newY = newCenterY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    // ROM: Solid_ResetFloor / PlatformObject loc_74DC unconditionally
                    // sets these three values for ALL new platform landings, regardless
                    // of air state. This handles terrain-to-platform transitions (air=false)
                    // where the platform surface angle (0) differs from terrain angle.
                    //   move.b  #0,obAngle(a1)
                    //   move.w  #0,obVelY(a1)
                    //   move.w  obVelX(a1),obInertia(a1)
                    player.setAngle((byte) 0);
                    player.setYSpeed((short) 0);
                    player.setGSpeed(player.getXSpeed());
                    // ROM: Sonic_ResetOnFloor is only called when Sonic is airborne
                    // (btst #1,obStatus(a1) / beq.s .notinair). This clears the air
                    // flag, resets rolling, and sets ground mode.
                    if (player.getAir()) {
                        player.setAir(false);
                        clearRollingOnLanding(player);
                        player.setGroundMode(GroundMode.GROUND);
                    }
                    player.setOnObject(true);
                }
                return SolidContact.STANDING;
            }

            // ROM velocity classification adjustment:
            // In the ROM, Sonic (slot 0) runs his physics first within
            // ExecuteObjects — xSpeed is applied to position BEFORE other
            // objects execute SolidObject checks. Our engine runs objects
            // before physics, so solid checks see the pre-physics position.
            // Adjust the side-vs-topbottom classification to account for
            // the pending X velocity. Only applied when the adjustment
            // INCREASES absDistX (shifting Side→TopBottom), never when it
            // decreases it (TopBottom→Side), since we don't compensate
            // ySpeed/gravity which would offset the decrease in the ROM.
            //
            // Skip when postMovement=true (S1 UNIFIED post-movement pass):
            // the player's position already reflects their velocity, so
            // the adjustment would double-count and misclassify side contacts.
            int classifyAbsDistX = absDistX;
            int velocityAdjustX = postMovement ? 0 : (player.getXSpeed() >> 8);
            if (velocityAdjustX != 0) {
                int adjustedRelX = relX + velocityAdjustX;
                if (adjustedRelX >= 0 && adjustedRelX < halfWidth * 2) {
                    int adjusted;
                    if (adjustedRelX >= halfWidth) {
                        adjusted = (halfWidth * 2) - adjustedRelX;
                    } else {
                        adjusted = adjustedRelX;
                    }
                    // Only apply when it increases absDistX (Side→TopBottom).
                    if (adjusted > absDistX) {
                        classifyAbsDistX = adjusted;
                    }
                }
            }

            if (classifyAbsDistX <= absDistY) {
                if (instance != null
                        && instance.getSpawn().objectId() == OBJ85_ID
                        && instance.getSpawn().subtype() == 0
                        && player.getYSpeed() >= 0) {
                    int anchorX = playerCenterX - (relX - halfWidth);
                    int anchorY = playerCenterY - (relY - 4 - maxTop);
                    int landingThreshold = 0x14; // ROM: Obj85 uses 0x14 in SolidObject_TopBottom
                    int landingFrame = 0;
                    int landingPrevRelX = 0;
                    int landingPrevRelY = 0;
                    boolean landingCrossedTop = false;
                    int[] prevRelX = new int[3];
                    int[] prevRelY = new int[3];
                    for (int framesBehind = 1; framesBehind <= 3; framesBehind++) {
                        short prevCenterX = player.getCentreX(framesBehind);
                        short prevCenterY = player.getCentreY(framesBehind);
                        int idx = framesBehind - 1;
                        prevRelX[idx] = prevCenterX - anchorX + halfWidth;
                        prevRelY[idx] = prevCenterY - anchorY + 4 + maxTop;
                        if (landingFrame == 0
                                && prevRelX[idx] >= 0 && prevRelX[idx] <= halfWidth * 2
                                && prevRelY[idx] < landingThreshold) {
                            boolean withinTop = prevRelY[idx] >= 0 && prevRelY[idx] <= maxTop;
                            boolean crossedTop = prevRelY[idx] < 0 && relY >= 0;
                            if (!withinTop && !crossedTop) {
                                continue;
                            }
                            landingFrame = framesBehind;
                            landingPrevRelX = prevRelX[idx];
                            landingPrevRelY = prevRelY[idx];
                            landingCrossedTop = crossedTop;
                        }
                    }
                    if (landingFrame > 0) {
                        // ROM: Solid_Landed narrow width only for NEW landings
                        if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                            return null;
                        }
                        if (apply) {
                            int newCenterY = anchorY - maxTop - 1;
                            int newY = newCenterY - (player.getHeight() / 2);
                            player.setY((short) newY);
                            // ROM: Solid_ResetFloor unconditionally sets these.
                            player.setAngle((byte) 0);
                            player.setYSpeed((short) 0);
                            player.setGSpeed(player.getXSpeed());
                            if (player.getAir()) {
                                player.setAir(false);
                                clearRollingOnLanding(player);
                                player.setGroundMode(GroundMode.GROUND);
                            }
                            player.setOnObject(true);
                        }
                        return SolidContact.STANDING;
                    }
                }

                if (topSolidOnly) {
                    return null;
                }
                // Determine which side player is on based on relX, not distX.
                // When distX=0 (at exact edge), distX>0 would be false for both sides,
                // causing incorrect movingInto detection for left side pushes.
                boolean leftSide = relX < halfWidth;
                // ROM: cmpi.w #4,d1
                boolean nearVerticalEdge = absDistY <= 4;
                // Only set pushing if player is on ground AND actively pressing into the object
                boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
                boolean pushing = !player.getAir() && movingInto;

                if (nearVerticalEdge) {
                    // Near top/bottom edge: don't stop player and don't set pushing.
                    // This avoids false push-state while stepping across adjacent solid tops.
                    return SolidContact.SIDE_NO_PUSH;
                }
                // ROM: sub SolidObject.asm lines 173-196
                // When d0==0 (distX==0), ROM branches to Solid_Centre which does
                // "sub.w d0,obX(a1)" (no-op) — NO speed zeroing, NO position change.
                // When d0!=0 AND movingInto, ROM hits Solid_Left which zeros obInertia
                // and obVelX, then falls through to Solid_Centre (position correction).
                // When d0!=0 AND NOT movingInto, ROM goes to Solid_Centre directly
                // (position correction only, no speed zeroing).
                // deferSideToPostMovement: only defer when player is airborne.
                // Airborne side collision is handled more accurately by post-movement pass
                // (ROM processes objects after Sonic moves). Ground side collision keeps
                // pre-movement behavior for wall alignment consistency.
                boolean skipSideEffects = deferSideToPostMovement && player.getAir();
                if (apply && !skipSideEffects) {
                    if (postMovement) {
                        // Post-movement pass (S1 UNIFIED): ROM-accurate behavior.
                        // ROM: Solid_Left zeros speed BEFORE the airborne check.
                        // Speed is zeroed when movingInto regardless of air state.
                        // The airborne check only affects the PUSH flag, not speed.
                        if (distX != 0 && movingInto) {
                            player.setXSpeed((short) 0);
                            player.setGSpeed((short) 0);
                        }
                        if (distX != 0) {
                            player.move((short) (-distX * 256), (short) 0);
                        }
                    } else {
                        // Pre-movement pass (S2/S3K): compensate for batched processing architecture.
                        // Push-driven objects (preserveSubpixels=true) skip the distX==0 block
                        // to preserve ROM push cadence.
                        boolean preserveSubpixels = preservesEdgeSubpixelMotion(instance);
                        if (distX == 0 && !preserveSubpixels) {
                            player.setCentreX((short) playerCenterX);
                            if (movingInto) {
                                player.setXSpeed((short) 0);
                                player.setGSpeed((short) 0);
                            }
                        }
                        if (distX != 0 && movingInto) {
                            player.setXSpeed((short) 0);
                            player.setGSpeed((short) 0);
                        }
                        if (distX != 0) {
                            if (preserveSubpixels) {
                                player.move((short) (-distX * 256), (short) 0);
                            } else {
                                player.setCentreX((short) (playerCenterX - distX));
                            }
                        }
                    }
                }
                return SolidContact.side(pushing, distX);
            }

            if (distY >= 0 || (sticky && distY >= -16)) {
                if (player.getYSpeed() < 0) {
                    return null;
                }


                if (topSolidOnly && player.getYSpeed() < 0) {
                    return null;
                }

                // ROM: cmpi.w #$10,d3
                int landingThreshold = 0x10;
                if (distY >= landingThreshold) {
                    return null;
                }
                // ROM: Solid_Landed uses narrow obActWid for NEW landings only.
                // When sticky (already riding), ROM uses ExitPlatform's full collision width.
                if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                    return null;
                }

                if (apply) {
                    int newCenterY = playerCenterY - distY + 3;
                    int newY = newCenterY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    // ROM: Solid_ResetFloor / PlatformObject loc_74DC unconditionally
                    // sets angle, ySpeed, and gSpeed for ALL platform landings.
                    player.setAngle((byte) 0);
                    player.setYSpeed((short) 0);
                    player.setGSpeed(player.getXSpeed());
                    if (player.getAir()) {
                        LOGGER.fine(() -> "Solid object landing at (" + player.getX() + "," + player.getY() +
                            ") distY=" + distY);
                        player.setAir(false);
                        clearRollingOnLanding(player);
                        player.setGroundMode(GroundMode.GROUND);
                    }
                    // ROM: bset #status.player.on_object (s2.asm:35739)
                    player.setOnObject(true);
                }
                return SolidContact.STANDING;
            }

            if (topSolidOnly) {
                return null;
            }

            // ROM: SolidObject_InsideBottom (s2.asm:35307-35333)
            // When y_vel == 0 and player is on ground, the ROM branches to SolidObject_Squash
            // which checks horizontal overlap and kills the player if sandwiched.
            if (player.getYSpeed() == 0 && !player.getAir()) {
                // ROM: SolidObject_Squash (s2.asm:35336-35361)
                // mvabs.w d0,d4; cmpi.w #$10,d4; blo.w SolidObject_LeftRight
                // If player is near the horizontal edge (absDistX < 16), push sideways instead.
                if (absDistX < 0x10) {
                    boolean leftSide = relX < halfWidth;
                    boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
                    boolean pushing = !player.getAir() && movingInto;
                    boolean skipSquashSide = deferSideToPostMovement && player.getAir();
                    if (apply && !skipSquashSide) {
                        if (postMovement) {
                            if (distX != 0 && movingInto) {
                                player.setXSpeed((short) 0);
                                player.setGSpeed((short) 0);
                            }
                            if (distX != 0) {
                                player.move((short) (-distX * 256), (short) 0);
                            }
                        } else {
                            if (movingInto) {
                                player.setXSpeed((short) 0);
                                player.setGSpeed((short) 0);
                            }
                            if (distX != 0) {
                                player.setCentreX((short) (playerCenterX - distX));
                            }
                        }
                    }
                    return SolidContact.side(pushing, distX);
                }
                // Player is well inside horizontally - crush death.
                // ROM: KillCharacter (s2.asm:84995) - unconditional death.
                if (apply) {
                    LOGGER.fine(() -> "SolidObject crush: player sandwiched (absDistX=" + absDistX + ")");
                    player.applyCrushDeath();
                }
                return SolidContact.CEILING;
            }

            // ROM: Solid_Below — only correct position when ySpeed < 0 (moving upward into object).
            // When ySpeed > 0 (falling) or ySpeed == 0 with air, ROM's Solid_TopBtmAir returns
            // d4=-1 without correcting position or zeroing speed. This lets the player fall
            // through the bottom of the object naturally, preventing spikes from trapping Sonic
            // by continuously pushing him back into the collision box.
            if (apply && player.getYSpeed() < 0) {
                int newCenterY = playerCenterY - distY;
                int newY = newCenterY - (player.getHeight() / 2);
                player.setY((short) newY);
                LOGGER.fine(() -> "Solid object ceiling hit, zeroing ySpeed from " + player.getYSpeed());
                player.setYSpeed((short) 0);
            }
            return SolidContact.CEILING;
        }

        private boolean isWithinTopLandingWidth(ObjectInstance instance, PlayableEntity player, int relX,
                int collisionHalfWidth) {
            if (!(instance instanceof SolidObjectProvider provider)) {
                return true;
            }

            int configuredHalfWidth = provider.getTopLandingHalfWidth(player, collisionHalfWidth);
            int allowedHalfWidth;
            if (configuredHalfWidth < collisionHalfWidth) {
                // Provider explicitly set a narrower landing width
                allowedHalfWidth = configuredHalfWidth;
            } else {
                // ROM: Solid_Landed re-reads obActWid (= width_pixels), which is
                // narrower than collision halfWidth (= width_pixels + $B). This applies
                // to both S1 and S2/S3K — ALL games use the standard SolidObject
                // convention where collision width = obActWid + $B.
                allowedHalfWidth = Math.max(0, collisionHalfWidth - 0x0B);
            }

            // ROM: Solid_Landed checks: d1 = playerX + obActWid - objX,
            // bmi.s Solid_Miss (d1 < 0), cmp.w d2,d1 / bhs.s Solid_Miss (d1 >= width*2).
            // Range for d1: [0, allowedHalfWidth*2) — inclusive left, exclusive right.
            // Using abs() would be symmetric and include the right boundary, causing
            // false landings at the exact right edge.
            int xFromCenter = relX - collisionHalfWidth;
            int d1 = xFromCenter + allowedHalfWidth;
            return d1 >= 0 && d1 < allowedHalfWidth * 2;
        }

        private boolean usesUnifiedCollisionModel(PlayableEntity player) {
            if (player == null) {
                return false;
            }
            PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
            return featureSet != null && featureSet.collisionModel() == CollisionModel.UNIFIED;
        }

        private boolean preservesEdgeSubpixelMotion(ObjectInstance instance) {
            if (!(instance instanceof SolidObjectProvider provider)) {
                return false;
            }
            return provider.preservesEdgeSubpixelMotion();
        }

        /**
         * ROM: SlopeObject2 / MvSonicOnSlope slope sampling for riding updates.
         * Returns the raw slope sample (surface offset from object Y), matching
         * the ROM formula: surfaceY = obY - slopeSample.
         *
         * Important: unlike resolveSlopedContact (landing path) which normalises
         * slope values via getSlopeBaseline(), the riding path uses the raw sample
         * exactly as the ROM does — no baseline subtraction.
         *
         * Shift/flip ordering matches the ROM: shift first (lsr.w #1), then flip
         * (not.w + add.w halfWidth). This differs from SlopeObject (landing) which
         * flips the full relX before shifting.
         */
        private int sampleSlopeY(PlayableEntity player, int objectX,
                int halfWidth, SlopedSolidProvider sloped) {
            byte[] slopeData = sloped.getSlopeData();
            if (slopeData == null || slopeData.length == 0) {
                return Integer.MIN_VALUE;
            }
            int playerCenterX = player.getCentreX();
            int relX = playerCenterX - objectX + halfWidth;
            int width2 = halfWidth * 2;
            // Clamp relX to the valid collision width. The riding bounds check
            // allows a sticky buffer (up to 16px beyond the collision edge),
            // so the player can be slightly outside the slope data range.
            if (relX < 0) {
                relX = 0;
            } else if (relX >= width2) {
                relX = width2 - 1;
            }
            // ROM: lsr.w #1,d0 — shift BEFORE flip (matches SlopeObject2/MvSonicOnSlope)
            int sampleX = relX >> 1;
            if (sloped.isSlopeFlipped()) {
                // ROM: not.w d0 / add.w d1,d0 — where d1 = halfWidth
                // not.w gives ~sampleX = -sampleX - 1, then + halfWidth
                sampleX = halfWidth - sampleX - 1;
            }
            if (sampleX < 0) {
                sampleX = 0;
            } else if (sampleX >= slopeData.length) {
                sampleX = slopeData.length - 1;
            }
            // ROM: move.b (a2,d0.w),d1 / ext.w d1 (S2) or moveq #0,d1 / move.b (S1)
            // Java byte is signed, matching S2's ext.w. S1 values are positive so no difference.
            return (byte) slopeData[sampleX];
        }

        private void clearRollingOnLanding(PlayableEntity player) {
            if (player == null || player.getPinballMode() || !player.getRolling()) {
                return;
            }
            player.setRolling(false);
            player.setY((short) (player.getY() - player.getRollHeightAdjustment()));
        }
    }
}
