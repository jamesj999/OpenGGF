package com.openggf.game.sonic2.events;

import com.openggf.camera.Camera;

/**
 * Sky Chase Zone events.
 * ROM: LevEvents_SCZ (s2.asm:21396-21485)
 *
 * TODO: Tornado auto-scroll velocity management - s2.asm:21396-21485.
 *   Routine 0: Set initial auto-scroll velocity (v_dle_data used as velocity).
 *   Routine 2: Accelerate scroll speed at camera X thresholds.
 *   Routine 4: Decelerate / change direction for boss approach.
 *   Routine 6: Boss trigger at camera X threshold + camera lock + bgm_Boss.
 *   SCZ uses auto-scroll (camera moves independently of player input).
 *   Velocity stored in v_dle_data words, applied to camera X each frame.
 */
public class Sonic2SCZEvents extends Sonic2ZoneEvents {

    public Sonic2SCZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void update(int act, int frameCounter) {
        // TODO: Implement SCZ event routines (see class Javadoc)
    }
}
