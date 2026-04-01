package com.openggf.game.sonic1.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.Sonic1RingPlacement;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
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
public class Sonic1RingInstance extends AbstractObjectInstance implements TouchResponseProvider {

    /** S1 ring collision type: $47 = powerup category ($40) + size index 7. */
    public static final int RING_COLLISION_FLAGS = 0x47;

    private enum State { INIT, ANIMATE, SPARKLE }

    private final RingSpawn ringSpawn;
    private final List<RingSpawn> childRingSpawns;

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
        this.state = State.INIT;
    }

    /**
     * Creates a child ring instance spawned by a parent.
     *
     * @param spawn     dynamically built spawn at child position
     * @param ringSpawn the child's RingSpawn reference in RingManager
     */
    Sonic1RingInstance(ObjectSpawn spawn, RingSpawn ringSpawn) {
        super(spawn, "Ring");
        this.ringSpawn = ringSpawn;
        this.childRingSpawns = List.of();
        this.state = State.ANIMATE;
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
                if (ringManager == null || ringManager.isCollectedAndSparkleDone(
                        ringSpawn.x(), ringSpawn.y(), frameCounter)) {
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

        for (int i = 0; i < childRingSpawns.size(); i++) {
            int childX = baseX + (i + 1) * dx;
            int childY = baseY + (i + 1) * dy;
            RingSpawn childRing = childRingSpawns.get(i);
            spawnChild(() -> new Sonic1RingInstance(buildSpawnAt(childX, childY), childRing));
        }
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
    public void appendRenderCommands(List<GLCommand> commands) {
        // No rendering: handled by RingManager
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }
}
