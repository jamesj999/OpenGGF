package uk.co.jamesj999.sonic.game;

import uk.co.jamesj999.sonic.Control.InputHandler;

/**
 * No-op implementation of {@link TitleScreenProvider} for games without title screens.
 * Used as the default implementation to avoid null checks.
 * Reports as immediately exiting so the engine skips to the next screen.
 */
public final class NoOpTitleScreenProvider implements TitleScreenProvider {
    public static final NoOpTitleScreenProvider INSTANCE = new NoOpTitleScreenProvider();

    private NoOpTitleScreenProvider() {}

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
}
