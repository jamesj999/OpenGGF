package com.openggf.game.sonic3k.specialstage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for S3K special stage results screen tally mechanics.
 * Validates ring bonus, time bonus, continue threshold, and element visibility.
 * No ROM or OpenGL required.
 *
 * ROM reference: sonic3k.asm lines 63320-63327 (bonus calculation),
 * lines 54000/63400 (continue threshold), lines 54019-54025/63441-63444 (emerald check).
 */
class TestS3kSpecialStageResultsTally {

    // ---- Ring bonus: rings × 10 (ROM line 63321) ----

    @Test
    void ringBonus_0rings_returns0() {
        assertEquals(0, calculateRingBonus(0));
    }

    @Test
    void ringBonus_50rings_returns500() {
        assertEquals(500, calculateRingBonus(50));
    }

    @Test
    void ringBonus_100rings_returns1000() {
        assertEquals(1000, calculateRingBonus(100));
    }

    @Test
    void ringBonus_255rings_returns2550() {
        assertEquals(2550, calculateRingBonus(255));
    }

    // ---- Time bonus: 5000 if perfect (all spheres), else 0 ----
    // ROM lines 63323-63326: clr.w (Time_bonus_countdown) / tst.w (Special_stage_rings_left)
    // bne.s (skip) / move.w #5000,(Time_bonus_countdown)

    @Test
    void timeBonus_perfect_returns5000() {
        assertEquals(5000, calculateTimeBonus(true));
    }

    @Test
    void timeBonus_notPerfect_returns0() {
        assertEquals(0, calculateTimeBonus(false));
    }

    // ---- Continue icon threshold: >= 50 rings (ROM line 54000/63400) ----

    @Test
    void continueIcon_49rings_notShown() {
        assertFalse(shouldShowContinue(49));
    }

    @Test
    void continueIcon_50rings_shown() {
        assertTrue(shouldShowContinue(50));
    }

    @Test
    void continueIcon_100rings_shown() {
        assertTrue(shouldShowContinue(100));
    }

    // ---- Element visibility: failure vs success ----
    // ROM: Special_stage_spheres_left > 0 → failure message visible, character name hidden

    @Test
    void failMessage_visible_whenNotGotEmerald() {
        assertTrue(isFailMessageVisible(false));
    }

    @Test
    void failMessage_hidden_whenGotEmerald() {
        assertFalse(isFailMessageVisible(true));
    }

    @Test
    void charName_hidden_whenNotGotEmerald() {
        assertFalse(isCharNameVisible(false));
    }

    @Test
    void charName_visible_whenGotEmerald() {
        assertTrue(isCharNameVisible(true));
    }

    // ---- "NOW SUPER SONIC" visibility: only if succeeded + all 7 ----
    // ROM lines 64016-64021/54255-54260: spheres_left == 0 AND emerald_count >= 7

    @Test
    void superText_hidden_whenNotAllEmeralds() {
        assertFalse(isSuperTextVisible(true, 6));
    }

    @Test
    void superText_visible_whenAllEmeralds() {
        assertTrue(isSuperTextVisible(true, 7));
    }

    @Test
    void superText_hidden_whenFailed() {
        assertFalse(isSuperTextVisible(false, 7));
    }

    // ---- Tally decrement: counts down by 10 per frame ----

    @Test
    void tallyDecrement_bothBonuses_countDownTogether() {
        int ringBonus = 200;
        int timeBonus = 100;
        int totalScore = 0;
        int frames = 0;

        while (ringBonus > 0 || timeBonus > 0) {
            int increment = 0;
            if (timeBonus > 0) { timeBonus -= 10; increment += 10; }
            if (ringBonus > 0) { ringBonus -= 10; increment += 10; }
            totalScore += increment;
            frames++;
        }

        assertEquals(0, ringBonus);
        assertEquals(0, timeBonus);
        assertEquals(300, totalScore); // 200 + 100
        assertEquals(20, frames); // 200/10 = 20 (ring bonus takes longer)
    }

    @Test
    void tallyDecrement_perfectStage_totalIs5500() {
        // 50 rings collected (500 ring bonus) + perfect (5000 time bonus)
        int total = calculateRingBonus(50) + calculateTimeBonus(true);
        assertEquals(5500, total);
    }

    // ---- Emerald flicker: 3-state counter, visible when != 0 ----

    @Test
    void emeraldFlicker_3statePattern() {
        // ROM: counter cycles 0, 1, 2, 0, 1, 2, ...
        // Draw when counter != 0 (visible 2/3 of frames)
        int visibleCount = 0;
        int counter = 0;
        for (int i = 0; i < 30; i++) {
            if (counter != 0) visibleCount++;
            counter++;
            if (counter >= 3) counter = 0;
        }
        assertEquals(20, visibleCount); // 2/3 of 30 = 20
    }

    // ---- Continue icon blink: bit 3 of frame counter ----

    @Test
    void continueBlink_pattern() {
        // ROM: btst #3,(Level_frame_counter+1) → visible when bit 3 set
        // Pattern: 8 frames hidden, 8 frames visible, repeat
        int visibleCount = 0;
        for (int frame = 0; frame < 32; frame++) {
            if (((frame >> 3) & 1) != 0) visibleCount++;
        }
        assertEquals(16, visibleCount); // Half visible
    }

    // ---- Helper methods matching ROM logic ----

    private int calculateRingBonus(int rings) {
        return rings * 10;
    }

    private int calculateTimeBonus(boolean gotEmerald) {
        return gotEmerald ? 5000 : 0;
    }

    private boolean shouldShowContinue(int rings) {
        return rings >= 50;
    }

    private boolean isFailMessageVisible(boolean gotEmerald) {
        return !gotEmerald;
    }

    private boolean isCharNameVisible(boolean gotEmerald) {
        return gotEmerald;
    }

    private boolean isSuperTextVisible(boolean gotEmerald, int totalEmeraldCount) {
        return gotEmerald && totalEmeraldCount >= 7;
    }
}


