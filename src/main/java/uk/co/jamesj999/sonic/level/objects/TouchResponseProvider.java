package uk.co.jamesj999.sonic.level.objects;

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
     * A single touch collision region with its own position and collision type.
     */
    record TouchRegion(int x, int y, int collisionFlags) {}
}
