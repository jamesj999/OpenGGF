package com.openggf.game;

import java.util.logging.Logger;

/**
 * Manages session-persistent game state such as Score, Lives, and Special Stage progress.
 * <p>
 * Special Stage tracking mirrors ROM variables:
 * - Current_Special_Stage: cycles 0-6, wraps at 7→0, increments on each entry
 * - Emerald_count: number of emeralds collected (0-7)
 * - Got_Emeralds_array: which specific emeralds have been obtained
 */
public class GameStateManager {
    private static final Logger LOGGER = Logger.getLogger(GameStateManager.class.getName());
    private static final int DEFAULT_SPECIAL_STAGE_COUNT = 7;
    private static final int DEFAULT_CHAOS_EMERALD_COUNT = 7;

    private static GameStateManager instance;

    private int score;
    private int lives;

    private int currentSpecialStageIndex;
    private int emeraldCount;
    private int specialStageCount;
    private int chaosEmeraldCount;
    private boolean[] gotEmeralds;

    /**
     * Current boss ID (ROM: Current_Boss_ID).
     * 0 = no boss active, non-zero = boss fight in progress.
     * Used by level boundary logic to remove the +64 right buffer during boss fights.
     */
    private int currentBossId;

    /**
     * Screen shake flag (ROM: Screen_Shaking_Flag at $FFFFF72C).
     * When active, scroll handlers should apply shake offsets from ripple data.
     * Used by boss fights and events like pillar rising in ARZ.
     */
    private boolean screenShakeActive;

    /**
     * HTZ-specific screen shake flag (ROM: Screen_Shaking_Flag_HTZ at $FFFFF7C3).
     * This is the master flag for HTZ earthquake sequences. Unlike the general
     * Screen_Shaking_Flag which gets cleared during delay periods, this flag
     * stays active for the entire earthquake sequence.
     * Used by Obj30 (RisingLava) to determine if the platform should be solid.
     */
    private boolean htzScreenShakeActive;

    /**
     * Giant Ring collected flag (S1 ROM: f_bigring at $FFFFF7AA).
     * Set when a Giant Ring flash triggers; prevents hidden bonuses from activating.
     * Reset on level load.
     */
    private boolean bigRingCollected;

    /**
     * WFZ/SCZ fire toggle (ROM: WFZ_SCZ_Fire_Toggle at $FFFFF72E).
     * Controls which palette cycling data is used in Wing Fortress Zone:
     * false (0) = fire palette (CyclingPal_WFZFire), timer 1
     * true (1)  = conveyor belt palette (CyclingPal_WFZBelt), timer 5
     * Toggled by Obj8B (WFZPalSwitcher) when player crosses trigger lines.
     */
    private boolean wfzFireToggle;

    /**
     * Item bonus chain counter (ROM: v_itembonus at $FFFFFEB2).
     * Tracks consecutive block/wall smashes in a level. Incremented by 2 per smash.
     * Used by Object 0x3C (Smashable Wall) and Object 0x51 (Smashable Green Block)
     * to award escalating points. Reset on level load.
     */
    private int itemBonus;

    /**
     * Reverse gravity flag (ROM: Reverse_gravity_flag at $FFFFF768).
     * When active, gravity is inverted for players and Y-dependent objects
     * must flip their behavior (e.g., up springs become down springs).
     * Used in S3K DEZ gravity-flip sections.
     * Currently always false - will be activated by level events when implemented.
     */
    private boolean reverseGravityActive;

    /**
     * S3K Special Stage Entry Ring collected bitfield (ROM: Collected_special_ring_array).
     * 32-bit bitfield where each ring's subtype (0-31) is a bit index.
     * Reset per level load.
     */
    private int collectedSpecialRings;

    /**
     * End-of-level signpost active flag (ROM: Level_end_flag at $FFFFFFD1).
     * Set when the end-of-act signpost begins its sequence; gates player
     * control lock and score tally trigger.
     */
    private boolean endOfLevelActive;

    /**
     * End-of-level completed flag (ROM: End_of_level_flag).
     * Set when the signpost spin/land sequence finishes and the act
     * transition should begin.
     */
    private boolean endOfLevelFlag;

    private GameStateManager() {
        configureSpecialStageProgress(DEFAULT_SPECIAL_STAGE_COUNT, DEFAULT_CHAOS_EMERALD_COUNT);
        resetSession();
    }

    public static synchronized GameStateManager getInstance() {
        if (instance == null) {
            instance = new GameStateManager();
        }
        return instance;
    }

    /**
     * Resets the game session state to defaults (Score: 0, Lives: 3, no emeralds).
     */
    public void resetSession() {
        this.score = 0;
        this.lives = 3;

        this.currentSpecialStageIndex = 0;
        this.emeraldCount = 0;
        for (int i = 0; i < gotEmeralds.length; i++) {
            gotEmeralds[i] = false;
        }

        this.currentBossId = 0;
        this.screenShakeActive = false;
        this.htzScreenShakeActive = false;
        this.bigRingCollected = false;
        this.wfzFireToggle = false;
        this.itemBonus = 0;
        this.reverseGravityActive = false;
        this.collectedSpecialRings = 0;
        this.endOfLevelActive = false;
        this.endOfLevelFlag = false;
    }

