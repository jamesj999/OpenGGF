package com.openggf.game.sonic3k.bonusstage.slots;

import java.util.Arrays;

public final class S3kSlotStageState {
    private int statTable;
    private int scalarIndex1;
    private int scalarIndex2;
    private int scalarResult0;
    private int scalarResult1;
    private int lastCollisionTileId;
    private int lastCollisionIndex = -1;
    private int bounceTimer;
    private int spikeThrottleTimer;
    private int slotValue;
    private boolean paletteCycleEnabled;
    private int optionCycleState;
    private int optionCycleCountdown;
    private int optionCycleActiveReelIndex;
    private int optionCycleTargetReelA;
    private int optionCycleTargetPackedBC;
    private int optionCycleLastPrize = Integer.MIN_VALUE;
    private int optionCycleLockProgress;
    private int optionCycleResolvedDisplayTimer;
    private int optionCycleCompletedCycles;
    private int optionCycleSpinCycleCounter;
    private final int[] optionCycleDisplaySymbols = new int[3];

    public static S3kSlotStageState bootstrap() {
        S3kSlotStageState state = new S3kSlotStageState();
        state.scalarIndex1 = 0x40;
        state.resetOptionCycleState();
        return state;
    }

    public int statTable() {
        return statTable;
    }

    void setStatTable(int statTable) {
        this.statTable = statTable;
    }

    public int scalarIndex1() {
        return scalarIndex1;
    }

    void setScalarIndex1(int scalarIndex1) {
        this.scalarIndex1 = scalarIndex1;
    }

    public int scalarIndex2() {
        return scalarIndex2;
    }

    void setScalarIndex2(int scalarIndex2) {
        this.scalarIndex2 = scalarIndex2;
    }

    public int scalarResult0() {
        return scalarResult0;
    }

    void setScalarResult0(int scalarResult0) {
        this.scalarResult0 = scalarResult0;
    }

    public int scalarResult1() {
        return scalarResult1;
    }

    void setScalarResult1(int scalarResult1) {
        this.scalarResult1 = scalarResult1;
    }

    public int lastCollisionTileId() {
        return lastCollisionTileId;
    }

    public int lastCollisionIndex() {
        return lastCollisionIndex;
    }

    int angle() {
        return statTable & 0xFF;
    }

    int rawStatTable() {
        return statTable & 0xFFFF;
    }

    void setLastCollision(int tileId, int layoutIndex) {
        lastCollisionTileId = tileId;
        lastCollisionIndex = layoutIndex;
    }

    void clearCollision() {
        lastCollisionTileId = 0;
        lastCollisionIndex = -1;
    }

    void setBounceTimer(int frames) {
        bounceTimer = frames;
    }

    int bounceTimer() {
        return bounceTimer;
    }

    boolean tickBounceTimer() {
        if (bounceTimer > 0) {
            bounceTimer--;
            return bounceTimer == 0;
        }
        return false;
    }

    int spikeThrottleTimer() {
        return spikeThrottleTimer;
    }

    void setSpikeThrottleTimer(int spikeThrottleTimer) {
        this.spikeThrottleTimer = spikeThrottleTimer;
    }

    boolean tickSpikeThrottleTimer() {
        if (spikeThrottleTimer > 0) {
            spikeThrottleTimer--;
            return spikeThrottleTimer == 0;
        }
        return false;
    }

    int slotValue() {
        return slotValue;
    }

    void setSlotValue(int slotValue) {
        this.slotValue = slotValue;
    }

    void incrementSlotValue() {
        if (slotValue < 4) {
            slotValue++;
        }
    }

    void negateScalarIndex1() {
        scalarIndex1 = -scalarIndex1;
    }

    public boolean paletteCycleEnabled() {
        return paletteCycleEnabled;
    }

    void setPaletteCycleEnabled(boolean paletteCycleEnabled) {
        this.paletteCycleEnabled = paletteCycleEnabled;
    }

    public int optionCycleState() {
        return optionCycleState;
    }

    void setOptionCycleState(int optionCycleState) {
        this.optionCycleState = optionCycleState;
    }

    public int optionCycleCountdown() {
        return optionCycleCountdown;
    }

    void setOptionCycleCountdown(int optionCycleCountdown) {
        this.optionCycleCountdown = optionCycleCountdown;
    }

    public int optionCycleActiveReelIndex() {
        return optionCycleActiveReelIndex;
    }

    void setOptionCycleActiveReelIndex(int optionCycleActiveReelIndex) {
        this.optionCycleActiveReelIndex = optionCycleActiveReelIndex;
    }

    public int optionCycleTargetReelA() {
        return optionCycleTargetReelA;
    }

    void setOptionCycleTargetReelA(int optionCycleTargetReelA) {
        this.optionCycleTargetReelA = optionCycleTargetReelA;
    }

    public int optionCycleTargetPackedBC() {
        return optionCycleTargetPackedBC;
    }

    void setOptionCycleTargetPackedBC(int optionCycleTargetPackedBC) {
        this.optionCycleTargetPackedBC = optionCycleTargetPackedBC;
    }

    public int optionCycleLastPrize() {
        return optionCycleLastPrize;
    }

    void setOptionCycleLastPrize(int optionCycleLastPrize) {
        this.optionCycleLastPrize = optionCycleLastPrize;
    }

    public int optionCycleLockProgress() {
        return optionCycleLockProgress;
    }

    void setOptionCycleLockProgress(int optionCycleLockProgress) {
        this.optionCycleLockProgress = optionCycleLockProgress;
    }

    public int optionCycleResolvedDisplayTimer() {
        return optionCycleResolvedDisplayTimer;
    }

    void setOptionCycleResolvedDisplayTimer(int optionCycleResolvedDisplayTimer) {
        this.optionCycleResolvedDisplayTimer = optionCycleResolvedDisplayTimer;
    }

    public int optionCycleCompletedCycles() {
        return optionCycleCompletedCycles;
    }

    void setOptionCycleCompletedCycles(int optionCycleCompletedCycles) {
        this.optionCycleCompletedCycles = optionCycleCompletedCycles;
    }

    public int optionCycleSpinCycleCounter() {
        return optionCycleSpinCycleCounter;
    }

    void setOptionCycleSpinCycleCounter(int optionCycleSpinCycleCounter) {
        this.optionCycleSpinCycleCounter = optionCycleSpinCycleCounter;
    }

    public int[] optionCycleDisplaySymbols() {
        return optionCycleDisplaySymbols;
    }

    void setOptionCycleDisplaySymbol(int index, int value) {
        optionCycleDisplaySymbols[index] = value;
    }

    void setOptionCycleDisplaySymbols(int left, int middle, int right) {
        optionCycleDisplaySymbols[0] = left;
        optionCycleDisplaySymbols[1] = middle;
        optionCycleDisplaySymbols[2] = right;
    }

    void resetOptionCycleState() {
        optionCycleState = 0;
        optionCycleCountdown = 0;
        optionCycleActiveReelIndex = 0;
        optionCycleTargetReelA = 0;
        optionCycleTargetPackedBC = 0;
        optionCycleLastPrize = Integer.MIN_VALUE;
        optionCycleLockProgress = 0;
        optionCycleResolvedDisplayTimer = 0;
        optionCycleCompletedCycles = 0;
        optionCycleSpinCycleCounter = 0;
        Arrays.fill(optionCycleDisplaySymbols, 0);
    }
}
