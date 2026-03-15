package com.openggf.graphics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestFadeManager {

    /**
     * Number of update frames for a standard fade to complete.
     * Matches FadeManager.FADE_DURATION (7 steps per RGB channel = 21 frames).
     */
    private static final int FADE_DURATION_FRAMES = 21;

    @Test
    public void testFadeToWhiteCompletes() {
        FadeManager.resetInstance();
        FadeManager fadeManager = FadeManager.getInstance();

        fadeManager.startFadeToWhite(null);
        for (int i = 0; i < FADE_DURATION_FRAMES; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.HOLD_WHITE, fadeManager.getState());
        fadeManager.update();
        assertEquals(FadeManager.FadeState.NONE, fadeManager.getState());
        assertFalse(fadeManager.isActive());
    }

    @Test
    public void testFadeToBlackWithHoldCompletes() {
        FadeManager.resetInstance();
        FadeManager fadeManager = FadeManager.getInstance();

        fadeManager.startFadeToBlack(null, 5);
        for (int i = 0; i < FADE_DURATION_FRAMES; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.HOLD_BLACK, fadeManager.getState());

        for (int i = 0; i < 5; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.NONE, fadeManager.getState());
    }
}
