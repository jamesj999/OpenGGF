package com.openggf.level.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.PlayableEntity;

import java.util.List;

public interface ObjectInstance {
    ObjectSpawn getSpawn();

    default int getX() {
        return getSpawn().x();
    }

    default int getY() {
        return getSpawn().y();
    }

    /**
     * Returns the object's X position as it was before the current frame's update loop.
     * Used by touch response collision checks to match ROM ordering, where ReactToItem
     * runs before ExecuteObjects — so objects are at pre-update positions during collision.
     * <p>
     * Defaults to current position. {@link AbstractObjectInstance} snapshots before updates.
     */
    default int getPreUpdateX() {
        return getX();
    }

    /**
     * Returns the object's Y position as it was before the current frame's update loop.
     * @see #getPreUpdateX()
     */
    default int getPreUpdateY() {
        return getY();
    }

    /**
     * Snapshots the current position as the pre-update position.
     * Called by ObjectManager before the object update loop each frame.
     */
    default void snapshotPreUpdatePosition() {
        // Default no-op; AbstractObjectInstance provides implementation.
    }

    /**
     * Returns the collision flags as they were before the current frame's object update.
     * ROM parity: ReactToItem runs in Sonic's slot (slot 0) BEFORE other objects update,
     * so it sees enemies at their previous frame's collision type. If an object activates
     * its collision type during this frame's update (e.g., lava geyser becoming active),
     * ReactToItem wouldn't see it. Default returns -1 (no snapshot, use current flags).
     */
    default int getPreUpdateCollisionFlags() {
        return -1;
    }

    /**
     * Returns true if this object needs to be updated on the same frame it was spawned.
     * <p>
     * ROM parity: In the ROM's ExecuteObjects, when an object at slot N creates a child
     * at slot M > N, the child IS processed in the same pass. The engine queues children
     * into pendingDynamicAdditions during the loop, so they miss the current frame.
     * Objects that return true here get a follow-up update in the finally block.
     * <p>
     * Use sparingly — most objects work correctly with the default 1-frame delay.
     * Only enable for objects whose position accuracy on the first frame matters
     * (e.g., projectiles checked for collision on subsequent frames).
     */
    default boolean requiresSameFrameUpdate() {
        return false;
    }

    /**
     * Returns true if this object was spawned during the current frame's update loop
     * and should be excluded from touch/hurt collision checks this frame.
     * <p>
     * ROM parity: In the ROM's ExecuteObjects, Sonic (slot 0) runs ReactToItem BEFORE
     * objects at higher slots create children. So newly created children are never checked
     * for touch collision on their spawning frame. The engine processes objects before
     * player physics, so children that receive same-frame updates must be excluded from
     * touch checks to match this behavior.
     */
    default boolean isSkipTouchThisFrame() {
        return false;
    }

    /**
     * Returns true if this object should skip SolidObject checks this frame.
     * ROM parity: on the first frame of an object's existence, obRender bit 7
     * is not yet set (DisplaySprite hasn't run), so the object's update skips
     * the SolidObject call. See "tst.b obRender(a0) / bpl.s" pattern in many
     * S1 objects (e.g., 46 MZ Bricks, 26 Monitor).
     */
    default boolean isSkipSolidContactThisFrame() {
        return false;
    }

    void update(int frameCounter, PlayableEntity player);

    void appendRenderCommands(List<GLCommand> commands);

    boolean isHighPriority();
    default int getPriorityBucket() {
        return RenderPriority.MIN;
    }

    boolean isDestroyed();

    /**
     * Returns true if this object should remain active even when its spawn position
     * is outside the camera window. Used by objects like spin tubes that need to
     * continue controlling the player after they've moved far from the object's origin.
     */
    default boolean isPersistent() {
        return false;
    }

    /**
     * Returns true if this object should stay in the active spawn set even after being
     * marked as remembered. Used by objects like monitors and capsules that need to
     * complete their destruction/animation sequence before being removed.
     * <p>
     * Objects that return true will:
     * - Be marked as remembered (won't respawn after death/restart)
     * - Stay in the active set to complete their logic
     * - Self-destruct by calling setDestroyed(true) when done
     * <p>
     * Default is false - most objects are removed from active immediately when remembered.
     */
    default boolean shouldStayActiveWhenRemembered() {
        return false;
    }

    /**
     * Returns the number of additional SST slots this object reserves for child
     * sub-objects. These slots are allocated via FindFreeObj (not FindNextFreeObj)
     * at spawn time, matching the ROM's object initialization.
     * <p>
     * ROM example: S1 ChainedStomper objects allocate 1 parent slot + N child slots
     * for multi-segment layout entries.
     *
     * @return number of extra slots to allocate (default 0)
     */
    default int getReservedChildSlotCount() {
        return 0;
    }

    /**
     * Returns true if ObjectManager should pre-allocate child slots for this object
     * BEFORE {@code syncActiveSpawnsLoad} runs (i.e. before ObjPosLoad).
     * <p>
     * ROM parity: in S1, ring objects (obj25) run Ring_Main during ExecuteObjects
     * BEFORE ObjPosLoad. Their child allocations via FindFreeObj therefore get lower
     * slot numbers than objects loaded by ObjPosLoad in the same frame. Returning
     * {@code true} here causes {@link #getReservedChildSlotCount()} slots to be
     * reserved before ObjPosLoad, giving children the correct lower slot numbers.
     * <p>
     * Objects that self-allocate child slots from within their own {@code update()}
     * (e.g. ChainedStomper via {@code allocateChildSlotsAfter}) should return
     * {@code false} to avoid double-allocation.
     *
     * @return {@code false} by default (self-allocating objects keep current behavior)
     */
    default boolean needsPreAllocatedChildSlots() {
        return false;
    }

    /**
     * Called when this object is being unloaded from the active object list.
     * Override to perform cleanup when the object goes off-screen or is removed.
     * Default implementation does nothing.
     */
    default void onUnload() {
        // Default no-op
    }

    /**
     * Append debug rendering commands for this object instance.
     * Called during the geometry phase when the OBJECT_DEBUG overlay is enabled.
     * Override to draw hitboxes, velocity vectors, AI state, etc.
     */
    default void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Default no-op
    }
}
