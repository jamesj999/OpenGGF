package com.openggf.game;

/**
 * No-op implementation of {@link RomOffsetProvider} for games without a ROM offset provider.
 * Used as the default implementation to avoid null checks.
 * All offset lookups return -1 (not found).
 */
public final class NoOpRomOffsetProvider implements RomOffsetProvider {
    public static final NoOpRomOffsetProvider INSTANCE = new NoOpRomOffsetProvider();

    private NoOpRomOffsetProvider() {}

    @Override
    public int getOffset(String category, String name) {
        return -1;
    }
}