    /**
     * Resets per-level state flags between act transitions.
     * Session-persistent state (score, lives, emeralds) is NOT reset.
     */
    public void resetForLevel() {
        endOfLevelActive = false;
        endOfLevelFlag = false;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int amount) {
        if (amount > 0) {
            this.score += amount;
        }
    }

    public int getLives() {
        return lives;
    }

    public void addLife() {
        this.lives++;
    }

    public void loseLife() {
        if (this.lives > 0) {
            this.lives--;
        }
    }

    /**
     * Gets the current special stage index (0-6).
     */
    public int getCurrentSpecialStageIndex() {
        return currentSpecialStageIndex;
    }

    /**
     * ROM behavior: get the current stage index and advance to next.
     * Stage index wraps from 6 back to 0.
     * @return The stage index to use for this entry (before increment)
     */
    public int consumeCurrentSpecialStageIndexAndAdvance() {
        if (specialStageCount <= 0) {
            return 0;
        }
        int index = currentSpecialStageIndex;
        currentSpecialStageIndex = (currentSpecialStageIndex + 1) % specialStageCount;
        return index;
    }

    /**
     * Gets the total number of emeralds collected (0-7).
     */
    public int getEmeraldCount() {
        return emeraldCount;
    }

    /**
     * Checks if a specific emerald has been collected.
     * @param index Emerald index (0-6)
     */
    public boolean hasEmerald(int index) {
        return index >= 0 && index < gotEmeralds.length && gotEmeralds[index];
    }

    /**
     * Marks an emerald as collected.
     * @param index Emerald index (0-6)
     */
    public synchronized void markEmeraldCollected(int index) {
        if (index < 0 || index >= gotEmeralds.length) {
            LOGGER.warning("Attempted to mark emerald " + index +
                " but valid range is 0-" + (gotEmeralds.length - 1));
            return;
        }
        if (!gotEmeralds[index]) {
            gotEmeralds[index] = true;
            emeraldCount++;
        }
    }

    /**
     * Checks if all 7 emeralds have been collected.
     */
    public boolean hasAllEmeralds() {
        return emeraldCount >= chaosEmeraldCount;
    }

    /**
     * Configures special stage cycle count and emerald target count for the current game.
     *
     * @param stageCount number of special stages in the rotation (minimum 1)
     * @param emeraldTarget number of chaos emeralds in this game (minimum 1)
     */
    public synchronized void configureSpecialStageProgress(int stageCount, int emeraldTarget) {
        int safeStageCount = Math.max(1, stageCount);
        int safeEmeraldTarget = Math.max(1, emeraldTarget);

        this.specialStageCount = safeStageCount;
        this.chaosEmeraldCount = safeEmeraldTarget;
        this.gotEmeralds = new boolean[safeEmeraldTarget];
        this.currentSpecialStageIndex = 0;
        this.emeraldCount = 0;
    }

    public int getSpecialStageCount() {
        return specialStageCount;
    }

    public int getChaosEmeraldCount() {
        return chaosEmeraldCount;
    }

    /**
     * Checks if a Special Stage entry ring has been collected.
     * ROM: btst d0,(Collected_special_ring_array).w
     */
    public boolean isSpecialRingCollected(int bitIndex) {
        return (collectedSpecialRings & (1 << (bitIndex & 0x1F))) != 0;
    }

    /**
     * Marks a Special Stage entry ring as collected.
     * ROM: bset d0,(Collected_special_ring_array).w
     */
    public void markSpecialRingCollected(int bitIndex) {
        collectedSpecialRings |= (1 << (bitIndex & 0x1F));
    }

    /**
     * Gets the current boss ID.
     * ROM: Current_Boss_ID - 0 means no boss active.
     */
    public int getCurrentBossId() {
        return currentBossId;
    }

    /**
     * Sets the current boss ID.
     * ROM: Current_Boss_ID - set to non-zero when entering a boss fight,
     * 0 when the boss is defeated. When non-zero, the +64 right boundary
     * buffer is removed to keep the player within the boss arena.
     */
    public void setCurrentBossId(int bossId) {
        this.currentBossId = bossId;
    }

    /**
     * Checks if a boss fight is currently active.
     */
    public boolean isBossFightActive() {
        return currentBossId != 0;
    }

    /**
     * Gets the screen shake active state.
     * ROM: tst.b (Screen_Shaking_Flag).w
     *
     * @return true if screen shake is active
     */
    public boolean isScreenShakeActive() {
        return screenShakeActive;
    }

