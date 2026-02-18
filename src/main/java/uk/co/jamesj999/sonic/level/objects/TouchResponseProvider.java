package uk.co.jamesj999.sonic.level.objects;

import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

public interface TouchResponseProvider {
    int getCollisionFlags();
    int getCollisionProperty();

    /**
     * Returns whether touch callbacks should fire every frame while overlapping.
     * <p>
     * Default behavior is edge-triggered (fires only when overlap begins),
     * which matches most touch objects. Some objects rely on per-frame
     * "collision_property"-style polling and need continuous callbacks.
     */
    default boolean requiresContinuousTouchCallbacks() {
        return false;
    }

    /**
     * Optional multi-region touch collision for objects with multiple independent
     * collision areas (e.g., spiked pole helix where each spike has its own hitbox).
     * <p>
     * When this returns non-null, the touch response system checks each region
     * independently instead of using the single spawn position. Each region
     * specifies its own center position and collision flags.
     * <p>
     * Returns null by default (single-region behavior using getCollisionFlags()).
     */
    default TouchRegion[] getMultiTouchRegions() {
        return null;
    }

    /**
     * Shield reaction flags, mirroring the S3K shield_reaction byte semantics.
     * Bit 3 indicates this object should be deflected by shield touch checks.
     */
    default int getShieldReactionFlags() {
        return 0;
    }

    /**
     * Called when the object is deflected by shield touch handling.
     *
     * @return true if the deflect was applied and regular hurt handling should be skipped
     */
    default boolean onShieldDeflect(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * A single touch collision region with its own position and collision type.
     */
    record TouchRegion(int x, int y, int collisionFlags) {}
}
