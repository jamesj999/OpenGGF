package com.openggf.game.rewind;

/**
 * A subsystem snapshot contract used by {@link RewindRegistry} and
 * {@link RewindController}.
 *
 * <p>Implementations capture an immutable snapshot of their state and
 * restore from one. The snapshot type {@code S} is typically a record
 * with primitive / immutable fields; opaque {@code Object} payloads are
 * also valid.
 *
 * <p>{@link #key()} must be stable across captures of the same
 * subsystem — it is used as the key into {@link CompositeSnapshot}.
 */
public interface RewindSnapshottable<S> {
    String key();
    S capture();
    void restore(S snapshot);
}
