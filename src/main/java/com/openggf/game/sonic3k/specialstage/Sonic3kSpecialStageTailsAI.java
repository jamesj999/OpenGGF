package com.openggf.game.sonic3k.specialstage;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;

/**
 * Tails follower AI for the S3K Blue Ball special stage.
 * <p>
 * Tails follows Player 1 with a 4-entry delay buffer. When Player 2
 * is not providing input (idle for 600 frames), the AI takes over
 * and mimics P1's actions from the delay buffer.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm sub_937C (line 11711)
 */
public class Sonic3kSpecialStageTailsAI {

    /** Position delay buffer (circular, 256 entries of 4 bytes each). */
    private final int[] posTableInput = new int[256];   // P1 held buttons
    private final int[] posTableJump = new int[256];    // P1 jumping state
    private int posTableIndex;

    /** Tails CPU idle timer. */
    private int cpuIdleTimer;

    /** Last P2 input for idle detection. */
    private int lastP2Input;

    public void initialize() {
        java.util.Arrays.fill(posTableInput, 0);
        java.util.Arrays.fill(posTableJump, 0);
        posTableIndex = 0;
        cpuIdleTimer = 0;
        lastP2Input = 0;
    }

    /**
     * Record P1 state and determine P2 (Tails) input.
     * ROM: sub_937C (sonic3k.asm:11711)
     *
     * @param p1HeldButtons P1 held buttons this frame
     * @param p1Jumping P1 jumping state
     * @param p2HeldButtons P2 held buttons (raw controller input)
     * @return effective input for Tails (buttons bitmask)
     */
    public int update(int p1HeldButtons, int p1Jumping, int p2HeldButtons) {
        // Record P1 state to the position table
        posTableInput[posTableIndex & 0xFF] = p1HeldButtons;
        posTableJump[posTableIndex & 0xFF] = p1Jumping;
        posTableIndex = (posTableIndex + 1) & 0xFF;

        // Check if P2 is actively pressing buttons
        int p2Movement = p2HeldButtons & 0x7F; // Up/Down/Left/Right/A/B/C
        if (p2Movement != 0) {
            cpuIdleTimer = TAILS_CPU_IDLE_TIMEOUT;
        }

        // If P2 is active (timer > 0), use P2 controller directly
        if (cpuIdleTimer > 0) {
            cpuIdleTimer--;
            return p2HeldButtons;
        }

        // AI mode: replay P1 input from 4 entries ago
        // ROM: reads from Pos_table at (index - 4*4) for jump state
        int delayedIndex = (posTableIndex - TAILS_POS_DELAY) & 0xFF;
        int prevIndex = (delayedIndex - 1) & 0xFF;

        int delayedJump = posTableJump[delayedIndex];
        int prevJump = posTableJump[prevIndex];

        // Auto-jump: if P1 was on ground at prev and jumping at delayed
        int result = 0;
        if (prevJump < 0 && delayedJump >= 0) {
            // P1 just landed at this delayed point
        } else if (delayedJump == 0x81 && prevJump >= 0) {
            // P1 was on a spring at this delayed point - trigger jump
            result = 0x70; // A+B+C pressed
        }

        return result;
    }

    /**
     * Check if Tails AI wants to jump (based on delayed P1 state).
     * ROM: sub_937C reads Pos_table and returns jump button if P1 was jumping.
     *
     * @return true if Tails should auto-jump this frame
     */
    public boolean shouldAutoJump() {
        int delayedIndex = (posTableIndex - TAILS_POS_DELAY) & 0xFF;
        int prevIndex = (delayedIndex - 1) & 0xFF;

        int currentJump = posTableJump[delayedIndex];
        int prevJump = posTableJump[prevIndex];

        // ROM: cmpi.b #-$7F,d2 (0x81 = spring jump) / tst.b d1
        return currentJump == 0x81 && prevJump >= 0;
    }
}