    /**
     * Sets the screen shake active state.
     * ROM: move.b #1,(Screen_Shaking_Flag).w to enable
     * ROM: move.b #0,(Screen_Shaking_Flag).w to disable
     *
     * When active, scroll handlers should use ripple data from ParallaxTables
     * to apply shake offsets to both horizontal and vertical scroll values.
     *
     * @param active true to enable screen shake, false to disable
     */
    public void setScreenShakeActive(boolean active) {
        this.screenShakeActive = active;
    }

    /**
     * Gets the HTZ-specific screen shake flag.
     * ROM: tst.b (Screen_Shaking_Flag_HTZ).w
     *
     * This is the master flag checked by Obj30 (RisingLava) to determine
     * if the invisible solid platforms should be active. Unlike the general
     * Screen_Shaking_Flag, this stays on during delay periods.
     *
     * @return true if HTZ earthquake sequence is active
     */
    public boolean isHtzScreenShakeActive() {
        return htzScreenShakeActive;
    }

    /**
     * Sets the HTZ-specific screen shake flag.
     * ROM: move.b #1,(Screen_Shaking_Flag_HTZ).w to enable
     * ROM: move.b #0,(Screen_Shaking_Flag_HTZ).w to disable
     *
     * This is set when entering an HTZ earthquake area and cleared when exiting.
     * The flag persists through delay periods when the lava pauses at limits.
     *
     * @param active true to enable HTZ earthquake mode, false to disable
     */
    public void setHtzScreenShakeActive(boolean active) {
        this.htzScreenShakeActive = active;
    }

    /**
     * Checks if a Giant Ring has been collected in this level.
     * S1 ROM: tst.b (f_bigring).w
     */
    public boolean isBigRingCollected() {
        return bigRingCollected;
    }

    /**
     * Sets the Giant Ring collected flag.
     * S1 ROM: move.b #1,(f_bigring).w — set by Ring Flash at trigger frame.
     */
    public void setBigRingCollected(boolean collected) {
        this.bigRingCollected = collected;
    }

    /**
     * Gets the WFZ/SCZ fire toggle state.
     * ROM: tst.b (WFZ_SCZ_Fire_Toggle).w
     *
     * @return true if toggled to conveyor belt palette, false for fire palette
     */
    public boolean isWfzFireToggle() {
        return wfzFireToggle;
    }

    /**
     * Sets the WFZ/SCZ fire toggle state.
     * ROM: move.b #1,(WFZ_SCZ_Fire_Toggle).w or move.b #0,(WFZ_SCZ_Fire_Toggle).w
     * Toggled by Obj8B (WFZPalSwitcher) when player crosses trigger boundary.
     *
     * @param toggle true for conveyor belt palette, false for fire palette
     */
    public void setWfzFireToggle(boolean toggle) {
        this.wfzFireToggle = toggle;
    }

    /**
     * Gets the current item bonus chain counter.
     * ROM: v_itembonus - tracks consecutive block/wall smashes.
     * Used as a word-sized index into score tables (increments by 2).
     */
    public int getItemBonus() {
        return itemBonus;
    }

    /**
     * Sets the item bonus chain counter.
     * ROM: move.w d2,(v_itembonus).w
     */
    public void setItemBonus(int value) {
        this.itemBonus = value;
    }

    /**
     * Resets the item bonus counter to zero.
     * Called on level load to reset the chain.
     */
    public void resetItemBonus() {
        this.itemBonus = 0;
    }

    /**
     * Gets the reverse gravity flag.
     * ROM: tst.b (Reverse_gravity_flag).w at $FFFFF768
     *
     * @return true if reverse gravity is active
     */
    public boolean isReverseGravityActive() {
        return reverseGravityActive;
    }

    /**
     * Sets the reverse gravity flag.
     * ROM: move.b #1,(Reverse_gravity_flag).w to enable
     *
     * @param active true to enable reverse gravity, false to disable
     */
    public void setReverseGravityActive(boolean active) {
        this.reverseGravityActive = active;
    }

    /**
     * Gets the end-of-level active flag.
     * ROM: tst.b (Level_end_flag).w
     *
     * @return true if the end-of-level signpost sequence is active
     */
    public boolean isEndOfLevelActive() { return endOfLevelActive; }

    /**
     * Sets the end-of-level active flag.
     * ROM: move.b #1,(Level_end_flag).w
     */
    public void setEndOfLevelActive(boolean active) { this.endOfLevelActive = active; }

    /**
     * Gets the end-of-level completed flag.
     * ROM: tst.b (End_of_level_flag).w
     *
     * @return true if the act transition should begin
     */
    public boolean isEndOfLevelFlag() { return endOfLevelFlag; }

    /**
     * Sets the end-of-level completed flag.
     * ROM: move.b #1,(End_of_level_flag).w
     */
    public void setEndOfLevelFlag(boolean flag) { this.endOfLevelFlag = flag; }
}

