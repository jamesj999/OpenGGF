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
}
