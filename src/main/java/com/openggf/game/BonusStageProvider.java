package com.openggf.game;

/**
 * Coordinator interface for bonus stage lifecycle.
 * Unlike special stages (which own their own rendering), bonus stages use
 * the normal level pipeline. This interface manages entry/exit state,
 * not frame updates or rendering.
 * Accessed via {@link GameServices#bonusStage()}.
 */
public interface BonusStageProvider {
    boolean hasBonusStages();
    BonusStageType selectBonusStage(int ringCount);
    void onEnter(BonusStageType type, BonusStageState savedState);
    void onExit();
    void onFrameUpdate();
    boolean isStageComplete();
    void requestExit();
    BonusStageRewards getRewards();
    int getZoneId(BonusStageType type);
    int getMusicId(BonusStageType type);
    BonusStageState getSavedState();

    /** Accumulate rings. ROM equivalent: add.w d0,(Saved_ring_count).w */
    default void addRings(int count) {}

    /** Accumulate lives. ROM equivalent: addq.b #1,(Life_count).w */
    default void addLife() {}

    /** Record shield awarded during bonus stage. */
    default void setAwardedShield(com.openggf.game.ShieldType type) {}

    record BonusStageRewards(
            int rings, int lives,
            boolean shield, boolean fireShield,
            boolean lightningShield, boolean bubbleShield
    ) {
        public static BonusStageRewards none() {
            return new BonusStageRewards(0, 0, false, false, false, false);
        }
    }
}
