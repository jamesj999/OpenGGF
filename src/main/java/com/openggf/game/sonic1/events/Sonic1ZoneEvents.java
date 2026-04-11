package com.openggf.game.sonic1.events;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;

import java.util.function.Supplier;

/**
 * Base class for Sonic 1 per-zone dynamic level events.
 * Each zone has its own event routine counter (ROM: v_dle_routine)
 * that tracks progress through Act 3 boss sequences.
 */
abstract class Sonic1ZoneEvents {
    protected int eventRoutine;

    Sonic1ZoneEvents() {
    }

    /**
     * Returns the current Camera singleton. Always call this accessor rather
     * than caching the reference, so it survives singleton replacement.
     */
    protected Camera camera() {
        return GameServices.camera();
    }

    protected LevelManager levelManager() {
        return GameServices.level();
    }

    protected AudioManager audio() {
        return GameServices.audio();
    }

    protected GameStateManager gameState() {
        return GameServices.gameState();
    }

    protected <T> T gameService(Class<T> type) {
        return GameServices.module().getGameService(type);
    }

    /** Reset event state for a new level. */
    void init() {
        eventRoutine = 0;
    }

    /** Run per-frame event logic for the given act. */
    abstract void update(int act);

    protected <T extends ObjectInstance> T spawnObject(Supplier<T> factory) {
        LevelManager lm = levelManager();
        if (lm == null || lm.getObjectManager() == null) {
            return null;
        }
        return lm.getObjectManager().createDynamicObject(factory);
    }

    int getEventRoutine() {
        return eventRoutine;
    }

    void setEventRoutine(int routine) {
        this.eventRoutine = routine;
    }
}
