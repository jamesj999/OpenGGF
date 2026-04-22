package com.openggf.game.sonic3k.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the CNZ Act 1 miniboss literal set against accidental drift.
 *
 * <p>ROM sources (all sonic3k.asm, S&K-side < 0x200000):
 * <ul>
 *   <li>line 144824: {@code move.w #$31E0,d0} — camera trigger X</li>
 *   <li>line 144832: {@code move.w #$1C0,(Camera_min_Y_pos).w} — arena min Y</li>
 *   <li>line 144834: {@code addi.w #$80,d0} — arena X span 0x31E0..0x3260</li>
 *   <li>line 144836: {@code move.w #$2B8,(Camera_max_Y_pos).w} — arena max Y</li>
 *   <li>line 144844: {@code moveq #$5D,d0} then {@code Load_PLC} — PLC id</li>
 *   <li>line 144888: {@code move.b #6,collision_property(a0)} — hit count</li>
 *   <li>line 144891: {@code move.w #$80,y_vel(a0)} — descent velocity</li>
 *   <li>line 144919: {@code move.w #$100,x_vel(a0)} — swing velocity magnitude</li>
 *   <li>line 144892: {@code move.w #$11F,$2E(a0)} — init wait</li>
 *   <li>line 144907: {@code move.w #$90,$2E(a0)} — go2 wait</li>
 *   <li>line 144920: {@code move.w #$9F,$2E(a0)} — go3 wait (swing duration)</li>
 *   <li>line 144937: {@code move.w #$13F,$2E(a0)} — direction-change wait</li>
 * </ul>
 */
class TestCnzMinibossConstants {

    @Test
    void plcIdMatchesRom() {
        assertEquals(0x5D, Sonic3kConstants.PLC_CNZ_MINIBOSS,
                "ROM sonic3k.asm:144844 reads `moveq #$5D,d0`");
    }

    @Test
    void arenaBounds() {
        assertEquals(0x31E0, Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        assertEquals(0x3260, Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_X);
        assertEquals(0x01C0, Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y);
        assertEquals(0x02B8, Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_Y);
    }

    @Test
    void stateMachineLiterals() {
        assertEquals(0x06,  Sonic3kConstants.CNZ_MINIBOSS_HIT_COUNT);
        assertEquals(0x80,  Sonic3kConstants.CNZ_MINIBOSS_INIT_Y_VEL);
        assertEquals(0x100, Sonic3kConstants.CNZ_MINIBOSS_SWING_X_VEL);
        assertEquals(0x11F, Sonic3kConstants.CNZ_MINIBOSS_INIT_WAIT);
        assertEquals(0x90,  Sonic3kConstants.CNZ_MINIBOSS_GO2_WAIT);
        assertEquals(0x9F,  Sonic3kConstants.CNZ_MINIBOSS_SWING_WAIT);
        assertEquals(0x13F, Sonic3kConstants.CNZ_MINIBOSS_CHANGEDIR_WAIT);
    }
}
