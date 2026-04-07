package com.openggf.level;

import com.openggf.game.BonusStageType;

/**
 * Holds all transition request/consume state that was previously scattered
 * across LevelManager fields.  LevelManager owns a single instance and
 * exposes it via {@code getTransitions()}.
 * <p>
 * This is a pure state holder — it never calls back into LevelManager or
 * any other singleton.
 */
public class LevelTransitionCoordinator {

    // ── Special stage ──────────────────────────────────────────────────
    private boolean specialStageRequestedFromCheckpoint;
    private boolean specialStageReturnLevelReloadRequested;

    // ── S3K big ring return (ROM: Saved2_* variables) ──────────
    private BigRingReturnState bigRingReturn;

    // ── Bonus stage ───────────────────────────────────────────────────
    private BonusStageType bonusStageRequested;
    private int bonusStageReturnCheckpointIndex = -1;

    // ── Title card ─────────────────────────────────────────────────────
    private boolean titleCardRequested;
    private int titleCardZone = -1;
    private int titleCardAct = -1;
    private boolean inLevelTitleCardRequested;
    private int inLevelTitleCardZone = -1;
    private int inLevelTitleCardAct = -1;

    // ── Transition request flags (for fade-coordinated transitions) ────
    private boolean respawnRequested;
    private boolean nextActRequested;
    private boolean nextZoneRequested;
    private boolean specificZoneActRequested;
    private int requestedZone = -1;
    private int requestedAct = -1;

    // ── Seamless transitions ───────────────────────────────────────────
    private boolean seamlessTransitionRequested;
    private SeamlessLevelTransitionRequest pendingSeamlessTransitionRequest;

    // ── Credits ────────────────────────────────────────────────────────
    private boolean creditsRequested;

    // ── HUD / music suppression ────────────────────────────────────────
    private boolean forceHudSuppressed;
    private boolean suppressNextMusicChange;

    // ── Level inactive flag ────────────────────────────────────────────
    private boolean levelInactiveForTransition;

    // ================================================================
    //  Special stage requests
    // ================================================================

    /**
     * Request entry to special stage from a checkpoint star.
     * Called by CheckpointStarInstance when the player touches a star.
     */
    public void requestSpecialStageFromCheckpoint() {
        requestSpecialStageEntry();
    }

    /**
     * Request entry to special stage using the current game's access method.
     */
    public void requestSpecialStageEntry() {
        this.specialStageRequestedFromCheckpoint = true;
    }

    /**
     * Consumes and clears the special stage request flag.
     *
     * @return true if a special stage was requested since last check
     */
    public boolean consumeSpecialStageRequest() {
        boolean requested = specialStageRequestedFromCheckpoint;
        specialStageRequestedFromCheckpoint = false;
        return requested;
    }

    /**
     * Consumes and clears the pending level-reload request for special-stage
     * return.
     *
     * @return true if the next act should be loaded before resuming gameplay
     */
    public boolean consumeSpecialStageReturnLevelReloadRequest() {
        boolean requested = specialStageReturnLevelReloadRequested;
        specialStageReturnLevelReloadRequested = false;
        return requested;
    }

    /**
     * Sets the special-stage return level reload flag.
     * Called by LevelManager when advancing to the next act after a special
     * stage return, and cleared at the start of seamless/level-load transitions.
     */
    public void setSpecialStageReturnLevelReloadRequested(boolean requested) {
        this.specialStageReturnLevelReloadRequested = requested;
    }

    // ================================================================
    //  Big ring return position
    // ================================================================

    /**
     * Saves the big ring return state (ROM: Save_Level_Data2 -> Saved2_*).
     */
    public void saveBigRingReturn(BigRingReturnState state) {
        this.bigRingReturn = state;
    }

    /** Returns true if a big ring return state is saved. */
    public boolean hasBigRingReturn() {
        return bigRingReturn != null;
    }

    /** Returns the saved big ring return state, or null if none. */
    public BigRingReturnState getBigRingReturn() {
        return bigRingReturn;
    }

    /** Clears the big ring return state. */
    public void clearBigRingReturn() {
        this.bigRingReturn = null;
    }

    // ================================================================
    //  Bonus stage requests
    // ================================================================

    /**
     * Request entry to a bonus stage from a star post bonus star.
     * Called by Sonic3kStarPostBonusStarChild on player touch.
     */
    public void requestBonusStageEntry(BonusStageType type) {
        this.bonusStageRequested = type;
    }

    /**
     * Consumes and clears the bonus stage request.
     * @return the requested bonus stage type, or null if none requested
     */
    public BonusStageType consumeBonusStageRequest() {
        BonusStageType requested = bonusStageRequested;
        bonusStageRequested = null;
        return requested;
    }

