package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #35 -- control lockout (locktime / objoff_3E) in Sonic 1.
 *
 * <p>Sonic 1 uses a per-sprite timer at objoff_3E (named {@code locktime} in
 * the disassembly) to temporarily lock D-Pad input. This is used by:
 * <ul>
 *   <li>Water slides (5 frames when exiting slide mode)</li>
 *   <li>Horizontal springs (15 frames after bouncing)</li>
 *   <li>Air bubbles (35 frames while breathing)</li>
 *   <li>Slope repel (30 frames when Sonic is pushed off a steep slope)</li>
 * </ul>
 *
 * <p>When locktime is non-zero, Sonic ignores D-Pad input but the timer counts
 * down by 1 each frame. Separately, {@code f_lockctrl} locks ALL controls
 * (including jump) during cutscenes like SBZ2 transition and end sequences.
 *
 * <p>Disassembly references:
 * <ul>
 *   <li>{@code docs/s1disasm/Constants.asm:141} -
 *       {@code locktime: equ $3E} -- temporary D-Pad control lock timer (2 bytes)</li>
 *   <li>{@code docs/s1disasm/_inc/LZWaterFeatures.asm:415} -
 *       {@code move.w #5,locktime(a1)} -- 5-frame lockout on slide exit</li>
 *   <li>{@code docs/s1disasm/_incObj/41 Springs.asm:127} -
 *       {@code move.w #15,locktime(a1)} -- 15-frame lockout after horizontal spring</li>
 *   <li>{@code docs/s1disasm/_incObj/64 Bubbles.asm:90} -
 *       {@code move.w #35,locktime(a1)} -- 35-frame lockout while breathing</li>
 *   <li>{@code docs/s1disasm/_incObj/01 Sonic.asm:1126} -
 *       {@code move.w #30,locktime(a1)} -- 30-frame lockout on slope repel</li>
 *   <li>{@code docs/s1disasm/_incObj/01 Sonic.asm:316} -
 *       {@code tst.w locktime(a0)} -- check if input is locked</li>
 *   <li>{@code docs/s1disasm/_incObj/01 Sonic.asm:1133} -
 *       {@code subq.w #1,locktime(a0)} -- countdown timer</li>
 * </ul>
 */
public class TestTodo35_ControlLockout {

    /** locktime SST offset from Constants.asm:141 */
    private static final int LOCKTIME_OFFSET = 0x3E;

    /** Water slide exit lockout duration (LZWaterFeatures.asm:415) */
    private static final int WATER_SLIDE_LOCKOUT_FRAMES = 5;

    /** Horizontal spring lockout duration (41 Springs.asm:127) */
    private static final int SPRING_LOCKOUT_FRAMES = 15;

    /** Air bubble lockout duration (64 Bubbles.asm:90) */
    private static final int BUBBLE_LOCKOUT_FRAMES = 35;

    /** Slope repel lockout duration (01 Sonic.asm:1126) */
    private static final int SLOPE_REPEL_LOCKOUT_FRAMES = 30;

    @Test
    @Ignore("TODO #35 -- S1 locktime timer not yet implemented in sprite system. " +
            "See docs/s1disasm/_incObj/01 Sonic.asm:316, 1111, 1126, 1133")
    public void testLocktimeCountdownBehavior() {
        // When implemented, this test should use HeadlessTestRunner to verify:
        // 1. Set locktime to 5 (simulating water slide exit)
        // 2. Step 1 frame - locktime should be 4, D-Pad input ignored
        // 3. Step 4 more frames - locktime should be 0
        // 4. Step 1 more frame - D-Pad input should work again
        //
        // The countdown is in Sonic_SlopeRepel (01 Sonic.asm:1131-1134):
        //   loc_13582:
        //   subq.w #1,locktime(a0)
        //   rts
        fail("locktime timer not yet implemented in sprite system");
    }
}
