package uk.co.jamesj999.sonic.game.sonic3k.titlecard;

/**
 * S3K title card animation states.
 * Simpler than S2's 8-state machine since S3K uses staggered priority-based exit
 * instead of cascading element-specific exit phases.
 */
public enum Sonic3kTitleCardState {
    SLIDE_IN,
    DISPLAY,
    EXIT,
    COMPLETE
}