    /**
     * Signals that the next level load is a bonus stage return.
     * Set before {@code loadZoneAndAct()} so that {@code onInitLevel()} can
     * detect the return and skip intros. The checkpoint index is restored
     * to {@code CheckpointState} after the load completes.
     *
     * @param checkpointIndex the Last_star_post_hit value saved before bonus entry
     */
    public void setBonusStageReturnCheckpointIndex(int checkpointIndex) {
        this.bonusStageReturnCheckpointIndex = checkpointIndex;
    }

    /** Returns true if this level load is a bonus stage return. */
    public boolean isBonusStageReturn() {
        return bonusStageReturnCheckpointIndex >= 0;
    }

    /** Returns the checkpoint index for bonus stage return, or -1 if not returning. */
    public int getBonusStageReturnCheckpointIndex() {
        return bonusStageReturnCheckpointIndex;
    }

    /** Clears the bonus stage return signal. */
    public void clearBonusStageReturn() {
        this.bonusStageReturnCheckpointIndex = -1;
    }

    // ================================================================
    //  Title card requests
    // ================================================================

    /**
     * Requests a title card to be shown for the current zone/act.
     * Called when a new level is loaded.
     *
     * @param zone Zone index (0-10)
     * @param act  Act index (0-2)
     */
    public void requestTitleCard(int zone, int act) {
        this.titleCardRequested = true;
        this.titleCardZone = zone;
        this.titleCardAct = act;
    }

    /**
     * Requests an in-level (transparent) title card overlay.
     */
    public void requestInLevelTitleCard(int zone, int act) {
        this.inLevelTitleCardRequested = true;
        this.inLevelTitleCardZone = zone;
        this.inLevelTitleCardAct = act;
    }

    /**
     * Checks if a title card has been requested.
     *
     * @return true if a title card was requested since last check
     */
    public boolean isTitleCardRequested() {
        return titleCardRequested;
    }

    /**
     * Consumes and clears the title card request flag.
     *
     * @return true if a title card was requested since last check
     */
    public boolean consumeTitleCardRequest() {
        boolean requested = titleCardRequested;
        titleCardRequested = false;
        return requested;
    }

    /**
     * Consumes and clears the in-level title card request flag.
     */
    public boolean consumeInLevelTitleCardRequest() {
        boolean requested = inLevelTitleCardRequested;
        inLevelTitleCardRequested = false;
        return requested;
    }

    /**
     * Gets the zone index for the requested title card.
     *
     * @return zone index, or -1 if none requested
     */
    public int getTitleCardZone() {
        return titleCardZone;
    }

    /**
     * Gets the act index for the requested title card.
     *
     * @return act index, or -1 if none requested
     */
    public int getTitleCardAct() {
        return titleCardAct;
    }

    public int getInLevelTitleCardZone() {
        return inLevelTitleCardZone;
    }

    public int getInLevelTitleCardAct() {
        return inLevelTitleCardAct;
    }

    // ================================================================
    //  Transition requests (fade-coordinated)
    // ================================================================

    /**
     * Request a respawn (death). GameLoop will handle the fade transition.
     */
    public void requestRespawn() {
        this.respawnRequested = true;
    }

    /**
     * Check and consume respawn request.
     *
     * @return true if respawn was requested
     */
    public boolean consumeRespawnRequest() {
        boolean requested = respawnRequested;
        respawnRequested = false;
        return requested;
    }

    /**
     * Request transition to next act. GameLoop will handle the fade transition.
     */
    public void requestNextAct() {
        this.nextActRequested = true;
    }

    /**
     * Check and consume next act request.
     *
     * @return true if next act was requested
     */
    public boolean consumeNextActRequest() {
        boolean requested = nextActRequested;
        nextActRequested = false;
        return requested;
    }

    /**
     * Request transition to next zone. GameLoop will handle the fade transition.
     */
    public void requestNextZone() {
        this.nextZoneRequested = true;
    }

    /**
     * Check and consume next zone request.
     *
     * @return true if next zone was requested
     */
    public boolean consumeNextZoneRequest() {
        boolean requested = nextZoneRequested;
        nextZoneRequested = false;
        return requested;
    }

    /**
     * Request transition to a specific zone and act. GameLoop will handle the fade transition.
     *
     * @param zone the zone index (0-based)
     * @param act the act index (0-based)
     */
    public void requestZoneAndAct(int zone, int act) {
        requestZoneAndAct(zone, act, false);
    }

