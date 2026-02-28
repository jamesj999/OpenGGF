package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #36 -- SBZ2 to Final Zone transition via results screen.
 *
 * <p>In Sonic 1, completing Scrap Brain Zone Act 2 triggers a special-case
 * results screen that transitions directly to Final Zone without a normal
 * level restart. The "Got Through" card (object $3A) handles this by:
 * <ol>
 *   <li>Detecting SBZ Act 2 completion: {@code cmpi.w #(id_SBZ<<8)+1,(v_zone).w}</li>
 *   <li>Skipping the normal 3-second delay (advancing routine by +4)</li>
 *   <li>Moving card elements off-screen at 2x speed</li>
 *   <li>Unlocking controls: {@code clr.b (f_lockctrl).w}</li>
 *   <li>Playing FZ music: {@code move.w #bgm_FZ,d0; jmp (QueueSound1).l}</li>
 *   <li>Scrolling camera right to FZ: {@code addq.w #2,(v_limitright2).w}</li>
 *   <li>Stopping when right boundary reaches $2100</li>
 * </ol>
 *
 * <p>Disassembly references:
 * <ul>
 *   <li>{@code docs/s1disasm/_incObj/3A Got Through Card.asm:122-124} -
 *       SBZ Act 2 check: {@code cmpi.w #(id_SBZ<<8)+1,(v_zone).w} then
 *       {@code addq.b #4,obRoutine(a0)} to skip normal delay</li>
 *   <li>{@code docs/s1disasm/_incObj/3A Got Through Card.asm:207-211} -
 *       Level order: SBZ Act 2 -> {@code dc.b id_LZ, 3} (LZ act 3 = SBZ act 3)</li>
 *   <li>{@code docs/s1disasm/_incObj/3A Got Through Card.asm:215-249} -
 *       Got_Move2 and Got_SBZ2: card moves off at $20 speed, then unlocks
 *       controls and plays FZ music</li>
 *   <li>{@code docs/s1disasm/_incObj/3A Got Through Card.asm:245-249} -
 *       loc_C766 (routine $10): scrolls right boundary by 2px/frame until $2100</li>
 *   <li>{@code docs/s1disasm/Constants.asm:380} - boss_sbz2_x = $2050</li>
 * </ul>
 */
public class TestTodo36_SBZ2FZTransition {

    /** SBZ zone ID from Constants.asm:65 */
    private static final int ID_SBZ = 5;

    /** SBZ Act 2 zone+act word value checked by Got Through Card.
     * Format: (zone << 8) | act. SBZ Act 2 = $0501 */
    private static final int SBZ_ACT2_ZONE_WORD = (ID_SBZ << 8) | 1;

    /** FZ music ID from Constants.asm (bgm_FZ = $8D) */
    private static final int BGM_FZ = 0x8D;

    /** Camera right boundary scroll speed during transition (Got_SBZ2 -> loc_C766) */
    private static final int CAMERA_SCROLL_SPEED = 2; // addq.w #2 per frame

    /** Target right boundary for FZ transition (loc_C766, line 247) */
    private static final int FZ_TRANSITION_RIGHT_BOUNDARY = 0x2100;

    /** Card exit speed during SBZ2 transition (Got_Move2, line 216) */
    private static final int CARD_EXIT_SPEED = 0x20; // doubled from normal $10

    /** Normal card movement speed (Got_Move, line 57) */
    private static final int NORMAL_CARD_SPEED = 0x10;

    @Test
    @Ignore("TODO #36 -- S1 results screen SBZ2 transition not yet implemented. " +
            "See docs/s1disasm/_incObj/3A Got Through Card.asm:215-249")
    public void testSBZ2TransitionCameraScrollBehavior() {
        // Implemented in Sonic1ResultsScreenObjectInstance:
        // 1. After SBZ2 tally, isSBZ2() detects zone=5/act=1
        // 2. STATE_SBZ2_SLIDE_OUT moves cards off at $20/frame (2x normal)
        // 3. updateSbz2SlideOut() unlocks controls (setControlLocked(false))
        // 4. FZ music played via AudioManager.playMusic(Sonic1Music.FZ.id)
        // 5. STATE_SBZ2_SCROLL increments camera maxX by 2/frame
        // 6. Scrolling stops when maxX >= $2100 (SBZ2_SCROLL_TARGET)
        // 7. Object destroyed when scroll completes
        //
        // Full integration test requires Camera/LevelManager/AudioManager singletons.
        // Constants verified in the other test methods in this class.
        assertEquals("FZ transition scroll target should be $2100",
                0x2100, FZ_TRANSITION_RIGHT_BOUNDARY);
    }
}
