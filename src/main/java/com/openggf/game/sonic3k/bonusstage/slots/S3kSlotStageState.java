package com.openggf.game.sonic3k.bonusstage.slots;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class S3kSlotStageState {
    private static final int DEFAULT_EVENTS_BG_X = 0x10;
    private static final int DEFAULT_EVENTS_BG_Y = 0x2D;

    private int statTable;
    private int scalarIndex1;
    private int scalarIndex2;
    private int scalarResult0;
    private int scalarResult1;
    private int lastCollisionTileId;
    private int lastCollisionIndex = -1;
    private int bounceTimer;
    private int spikeThrottleTimer;
    private int slotWallThrottleTimer;
    private int slotValue;
    private int lastSlotWallLayoutIndex = -1;
    private boolean slotWallContactActive;
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
    private final int[] optionCycleReelVelocities = new int[3];
    private final int[] optionCycleReelSubstates = new int[3];
    private int latchedPrize;
    private int latchedPrizeCycleId = -1;
    private boolean reelsFrozen;
    private int activeRewardObjects;
    private int rewardCounter;
    private int eventsBgX;
    private int eventsBgY;
    private final int[] optionCycleDisplaySymbols = new int[3];
    private final int[] optionCycleOffsets = new int[3];
    private final int[] optionCycleReelWords = new int[3];
    private final Deque<int[]> pendingRingRewardPositions = new ArrayDeque<>();
    private final Deque<int[]> pendingSpikeRewardPositions = new ArrayDeque<>();

    public static S3kSlotStageState bootstrap() {
        S3kSlotStageState state = new S3kSlotStageState();
        state.scalarIndex1 = 0x40;
        state.eventsBgX = DEFAULT_EVENTS_BG_X;
        state.eventsBgY = DEFAULT_EVENTS_BG_Y;
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
        // 68k move.b (Stat_table).w reads the first byte of the big-endian word,
        // which corresponds to the high byte of this 16-bit accumulator.
        return (statTable >>> 8) & 0xFF;
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

    int latchedPrize() {
        return latchedPrize;
    }

    void setLatchedPrize(int latchedPrize) {
        this.latchedPrize = latchedPrize;
    }

    int latchedPrizeCycleId() {
        return latchedPrizeCycleId;
    }

    void setLatchedPrizeCycleId(int latchedPrizeCycleId) {
        this.latchedPrizeCycleId = latchedPrizeCycleId;
    }

    boolean reelsFrozen() {
        return reelsFrozen;
    }

    void setReelsFrozen(boolean reelsFrozen) {
        this.reelsFrozen = reelsFrozen;
    }

    int activeRewardObjects() {
        return activeRewardObjects;
    }

    void incrementActiveRewardObjects() {
        activeRewardObjects++;
    }

    void decrementActiveRewardObjects() {
        if (activeRewardObjects > 0) {
            activeRewardObjects--;
        }
    }

    int rewardCounter() {
        return rewardCounter;
    }

    void incrementRewardCounter() {
        rewardCounter++;
    }

    boolean decrementRewardCounter() {
        if (rewardCounter <= 0) {
            return false;
        }
        rewardCounter--;
        return true;
    }

    void decrementRewardCounterUnchecked() {
        rewardCounter--;
    }

    void enqueueRingRewardPosition(int[] rewardPosition) {
        pendingRingRewardPositions.offer(rewardPosition);
    }

    int[] pollRingRewardPosition() {
        return pendingRingRewardPositions.poll();
    }

    boolean hasPendingRingRewardPositions() {
        return !pendingRingRewardPositions.isEmpty();
    }

    void enqueueSpikeRewardPosition(int[] rewardPosition) {
        pendingSpikeRewardPositions.offer(rewardPosition);
    }

    int[] pollSpikeRewardPosition() {
        return pendingSpikeRewardPositions.poll();
    }

    boolean hasPendingSpikeRewardPositions() {
        return !pendingSpikeRewardPositions.isEmpty();
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

    boolean tickSlotWallThrottleTimer() {
        if (slotWallThrottleTimer > 0) {
            slotWallThrottleTimer--;
            return slotWallThrottleTimer == 0;
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

    boolean shouldTriggerSlotWall(int layoutIndex) {
        if (layoutIndex < 0) {
            return false;
        }
        if (slotWallContactActive) {
            return false;
        }
        slotWallContactActive = true;
        lastSlotWallLayoutIndex = layoutIndex;
        slotWallThrottleTimer = 4;
        return true;
    }

    void clearSlotWallContact() {
        slotWallContactActive = false;
        lastSlotWallLayoutIndex = -1;
        slotWallThrottleTimer = 0;
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

    public int[] optionCycleOffsets() {
        return optionCycleOffsets;
    }

    public int[] optionCycleReelWords() {
        return optionCycleReelWords;
    }

    public int[] optionCycleReelVelocities() {
        return optionCycleReelVelocities;
    }

    public int[] optionCycleReelSubstates() {
        return optionCycleReelSubstates;
    }

    void setOptionCycleOffsets(int reel0, int reel1, int reel2) {
        optionCycleOffsets[0] = reel0 & 0xFF;
        optionCycleOffsets[1] = reel1 & 0xFF;
        optionCycleOffsets[2] = reel2 & 0xFF;
    }

    void setOptionCycleReelWord(int reelIndex, int reelWord) {
        optionCycleReelWords[reelIndex] = reelWord & 0xFFFF;
    }

    void setOptionCycleReelWords(int reel0, int reel1, int reel2) {
        optionCycleReelWords[0] = reel0 & 0xFFFF;
        optionCycleReelWords[1] = reel1 & 0xFFFF;
        optionCycleReelWords[2] = reel2 & 0xFFFF;
    }

    void setOptionCycleReelVelocity(int reelIndex, int value) {
        optionCycleReelVelocities[reelIndex] = value & 0xFF;
    }

    void setOptionCycleReelVelocities(int reel0, int reel1, int reel2) {
        optionCycleReelVelocities[0] = reel0 & 0xFF;
        optionCycleReelVelocities[1] = reel1 & 0xFF;
        optionCycleReelVelocities[2] = reel2 & 0xFF;
    }

    void setOptionCycleReelSubstate(int reelIndex, int value) {
        optionCycleReelSubstates[reelIndex] = value & 0xFF;
    }

    void setOptionCycleReelSubstates(int reel0, int reel1, int reel2) {
        optionCycleReelSubstates[0] = reel0 & 0xFF;
        optionCycleReelSubstates[1] = reel1 & 0xFF;
        optionCycleReelSubstates[2] = reel2 & 0xFF;
    }

    void setEventsBg(int eventsBgX, int eventsBgY) {
        this.eventsBgX = eventsBgX;
        this.eventsBgY = eventsBgY;
    }

    public int[] optionCycleDisplaySymbols() {
        return optionCycleDisplaySymbols;
    }

    public int eventsBgX() {
        return eventsBgX;
    }

    public int eventsBgY() {
        return eventsBgY;
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
        Arrays.fill(optionCycleOffsets, 0);
        Arrays.fill(optionCycleReelWords, 0);
        Arrays.fill(optionCycleReelVelocities, 0);
        Arrays.fill(optionCycleReelSubstates, 0);
    }

    void resetRewardState() {
        latchedPrize = 0;
        latchedPrizeCycleId = -1;
        reelsFrozen = false;
        activeRewardObjects = 0;
        rewardCounter = 0;
        pendingRingRewardPositions.clear();
        pendingSpikeRewardPositions.clear();
    }
}
