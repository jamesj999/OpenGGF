package uk.co.jamesj999.sonic.game;

import uk.co.jamesj999.sonic.Control.InputHandler;

/**
 * Interface for game-specific level select screens.
 * Each game (Sonic 1, Sonic 2, etc.) provides its own implementation
 * with game-accurate menu layout, text, and navigation.
 */
public interface LevelSelectProvider {

    enum State {
        /** Screen is not active */
        INACTIVE,
        /** Fading in from black */
        FADE_IN,
        /** Main interactive state */
        ACTIVE,
        /** Ready to exit and load selected level */
        EXITING
    }

    /**
     * Initializes the level select screen.
     * Loads data from ROM and begins fade-in.
     */
    void initialize();

    /**
     * Updates the level select state machine.
     *
     * @param input Input handler for keyboard input
     */
    void update(InputHandler input);

    /**
     * Renders the level select screen.
     */
    void draw();

    /**
     * Sets the OpenGL clear color to the level select backdrop color.
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
     * Returns true if the level select is exiting (level should be loaded).
     */
    boolean isExiting();

    /**
     * Returns true if the level select is active (not inactive).
     */
    boolean isActive();

    /**
     * Returns true if Special Stage is selected.
     */
    boolean isSpecialStageSelected();

    /**
     * Returns true if Sound Test is selected.
     */
    boolean isSoundTestSelected();

    /**
     * Gets the selected zone index or -1 for special stage/sound test.
     */
    int getSelectedZone();

    /**
     * Gets the selected act index or -1 for special stage/sound test.
     */
    int getSelectedAct();

    /**
     * Gets the selected zone/act word value.
     * High byte = zone ID, low byte = act number.
     * Special values are game-specific.
     */
    int getSelectedZoneAct();

    /**
     * Gets the current menu selection index.
     */
    int getSelectedIndex();

    /**
     * Gets the current sound test value.
     */
    int getSoundTestValue();
}
