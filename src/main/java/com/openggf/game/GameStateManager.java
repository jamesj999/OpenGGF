package com.openggf.game;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages session-persistent game state such as Score, Lives, and Special Stage progress.
 * <p>
 * Special Stage tracking mirrors ROM variables:
 * - Current_Special_Stage: cycles 0-6, wraps at 7→0, increments on each entry
 * - Emerald_count: number of emeralds collected (0-7)
 * - Got_Emeralds_array: which specific emeralds have been obtained
 */
public class GameStateManager implements RewindSnapshottable<GameStateSnapshot> {
    private static final Logger LOGGER = Logger.getLogger(GameStateManager.class.getName());
    private static final int DEFAULT_SPECIAL_STAGE_COUNT = 7;
    private static final int DEFAULT_CHAOS_EMERALD_COUNT = 7;

    private int score;
    private int lives;
    private int continues;

    private int currentSpecialStageIndex;
    private int emeraldCount;
    private int specialStageCount;
    private int chaosEmeraldCount;
    private boolean[] gotEmeralds;
    private boolean[] gotSuperEmeralds;

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
     * Background collision flag (ROM: Background_collision_flag at $FFFFF7C7).
     * When set, terrain collision routines (FindFloor, FindWall) perform a dual-path
     * scan: first against FG collision data, then against BG collision data. The
     * result with the greater distance (more lenient) is used. This allows the
     * player to collide with background-layer terrain during specific sequences
     * (e.g., HCZ2 wall chase, SSZ moving platforms).
     */
    private boolean backgroundCollisionFlag;

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

    public GameStateManager() {
        configureSpecialStageProgress(DEFAULT_SPECIAL_STAGE_COUNT, DEFAULT_CHAOS_EMERALD_COUNT);
        resetSession();
    }

    /**
     * Resets the game session state to defaults (Score: 0, Lives: 3, no emeralds).
     */
    public void resetSession() {
        this.score = 0;
        this.lives = 3;
        this.continues = 0;

        this.currentSpecialStageIndex = 0;
        this.emeraldCount = 0;
        for (int i = 0; i < gotEmeralds.length; i++) {
            gotEmeralds[i] = false;
        }
        if (gotSuperEmeralds != null) {
            for (int i = 0; i < gotSuperEmeralds.length; i++) {
                gotSuperEmeralds[i] = false;
            }
        }

        this.currentBossId = 0;
        this.screenShakeActive = false;
        this.backgroundCollisionFlag = false;
        this.bigRingCollected = false;
        this.wfzFireToggle = false;
        this.itemBonus = 0;
        this.reverseGravityActive = false;
        this.collectedSpecialRings = 0;
        this.endOfLevelActive = false;
        this.endOfLevelFlag = false;
    }

