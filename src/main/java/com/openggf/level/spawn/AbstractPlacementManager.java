package com.openggf.level.spawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Shared windowing support for spawn placement managers.
 */
public abstract class AbstractPlacementManager<T extends SpawnPoint> {
    private static final Logger LOGGER = Logger.getLogger(AbstractPlacementManager.class.getName());

    protected final List<T> spawns;
    protected final Set<T> active = new LinkedHashSet<>();
    protected final Collection<T> activeUnmodifiable = Collections.unmodifiableCollection(active);
    protected final Map<T, Integer> spawnIndexMap = new IdentityHashMap<>();
    private final int loadAhead;
    private final int unloadBehind;

    protected AbstractPlacementManager(List<T> spawns, int loadAhead, int unloadBehind) {
        ArrayList<T> sorted = new ArrayList<>(spawns);
        sorted.sort(Comparator.comparingInt(SpawnPoint::x));
        this.spawns = Collections.unmodifiableList(sorted);
        this.loadAhead = loadAhead;
        this.unloadBehind = unloadBehind;
        for (int i = 0; i < this.spawns.size(); i++) {
            spawnIndexMap.put(this.spawns.get(i), i);
        }
    }

    public List<T> getAllSpawns() {
        return spawns;
    }

    public Collection<T> getActiveSpawns() {
        return activeUnmodifiable;
    }

    /**
     * Returns the index of the given spawn in the sorted spawns list.
     * Uses identity-based lookup first (fast path), then falls back to
     * equals-based linear scan if the reference doesn't match.
     * The fallback handles cases where an object instance holds a spawn
     * reference that differs from the canonical reference stored during
     * construction (e.g. if the spawn was reconstructed or deserialized).
     */
    public int getSpawnIndex(T spawn) {
        Integer index = spawnIndexMap.get(spawn);
        if (index != null) {
            return index;
        }
        // Fallback: linear scan using equals() in case the identity reference
        // doesn't match the canonical reference stored in spawnIndexMap.
        for (int i = 0; i < spawns.size(); i++) {
            if (spawns.get(i).equals(spawn)) {
                final int foundIndex = i;
                LOGGER.warning(() -> "getSpawnIndex: identity miss for spawn at ("
                        + spawn.x() + "," + spawn.y() + "), found via equals at index " + foundIndex);
                return i;
            }
        }
        return -1;
    }

    protected int getLoadAhead() {
        return loadAhead;
    }

    protected int getUnloadBehind() {
        return unloadBehind;
    }

    protected int getWindowStart(int cameraX) {
        return Math.max(0, cameraX - unloadBehind);
    }

    protected int getWindowEnd(int cameraX) {
        return cameraX + loadAhead;
    }

    protected int lowerBound(int value) {
        int low = 0;
        int high = spawns.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (spawns.get(mid).x() < value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    protected int upperBound(int value) {
        int low = 0;
        int high = spawns.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (spawns.get(mid).x() <= value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
