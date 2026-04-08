package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * 7-state reel cycle matching ROM Slots_CycleOptions (lines 99609-99931).
 * Called once per frame. Drives the 3-reel spin/stop/award sequence.
 *
 * <p>States: 0=init, 1=spinning, 2=pick_targets, 3=decelerate,
 * 4=lock_reels, 5=award, 6=idle.
 */
public final class S3kSlotReelStateMachine {

    private int state;
    private int countdown;        // 1(a4) — multi-purpose countdown
    private int targetReelA;      // 4(a4) — target symbol for reel A
    private byte targetPackedBC;  // 5(a4) — packed target for reels B and C
    private int lastPrize = Integer.MIN_VALUE;
    private int lockProgress;     // tracks how many reels have been locked (0-3)
    private int resolvedDisplayTimer;
    private int completedCycles;
    // ROM sub_4C6D6/sub_4C77C: visible symbol per reel (0-7) for renderer
    private final int[] displaySymbols = new int[3];
    private int spinCycleCounter;  // cycles display during spin

    public void reset() {
        state = 0;
        countdown = 0;
        targetReelA = 0;
        targetPackedBC = 0;
        lastPrize = Integer.MIN_VALUE;
        lockProgress = 0;
        resolvedDisplayTimer = 0;
        completedCycles = 0;
        displaySymbols[0] = 0;
        displaySymbols[1] = 0;
        displaySymbols[2] = 0;
        spinCycleCounter = 0;
    }

    public int state() { return state; }

    /** Returns the prize from the last completed cycle, or Integer.MIN_VALUE if none yet */
    public int lastPrizeResult() { return lastPrize; }

    public int targetReelA() { return targetReelA; }
    public byte targetPackedBC() { return targetPackedBC; }
    public boolean isResolved() { return state == 6; }
    public int completedCycles() { return completedCycles; }

    /** ROM sub_4C6D6: returns the currently visible symbol (0-7) per reel for rendering. */
    public int[] displaySymbols() { return displaySymbols; }

    /**
     * Advance the reel state machine by one frame.
     * @param frameCounter current frame count (used as random seed, like V_int_run_count)
     */
    public void tick(int frameCounter) {
        switch (state) {
            case 0 -> tickInit(frameCounter);
            case 1 -> tickSpinning();
            case 2 -> tickPickTargets(frameCounter);
            case 3 -> tickDecelerate(frameCounter);
            case 4 -> tickLockReels();
            case 5 -> tickAward();
            case 6 -> tickResolved();
            default -> { }
        }
    }

    /** State 0 (loc_4C416): Initialize reels with seeds, set countdown, advance to state 1 */
    private void tickInit(int frameCounter) {
        // ROM clears 18 bytes of reel state, sets initial seeds from V_int_run_count
        countdown = 1; // ROM: move.b #1,1(a4) — spin for 1 cycle before pick
        lockProgress = 0;
        lastPrize = Integer.MIN_VALUE;
        state = 1;
    }

    /** State 1 (loc_4C462): Spinning — decrement countdown, advance when done */
    private void tickSpinning() {
        // Cycle display symbols through reel sequences to create visual spin
        spinCycleCounter++;
        int idx = spinCycleCounter & 7;
        displaySymbols[0] = S3kSlotRomData.REEL_SEQUENCE_A[idx] & 0xFF;
        displaySymbols[1] = S3kSlotRomData.REEL_SEQUENCE_B[idx] & 0xFF;
        displaySymbols[2] = S3kSlotRomData.REEL_SEQUENCE_C[idx] & 0xFF;
        if (countdown > 0) {
            countdown--;
            return;
        }
        state = 2;
    }

    /** State 2 (loc_4C480): Pick target symbols from probability table */
    private void tickPickTargets(int frameCounter) {
        // ROM uses V_int_run_count as random seed, rotated
        int seed = frameCounter & 0xFF;
        int rotated = ((seed >>> 3) | (seed << 5)) & 0xFF;

        int fixedRow = S3kSlotRewardResolver.pickFixedRow(rotated);
        if (fixedRow >= 0) {
            int rowBase = fixedRow * 3;
            targetReelA = S3kSlotRomData.TARGET_ROWS[rowBase + 1] & 0xFF;
            targetPackedBC = S3kSlotRomData.TARGET_ROWS[rowBase + 2];
        } else {
            // Dynamic random: loc_4C4F8
            pickRandomTargets(frameCounter);
        }

        countdown = 2; // ROM: move.b #2,1(a4)
        state = 3;
    }

    private void pickRandomTargets(int frameCounter) {
        // ROM: jsr (Random_Number).l + V_int_run_count mixing
        int rnd = (frameCounter * 2654435761L & 0xFFFFFFFFL) > 0
                ? (int) ((frameCounter * 2654435761L) >>> 16) : frameCounter;
        rnd = (rnd + ((frameCounter >>> 8) & 0xFF)) & 0xFFFF;
        rnd = ((rnd >>> 4) | (rnd << 12)) & 0xFFFF;

        int idxA = rnd & 7;
        targetReelA = S3kSlotRomData.REEL_SEQUENCE_A[idxA];

        int idxB = (rnd >>> 3) & 7;
        int b = S3kSlotRomData.REEL_SEQUENCE_B[idxB];

        int idxC = (rnd >>> 6) & 7;
        int c = S3kSlotRomData.REEL_SEQUENCE_C[idxC];

        targetPackedBC = (byte) ((b << 4) | c);
    }

    /** State 3 (loc_4C540): Decelerate spin, advance when countdown reaches 0 */
    private void tickDecelerate(int frameCounter) {
        // Continue cycling display during deceleration
        spinCycleCounter++;
        int idx = spinCycleCounter & 7;
        displaySymbols[0] = S3kSlotRomData.REEL_SEQUENCE_A[idx] & 0xFF;
        displaySymbols[1] = S3kSlotRomData.REEL_SEQUENCE_B[idx] & 0xFF;
        displaySymbols[2] = S3kSlotRomData.REEL_SEQUENCE_C[idx] & 0xFF;
        if (countdown > 0) {
            countdown--;
            return;
        }
        // ROM: random additional countdown 0x0C + (frameCounter & 0x0F)
        countdown = 0x0C + (frameCounter & 0x0F);
        lockProgress = 0;
        state = 4;
    }

    /** State 4 (loc_4C576): Lock reels to target symbols one at a time */
    private void tickLockReels() {
        // Snap display to target symbols as each reel locks
        if (lockProgress == 0) {
            displaySymbols[0] = targetReelA & 0xFF;
        } else if (lockProgress == 1) {
            displaySymbols[1] = (targetPackedBC >> 4) & 0x0F;
        } else if (lockProgress == 2) {
            displaySymbols[2] = targetPackedBC & 0x0F;
        }
        lockProgress++;
        // Lock all 3 reels (each takes a few frames in ROM, simplified here)
        if (lockProgress >= 3) {
            state = 5;
        }
    }

    /** State 5 (loc_4C6BC): Calculate and store prize, transition to idle */
    private void tickAward() {
        // Ensure display shows final resolved symbols
        displaySymbols[0] = targetReelA & 0xFF;
        displaySymbols[1] = (targetPackedBC >> 4) & 0x0F;
        displaySymbols[2] = targetPackedBC & 0x0F;
        lastPrize = S3kSlotPrizeCalculator.calculate(targetReelA, targetPackedBC);
        resolvedDisplayTimer = 0x20;
        completedCycles++;
        state = 6;
    }

    private void tickResolved() {
        if (resolvedDisplayTimer > 0) {
            resolvedDisplayTimer--;
            return;
        }
        state = 0;
    }
}