    /**
     * Resets all mutable state for test teardown.
     * Delegates to {@link #resetSession()} for the actual reset logic.
     * This method exists for naming consistency with other singletons
     * (Camera, CollisionSystem, TimerManager, etc.).
     */
    public void resetState() {
        resetSession();
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

    public int getContinues() {
        return continues;
    }

    public void addContinue() {
        this.continues++;
    }

    public void restoreSaveProgress(int lives, int continues, List<Integer> chaosEmeralds, List<Integer> superEmeralds) {
        this.lives = Math.max(0, lives);
        this.continues = Math.max(0, continues);
        this.emeraldCount = 0;
        for (int i = 0; i < gotEmeralds.length; i++) {
            gotEmeralds[i] = false;
        }
        if (gotSuperEmeralds != null) {
            for (int i = 0; i < gotSuperEmeralds.length; i++) {
                gotSuperEmeralds[i] = false;
            }
        }
        if (chaosEmeralds != null) {
            for (Integer emeraldIndex : chaosEmeralds) {
                if (emeraldIndex != null && emeraldIndex >= 0 && emeraldIndex < gotEmeralds.length
                        && !gotEmeralds[emeraldIndex]) {
                    gotEmeralds[emeraldIndex] = true;
                    emeraldCount++;
                }
            }
        }
        if (gotSuperEmeralds != null && superEmeralds != null) {
            for (Integer emeraldIndex : superEmeralds) {
                if (emeraldIndex != null && emeraldIndex >= 0 && emeraldIndex < gotSuperEmeralds.length
                        && gotEmeralds[emeraldIndex]) {
                    gotSuperEmeralds[emeraldIndex] = true;
                }
            }
        }
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
     * S3K behavior: scan from the current special stage index until an
     * uncollected stage for the active emerald mode is found, then advance
     * the cursor to the following slot.
     *
     * @param superEmeraldMode when true, skip collected super emerald stages;
     *                         otherwise skip collected chaos emerald stages
     * @return the stage index selected for entry
     */
    public int consumeCurrentSpecialStageIndexAndAdvanceS3k(boolean superEmeraldMode) {
        if (specialStageCount <= 0) {
            return 0;
        }
        int startIndex = Math.floorMod(currentSpecialStageIndex, specialStageCount);
        int selectedIndex = startIndex;
        for (int offset = 0; offset < specialStageCount; offset++) {
            int candidateIndex = (startIndex + offset) % specialStageCount;
            if (isS3kSpecialStageUncollected(candidateIndex, superEmeraldMode)) {
                selectedIndex = candidateIndex;
                break;
            }
        }
        currentSpecialStageIndex = (selectedIndex + 1) % specialStageCount;
        return selectedIndex;
    }

    private boolean isS3kSpecialStageUncollected(int index, boolean superEmeraldMode) {
        if (superEmeraldMode) {
            return !hasSuperEmerald(index);
        }
        return !hasEmerald(index);
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

    public List<Integer> getCollectedChaosEmeraldIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < gotEmeralds.length; i++) {
            if (gotEmeralds[i]) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
    }

    public List<Integer> getCollectedSuperEmeraldIndices() {
        List<Integer> indices = new ArrayList<>();
        if (gotSuperEmeralds == null) {
            return List.of();
        }
        for (int i = 0; i < gotSuperEmeralds.length; i++) {
            if (gotSuperEmeralds[i]) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
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

    public boolean hasSuperEmerald(int index) {
        return gotSuperEmeralds != null
                && index >= 0
                && index < gotSuperEmeralds.length
                && gotSuperEmeralds[index];
    }

    public synchronized void markSuperEmeraldCollected(int index) {
        if (gotSuperEmeralds == null || index < 0 || index >= gotSuperEmeralds.length) {
            LOGGER.warning("Attempted to mark super emerald " + index +
                    " but valid range is 0-" + ((gotSuperEmeralds == null ? 0 : gotSuperEmeralds.length) - 1));
            return;
        }
        gotSuperEmeralds[index] = true;
    }

    public boolean hasAllSuperEmeralds() {
        if (gotSuperEmeralds == null || gotSuperEmeralds.length == 0) {
            return false;
        }
        for (boolean gotSuperEmerald : gotSuperEmeralds) {
            if (!gotSuperEmerald) {
                return false;
            }
        }
        return true;
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
        this.gotSuperEmeralds = new boolean[safeEmeraldTarget];
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
     * Gets the background collision flag.
     * ROM: tst.b (Background_collision_flag).w
     *
     * @return true if background collision is enabled
     */
    public boolean isBackgroundCollisionFlag() {
        return backgroundCollisionFlag;
    }

    /**
     * Sets the background collision flag.
     * ROM: st (Background_collision_flag).w / clr.b (Background_collision_flag).w
     *
     * @param flag true to enable dual-path BG collision, false to disable
     */
    public void setBackgroundCollisionFlag(boolean flag) {
        this.backgroundCollisionFlag = flag;
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

    @Override
    public String key() {
        return "gamestate";
    }

    @Override
    public GameStateSnapshot capture() {
        return new GameStateSnapshot(
                score, lives, continues, currentSpecialStageIndex, emeraldCount,
                gotEmeralds, gotSuperEmeralds, currentBossId,
                screenShakeActive, backgroundCollisionFlag, bigRingCollected,
                wfzFireToggle, itemBonus, reverseGravityActive,
                collectedSpecialRings, endOfLevelActive, endOfLevelFlag);
    }

    @Override
    public void restore(GameStateSnapshot snapshot) {
        this.score = snapshot.score();
        this.lives = snapshot.lives();
        this.continues = snapshot.continues();
        this.currentSpecialStageIndex = snapshot.currentSpecialStageIndex();
        this.emeraldCount = snapshot.emeraldCount();
        this.gotEmeralds = snapshot.gotEmeralds().clone();
        this.gotSuperEmeralds = snapshot.gotSuperEmeralds().clone();
        this.currentBossId = snapshot.currentBossId();
        this.screenShakeActive = snapshot.screenShakeActive();
        this.backgroundCollisionFlag = snapshot.backgroundCollisionFlag();
        this.bigRingCollected = snapshot.bigRingCollected();
        this.wfzFireToggle = snapshot.wfzFireToggle();
        this.itemBonus = snapshot.itemBonus();
        this.reverseGravityActive = snapshot.reverseGravityActive();
        this.collectedSpecialRings = snapshot.collectedSpecialRings();
        this.endOfLevelActive = snapshot.endOfLevelActive();
        this.endOfLevelFlag = snapshot.endOfLevelFlag();
    }
}
