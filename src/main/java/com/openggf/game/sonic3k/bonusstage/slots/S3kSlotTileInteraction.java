package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.physics.TrigLookupTable;

/**
 * Tile interaction handler matching ROM sub_4BE3A (lines 99190-99303).
 * Processes effects when the slot stage player collides with special tiles (1-6).
 */
public final class S3kSlotTileInteraction {

    private static final int BUMPER_LAUNCH_SPEED = 0x700;
    private static final int SPIKE_THROTTLE_FRAMES = 0x1E;

    private S3kSlotTileInteraction() {
    }

    /**
     * Process a tile interaction after collision detection.
     *
     * @param tileId the tile type (1-6 are special)
     * @param playerX player X position
     * @param playerY player Y position
     * @param tileCenterX center X of the hit tile
     * @param tileCenterY center Y of the hit tile
     * @param controller stage controller (for scalar negation)
     * @param state persistent interaction state (throttle timers, slot value tracking)
     * @return the effect to apply
     */
    public static Response process(int tileId, short playerX, short playerY,
                                   short tileCenterX, short tileCenterY,
                                   S3kSlotStageController controller, State state) {
        switch (tileId) {
            case 5: return processBumper(playerX, playerY, tileCenterX, tileCenterY);
            case 4: return new Response(Effect.GOAL_EXIT, (short) 0, (short) 0);
            case 6: return processSpikeReversal(controller, state);
            case 1: case 2: case 3: return processSlotReel(state);
            default: return Response.NONE;
        }
    }

    private static Response processBumper(short playerX, short playerY,
                                          short tileCenterX, short tileCenterY) {
        int dx = tileCenterX - playerX;
        int dy = tileCenterY - playerY;
        if (dx == 0 && dy == 0) {
            // Degenerate case: push straight up
            return new Response(Effect.BUMPER_LAUNCH, (short) 0, (short) -BUMPER_LAUNCH_SPEED);
        }
        int angle = TrigLookupTable.calcAngle((short) dx, (short) dy);
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        // ROM: muls.w #-$700,d1 (AWAY from bumper center)
        short launchX = (short) ((cos * -BUMPER_LAUNCH_SPEED) >> 8);
        short launchY = (short) ((sin * -BUMPER_LAUNCH_SPEED) >> 8);
        return new Response(Effect.BUMPER_LAUNCH, launchX, launchY);
    }

    private static Response processSpikeReversal(S3kSlotStageController controller, State state) {
        if (state.spikeThrottleTimer > 0) {
            return Response.NONE;
        }
        state.spikeThrottleTimer = SPIKE_THROTTLE_FRAMES;
        controller.negateScalar();
        return new Response(Effect.SPIKE_REVERSAL, (short) 0, (short) 0);
    }

    private static Response processSlotReel(State state) {
        state.slotValue++;
        if (state.slotValue > 4) {
            state.slotValue = 4; // ROM: cmpi.b #4,d0 / bls.s loc_4BF54
        }
        return new Response(Effect.SLOT_REEL_INCREMENT, (short) 0, (short) 0);
    }

    public enum Effect {
        NONE, BUMPER_LAUNCH, GOAL_EXIT, SPIKE_REVERSAL, SLOT_REEL_INCREMENT
    }

    public record Response(Effect effect, short launchXVel, short launchYVel) {
        public static final Response NONE = new Response(Effect.NONE, (short) 0, (short) 0);
    }

    /** Mutable state that persists across frames for throttle timers and slot tracking */
    public static final class State {
        int spikeThrottleTimer;
        int slotValue;

        /** Call once per frame to decrement active throttle timers */
        public void tickTimers() {
            if (spikeThrottleTimer > 0) spikeThrottleTimer--;
        }

        public int lastSlotValue() { return slotValue; }
        public int spikeThrottleRemaining() { return spikeThrottleTimer; }

        public void reset() {
            spikeThrottleTimer = 0;
            slotValue = 0;
        }
    }
}
