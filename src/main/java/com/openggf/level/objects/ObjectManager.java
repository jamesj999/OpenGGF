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
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
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
    private final List<ObjectInstance> pendingDynamicAdditions = new ArrayList<>();
    private final List<GLCommand> renderCommands = new ArrayList<>();
    private int frameCounter;
    private boolean updating;
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

    private final PlaneSwitchers planeSwitchers;
    private final SolidContacts solidContacts;
    private final TouchResponses touchResponses;

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
        pendingDynamicAdditions.clear();
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

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks, int touchFrameCounter) {
        update(cameraX, player, sidekicks, touchFrameCounter, true);
    }

    /**
     * Run touch responses for a single player outside the main update loop.
     * ROM order: ReactToItem runs during each player's slot within ExecuteObjects,
     * after their physics but before other objects' solid checks.
     */
    public void runTouchResponsesForPlayer(PlayableEntity player, int touchFrameCounter) {
        if (touchResponses == null) return;
        touchResponses.debugState.setEnabled(
                DebugOverlayManager.getInstance().isEnabled(DebugOverlayToggle.TOUCH_RESPONSE));
        touchResponses.update(player, touchFrameCounter);
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses) {
        frameCounter++;
        updateCameraBounds();
        syncActiveSpawns();

        updating = true;
        boolean objectsRemoved = false;
        try {
            Iterator<ObjectInstance> dynamicIterator = dynamicObjects.iterator();
            while (dynamicIterator.hasNext()) {
                ObjectInstance instance = dynamicIterator.next();
                instance.update(frameCounter, player);
                if (instance.isDestroyed()) {
                    instance.onUnload();
                    dynamicIterator.remove();
                    objectsRemoved = true;
                }
            }

            Iterator<Map.Entry<ObjectSpawn, ObjectInstance>> iterator = activeObjects.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ObjectSpawn, ObjectInstance> entry = iterator.next();
                ObjectInstance instance = entry.getValue();
                instance.update(frameCounter, player);
                if (instance.isDestroyed()) {
                    instance.onUnload();
                    // Clear stayActive so the remembered check in trySpawn() permanently
                    // blocks this spawn. Without this, stayActive objects (e.g. EggPrison)
                    // that self-destruct after their animation would respawn on camera
                    // re-entry because stayActive bypasses the remembered gate.
                    placement.clearStayActive(entry.getKey());
                    // ROM: Delete_Current_Sprite leaves the respawn bit set, preventing
                    // re-loading until the spawn leaves the stream window. Match this by
                    // removing from the active set and setting the destroyedInWindow latch.
                    // Without this, syncActiveSpawns() on the next frame sees the spawn
                    // in the active set but not in activeObjects and immediately respawns it.
                    placement.removeFromActive(entry.getKey());
                    iterator.remove();
                    objectsRemoved = true;
                }
            }
        } finally {
            updating = false;
            if (!pendingDynamicAdditions.isEmpty()) {
                dynamicObjects.addAll(pendingDynamicAdditions);
                pendingDynamicAdditions.clear();
                objectsRemoved = true; // Adding also requires re-bucketing
            }
            if (objectsRemoved) {
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
        }

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
        placement.update(cameraX);
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

        // Draw low-priority objects first (sets priority per-instance in shader)
        if (!lowPriorityBuckets[idx].isEmpty()) {
            gfx.setCurrentSpriteHighPriority(false);
            drawBucketInstancesWithPriority(lowPriorityBuckets[idx]);
        }

        // Draw high-priority objects (sets priority per-instance in shader)
        // No batch flush needed - priority is baked into instance data
        if (!highPriorityBuckets[idx].isEmpty()) {
            gfx.setCurrentSpriteHighPriority(true);
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
            // Sort by spawn X position to ensure deterministic iteration order.
            // ROM processes objects in slot order, which correlates with spawn-window
            // entry order (left-to-right as camera scrolls). Using spawn X as the
            // sort key matches this behavior. Dynamic objects (null spawn) sort last.
            cachedActiveObjects.sort((a, b) -> {
                ObjectSpawn sa = a.getSpawn();
                ObjectSpawn sb = b.getSpawn();
                int xa = sa != null ? sa.x() : Integer.MAX_VALUE;
                int xb = sb != null ? sb.x() : Integer.MAX_VALUE;
                if (xa != xb) return Integer.compare(xa, xb);
                // Tie-break by spawn Y for objects at the same X
                int ya = sa != null ? sa.y() : Integer.MAX_VALUE;
                int yb = sb != null ? sb.y() : Integer.MAX_VALUE;
                return Integer.compare(ya, yb);
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
        if (object instanceof AbstractObjectInstance aoi) {
            aoi.setServices(objectServices);
        }
        if (updating) {
            pendingDynamicAdditions.add(object);
        } else {
            dynamicObjects.add(object);
        }
        bucketsDirty = true;
        activeObjectsCacheDirty = true;
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

    private void syncActiveSpawns() {
        Collection<ObjectSpawn> activeSpawns = placement.getActiveSpawns();
        boolean changed = false;
        for (ObjectSpawn spawn : activeSpawns) {
            if (!activeObjects.containsKey(spawn)) {
                // Don't recreate instances for remembered spawns - they've been destroyed
                // and shouldn't respawn. Exception: stayActive spawns (e.g. broken monitors)
                // should be re-created when the player scrolls back into their area.
                if (placement.isRemembered(spawn) && !placement.isStayActive(spawn)) {
                    continue;
                }
                AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(objectServices);
                try {
                    ObjectInstance instance = registry != null ? registry.create(spawn) : null;
                    if (instance != null) {
                        if (instance instanceof AbstractObjectInstance aoi) {
                            aoi.setServices(objectServices);
                        }
                        activeObjects.put(spawn, instance);
                        changed = true;
                    }
                } finally {
                    AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
                }
            }
        }

        Iterator<Map.Entry<ObjectSpawn, ObjectInstance>> iterator = activeObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ObjectSpawn, ObjectInstance> entry = iterator.next();
            if (!activeSpawns.contains(entry.getKey())) {
                if (!entry.getValue().isPersistent()) {
                    entry.getValue().onUnload();
                    iterator.remove();
                    changed = true;
                }
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

        private final BitSet remembered = new BitSet();
        /** Tracks spawns that should stay in active even when remembered (e.g. broken monitors). */
        private final BitSet stayActive = new BitSet();
        /** Tracks spawns destroyed while in the window - prevents respawn until they leave the window. */
        private final BitSet destroyedInWindow = new BitSet();
        private int cursorIndex = 0;
        private int lastCameraX = Integer.MIN_VALUE;
        private int lastCameraChunk = Integer.MIN_VALUE;

        Placement(List<ObjectSpawn> spawns) {
            super(spawns, LOAD_AHEAD, UNLOAD_BEHIND);
        }

        /** Replaces spawns and clears all tracking state. */
        void replaceSpawnsAndReset(List<ObjectSpawn> newSpawns) {
            replaceSpawns(newSpawns);
            remembered.clear();
            stayActive.clear();
            destroyedInWindow.clear();
            cursorIndex = 0;
            lastCameraX = Integer.MIN_VALUE;
            lastCameraChunk = Integer.MIN_VALUE;
        }

        void reset(int cameraX) {
            active.clear();
            remembered.clear();
            stayActive.clear();
            destroyedInWindow.clear();
            cursorIndex = 0;
            lastCameraX = cameraX;
            lastCameraChunk = toCoarseChunk(cameraX);
            refreshWindow(cameraX);
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

            int delta = cameraX - lastCameraX;
            if (delta < 0 || delta > (getLoadAhead() + getUnloadBehind())) {
                refreshWindow(cameraX);
            } else {
                spawnForward(cameraX);
                trimActive(cameraX);
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

        private void spawnForward(int cameraX) {
            int spawnLimit = getWindowEnd(cameraX);
            while (cursorIndex < spawns.size() && spawns.get(cursorIndex).x() <= spawnLimit) {
                trySpawn(cursorIndex);
                cursorIndex++;
            }
        }

        private void trimActive(int cameraX) {
            int windowStart = getWindowStart(cameraX);
            int windowEnd = getWindowEnd(cameraX);
            Iterator<ObjectSpawn> iterator = active.iterator();
            while (iterator.hasNext()) {
                ObjectSpawn spawn = iterator.next();
                if (spawn.x() < windowStart || spawn.x() > windowEnd) {
                    iterator.remove();
                }
            }
            clearDestroyedLatchOutsideWindow(windowStart, windowEnd);
        }

        private void refreshWindow(int cameraX) {
            int windowStart = getWindowStart(cameraX);
            int windowEnd = getWindowEnd(cameraX);
            int start = lowerBound(windowStart);
            int end = upperBound(windowEnd);
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

        private int toCoarseChunk(int cameraX) {
            return cameraX & CHUNK_MASK;
        }

        /**
         * Extend the active set for spawns visible with the post-camera position.
         * <p>
         * When the post-camera X is in a higher chunk than the last processed chunk,
         * scans spawns in the gap between the old and new window right edges and
         * adds eligible ones to the active set. Does NOT update lastCameraChunk
         * or the cursor, preserving the primary placement pass's ability to
         * process the chunk boundary normally on the next frame.
         */
        void extendForPostCamera(int postCameraX) {
            return; // TEMP: disabled to test sort-only baseline
            /*
            int postChunk = toCoarseChunk(postCameraX);
            if (postChunk <= lastCameraChunk) {
                return; // Camera didn't advance to a new chunk
            }
            int oldWindowEnd = getWindowEnd(lastCameraX);
            int newWindowEnd = postChunk + LOAD_AHEAD;
            // Scan spawns from current cursor (everything before cursor is already processed)
            // through the new window end.
            for (int i = cursorIndex; i < spawns.size(); i++) {
                int sx = spawns.get(i).x();
                if (sx > newWindowEnd) {
                    break;
                }
                if (sx > oldWindowEnd) {
                    trySpawn(i);
                }
            }
            */
        }

        /**
         * Returns true if the given spawn index has the destroyedInWindow latch set.
         */
        boolean isDestroyedInWindow(int index) {
            return index >= 0 && destroyedInWindow.get(index);
        }


        private void clearDestroyedLatchOutsideWindow(int windowStart, int windowEnd) {
            // Clear destroyed-in-window latch once the spawn fully leaves the current stream window.
            for (int i = destroyedInWindow.nextSetBit(0); i >= 0; i = destroyedInWindow.nextSetBit(i + 1)) {
                ObjectSpawn spawn = spawns.get(i);
                if (spawn.x() < windowStart || spawn.x() > windowEnd) {
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

                int flags = provider.getCollisionFlags();
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

                // ROM: Touch_CheckCollision uses x_pos(a1)/y_pos(a1) — the object's current
                // position, not its spawn position. Use getX()/getY() which moving objects
                // (badniks, projectiles, boss children) override to return current coords.
                int objX = instance.getX();
                int objY = instance.getY();
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
                        // ROM: exits after first hit found per frame (bne.w branch)
                        break;
                    }
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
                    return true; // Hit triggered — signal caller to break for player path
                }
                return false; // Overlap but not triggered (edge-trigger suppressed)
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
                int ridingHalfWidth = halfWidth;
                int topLandingHalfWidth = provider.getTopLandingHalfWidth(player, halfWidth);
                if (topLandingHalfWidth >= 0 && topLandingHalfWidth < halfWidth) {
                    ridingHalfWidth = topLandingHalfWidth;
                }

                // ROM: Bounds check uses collision-offset X (anchorX = obX + offsetX),
                // while delta tracking uses raw object X for movement following.
                int boundsX = currentX + params.offsetX();
                int relX = player.getCentreX() - boundsX + ridingHalfWidth;
                // Keep riding contact with the same sticky X tolerance used by collision
                // resolution to avoid one-frame edge drops on moving solids.
                // For objects with a narrowed top-standing area, avoid extending beyond
                // that standable surface while riding.
                int stickyX = (provider.usesStickyContactBuffer() && ridingHalfWidth == halfWidth) ? 16 : 0;
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
                    int surfaceOffset;
                    if (ridingObject instanceof SlopedSolidProvider sloped) {
                        int slopeY = sampleSlopeY(player, currentX, params.halfWidth(), sloped);
                        surfaceOffset = (slopeY != Integer.MIN_VALUE) ? slopeY : params.airHalfHeight();
                    } else {
                        surfaceOffset = params.airHalfHeight();
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

            // SPG: Landing check - player Y relative to top of combined box must be < 16
            // AND player X must be within monitor width + 4px margin on each side
            // distY represents playerY - topCombinedBox when relY <= maxTop
            boolean canLand = distY >= 0 && distY < 16;

            // Check X margin for landing: player must be within halfWidth + 4 of center
            // relX is already relative to left edge, so center-relative = relX - halfWidth
            int xFromCenter = relX - halfWidth;
            int landingXMargin = halfWidth + 4;
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
                    if (player.getYSpeed() > 0) {
                        player.setYSpeed((short) 0);
                    }
                    if (player.getAir()) {
                        LOGGER.fine(() -> "Monitor landing at (" + player.getX() + "," + player.getY() +
                            ") distY=" + distY);
                        player.setGSpeed(player.getXSpeed());
                        player.setAir(false);
                        clearRollingOnLanding(player);
                        player.setAngle((byte) 0);
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

            return resolveContactInternal(player, relX, relY, halfWidth, maxTop, playerCenterX, playerCenterY,
                    topSolidOnly, riding, apply, instance);
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
                if (!isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                    return null;
                }
                if (apply) {
                    int newCenterY = playerCenterY - distY + 3;
                    int newY = newCenterY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    if (player.getYSpeed() > 0) {
                        player.setYSpeed((short) 0);
                    }
                    if (player.getAir()) {
                        player.setGSpeed(player.getXSpeed());
                        player.setAir(false);
                        clearRollingOnLanding(player);
                        player.setAngle((byte) 0);
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
                        if (!isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                            return null;
                        }
                        if (apply) {
                            int newCenterY = anchorY - maxTop - 1;
                            int newY = newCenterY - (player.getHeight() / 2);
                            player.setY((short) newY);
                            if (player.getYSpeed() > 0) {
                                player.setYSpeed((short) 0);
                            }
                            if (player.getAir()) {
                                player.setGSpeed(player.getXSpeed());
                                player.setAir(false);
                                clearRollingOnLanding(player);
                                player.setAngle((byte) 0);
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
                        // d0==0 → no speed zeroing, no position change.
                        // d0!=0 → speed zeroing if movingInto, position correction
                        // via pixel-only subtraction (preserves subpixels).
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
                if (!isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                    return null;
                }

                if (apply) {
                    int newCenterY = playerCenterY - distY + 3;
                    int newY = newCenterY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    if (player.getYSpeed() > 0) {
                        player.setYSpeed((short) 0);
                    }
                    if (player.getAir()) {
                        LOGGER.fine(() -> "Solid object landing at (" + player.getX() + "," + player.getY() +
                            ") distY=" + distY);
                        player.setGSpeed(player.getXSpeed());
                        player.setAir(false);
                        clearRollingOnLanding(player);
                        player.setAngle((byte) 0);
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
            } else if (!usesUnifiedCollisionModel(player)) {
                // ROM: s2.asm:35345-35353 — S2/S3K landing check reloads width_pixels,
                // which is narrower than collision halfWidth (collision = width_pixels + $B).
                allowedHalfWidth = Math.max(0, collisionHalfWidth - 0x0B);
            } else {
                // S1 unified: no $B offset convention, use full collision width
                allowedHalfWidth = collisionHalfWidth;
            }

            int xFromCenter = relX - collisionHalfWidth;
            return Math.abs(xFromCenter) <= allowedHalfWidth;
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
