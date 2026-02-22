package com.openggf.data;

import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPatternManager;

import java.io.IOException;

/**
 * Provides zone animated pattern managers for a game.
 */
public interface AnimatedPatternProvider {
    AnimatedPatternManager loadAnimatedPatternManager(Level level, int zoneIndex) throws IOException;
}
