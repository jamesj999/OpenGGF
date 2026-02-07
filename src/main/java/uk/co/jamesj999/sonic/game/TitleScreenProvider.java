package uk.co.jamesj999.sonic.game;

import uk.co.jamesj999.sonic.Control.InputHandler;

/**
 * Interface for game-specific title screens.
 * Each game (Sonic 1, Sonic 2, etc.) provides its own implementation
 * with game-accurate art, palettes, and scrolling.
 */
public interface TitleScreenProvider {

    enum State {
        /** Screen is not active */
        INACTIVE,
        /** Intro text fading in from black */
        INTRO_TEXT_FADE_IN,
        /** Intro text displayed (hold) */
        INTRO_TEXT_HOLD,
        /** Intro text fading out to black */
        INTRO_TEXT_FADE_OUT,
        /** Fading in from black */
        FADE_IN,
        /** Main interactive state */
        ACTIVE,
        /** Ready to exit */
        EXITING
    }

    /**
     * Initializes the title screen.
     * Loads data from ROM and begins fade-in.
     */
    void initialize();

    /**
     * Updates the title screen state machine.
     *
     * @param input Input handler for keyboard input
     */
    void update(InputHandler input);

    /**
     * Renders the title screen.
     */
    void draw();

    /**
     * Sets the OpenGL clear color to the title screen backdrop color.
     */
    void setClearColor();

    /**
     * Resets the manager to inactive state.
     */
    void reset();

    /**
     * Returns the current state.
     */
    State getState();

    /**
     * Returns true if the title screen is exiting (should transition to next screen).
     */
    boolean isExiting();

    /**
     * Returns true if the title screen is active (not inactive).
     */
    boolean isActive();
}
