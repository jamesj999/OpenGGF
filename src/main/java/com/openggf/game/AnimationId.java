package com.openggf.game;

/**
 * Common interface for game-specific player animation ID enums.
 * Each game defines its own enum implementing this interface.
 */
public interface AnimationId {
    int id();

    /**
     * Resolves a CanonicalAnimation back to the native animation ID for the given
     * set of AnimationId values. Returns -1 if the canonical animation is not
     * natively supported by any entry in the set.
     */
    static <E extends Enum<E> & AnimationId> int fromCanonical(E[] values, java.util.function.Function<E, CanonicalAnimation> toCanonical, CanonicalAnimation canonical) {
        for (E anim : values) {
            if (toCanonical.apply(anim) == canonical) {
                return anim.id();
            }
        }
        return -1;
    }
}
