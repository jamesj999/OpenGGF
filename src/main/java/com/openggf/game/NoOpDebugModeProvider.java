package com.openggf.game;

/**
 * No-op sentinel implementation of {@link DebugModeProvider} for games without game-specific
 * debug modes. Reports no debug capabilities via {@code has*()} guard methods.
 *
 * <p>Note: {@link #getSpecialStageDebugController()} returns {@code null}. Callers must guard
 * with {@link #hasSpecialStageDebug()} before calling it.
 */
public final class NoOpDebugModeProvider implements DebugModeProvider {
    public static final NoOpDebugModeProvider INSTANCE = new NoOpDebugModeProvider();

    private NoOpDebugModeProvider() {}

    @Override
    public boolean hasSpecialStageDebug() {
        return false;
    }

    @Override
    public SpecialStageDebugController getSpecialStageDebugController() {
        return null;
    }

    @Override
    public boolean hasLevelDebug() {
        return false;
    }
}
