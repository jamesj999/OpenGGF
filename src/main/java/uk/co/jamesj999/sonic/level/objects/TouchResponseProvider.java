package uk.co.jamesj999.sonic.level.objects;

public interface TouchResponseProvider {
    int getCollisionFlags();
    int getCollisionProperty();

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
