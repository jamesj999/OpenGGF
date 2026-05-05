package com.openggf.game.rewind.snapshot;

/**
 * Snapshot of {@link com.openggf.game.zone.ZoneRuntimeRegistry} per-frame state.
 * Per-zone runtime state is captured polymorphically via
 * {@link com.openggf.game.zone.ZoneRuntimeState#captureBytes()} /
 * {@link com.openggf.game.zone.ZoneRuntimeState#restoreBytes(byte[])}.
 * The default no-op implementation covers zones without dynamic runtime state;
 * implementations such as HTZ earthquake or CNZ bumper timers override the hooks
 * to serialize their fields deterministically.
 */
public record ZoneRuntimeSnapshot(byte[] stateBytes) {}
