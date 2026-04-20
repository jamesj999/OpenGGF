package com.openggf.game.sonic1.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.Sonic1RingPlacement;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;

import java.util.List;

/**
 * ROM-faithful ring object for Sonic 1. Each instance represents a single ring
 * occupying one dynamic slot, matching the ROM's Ring_Main / FindFreeObj behavior.
 *
 * <p>Ring rendering and collection detection are handled by {@link RingManager}
 * (unified across all games). This object manages:
 * <ul>
 *   <li>Slot allocation (parent spawns children via {@code spawnChild()})</li>
 *   <li>Touch response blocking (collision flags $47 for ReactToItem scan order)</li>
 *   <li>Sparkle countdown and self-destruction</li>
 * </ul>
 */
public class Sonic1RingInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener {

    /** S1 ring collision type: $47 = powerup category ($40) + size index 7. */
    public static final int RING_COLLISION_FLAGS = 0x47;

    private enum State { INIT, ANIMATE, SPARKLE }

    private final RingSpawn ringSpawn;
    private final List<RingSpawn> childRingSpawns;
    private final int outOfRangeAnchorX;

    private State state;

    /**
     * Creates a parent ring instance from a layout entry.
     *
     * @param spawn           the ObjectSpawn from layout data
     * @param allRingSpawns   expanded ring positions: index 0 = this ring, 1..N = children
     */
    public Sonic1RingInstance(ObjectSpawn spawn, List<RingSpawn> allRingSpawns) {
        super(spawn, "Ring");
        this.ringSpawn = allRingSpawns.get(0);
        this.childRingSpawns = allRingSpawns.size() > 1
                ? allRingSpawns.subList(1, allRingSpawns.size())
                : List.of();
        this.outOfRangeAnchorX = spawn.x();
        this.state = State.INIT;
    }

    /**
     * Creates a child ring instance spawned by a parent.
     *
     * @param spawn     dynamically built spawn at child position
     * @param ringSpawn the child's RingSpawn reference in RingManager
     */
    Sonic1RingInstance(ObjectSpawn spawn, RingSpawn ringSpawn, int outOfRangeAnchorX) {
        super(spawn, "Ring");
        this.ringSpawn = ringSpawn;
        this.childRingSpawns = List.of();
        this.outOfRangeAnchorX = outOfRangeAnchorX;
        this.state = State.ANIMATE;
    }

    @Override
    public int getReservedChildSlotCount() {
        return childRingSpawns.size();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        switch (state) {
            case INIT -> {
                spawnChildren();
                state = State.ANIMATE;
            }
            case ANIMATE -> {
                RingManager ringManager = services().ringManager();
                if (ringManager != null && ringManager.isCollected(ringSpawn)) {
                    state = State.SPARKLE;
                }
            }
            case SPARKLE -> {
                RingManager ringManager = services().ringManager();
                int gameplayFrameCounter = frameCounter;
                ObjectManager objectManager = services().objectManager();
                if (objectManager != null) {
                    // ROM parity: Ring_Sparkle advances only when ExecuteObjects runs.
                    // Lag frames still bump v_vbla_byte, but they do not run the ring
                    // object's AnimateSprite/DeleteObject path. Use gameplay-frame
                    // time here so collected ring slots persist through lag exactly
                    // as long as the ROM object routine does.
                    gameplayFrameCounter = objectManager.getFrameCounter();
                }
                if (ringManager == null || ringManager.isCollectedAndSparkleDone(
                        ringSpawn, gameplayFrameCounter)) {
                    setDestroyed(true);
                }
            }
        }
    }

    private void spawnChildren() {
        if (childRingSpawns.isEmpty()) {
            return;
        }
        int subtype = spawn.subtype();
        int[] spacing = Sonic1RingPlacement.getRingSpacing(subtype);
        int dx = spacing[0];
        int dy = spacing[1];
        int baseX = spawn.x();
        int baseY = spawn.y();

        ObjectManager om = services().objectManager();
        if (om != null) {
            // ROM parity: Ring_Main calls FindFreeObj during this object's own
            // ExecuteObjects slot, after lower-numbered slots have already had a
            // chance to delete themselves.
            om.allocateChildSlots(spawn, childRingSpawns.size());
        }
        for (int i = 0; i < childRingSpawns.size(); i++) {
            int childX = baseX + (i + 1) * dx;
            int childY = baseY + (i + 1) * dy;
            RingSpawn childRing = childRingSpawns.get(i);
            if (om != null) {
                // Use the slots allocated above from the live SST state of this
                // exec sweep so child numbers match the ROM's FindFreeObj order.
                // The child constructor doesn't need services() — ring children only
                // use services() after construction, so it's safe to construct without
                // CONSTRUCTION_CONTEXT. setServices() is called by addDynamicObjectToReservedSlot.
                Sonic1RingInstance child = new Sonic1RingInstance(
                        buildSpawnAt(childX, childY), childRing, outOfRangeAnchorX);
                om.addDynamicObjectToReservedSlot(child, spawn, i);
            } else {
                spawnChild(() -> new Sonic1RingInstance(
                        buildSpawnAt(childX, childY), childRing, outOfRangeAnchorX));
            }
        }
    }

    @Override
    public int getOutOfRangeReferenceX() {
        return outOfRangeAnchorX;
    }

    // ── TouchResponseProvider ─────────────────────────────────────────────

    @Override
    public int getCollisionFlags() {
        return state == State.ANIMATE ? RING_COLLISION_FLAGS : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (state != State.ANIMATE || result.category() != TouchCategory.SPECIAL) {
            return;
        }
        if (!(playerEntity instanceof com.openggf.sprites.playable.AbstractPlayableSprite player)) {
            return;
        }
        RingManager ringManager = services().ringManager();
        if (ringManager == null || !ringManager.collectPlacedRing(ringSpawn, player, frameCounter)) {
            return;
        }
        // ROM: ReactToItem advances routine immediately on touch, so the ring
        // stops colliding in the same frame even though this engine runs the
        // ring object's normal update before the player touch pass.
        state = State.SPARKLE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No rendering: handled by RingManager
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }
}
