package com.openggf.game;

/**
 * Factory for power-up visual objects (shields, invincibility stars, splash effects).
 * <p>
 * Decouples {@code sprites.playable.AbstractPlayableSprite} from concrete object
 * types in {@code level.objects} and game-specific packages. The implementation
 * ({@code DefaultPowerUpSpawner}) knows the concrete classes and registers
 * created objects with the {@code ObjectManager}.
 * <p>
 * Lives in {@code com.openggf.game} so it can be referenced from any layer
 * without introducing circular dependencies.
 */
public interface PowerUpSpawner {

    /**
     * Creates a shield object of the given type, registers it with the
     * object manager, and returns a handle.
     *
     * @param player the player to attach the shield to
     * @param type   the shield variant (NORMAL, FIRE, LIGHTNING, BUBBLE)
     * @return a handle to the created shield object
     */
    PowerUpObject spawnShield(PlayableEntity player, ShieldType type);

    /**
     * Creates invincibility stars, registers them with the object manager,
     * and returns a handle.
     *
     * @param player the player to orbit with stars
     * @return a handle to the created invincibility stars object
     */
    PowerUpObject spawnInvincibilityStars(PlayableEntity player);

    /**
     * Creates an insta-shield object (not yet registered — registration is
     * deferred to {@link #registerObject} during {@code tickStatus()}).
     *
     * @param player the player (must be {@code AbstractPlayableSprite})
     * @return a handle to the created insta-shield object
     */
    InstaShieldHandle createInstaShield(PlayableEntity player);

    /**
     * Registers (or re-registers) a previously created power-up object
     * with the current object manager. Used for deferred insta-shield
     * registration and act-transition re-registration.
     *
     * @param obj the power-up object to register
     */
    void registerObject(PowerUpObject obj);

    /**
     * Spawns a water splash effect at the player's position.
     * The implementation determines the water level and chooses the
     * correct splash variant (S1 vs S2/S3K) automatically.
     *
     * @param player the player entering/exiting water
     */
    void spawnSplash(PlayableEntity player);
}
