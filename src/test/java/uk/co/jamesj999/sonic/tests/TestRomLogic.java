package uk.co.jamesj999.sonic.tests;

import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Loads a ROM as Sonic2, checks if the game is compatible, reads and validates the checksum, then loads a level.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestRomLogic {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    @Test
    public void testRomLogic() throws IOException {
        Rom rom = romRule.rom();
        Game game = new Sonic2(rom);

        assertTrue(game.isCompatible());

        int storedChecksum = rom.readChecksum();
        int actualChecksum = rom.calculateChecksum();

        assertEquals(actualChecksum,storedChecksum);

        Level level = game.loadLevel(0);
        level.getBlockCount();
    }

}
