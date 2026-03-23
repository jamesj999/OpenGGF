package com.openggf.game;

/**
 * No-op implementation of {@link DebugModeProvider} for games without game-specific debug modes.
 * Used as the default implementation to avoid null checks.
 * Reports no debug capabilities and returns null for any controller.
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
