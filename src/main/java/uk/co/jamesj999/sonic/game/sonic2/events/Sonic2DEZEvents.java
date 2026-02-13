package uk.co.jamesj999.sonic.game.sonic2.events;

import uk.co.jamesj999.sonic.camera.Camera;

/**
 * Death Egg Zone events.
 * ROM: LevEvents_DEZ (s2.asm:21311-21395)
 *
 * TODO: Silver Sonic arena + main boss arena - s2.asm:21311-21395.
 *   Routine 0: Camera X trigger for Silver Sonic spawn + camera lock + bgm_Boss.
 *   Routine 2: Post-Silver Sonic left boundary lock.
 *   Routine 4: Camera X trigger for Death Egg Robot spawn + camera lock.
 *   Routine 6: Post-boss left boundary lock.
 *   Two sequential boss fights in a single act.
 */
public class Sonic2DEZEvents extends Sonic2ZoneEvents {

    public Sonic2DEZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void update(int act, int frameCounter) {
        // TODO: Implement DEZ event routines (see class Javadoc)
    }
}
