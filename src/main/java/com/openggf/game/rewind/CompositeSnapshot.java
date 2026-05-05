package com.openggf.game.rewind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable composite of per-subsystem snapshots, keyed by
 * {@link RewindSnapshottable#key()}, in registration order. Returned by
 * {@link RewindRegistry#capture()} and consumed by
 * {@link RewindRegistry#restore(CompositeSnapshot)}.
 */
public final class CompositeSnapshot {

    private final Map<String, Object> entries;

    public CompositeSnapshot(Map<String, Object> entries) {
        Objects.requireNonNull(entries, "entries");
        // Defensive copy preserves insertion order via LinkedHashMap.
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public Map<String, Object> entries() {
        return entries;
    }

    public Object get(String key) {
        return entries.get(key);
    }
}
