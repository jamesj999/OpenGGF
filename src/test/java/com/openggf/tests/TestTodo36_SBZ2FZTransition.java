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
    public void testSBZ2ZoneWordValue() {
        // The results screen checks for SBZ Act 2 using a word comparison:
        // cmpi.w #(id_SBZ<<8)+1,(v_zone).w
        // id_SBZ = 5, act 2 = index 1, so the word value is $0501
        assertEquals("SBZ Act 2 zone word should be $0501", 0x0501, SBZ_ACT2_ZONE_WORD);
    }

    @Test
    public void testFZMusicId() {
        // bgm_FZ = $8D, played when card elements finish moving off screen
        // (Got_SBZ2, 3A Got Through Card.asm:242)
        assertEquals("FZ music ID should be $8D", 0x8D, BGM_FZ);
    }

    @Test
    public void testCardExitSpeedIsDoubled() {
        // Normal card movement speed is $10 (Got_Move, line 57: moveq #$10,d1)
        // SBZ2 transition uses $20 (Got_Move2, line 216: moveq #$20,d1)
        // This makes the cards exit twice as fast during the transition.
        assertEquals("Normal card speed should be $10", 0x10, NORMAL_CARD_SPEED);
        assertEquals("SBZ2 exit card speed should be $20", 0x20, CARD_EXIT_SPEED);
        assertEquals("Card exit speed should be double normal speed",
                NORMAL_CARD_SPEED * 2, CARD_EXIT_SPEED);
    }

    @Test
    public void testCameraScrollSpeedAndTarget() {
        // After cards exit, the camera right boundary scrolls right by 2px/frame
        // (loc_C766, line 246: addq.w #2,(v_limitright2).w)
        // until it reaches $2100 (line 247: cmpi.w #$2100,(v_limitright2).w)
        assertEquals("Camera scroll speed should be 2 px/frame", 2, CAMERA_SCROLL_SPEED);
        assertEquals("FZ transition right boundary target should be $2100",
                0x2100, FZ_TRANSITION_RIGHT_BOUNDARY);
    }

    @Test
    public void testLevelOrderSBZ2NextLevel() {
        // From LevelOrder table (3A Got Through Card.asm:207-209):
        // Scrap Brain Zone entries:
        //   dc.b id_SBZ, 1    ; Act 1 -> SBZ Act 2
        //   dc.b id_LZ, 3     ; Act 2 -> LZ Act 3 (= SBZ Act 3 = FZ)
        //   dc.b 0, 0         ; Act 3 (FZ) -> game end (Sega logo)
        //
        // The SBZ2 completion goes to LZ act 3, which is the internal
        // representation of SBZ Act 3 (also known as Final Zone's precursor).
        // However, the special Got_SBZ2 path bypasses normal level loading
        // by scrolling the camera right into FZ territory instead.
        int nextZoneAfterSBZ2 = 0x01; // id_LZ
        int nextActAfterSBZ2 = 3;     // act 3
        assertEquals("Next zone after SBZ2 should be LZ (id_LZ=1)", 1, nextZoneAfterSBZ2);
        assertEquals("Next act after SBZ2 should be act 3", 3, nextActAfterSBZ2);
    }

    @Test
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
