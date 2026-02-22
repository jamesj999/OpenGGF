package com.openggf.game.sonic2.events;

import com.openggf.camera.Camera;

/**
 * Metropolis Zone events.
 * ROM: LevEvents_MTZ (s2.asm:21036-21173)
 *
 * TODO: 5 routines (standard boss arena pattern) - s2.asm:21082-21173.
 *   Routine 0: Bottom boundary adjustment at X thresholds (Act 1/2/3 differ).
 *   Routine 2: Pre-boss boundary set + advance.
 *   Routine 4: Boss spawn + camera lock + bgm_Boss.
 *   Routine 6: Post-boss left boundary lock.
 *   Routine 8: Left boundary lock (same as routine 6).
 *   MTZ acts 1-2 have vertical wrapping section boundary adjustments.
 */
public class Sonic2MTZEvents extends Sonic2ZoneEvents {

    public Sonic2MTZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void update(int act, int frameCounter) {
        // TODO: Implement MTZ event routines (see class Javadoc)
    }
}
