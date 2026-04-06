package com.openggf.game.sonic3k.titlecard;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests bonus mode behavior in Sonic3kTitleCardManager.
 * These tests don't require ROM or OpenGL — they exercise the state machine only.
 */
public class TestSonic3kBonusTitleCard {

    private Sonic3kTitleCardManager manager;

    @Before
    public void setUp() {
        manager = Sonic3kTitleCardManager.getInstance();
        manager.reset();
    }

    @Test
    public void initializeBonusSetsSlideInState() {
        manager.initializeBonus();
        assertFalse("Should not be complete after init", manager.isComplete());
        assertFalse("Should not release control during slide-in", manager.shouldReleaseControl());
    }

    @Test
    public void bonusModeReleasesControlOnExit() {
        manager.initializeBonus();

        // Advance through SLIDE_IN (elements slide from right to target)
        // Max distance is 360-168=192 at 16px/frame = 12 frames
        for (int i = 0; i < 20; i++) {
            manager.update();
        }
        // Advance through DISPLAY hold (90 frames)
        for (int i = 0; i < 90; i++) {
            manager.update();
        }
        // Should now be in EXIT phase — control released
        assertTrue("Should release control during exit", manager.shouldReleaseControl());
    }

    @Test
    public void bonusModeCompletesAfterFullAnimation() {
        manager.initializeBonus();
        // Run enough frames for full animation cycle:
        // SLIDE_IN (~12 frames) + DISPLAY (90 frames) + EXIT (~10 frames)
        for (int i = 0; i < 150; i++) {
            manager.update();
        }
        assertTrue("Should be complete after full animation", manager.isComplete());
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
        assertFalse("Should not be complete after normal init", manager.isComplete());
    }

    @Test
    public void shouldNotRunPlayerPhysicsInBonusMode() {
        manager.initializeBonus();
        assertFalse(manager.shouldRunPlayerPhysics());
    }
}
