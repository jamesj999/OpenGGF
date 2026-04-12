package com.openggf.game.sonic3k.dataselect;

import com.openggf.control.InputHandler;
import com.openggf.game.dataselect.AbstractDataSelectProvider;

/**
 * S3K-specific data select screen manager.
 * Implements the data select lifecycle for Sonic 3 &amp; Knuckles save file selection.
 */
public final class S3kDataSelectManager extends AbstractDataSelectProvider {

    @Override
    public void initialize() {
        state = State.FADE_IN;
    }

    @Override
    public void update(InputHandler input) {
        if (state == State.FADE_IN) {
            state = State.ACTIVE;
        }
    }

    @Override
    public void draw() {
        // Will be implemented in future tasks with actual rendering
    }

    @Override
    public void setClearColor() {
        // Will be implemented in future tasks
    }

    @Override
    public void reset() {
        state = State.INACTIVE;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public boolean isExiting() {
        return state == State.EXITING;
    }

    @Override
    public boolean isActive() {
        return state != State.INACTIVE;
    }
}
