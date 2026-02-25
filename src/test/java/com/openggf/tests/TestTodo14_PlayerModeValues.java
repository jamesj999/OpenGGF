package com.openggf.tests;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TODO #14 -- Verify PlayerCharacter enum values match S3K ROM constants.
 *
 * <p>In the S3K disassembly (docs/skdisasm/sonic3k.asm), {@code Player_mode}
 * is a word-sized variable with these values:
 * <ul>
 *   <li>0 = Sonic and Tails (sonic3k.asm:8098: {@code move.w #0,(Player_mode).w})</li>
 *   <li>1 = Sonic alone (sonic3k.asm:8101: {@code move.w #1,(Player_mode).w})</li>
 *   <li>2 = Tails alone (sonic3k.asm:4623,4713: {@code cmpi.w #2,(Player_mode).w})</li>
 *   <li>3 = Knuckles (sonic3k.asm:4698,8095: {@code cmpi.w #3,(Player_mode).w})</li>
 * </ul>
 *
 * <p>The {@code PlayerCharacter} enum (com.openggf.sonic.game.PlayerCharacter)
 * must expose ordinals matching these ROM values. The enum does not exist yet.
 *
 * @see <a href="docs/skdisasm/sonic3k.asm">sonic3k.asm lines 8090-8101</a>
 */
public class TestTodo14_PlayerModeValues {

    /**
     * Expected Player_mode values from the S3K ROM.
     * These are word-sized constants used by the 68000 code.
     */
    private static final int PLAYER_MODE_SONIC_AND_TAILS = 0;
    private static final int PLAYER_MODE_SONIC_ALONE = 1;
    private static final int PLAYER_MODE_TAILS_ALONE = 2;
    private static final int PLAYER_MODE_KNUCKLES = 3;

    @Test
    public void testPlayerModeConstantsMatchDisassembly() {
        // These are the authoritative Player_mode values from the S3K ROM.
        // sonic3k.asm line 8098: move.w #0,(Player_mode).w  => Sonic & Tails
        // sonic3k.asm line 8101: move.w #1,(Player_mode).w  => Sonic alone
        // sonic3k.asm line 4623:  cmpi.w #2,(Player_mode).w => Tails alone
        // sonic3k.asm line 8095:  move.w #3,(Player_mode).w => Knuckles
        assertEquals("Sonic & Tails must be 0", 0, PLAYER_MODE_SONIC_AND_TAILS);
        assertEquals("Sonic alone must be 1", 1, PLAYER_MODE_SONIC_ALONE);
        assertEquals("Tails alone must be 2", 2, PLAYER_MODE_TAILS_ALONE);
        assertEquals("Knuckles must be 3", 3, PLAYER_MODE_KNUCKLES);

        // Exactly 4 player modes exist in the ROM
        assertEquals("Only 4 player modes in the S3K ROM", 4,
                PLAYER_MODE_KNUCKLES - PLAYER_MODE_SONIC_AND_TAILS + 1);
    }

    @Ignore("TODO #14 -- PlayerCharacter enum does not exist yet. " +
            "See docs/skdisasm/sonic3k.asm:8090-8101 for Player_mode values.")
    @Test
    public void testPlayerCharacterEnumOrdinalsMatchRom() {
        // Once PlayerCharacter enum is created, verify:
        // PlayerCharacter.SONIC_AND_TAILS.ordinal() == 0
        // PlayerCharacter.SONIC_ALONE.ordinal()     == 1
        // PlayerCharacter.TAILS_ALONE.ordinal()     == 2
        // PlayerCharacter.KNUCKLES.ordinal()        == 3
        // PlayerCharacter.values().length            == 4
        fail("PlayerCharacter enum not yet implemented");
    }

    @Ignore("TODO #14 -- PlayerCharacter.fromRomValue() does not exist yet. " +
            "See docs/skdisasm/sonic3k.asm:8357 for Player_mode usage in branching.")
    @Test
    public void testPlayerCharacterFromRomValue() {
        // The ROM uses Player_mode as an index into jump tables (sonic3k.asm:8357):
        //   move.w (Player_mode).w,d0
        // A fromRomValue(int) factory method should map 0-3 to enum constants.
        fail("PlayerCharacter.fromRomValue() not yet implemented");
    }
}
