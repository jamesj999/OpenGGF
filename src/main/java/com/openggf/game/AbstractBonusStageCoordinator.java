package com.openggf.game;

import java.util.logging.Logger;

/**
 * Shared base class for bonus stage lifecycle coordination.
 * Manages entry/exit state, ring persistence, and object communication.
 * Subclasses provide game-specific zone/music mapping.
 * Methods are non-final to allow overrides (e.g., Slots player sprite swap).
 */
public abstract class AbstractBonusStageCoordinator implements BonusStageProvider {

    private static final Logger LOGGER = Logger.getLogger(AbstractBonusStageCoordinator.class.getName());

    private BonusStageState savedState;
    private BonusStageType activeType = BonusStageType.NONE;
    private boolean exitRequested;
    private int ringsCollected;
    private int livesAwarded;

    @Override
    public boolean hasBonusStages() { return true; }

    @Override
    public void onEnter(BonusStageType type, BonusStageState savedState) {
        this.savedState = savedState;
        this.activeType = type;
        this.exitRequested = false;
        this.ringsCollected = 0;
        this.livesAwarded = 0;
        LOGGER.info("Entering bonus stage: " + type + " (zone 0x"
                + Integer.toHexString(getZoneId(type)) + ")");
    }

    @Override
    public void onExit() {
        LOGGER.info("Exiting bonus stage: " + activeType
                + " (rings collected: " + ringsCollected + ")");
        activeType = BonusStageType.NONE;
        exitRequested = false;
    }

    @Override
    public void onFrameUpdate() {
        // Default: no per-frame work. Subclasses may override.
    }

    @Override
    public boolean isStageComplete() { return exitRequested; }

    @Override
    public void requestExit() { exitRequested = true; }

    @Override
    public BonusStageRewards getRewards() {
        return new BonusStageRewards(ringsCollected, livesAwarded,
                false, false, false, false);
    }

    @Override
    public BonusStageState getSavedState() { return savedState; }

    public BonusStageType getActiveType() { return activeType; }

    /** Accumulate rings during the bonus stage. Called by gumball item objects. */
    public void addRings(int count) { ringsCollected += count; }

    /** Accumulate lives during the bonus stage. */
    public void addLife() { livesAwarded++; }
}
