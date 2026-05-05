package com.openggf.level.objects;

/**
 * Immutable capture of the standard mutable field surface of
 * {@link AbstractObjectInstance} for rewind snapshots.
 *
 * <p>This record covers every field declared on {@code AbstractObjectInstance}
 * that changes during gameplay.  Fields that are {@code final} or purely
 * transient (service handles, static camera cache) are intentionally excluded.
 *
 * <p><strong>Subclass contract:</strong> Subclasses that hold private
 * gameplay-relevant state (boss phase counters, badnik AI timers, sub-state
 * machine indices, etc.) <em>must</em> override both
 * {@link AbstractObjectInstance#captureRewindState()} and
 * {@link AbstractObjectInstance#restoreRewindState(PerObjectRewindSnapshot)} and
 * incorporate their own extra fields.  Otherwise that state will silently fail
 * to round-trip across a rewind.  Classes known to need overrides include any
 * boss instance (phase counters, arena flags), badniks with multi-phase AI
 * (e.g. Turtloid, Spiker), and any object that accumulates a timer beyond
 * {@code animTimer} (e.g. CNZ bumper reload, HTZ earthquake object).
 */
public record PerObjectRewindSnapshot(
        // Lifecycle / destruction flags
        boolean destroyed,
        boolean destroyedRespawnable,

        // Dynamic position (null when object has not called updateDynamicSpawn yet;
        // stored as pair to avoid capturing the live ObjectSpawn reference beyond
        // what is needed — position is the only mutable part of a dynamic spawn).
        boolean hasDynamicSpawn,
        int dynamicSpawnX,
        int dynamicSpawnY,

        // Pre-update position snapshot (frame-start position used by touch collision)
        int preUpdateX,
        int preUpdateY,
        boolean preUpdateValid,
        int preUpdateCollisionFlags,

        // Per-frame timing / touch gating flags
        boolean skipTouchThisFrame,
        boolean solidContactFirstFrame,

        // Slot bookkeeping (set by ObjectManager at construction, may change if slot
        // is released and re-assigned, e.g. ring parent-slot release)
        int slotIndex,

        // S1 counter-based respawn index (-1 when not used)
        int respawnStateIndex,

        // Badnik movement state (nullable; only present when capturing
        // AbstractBadnikInstance or subclass)
        BadnikRewindExtra badnikExtra
) {
    /**
     * Immutable capture of {@link AbstractBadnikInstance} movement-state fields
     * (currentX, currentY, xVelocity, yVelocity, animTimer, animFrame, facingLeft).
     */
    public static record BadnikRewindExtra(
            int currentX,
            int currentY,
            int xVelocity,
            int yVelocity,
            int animTimer,
            int animFrame,
            boolean facingLeft
    ) {}
}
