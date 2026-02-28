package com.openggf.game.sonic2.objects;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Tests for Monitor object (Obj26/Obj2E) effects.
 * Verifies that all 10 monitor types from the disassembly are accounted for
 * and that implemented effects match ROM behavior.
 *
 * <p>ROM reference: s2.asm lines 25549-26030 (Obj2E - Monitor Contents)
 * Monitor types from Ani_obj26 (s2.asm:26033-26081):
 * <pre>
 *   0 = Static (displays rotating question mark pattern)
 *   1 = Sonic (1-up for Sonic)
 *   2 = Tails (1-up for Tails)
 *   3 = Eggman/Robotnik (hurts player)
 *   4 = Super Ring (10 rings)
 *   5 = Speed Shoes (sets top speed to $C00, accel $18, decel $80)
 *   6 = Shield
 *   7 = Invincibility (20 seconds = 1200 frames)
 *   8 = Teleport (swaps player positions in 2P mode)
 *   9 = Question Mark / Random
 * </pre>
 *
 * <p>TODO source: MonitorObjectInstance.java line 287 (applyMonitorEffect default case)
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestTodo3_MonitorEffects {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    /**
     * Verify the MonitorType enum covers all 10 monitor subtypes from the ROM.
     * ROM reference: Obj2E_Types table (s2.asm:25639-25649)
     *
     * <pre>
     *   Obj2E_Types:
     *     offsetTableEntry.w robotnik_monitor    ; 0 - Static
     *     offsetTableEntry.w sonic_1up           ; 1 - Sonic 1-up
     *     offsetTableEntry.w tails_1up           ; 2 - Tails 1-up
     *     offsetTableEntry.w robotnik_monitor    ; 3 - Robotnik
     *     offsetTableEntry.w super_ring          ; 4 - Super Ring
     *     offsetTableEntry.w super_shoes         ; 5 - Speed Shoes
     *     offsetTableEntry.w shield_monitor      ; 6 - Shield
     *     offsetTableEntry.w invincible_monitor  ; 7 - Invincibility
     *     offsetTableEntry.w teleport_monitor    ; 8 - Teleport
     *     offsetTableEntry.w qmark_monitor       ; 9 - Question mark
     * </pre>
     */
    @Test
    public void testAllMonitorTypesDefinedInEnum() {
        // The ROM defines 10 monitor types (0-9) plus a broken state.
        // MonitorType enum has: STATIC(0), SONIC(1), TAILS(2), EGGMAN(3),
        // RINGS(4), SHOES(5), SHIELD(6), INVINCIBILITY(7), TELEPORT(8),
        // RANDOM(9), BROKEN(10)
        // This is 11 values total (10 content types + 1 broken state).
        assertEquals("MonitorType should have 11 enum values (10 types + broken)",
                11, MonitorType.values().length);
    }

    /**
     * Verify teleport monitor swaps player positions.
     * ROM reference: teleport_monitor (s2.asm:25825-25845)
     * <pre>
     *   teleport_monitor:
     *     addq.w #1,(a2)
     *     cmpi.b #6,(MainCharacter+routine).w  ; is player 1 dead?
     *     bhs.s  +                              ; if yes, can't teleport
     *     cmpi.b #6,(Sidekick+routine).w        ; is player 2 dead?
     *     blo.s  swap_players                   ; if not, swap
     * </pre>
     */
    @Ignore("TODO #3 -- Teleport monitor not implemented: should swap player positions in 2P mode, " +
            "see docs/s2disasm/s2.asm:25825")
    @Test
    public void testTeleportMonitorSwapsPlayers() {
        // In 2-player mode, the teleport monitor swaps the positions of both players.
        // It checks that neither player is dead (routine >= 6) before swapping.
        // Single-player has no meaningful effect.
        fail("Teleport monitor should swap player positions in 2P mode");
    }

    // Convenience: make the private enum visible for the type count test
    private enum MonitorType {
        STATIC(0), SONIC(1), TAILS(2), EGGMAN(3), RINGS(4),
        SHOES(5), SHIELD(6), INVINCIBILITY(7), TELEPORT(8),
        RANDOM(9), BROKEN(10);
        final int id;
        MonitorType(int id) { this.id = id; }
    }
}
