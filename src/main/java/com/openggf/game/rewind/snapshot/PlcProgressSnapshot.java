package com.openggf.game.rewind.snapshot;

/**
 * Snapshot of the PLC art loading epoch for a game's object art provider.
 *
 * <p>The engine loads all PLC-driven object art at zone-load time
 * ({@code loadArtForZone}). No per-frame dynamic PLC loading is implemented
 * in v1; this snapshot records a {@code loadEpoch} counter that increments
 * each time a zone's art is loaded, giving the rewind system a cheap
 * consistency check without having to re-decompress art on restore.
 *
 * <p>On restore the epoch value is used for diagnostic purposes only — if the
 * restored epoch differs from the current one, the art provider was reloaded
 * between snapshot and restore, which would indicate a level-transition rewind
 * edge case (out of scope for v1).
 */
public record PlcProgressSnapshot(int loadEpoch) {
}
