package com.openggf.game.sonic1.titlecard;

/**
 * States for the Sonic 1 title card animation state machine.
 *
 * <p>Sonic 1's title card is simpler than Sonic 2's - all 4 elements
 * (zone name, "ZONE", act number, oval) slide in/out together with
 * no cascading exit sequence.
 *
 * <p>From the disassembly (Object 34):
 * <ul>
 *   <li>Routine 0: Initialize elements (Card_CheckSBZ3)</li>
 *   <li>Routine 2: Slide in at 16px/frame (Card_ChkPos)</li>
 *   <li>Routine 4: Wait 60 frames (Card_Wait), then slide out at 32px/frame (Card_ChkPos2)</li>
 * </ul>
 *
 * <pre>
 * SLIDE_IN -> DISPLAY -> SLIDE_OUT -> COMPLETE
 * </pre>
 *
 * <p>control is released at the start of SLIDE_OUT, allowing the player to move
 * while the elements slide off-screen over the visible level.
 */
public enum Sonic1TitleCardState {
    /** All 4 elements are sliding toward their target positions at 16 px/frame */
    SLIDE_IN,

    /** All elements are at target positions, holding for 60 frames */
    DISPLAY,

    /** All elements slide back to start positions at 32 px/frame over the visible level */
    SLIDE_OUT,

    /** Animation complete */
    COMPLETE
}
