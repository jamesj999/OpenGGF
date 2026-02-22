package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.game.sonic2.audio.smps.Sonic2PsgEnvelopes;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

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
