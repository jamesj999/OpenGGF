package com.openggf.game.sonic1.events;

import com.openggf.camera.Camera;

/**
 * Base class for Sonic 1 per-zone dynamic level events.
 * Each zone has its own event routine counter (ROM: v_dle_routine)
 * that tracks progress through Act 3 boss sequences.
 */
abstract class Sonic1ZoneEvents {
    protected final Camera camera;
    protected int eventRoutine;

    Sonic1ZoneEvents(Camera camera) {
        this.camera = camera;
    }

    /** Reset event state for a new level. */
    void init() {
        eventRoutine = 0;
    }

    /** Run per-frame event logic for the given act. */
    abstract void update(int act);

    int getEventRoutine() {
        return eventRoutine;
    }

    void setEventRoutine(int routine) {
        this.eventRoutine = routine;
    }
}
