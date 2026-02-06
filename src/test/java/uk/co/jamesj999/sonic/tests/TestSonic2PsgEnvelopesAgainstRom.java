package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.game.sonic2.audio.smps.Sonic2PsgEnvelopes;
import uk.co.jamesj999.sonic.game.sonic2.audio.smps.Sonic2SmpsLoader;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
public class TestSonic2PsgEnvelopesAgainstRom {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic2SmpsLoader loader;

    @Before
    public void setUp() {
        Rom rom = romRule.rom();
        loader = new Sonic2SmpsLoader(rom);
    }

    @Test
    public void testHardcodedEnvelopesMatchDataFromRom() {
        // Load any music entry to ensure loader extracts PSG envelopes from ROM.
        AbstractSmpsData data = loader.loadMusic(0x81); // Emerald Hill
        assertNotNull("Music data should load", data);

        for (int id = 1; ; id++) {
            byte[] expected = Sonic2PsgEnvelopes.getEnvelope(id);
            if (expected == null) break;
            byte[] fromRom = data.getPsgEnvelope(id);
            assertNotNull("Envelope " + id + " should exist in ROM-derived data", fromRom);
            assertArrayEquals("Envelope " + id + " should match ROM-derived data", expected, fromRom);
        }
    }
}
