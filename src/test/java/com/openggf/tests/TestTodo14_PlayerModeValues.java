package com.openggf.tests;

import com.openggf.game.PlayerCharacter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verify PlayerCharacter enum ordinals match S3K ROM Player_mode values.
 * ROM reference: docs/skdisasm/sonic3k.asm lines 8090-8101
 */
public class TestTodo14_PlayerModeValues {

    @Test
    public void testPlayerCharacterEnumOrdinalsMatchRom() {
        assertEquals("SONIC_AND_TAILS should be Player_mode 0",
                0, PlayerCharacter.SONIC_AND_TAILS.ordinal());
        assertEquals("SONIC_ALONE should be Player_mode 1",
                1, PlayerCharacter.SONIC_ALONE.ordinal());
        assertEquals("TAILS_ALONE should be Player_mode 2",
                2, PlayerCharacter.TAILS_ALONE.ordinal());
        assertEquals("KNUCKLES should be Player_mode 3",
                3, PlayerCharacter.KNUCKLES.ordinal());
    }

    @Test
    public void testPlayerCharacterCount() {
        assertEquals("Should have exactly 4 player modes",
                4, PlayerCharacter.values().length);
    }

}
