package com.openggf.game.sonic3k.titlecard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests bonus mode behavior in Sonic3kTitleCardManager.
 * These tests don't require ROM or OpenGL â€” they exercise the state machine only.
 */
public class TestSonic3kBonusTitleCard {

    private Sonic3kTitleCardManager manager;

    @BeforeEach
    public void setUp() {
        manager = new Sonic3kTitleCardManager();
        manager.reset();
    }

    @Test
    public void initializeBonusSetsSlideInState() {
        manager.initializeBonus();
        assertFalse(manager.isComplete(), "Should not be complete after init");
        assertFalse(manager.shouldReleaseControl(), "Should not release control during slide-in");
    }

    @Test
    public void bonusModeReleasesControlOnExit() {
        manager.initializeBonus();

        // Advance through SLIDE_IN (elements slide from right to target)
        // Max distance is 360-168=192 at 16px/frame = 12 frames
        for (int i = 0; i < 20; i++) {
            manager.update();
        }
        // Advance through DISPLAY hold (90 frames, fade runs in last 22)
        for (int i = 0; i < 90; i++) {
            manager.update();
        }
        // Should now be in EXIT phase â€” control released
        assertTrue(manager.shouldReleaseControl(), "Should release control during exit");
    }

    @Test
    public void bonusModeCompletesAfterFullAnimation() {
        manager.initializeBonus();
        // Run enough frames for full animation cycle:
        // SLIDE_IN (~12 frames) + DISPLAY (90 frames) + EXIT (~10 frames)
        for (int i = 0; i < 150; i++) {
            manager.update();
        }
        assertTrue(manager.isComplete(), "Should be complete after full animation");
    }

    @Test
    public void normalModeStillWorksAfterBonusMode() {
        // First run bonus mode
        manager.initializeBonus();
        for (int i = 0; i < 150; i++) {
            manager.update();
        }
        assertTrue(manager.isComplete());

        // Then run normal mode
        manager.initialize(0, 0); // AIZ act 1
        assertFalse(manager.isComplete(), "Should not be complete after normal init");
    }

    @Test
    public void shouldNotRunPlayerPhysicsInBonusMode() {
        manager.initializeBonus();
        assertFalse(manager.shouldRunPlayerPhysics());
    }
}


