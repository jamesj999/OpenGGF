package com.openggf.game.sonic3k.specialstage;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;

/**
 * "Get Blue Spheres" intro/outro banner for the S3K Blue Ball special stage.
 * <p>
 * The banner consists of two halves that slide in from the screen edges,
 * display for 3 seconds, then slide out. When the banner finishes sliding
 * out, the player begins auto-advancing.
 * <p>
 * The banner can re-enter when all converted rings are collected.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm Obj_SStage_8E40 (line 11310)
 */
public class Sonic3kSpecialStageBanner {

    /** Banner state. */
    public enum Phase {
        /** Banner is sliding in from the edges. */
        SLIDING_IN,
        /** Banner is fully displayed, timer counting down. */
        DISPLAYING,
        /** Banner is sliding out to the edges. */
        SLIDING_OUT,
        /** Banner has exited, normal gameplay. */
        IDLE,
        /** Banner re-entering (all rings collected message). */
        RE_ENTERING
    }

    private Phase phase;
    private int slideOffset;
    private int displayTimer;
    /** Set when the banner triggers player auto-advance on exit. */
    private boolean triggeredAdvance;

    public void initialize() {
        phase = Phase.SLIDING_IN;
        slideOffset = 0;
        displayTimer = 0;
        triggeredAdvance = false;
    }

    /**
     * Update the banner state machine.
     *
     * @return true if the banner just finished sliding out (trigger player advance)
     */
    public boolean update() {
        switch (phase) {
            case SLIDING_IN:
                slideOffset += BANNER_SLIDE_SPEED;
                if (slideOffset >= BANNER_MAX_OFFSET) {
                    slideOffset = BANNER_MAX_OFFSET;
                    phase = Phase.DISPLAYING;
                    displayTimer = BANNER_DISPLAY_FRAMES;
                }
                break;

            case DISPLAYING:
                displayTimer--;
                if (displayTimer <= 0) {
                    phase = Phase.SLIDING_OUT;
                }
                break;

            case SLIDING_OUT:
                slideOffset -= BANNER_SLIDE_SPEED;
                if (slideOffset <= 0) {
                    slideOffset = 0;
                    phase = Phase.IDLE;
                    if (!triggeredAdvance) {
                        triggeredAdvance = true;
                        return true; // Signal: trigger player auto-advance
                    }
                }
                break;

            case RE_ENTERING:
                slideOffset += BANNER_SLIDE_SPEED;
                if (slideOffset >= BANNER_MAX_OFFSET) {
                    slideOffset = BANNER_MAX_OFFSET;
                    phase = Phase.DISPLAYING;
                    displayTimer = BANNER_DISPLAY_FRAMES;
                }
                break;

            case IDLE:
                // No-op: waiting for external trigger
                break;
        }
        return false;
    }

    /**
     * Trigger the banner to re-enter (when all rings from conversion are collected).
     * ROM: loc_8EEC (sonic3k.asm:11362) - checks Special_stage_rings_left == 0
     */
    public void triggerReEntry() {
        if (phase == Phase.IDLE) {
            phase = Phase.RE_ENTERING;
            slideOffset = 0;
        }
    }

    /**
     * Get the left half X position.
     * ROM: left half moves from -BANNER_MAX_OFFSET to center.
     */
    public int getLeftX() {
        return BANNER_CENTER_X - slideOffset;
    }

    /**
     * Get the right half X position.
     */
    public int getRightX() {
        return BANNER_CENTER_X + slideOffset;
    }

    public Phase getPhase() { return phase; }
    public int getSlideOffset() { return slideOffset; }
    public boolean isVisible() { return phase != Phase.IDLE; }
}
