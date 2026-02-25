package com.openggf.game.sonic2.events;

import com.openggf.camera.Camera;

/**
 * Sky Chase Zone events.
 * ROM: LevEvents_SCZ (s2.asm:21803-21861)
 *
 * SCZ level events (Tornado velocity management and camera auto-scroll)
 * are implemented directly in {@link com.openggf.level.scroll.SwScrlScz}
 * because they are tightly coupled with the scroll handler's camera
 * updates. The scroll handler owns Tornado_Velocity_X/Y and
 * Dynamic_Resize_Routine for SCZ.
 *
 * Act 2 (LevEvents_SCZ2) has no events — just returns.
 */
public class Sonic2SCZEvents extends Sonic2ZoneEvents {

    public Sonic2SCZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void update(int act, int frameCounter) {
        // SCZ events are handled by SwScrlScz.updateLevelEvents()
    }
}
