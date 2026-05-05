package com.openggf.game.rewind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the list of {@link RewindSnapshottable} subsystems for the
 * current gameplay session. Owned by {@code GameplayModeContext}.
 *
 * <p>Capture and restore are atomic per frame: no subsystem is mid-step
 * during these operations, so registration order does not affect
 * correctness. Order is preserved for predictable diffing during
 * debugging.
 *
 * <p>Restore is tolerant of unknown keys (a subsystem that was
 * registered when a snapshot was captured may have been deregistered
 * since); such entries are skipped. The reverse — registered subsystems
 * with no entry in the snapshot — leaves them at their current state.
 */
public final class RewindRegistry {

    private final Map<String, RewindSnapshottable<?>> entries = new LinkedHashMap<>();

    public void register(RewindSnapshottable<?> s) {
        Objects.requireNonNull(s, "s");
        if (entries.putIfAbsent(s.key(), s) != null) {
            throw new IllegalStateException(
                    "RewindSnapshottable already registered: " + s.key());
        }
    }

    public void deregister(String key) {
        entries.remove(key);
    }

    public CompositeSnapshot capture() {
        var bundle = new LinkedHashMap<String, Object>(entries.size());
        for (var e : entries.entrySet()) {
            bundle.put(e.getKey(), e.getValue().capture());
        }
        return new CompositeSnapshot(bundle);
    }

    public void restore(CompositeSnapshot cs) {
        Objects.requireNonNull(cs, "cs");
        for (var e : entries.entrySet()) {
            Object snap = cs.get(e.getKey());
            if (snap == null) continue;
            @SuppressWarnings({"rawtypes", "unchecked"})
            RewindSnapshottable raw = e.getValue();
            raw.restore(snap);
        }
    }
}
