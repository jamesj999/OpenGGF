package com.openggf.game.sonic3k.events;

/**
 * Visual phase for the AIZ fire curtain overlay.
 */
public enum FireCurtainStage {
    INACTIVE,
    AIZ1_RISING,
    AIZ1_REFRESH,
    AIZ1_FINISH,
    AIZ2_REDRAW,
    AIZ2_WAIT_FIRE,
    AIZ2_BG_REDRAW
}
