package com.openggf.data;

import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.io.IOException;

/**
 * Provides palette animation managers for a given level/zone.
 */
public interface AnimatedPaletteProvider {
    AnimatedPaletteManager loadAnimatedPaletteManager(Level level, int zoneIndex) throws IOException;
}
