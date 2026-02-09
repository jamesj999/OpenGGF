package uk.co.jamesj999.sonic.game;

/**
 * Per-game physics provider interface.
 * Returns character-specific physics profiles, modifier rules, and feature flags.
 *
 * <p>Implementations are game-specific (S1, S2, S3K) and accessed via
 * {@link GameModule#getPhysicsProvider()}.
 */
public interface PhysicsProvider {

    /**
     * Returns the physics profile for the given character type.
     *
     * @param characterType identifier such as "sonic" or "tails"
     * @return the physics profile for the character
     */
    PhysicsProfile getProfile(String characterType);

    /**
     * Returns the physics modifiers (water/speed shoes rules) for this game.
     *
     * @return the physics modifiers
     */
    PhysicsModifiers getModifiers();

    /**
     * Returns the feature set (spindash availability, etc.) for this game.
     *
     * @return the physics feature set
     */
    PhysicsFeatureSet getFeatureSet();
}
