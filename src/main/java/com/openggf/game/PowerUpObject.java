package com.openggf.game;

/**
 * Lightweight handle for power-up visual objects (shields, invincibility stars).
 * <p>
 * Lives in the {@code game} package so that {@code sprites.playable} can reference
 * power-up objects without importing concrete types from {@code level.objects}.
 * Implementations live in {@code level.objects} and {@code game.sonic3k.objects}.
 */
public interface PowerUpObject {

    /** Marks this object as destroyed (removes it from rendering/updates). */
    void destroy();

    /** Returns {@code true} if this object has been destroyed. */
    boolean isDestroyed();

    /** Shows or hides the visual representation without destroying the object. */
    void setVisible(boolean visible);

    /**
     * Notifies this power-up that the player activated its secondary ability.
     * <p>
     * Elemental shields override this to trigger their attack animation/effect:
     * <ul>
     *   <li>Fire shield: dash animation (actionId=1)</li>
     *   <li>Lightning shield: spark particles (actionId=1)</li>
     *   <li>Bubble shield: bounce animation (actionId=1 for slam, actionId=2 for ground bounce)</li>
     * </ul>
     * <p>
     * Non-elemental power-ups (standard shield, invincibility) ignore this call.
     *
     * @param actionId the ability action identifier (1=primary, 2=secondary/variant)
     */
    default void onAbilityActivated(int actionId) {
        // Default no-op for non-elemental power-ups
    }
}
