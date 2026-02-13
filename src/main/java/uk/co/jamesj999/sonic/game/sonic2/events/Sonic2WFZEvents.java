package uk.co.jamesj999.sonic.game.sonic2.events;

import uk.co.jamesj999.sonic.camera.Camera;

/**
 * Wing Fortress Zone events.
 * ROM: LevEvents_WFZ (s2.asm:21174-21310)
 *
 * TODO: Platform ride counters, BG scroll lock, boss arena - s2.asm:21174-21310.
 *   Routine 0: Increment platform ride counter (v_dle_data+0).
 *   Routine 2: When counter reaches threshold, lock BG X-scroll and advance.
 *   Routine 4: Boss trigger at camera X threshold + camera lock + bgm_Boss.
 *   Routine 6: Post-boss left boundary lock.
 *   WFZ also has special BG scroll management (bg_x_pos_diff tracking).
 */
public class Sonic2WFZEvents extends Sonic2ZoneEvents {

    public Sonic2WFZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void update(int act, int frameCounter) {
        // TODO: Implement WFZ event routines (see class Javadoc)
    }
}
