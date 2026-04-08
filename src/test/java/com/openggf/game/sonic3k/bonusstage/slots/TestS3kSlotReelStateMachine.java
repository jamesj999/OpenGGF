package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kSlotReelStateMachine {

    @Test
    void resetSetsStateToZero() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();
        assertEquals(0, reel.state());
        assertEquals(Integer.MIN_VALUE, reel.lastPrizeResult());
    }

    @Test
    void initStateAdvancesToSpinning() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();
        reel.tick(0); // state 0 → init → state 1
        assertEquals(1, reel.state());
    }

    @Test
    void fullCycleReachesAwardState() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();

        boolean reachedAward = false;
        for (int i = 0; i < 200; i++) {
            reel.tick(i);
            if (reel.state() >= 5) {
                reachedAward = true;
                break;
            }
        }
        assertTrue(reachedAward, "Should reach award state within 200 frames, got state " + reel.state());
    }

    @Test
    void prizeIsPopulatedAfterAwardState() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();

        for (int i = 0; i < 200; i++) {
            reel.tick(i);
            if (reel.state() == 6) break; // idle after award
        }
        assertEquals(6, reel.state());
        assertNotEquals(Integer.MIN_VALUE, reel.lastPrizeResult());
    }

    @Test
    void resetAfterCycleAllowsReplay() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();

        // Run through first cycle
        for (int i = 0; i < 200; i++) {
            reel.tick(i);
            if (reel.state() == 6) break;
        }
        int firstPrize = reel.lastPrizeResult();

        // Reset and run again with different seed
        reel.reset();
        assertEquals(0, reel.state());
        assertEquals(Integer.MIN_VALUE, reel.lastPrizeResult());

        for (int i = 100; i < 300; i++) {
            reel.tick(i);
            if (reel.state() == 6) break;
        }
        assertNotEquals(Integer.MIN_VALUE, reel.lastPrizeResult());
    }

    @Test
    void idleStateDoesNotAdvanceFurther() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();

        for (int i = 0; i < 200; i++) {
            reel.tick(i);
            if (reel.state() == 6) break;
        }
        assertEquals(6, reel.state());

        // Additional ticks should stay at state 6
        reel.tick(999);
        assertEquals(6, reel.state());
    }

    @Test
    void statesProgressMonotonically() {
        S3kSlotReelStateMachine reel = new S3kSlotReelStateMachine();
        reel.reset();

        int previousState = -1;
        for (int i = 0; i < 200; i++) {
            reel.tick(i);
            int currentState = reel.state();
            assertTrue(currentState >= previousState,
                    "State should not decrease: was " + previousState + " now " + currentState);
            previousState = currentState;
            if (currentState == 6) break;
        }
    }
}
