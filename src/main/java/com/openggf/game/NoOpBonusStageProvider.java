package com.openggf.game;

/**
 * No-op implementation of {@link BonusStageProvider} for games without bonus stages.
 */
public final class NoOpBonusStageProvider implements BonusStageProvider {
    public static final NoOpBonusStageProvider INSTANCE = new NoOpBonusStageProvider();
    private NoOpBonusStageProvider() {}

    @Override public boolean hasBonusStages() { return false; }
    @Override public BonusStageType selectBonusStage(int ringCount) { return BonusStageType.NONE; }
    @Override public void onEnter(BonusStageType type, BonusStageState savedState) {}
    @Override public void onExit() {}
    @Override public void onFrameUpdate() {}
    @Override public boolean isStageComplete() { return false; }
    @Override public void requestExit() {}
    @Override public BonusStageRewards getRewards() { return BonusStageRewards.none(); }
    @Override public int getZoneId(BonusStageType type) { return -1; }
    @Override public int getMusicId(BonusStageType type) { return -1; }
    @Override public BonusStageState getSavedState() { return null; }
}
