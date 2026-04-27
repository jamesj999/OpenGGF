package com.openggf.trace;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;

import java.util.Collection;
import java.util.List;

/**
 * Legacy compatibility wrapper for pre-trace ROM object snapshots.
 *
 * <p>Snapshots are now read-only diagnostics. The replay harness must not copy
 * recorded object memory back into engine instances.
 */
public final class TraceObjectSnapshotBinder {

    private TraceObjectSnapshotBinder() {}

    public record Result(int attempted, int matched, List<String> warnings) {}

    /**
     * Does not mutate engine objects. Kept so older callers can receive an
     * explicit no-op result while the trace ledger remains read-only.
     */
    public static Result apply(ObjectManager objectManager,
                               List<TraceEvent.ObjectStateSnapshot> snapshots) {
        return apply(objectManager != null ? objectManager.getActiveObjects() : null, snapshots);
    }

    /**
     * Collection-based no-op overload for unit testing and custom instance sources.
     */
    public static Result apply(Collection<? extends ObjectInstance> candidates,
                               List<TraceEvent.ObjectStateSnapshot> snapshots) {
        return new Result(snapshots != null ? snapshots.size() : 0, 0, List.of());
    }
}
