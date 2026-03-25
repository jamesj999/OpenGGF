package com.openggf.game;

import com.openggf.control.InputHandler;

/**
 * No-op implementation of {@link LevelSelectProvider} for games without level select.
 * Used as the default implementation to avoid null checks.
 * Reports as immediately exiting with zone 0, act 0 selected.
 */
public final class NoOpLevelSelectProvider implements LevelSelectProvider {
    public static final NoOpLevelSelectProvider INSTANCE = new NoOpLevelSelectProvider();

    private NoOpLevelSelectProvider() {}

    @Override
    public void initialize() {
        // No-op
    }

    @Override
    public void update(InputHandler input) {
        // No-op
    }

    @Override
    public void draw() {
        // No-op
    }

    @Override
    public void setClearColor() {
        // No-op
    }

    @Override
    public void reset() {
        // No-op
    }

    @Override
    public State getState() {
        return State.EXITING;
    }

    @Override
    public boolean isExiting() {
        return true;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean isSpecialStageSelected() {
        return false;
    }

    @Override
    public boolean isSoundTestSelected() {
        return false;
    }

    @Override
    public int getSelectedZone() {
        return 0;
    }

    @Override
    public int getSelectedAct() {
        return 0;
    }

    @Override
    public int getSelectedZoneAct() {
        return 0;
    }

    @Override
    public int getSelectedIndex() {
        return 0;
    }

    @Override
    public int getSoundTestValue() {
        return 0;
    }
}
