package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotOptionCycleSystem {
    private static final int STATE_INIT = 0;
    private static final int STATE_SPIN = 4;
    private static final int STATE_PICK_TARGETS = 8;
    private static final int STATE_DECELERATE = 12;
    private static final int STATE_LOCK_REELS = 16;
    private static final int STATE_AWARD = 20;
    private static final int STATE_IDLE = 24;

    public void bootstrap(S3kSlotStageState state) {
        if (state == null) {
            return;
        }
        state.resetOptionCycleState();
    }

    public void tick(S3kSlotStageState state, int frameCounter) {
        if (state == null) {
            return;
        }
        switch (state.optionCycleState()) {
            case STATE_INIT -> tickInit(state);
            case STATE_SPIN -> tickSpin(state);
            case STATE_PICK_TARGETS -> tickPickTargets(state, frameCounter);
            case STATE_DECELERATE -> tickDecelerate(state, frameCounter);
            case STATE_LOCK_REELS -> tickLockReels(state);
            case STATE_AWARD -> tickAward(state);
            case STATE_IDLE -> tickIdle(state);
            default -> {
                state.setOptionCycleState(STATE_INIT);
            }
        }
    }

    public boolean isResolved(S3kSlotStageState state) {
        return state != null && state.optionCycleState() == STATE_IDLE;
    }

    public int lastPrizeResult(S3kSlotStageState state) {
        return state != null ? state.optionCycleLastPrize() : Integer.MIN_VALUE;
    }

    public int completedCycles(S3kSlotStageState state) {
        return state != null ? state.optionCycleCompletedCycles() : 0;
    }

    public int[] displaySymbols(S3kSlotStageState state) {
        return state != null ? state.optionCycleDisplaySymbols() : new int[0];
    }

    public static int sourceOffsetFor(int symbol, int reelWord) {
        return ((symbol & 0x07) << 9) + ((reelWord & 0x00F8) >> 1);
    }

    private void tickInit(S3kSlotStageState state) {
        state.setOptionCycleState(STATE_SPIN);
        state.setOptionCycleCountdown(1);
        state.setOptionCycleActiveReelIndex(0);
        state.setOptionCycleLockProgress(0);
        state.setOptionCycleResolvedDisplayTimer(0);
        state.setOptionCycleSpinCycleCounter(0);
        state.setOptionCycleDisplaySymbols(8, 8, 8);
    }

    private void tickSpin(S3kSlotStageState state) {
        advanceSpinningSymbols(state);
        int countdown = state.optionCycleCountdown();
        if (countdown > 0) {
            state.setOptionCycleCountdown(countdown - 1);
            return;
        }
        state.setOptionCycleState(STATE_PICK_TARGETS);
    }

    private void tickPickTargets(S3kSlotStageState state, int frameCounter) {
        int seed = frameCounter & 0xFF;
        int rotated = ((seed >>> 3) | (seed << 5)) & 0xFF;

        int fixedRow = S3kSlotRewardResolver.pickFixedRow(rotated);
        if (fixedRow >= 0) {
            int rowBase = fixedRow * 3;
            state.setOptionCycleTargetReelA(S3kSlotRomData.TARGET_ROWS[rowBase + 1] & 0xFF);
            state.setOptionCycleTargetPackedBC(S3kSlotRomData.TARGET_ROWS[rowBase + 2] & 0xFF);
        } else {
            pickRandomTargets(state, frameCounter);
        }

        state.setOptionCycleCountdown(2);
        state.setOptionCycleLockProgress(0);
        state.setOptionCycleActiveReelIndex(0);
        state.setOptionCycleState(STATE_DECELERATE);
    }

    private void pickRandomTargets(S3kSlotStageState state, int frameCounter) {
        long mixed = (frameCounter * 2654435761L) & 0xFFFFFFFFL;
        int rnd = mixed > 0 ? (int) (mixed >>> 16) : frameCounter;
        rnd = (rnd + ((frameCounter >>> 8) & 0xFF)) & 0xFFFF;
        rnd = ((rnd >>> 4) | (rnd << 12)) & 0xFFFF;

        int idxA = rnd & 7;
        int targetA = S3kSlotRomData.REEL_SEQUENCE_A[idxA];

        int idxB = (rnd >>> 3) & 7;
        int targetB = S3kSlotRomData.REEL_SEQUENCE_B[idxB];

        int idxC = (rnd >>> 6) & 7;
        int targetC = S3kSlotRomData.REEL_SEQUENCE_C[idxC];

        state.setOptionCycleTargetReelA(targetA);
        state.setOptionCycleTargetPackedBC((targetB << 4) | targetC);
    }

    private void tickDecelerate(S3kSlotStageState state, int frameCounter) {
        advanceSpinningSymbols(state);
        int countdown = state.optionCycleCountdown();
        if (countdown > 0) {
            state.setOptionCycleCountdown(countdown - 1);
            return;
        }
        state.setOptionCycleCountdown(0x0C + (frameCounter & 0x0F));
        state.setOptionCycleLockProgress(0);
        state.setOptionCycleState(STATE_LOCK_REELS);
    }

    private void tickLockReels(S3kSlotStageState state) {
        int lockProgress = state.optionCycleLockProgress();
        int[] displaySymbols = state.optionCycleDisplaySymbols();
        if (lockProgress == 0) {
            displaySymbols[0] = state.optionCycleTargetReelA() & 0x0F;
        } else if (lockProgress == 1) {
            displaySymbols[1] = (state.optionCycleTargetPackedBC() >> 4) & 0x0F;
        } else if (lockProgress == 2) {
            displaySymbols[2] = state.optionCycleTargetPackedBC() & 0x0F;
        }
        state.setOptionCycleLockProgress(lockProgress + 1);
        if (state.optionCycleLockProgress() >= 3) {
            state.setOptionCycleState(STATE_AWARD);
        }
    }

    private void tickAward(S3kSlotStageState state) {
        state.setOptionCycleDisplaySymbols(
                state.optionCycleTargetReelA() & 0x0F,
                (state.optionCycleTargetPackedBC() >> 4) & 0x0F,
                state.optionCycleTargetPackedBC() & 0x0F);
        state.setOptionCycleLastPrize(S3kSlotPrizeCalculator.calculate(
                state.optionCycleTargetReelA(),
                (byte) state.optionCycleTargetPackedBC()));
        state.setOptionCycleResolvedDisplayTimer(0x20);
        state.setOptionCycleCompletedCycles(state.optionCycleCompletedCycles() + 1);
        state.setOptionCycleState(STATE_IDLE);
    }

    private void tickIdle(S3kSlotStageState state) {
        int timer = state.optionCycleResolvedDisplayTimer();
        if (timer > 0) {
            state.setOptionCycleResolvedDisplayTimer(timer - 1);
            return;
        }
        state.setOptionCycleState(STATE_INIT);
    }

    private void advanceSpinningSymbols(S3kSlotStageState state) {
        int cycle = state.optionCycleSpinCycleCounter() + 1;
        state.setOptionCycleSpinCycleCounter(cycle);
        int idx = cycle & 7;
        state.setOptionCycleDisplaySymbols(
                S3kSlotRomData.REEL_SEQUENCE_A[idx] & 0xFF,
                S3kSlotRomData.REEL_SEQUENCE_B[idx] & 0xFF,
                S3kSlotRomData.REEL_SEQUENCE_C[idx] & 0xFF);
    }
}