    /**
     * Request transition to a specific zone and act with optional level deactivation
     * during the pending fade.
     *
     * @param zone                the zone index (0-based)
     * @param act                 the act index (0-based)
     * @param deactivateLevelNow  true to freeze level updates until the transition completes
     */
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
        this.requestedZone = zone;
        this.requestedAct = act;
        this.specificZoneActRequested = true;
        this.levelInactiveForTransition = deactivateLevelNow;
    }

    /**
     * Check and consume specific zone/act request.
     *
     * @return true if a specific zone/act was requested
     */
    public boolean consumeZoneActRequest() {
        boolean requested = specificZoneActRequested;
        specificZoneActRequested = false;
        return requested;
    }

    /**
     * Get the requested zone index. Only valid after consumeZoneActRequest() returns true.
     *
     * @return the requested zone index
     */
    public int getRequestedZone() {
        return requestedZone;
    }

    /**
     * Get the requested act index. Only valid after consumeZoneActRequest() returns true.
     *
     * @return the requested act index
     */
    public int getRequestedAct() {
        return requestedAct;
    }

    // ================================================================
    //  Seamless transitions
    // ================================================================

    /**
     * Request an in-place seamless transition. GameLoop will execute it directly
     * without fade.
     */
    public void requestSeamlessTransition(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return;
        }
        this.pendingSeamlessTransitionRequest = request;
        this.seamlessTransitionRequested = true;
        this.levelInactiveForTransition = request.deactivateLevelNow();
    }

    /**
     * Consumes the pending seamless transition request.
     */
    public SeamlessLevelTransitionRequest consumeSeamlessTransitionRequest() {
        if (!seamlessTransitionRequested) {
            return null;
        }
        seamlessTransitionRequested = false;
        SeamlessLevelTransitionRequest request = pendingSeamlessTransitionRequest;
        pendingSeamlessTransitionRequest = null;
        return request;
    }

    // ================================================================
    //  Credits
    // ================================================================

    /**
     * Request transition to ending credits sequence.
     * Called by Sonic1EndingSTHObjectInstance after the STH logo timer expires.
     */
    public void requestCreditsTransition() {
        this.creditsRequested = true;
    }

    /**
     * Check and consume credits transition request.
     *
     * @return true if credits were requested
     */
    public boolean consumeCreditsRequest() {
        boolean requested = creditsRequested;
        creditsRequested = false;
        return requested;
    }

    // ================================================================
    //  HUD / music suppression
    // ================================================================

    /**
     * Force-suppress HUD rendering. Used during credits demo playback
     * where the HUD should not appear regardless of zone settings.
     */
    public void setForceHudSuppressed(boolean suppressed) {
        this.forceHudSuppressed = suppressed;
    }

    /** Returns true if HUD rendering is force-suppressed. */
    public boolean isForceHudSuppressed() {
        return forceHudSuppressed;
    }

    /**
     * Suppresses the zone music that normally plays on the next loadLevel() call.
     * Resets after one use. Used by credits sequence to prevent zone music from
     * overriding the credits music.
     */
    public void setSuppressNextMusicChange(boolean suppress) {
        this.suppressNextMusicChange = suppress;
    }

    /** Returns true if the next music change should be suppressed. */
    public boolean isSuppressNextMusicChange() {
        return suppressNextMusicChange;
    }

    // ================================================================
    //  Level inactive flag
    // ================================================================

    /**
     * Returns true while the current level should be treated as inactive for a
     * pending zone/act transition.
     */
    public boolean isLevelInactiveForTransition() {
        return levelInactiveForTransition;
    }

    /**
     * Sets the level-inactive-for-transition flag.
     */
    public void setLevelInactiveForTransition(boolean inactive) {
        this.levelInactiveForTransition = inactive;
    }

    // ================================================================
    //  Bulk reset
    // ================================================================

    /**
     * Clears all transition-related state.
     * Called from {@code LevelManager.resetState()}.
     */
    public void resetState() {
        specialStageRequestedFromCheckpoint = false;
        specialStageReturnLevelReloadRequested = false;
        bigRingReturn = null;
        bonusStageRequested = null;
        bonusStageReturnCheckpointIndex = -1;
        titleCardRequested = false;
        titleCardZone = -1;
        titleCardAct = -1;
        inLevelTitleCardRequested = false;
        inLevelTitleCardZone = -1;
        inLevelTitleCardAct = -1;
        respawnRequested = false;
        nextActRequested = false;
        nextZoneRequested = false;
        specificZoneActRequested = false;
        seamlessTransitionRequested = false;
        creditsRequested = false;
        forceHudSuppressed = false;
        suppressNextMusicChange = false;
        levelInactiveForTransition = false;
        requestedZone = -1;
        requestedAct = -1;
        pendingSeamlessTransitionRequest = null;
    }
}
