package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSlotTileInteraction {

    private S3kSlotStageController controller;
    private S3kSlotTileInteraction.State state;

    @BeforeEach
    void setUp() {
        controller = new S3kSlotStageController();
        controller.bootstrap();
        state = new S3kSlotTileInteraction.State();
    }

    // --- Tile 5 (bumper) ---

    @Test
    void bumperLaunchesPlayerAwayFromBumperCenter() {
        // Player is to the right of bumper center → should be launched rightward (away)
        short playerX = (short) 200;
        short playerY = (short) 100;
        short tileCenterX = (short) 100; // bumper is to the left
        short tileCenterY = (short) 100;

        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(5, playerX, playerY, tileCenterX, tileCenterY, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.BUMPER_LAUNCH, response.effect());
        // Player is to the right (dx = tileCenterX - playerX = -100), so launch X should be positive (away)
        assertTrue(response.launchXVel() > 0, "Player to right of bumper should be launched rightward");
    }

    @Test
    void bumperVelocityMagnitudeDoesNotExceedLaunchSpeed() {
        short playerX = (short) 50;
        short playerY = (short) 50;
        short tileCenterX = (short) 200;
        short tileCenterY = (short) 200;

        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(5, playerX, playerY, tileCenterX, tileCenterY, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.BUMPER_LAUNCH, response.effect());
        int vx = response.launchXVel();
        int vy = response.launchYVel();
        double magnitude = Math.sqrt((double) vx * vx + (double) vy * vy);
        assertTrue(magnitude <= 0x700, "Bumper launch magnitude should not exceed 0x700, was: " + magnitude);
    }

    @Test
    void bumperWithZeroOffsetProducesNonZeroFallbackVelocity() {
        // Player and bumper at same position — degenerate case
        short pos = (short) 100;

        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(5, pos, pos, pos, pos, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.BUMPER_LAUNCH, response.effect());
        // Fallback should be straight up: launchX = 0, launchY = -0x700
        assertEquals((short) 0, response.launchXVel());
        assertEquals((short) -0x700, response.launchYVel());
    }

    // --- Tile 4 (goal) ---

    @Test
    void goalTileReturnsGoalExitEffect() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(4, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.GOAL_EXIT, response.effect());
        assertEquals((short) 0, response.launchXVel());
        assertEquals((short) 0, response.launchYVel());
    }

    // --- Tile 6 (spike / R tile) ---

    @Test
    void spikeTileNegatesScalarOnFirstHit() {
        int initialScalar = controller.scalarIndex(); // 0x40 after bootstrap
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.SPIKE_REVERSAL, response.effect());
        assertEquals(-initialScalar, controller.scalarIndex());
    }

    @Test
    void spikeThrottleBlocksSecondHitWithinThrottleWindow() {
        // First hit: should negate scalar
        S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);
        int scalarAfterFirstHit = controller.scalarIndex();

        // Second hit immediately after: should be throttled
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.NONE, response.effect());
        // Scalar should be unchanged
        assertEquals(scalarAfterFirstHit, controller.scalarIndex());
    }

    @Test
    void spikeHitAfterThrottleExpiresNegatesAgain() {
        // First hit
        S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);
        int scalarAfterFirstHit = controller.scalarIndex();

        // Tick timers 0x1E (30) times to expire throttle
        for (int i = 0; i < 0x1E; i++) {
            state.tickTimers();
        }

        assertEquals(0, state.spikeThrottleRemaining(), "Throttle should be expired after 0x1E ticks");

        // Second hit after throttle expiry should work
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.SPIKE_REVERSAL, response.effect());
        // Scalar should be negated again (back to original positive value)
        assertEquals(-scalarAfterFirstHit, controller.scalarIndex());
    }

    @Test
    void spikeThrottleTimerIsSetToCorrectValue() {
        S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);
        assertEquals(0x1E, state.spikeThrottleRemaining());
    }

    // --- Tiles 1-3 (slot reels) ---

    @Test
    void slotReelTile1IncrementsSlotValue() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(1, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.SLOT_REEL_INCREMENT, response.effect());
        assertEquals(1, state.lastSlotValue());
    }

    @Test
    void slotReelTile2IncrementsSlotValue() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(2, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.SLOT_REEL_INCREMENT, response.effect());
        assertEquals(1, state.lastSlotValue());
    }

    @Test
    void slotReelTile3IncrementsSlotValue() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(3, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.SLOT_REEL_INCREMENT, response.effect());
        assertEquals(1, state.lastSlotValue());
    }

    @Test
    void slotReelValueCapsAt4AfterMultipleHits() {
        // Hit 5 times — value should cap at 4
        for (int i = 0; i < 5; i++) {
            S3kSlotTileInteraction.process(1, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);
        }
        assertEquals(4, state.lastSlotValue(), "Slot reel value must cap at 4 per ROM cmpi.b #4");
    }

    @Test
    void slotReelValueDoesNotExceed4() {
        // Hit many times — should never exceed 4
        for (int i = 0; i < 20; i++) {
            S3kSlotTileInteraction.process(2, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);
        }
        assertEquals(4, state.lastSlotValue());
    }

    // --- Unknown/passable tiles ---

    @Test
    void unknownTile7ReturnsNone() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(7, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.NONE, response.effect());
    }

    @Test
    void tile0ReturnsNone() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(0, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.NONE, response.effect());
    }

    @Test
    void largeTileIdReturnsNone() {
        S3kSlotTileInteraction.Response response =
                S3kSlotTileInteraction.process(255, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        assertEquals(S3kSlotTileInteraction.Effect.NONE, response.effect());
    }

    // --- State reset ---

    @Test
    void stateResetClearsThrottleAndSlotValue() {
        // Build up some state
        S3kSlotTileInteraction.process(6, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);
        S3kSlotTileInteraction.process(1, (short) 0, (short) 0, (short) 0, (short) 0, controller, state);

        state.reset();

        assertEquals(0, state.spikeThrottleRemaining());
        assertEquals(0, state.lastSlotValue());
    }
}
