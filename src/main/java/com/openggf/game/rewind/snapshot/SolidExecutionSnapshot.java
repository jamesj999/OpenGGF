package com.openggf.game.rewind.snapshot;

/**
 * Snapshot record for the solid execution registry.
 *
 * <p>{@code DefaultSolidExecutionRegistry} holds two IdentityHashMaps keyed by
 * object reference ({@code previous} for cross-frame standing state,
 * {@code current} for in-frame contacts). The {@code current} map is cleared on
 * every {@code beginFrame()} call; {@code previous} carries last-frame standing
 * results but is keyed by live {@code ObjectInstance} references that cannot be
 * meaningfully serialised into a frame-boundary snapshot. Restoring by reference
 * after a rewind would leave stale keys referring to objects that may have been
 * despawned or restarted.
 *
 * <p>Consequence: rewind restores a clean {@code previous} map, equivalent to
 * Sonic having just entered the level. Platform-riding standing state will
 * re-establish itself within one frame of physics execution — the same graceful
 * convergence that occurs on a normal level start.
 *
 * <p>This record carries no fields; it exists solely so the key
 * {@code "solid-execution"} appears in {@link com.openggf.game.rewind.CompositeSnapshot}
 * for completeness.
 */
public record SolidExecutionSnapshot() {}
