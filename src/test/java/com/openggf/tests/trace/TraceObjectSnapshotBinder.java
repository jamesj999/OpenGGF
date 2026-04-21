package com.openggf.tests.trace;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Applies pre-trace ROM object snapshots (recorded by the Lua recorder at frame -1)
 * onto the freshly-spawned engine object instances.
 *
 * <p>The ROM runs many Level_MainLoop iterations during title card and level
 * initialization before the Lua recorder begins emitting trace frames. During
 * those iterations, object state machines (routines, timers, climb cursors)
 * accumulate state. The engine, by contrast, starts with freshly-constructed
 * instances at trace frame 0. Without snapshot hydration, engine and ROM state
 * diverge after the first object update.
 *
 * <p>Matching strategy (first-match-wins on a shrinking unclaimed pool):
 * <ol>
 *   <li><b>Exact triple:</b> (objectType, spawn.x, spawn.y) match. Handles
 *       stationary objects that have not moved from their placement.</li>
 *   <li><b>Same-type + same X:</b> Handles objects like Coconuts whose Y has
 *       shifted during pre-trace climbing but whose X is spawn-pinned.</li>
 *   <li><b>Single same-type candidate:</b> When exactly one unclaimed engine
 *       instance of the ROM's object type remains, claim it. Safe because the
 *       ambiguity is resolved by uniqueness.</li>
 * </ol>
 *
 * <p>Unmatched snapshots produce warnings but do not fail the test — they may
 * represent ephemeral ROM objects (explosions, points popups) that the engine
 * has not yet spawned.
 */
public final class TraceObjectSnapshotBinder {

    private TraceObjectSnapshotBinder() {}

    public record Result(int attempted, int matched, List<String> warnings) {}

    /**
     * Hydrates engine objects from the given snapshot list. Non-throwing:
     * any per-snapshot failure is reported as a warning.
     */
    public static Result apply(ObjectManager objectManager,
                               List<TraceEvent.ObjectStateSnapshot> snapshots) {
        if (objectManager == null) {
            return new Result(0, 0, new ArrayList<>());
        }
        return apply(objectManager.getActiveObjects(), snapshots);
    }

    /**
     * Collection-based overload for unit testing and custom instance sources.
     */
    public static Result apply(Collection<? extends ObjectInstance> candidates,
                               List<TraceEvent.ObjectStateSnapshot> snapshots) {
        List<String> warnings = new ArrayList<>();
        if (candidates == null || snapshots == null || snapshots.isEmpty()) {
            return new Result(0, 0, warnings);
        }

        List<AbstractObjectInstance> unclaimed = new ArrayList<>();
        for (ObjectInstance inst : candidates) {
            if (inst instanceof AbstractObjectInstance aoi
                    && aoi.getSpawn() != null
                    && aoi.getSpawn().objectId() != 0) {
                unclaimed.add(aoi);
            }
        }

        int matched = 0;
        for (TraceEvent.ObjectStateSnapshot snapshot : snapshots) {
            AbstractObjectInstance target = findMatch(snapshot, unclaimed, warnings);
            if (target == null) {
                continue;
            }
            try {
                target.hydrateFromRomSnapshot(snapshot.fields());
                unclaimed.remove(target);
                matched++;
            } catch (RuntimeException e) {
                warnings.add(String.format(
                        "Hydration failed for ROM slot %d type 0x%02X: %s",
                        snapshot.slot(), snapshot.objectType(), e.getMessage()));
            }
        }

        return new Result(snapshots.size(), matched, warnings);
    }

    private static AbstractObjectInstance findMatch(
            TraceEvent.ObjectStateSnapshot snapshot,
            List<AbstractObjectInstance> unclaimed,
            List<String> warnings) {
        int type = snapshot.objectType() & 0xFF;
        RomObjectSnapshot fields = snapshot.fields();
        int romX = fields.xPos() & 0xFFFF;
        int romY = fields.yPos() & 0xFFFF;

        AbstractObjectInstance exactMatch = null;
        AbstractObjectInstance sameXMatch = null;
        AbstractObjectInstance sameTypeLone = null;
        int sameTypeCount = 0;

        for (AbstractObjectInstance aoi : unclaimed) {
            ObjectSpawn spawn = aoi.getSpawn();
            if ((spawn.objectId() & 0xFF) != type) {
                continue;
            }
            sameTypeCount++;
            sameTypeLone = aoi;

            int sx = spawn.x() & 0xFFFF;
            int sy = spawn.y() & 0xFFFF;
            if (sx == romX && sy == romY) {
                exactMatch = aoi;
                break;
            }
            if (sx == romX && sameXMatch == null) {
                sameXMatch = aoi;
            }
        }

        if (exactMatch != null) return exactMatch;
        if (sameXMatch != null) return sameXMatch;
        if (sameTypeCount == 1) return sameTypeLone;

        warnings.add(String.format(
                "No engine instance for ROM slot %d type 0x%02X @ (%d,%d) [%d same-type candidates]",
                snapshot.slot(), type, romX, romY, sameTypeCount));
        return null;
    }
}
