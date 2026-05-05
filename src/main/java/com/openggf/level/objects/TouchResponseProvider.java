package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

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
     * Returns whether S3K {@code Touch_Special} property-style {@code 0xC0}
     * collision flags should dispatch as listener-only special callbacks instead
     * of boss touch handling.
     */
    default boolean usesS3kTouchSpecialPropertyResponse() {
        return false;
    }

    /**
     * Returns whether touch response should be gated by the engine's render-flag
     * equivalent before testing this object.
     */
    default boolean requiresRenderFlagForTouch() {
        return true;
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
    default boolean onShieldDeflect(PlayableEntity player) {
        return false;
    }

    /**
     * A single touch collision region with its own position and collision type.
     */
    record TouchRegion(int x, int y, int collisionFlags, int shieldReactionFlags) {
        public TouchRegion(int x, int y, int collisionFlags) {
            this(x, y, collisionFlags, 0);
        }
    }
}
