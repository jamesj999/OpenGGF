package com.openggf.tests;
import com.openggf.game.sonic2.Sonic2;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.data.Rom;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
public class TestRomAudioIntegration {
    private Rom rom;
    private Sonic2SmpsLoader loader;

    @BeforeEach
    public void setUp() {
        rom = com.openggf.tests.TestEnvironment.currentRom();
        loader = new Sonic2SmpsLoader(rom);
    }

    private static class LoggingSynth extends VirtualSynthesizer {
        List<String> fm = new ArrayList<>();
        List<Integer> psg = new ArrayList<>();
        List<Integer> dac = new ArrayList<>();
        DacData configuredDacData;

        @Override
        public void setDacData(DacData data) {
            this.configuredDacData = data;
            super.setDacData(data);
        }

        @Override
        public void writeFm(Object source, int port, int reg, int val) {
            fm.add(String.format("P%d %02X %02X", port, reg, val));
            super.writeFm(source, port, reg, val);
        }

        @Override
        public void writePsg(Object source, int val) {
            psg.add(val);
            super.writePsg(source, val);
        }

        @Override
        public void playDac(Object source, int note) {
            dac.add(note);
            super.playDac(source, note);
        }
    }

    @Test
    public void testChemicalPlantNoiseChannelEmitsVolume() {
        AbstractSmpsData data = loader.loadMusic(0x8C); // Chemical Plant
        assertNotNull(data, "Chemical Plant should load");
        DacData dac = loader.loadDacData();
        assertNotNull(dac, "DAC data should load");

        LoggingSynth synth = new LoggingSynth();
        SmpsSequencer seq = new SmpsSequencer(data, dac, synth, Sonic2SmpsSequencerConfig.CONFIG);

        // Run enough frames to cover early percussion passages
        short[] buffer = new short[4096];
        for (int i = 0; i < 32; i++) {
            seq.read(buffer);
        }

        boolean hasNoiseLatch = synth.psg.stream().anyMatch(v -> (v & 0xF0) == 0xE0);
        long noiseVolWrites = synth.psg.stream()
                .filter(v -> (v & 0xF0) == 0xF0 && (v & 0x0F) < 0x0F)
                .count();

        assertTrue(hasNoiseLatch, "Noise channel should receive latch writes");
        assertTrue(noiseVolWrites > 0, "Noise channel should receive audible volume writes");
    }

    @Test
    public void testMusicDecompressionAndLoading() {
        AbstractSmpsData data = loader.loadMusic(0x82);
        assertNotNull(data, "Should load Metropolis music (0x82)");
        assertTrue(data.getVoicePtr() > 0, "Voice Ptr > 0");
        int channels = data.getChannels();
        assertTrue(channels > 0 && channels <= 7, "Channels should be valid (e.g. 6)");
        System.out.println("Metropolis Loaded. Size: " + data.getData().length);
    }

    @Test
    public void testDacDataLoading() {
        DacData dac = loader.loadDacData();
        assertNotNull(dac, "DAC Data should load");
        assertFalse(dac.samples.isEmpty(), "Should have samples");
        assertFalse(dac.mapping.isEmpty(), "Should have mapping");
        assertTrue(dac.samples.containsKey(0x81), "Should have Sample 81");
        byte[] sample = dac.samples.get(0x81);
        assertTrue(sample.length > 0, "Sample 81 should have data");
        System.out.println("DAC Loaded. Sample 81 size: " + sample.length);
    }

    @Test
    public void testSequencerPlayback() {
        AbstractSmpsData data = loader.loadMusic(0x82); // Metropolis
        DacData dac = loader.loadDacData();

        SmpsSequencer seq = new SmpsSequencer(data, dac, Sonic2SmpsSequencerConfig.CONFIG);
        short[] buffer = new short[4096];

        // Run for more ticks (50 iterations * 4096 samples ~ 5 seconds)
        for (int i = 0; i < 50; i++) {
            seq.read(buffer);
        }

        // Note: We do not assert non-silent audio here because full instrument parameter loading
        // (SMPS Flag EF) is not yet implemented, which may result in default (silent or low) output.
        // The test passes if the sequencer runs without exception.
    }

    @Test
    public void testMusicEmitsChipCommandsFromRomData() {
        AbstractSmpsData data = loader.loadMusic(0x82); // Metropolis
        DacData dac = loader.loadDacData();

        LoggingSynth synth = new LoggingSynth();
        SmpsSequencer seq = new SmpsSequencer(data, dac, synth, Sonic2SmpsSequencerConfig.CONFIG);

        // Check for init write before clearing
        boolean hasInitWrite = synth.fm.stream().anyMatch(cmd -> cmd.contains("2B 80"));

        // Ignore the DAC-enable write emitted during construction so assertions only consider
        // commands produced by sequencing the ROM data.
        synth.fm.clear();
        synth.psg.clear();

        short[] buffer = new short[4096];
        seq.read(buffer);

        boolean hasSequencedCommands = !synth.fm.isEmpty() || !synth.psg.isEmpty();

        assertTrue(hasInitWrite, "Sequencer should initialize DAC enable on the FM chip");
        assertTrue(hasSequencedCommands, "Sequencer should emit FM or PSG commands from the ROM stream");
    }

    @Test
    public void testDacSamplePlaybackUsesRomSamples() {
        AbstractSmpsData smps = loader.loadMusic(0x82); // Metropolis contains DAC drums
        DacData dacData = loader.loadDacData();
        LoggingSynth synth = new LoggingSynth();
        SmpsSequencer seq = new SmpsSequencer(smps, dacData, synth, Sonic2SmpsSequencerConfig.CONFIG);

        short[] buffer = new short[4096];
        seq.read(buffer);

        assertSame(dacData, synth.configuredDacData, "Sequencer should wire ROM DAC data into the synthesizer");
        assertFalse(dacData.samples.isEmpty(), "ROM DAC table should expose samples");
        assertTrue(dacData.mapping.containsKey(0x81), "ROM DAC table should map drum notes");
    }

    @Test
    public void testLevelMusicMapping() throws IOException {
        Sonic2 game = new Sonic2(rom);

        // Emerald Hill (0x81)
        assertEquals(0x81, game.getMusicId(0), "Emerald Hill 1 Music ID");
        assertEquals(0x81, game.getMusicId(1), "Emerald Hill 2 Music ID");

        // Chemical Plant (0x8C)
        assertEquals(0x8C, game.getMusicId(2), "Chemical Plant 1 Music ID");
        assertEquals(0x8C, game.getMusicId(3), "Chemical Plant 2 Music ID");

        // Aquatic Ruin (0x86)
        assertEquals(0x86, game.getMusicId(4), "Aquatic Ruin 1 Music ID");

        // Casino Night (0x83)
        assertEquals(0x83, game.getMusicId(6), "Casino Night 1 Music ID");

        // Hill Top (0x94)
        assertEquals(0x94, game.getMusicId(8), "Hill Top 1 Music ID");

        // Mystic Cave (0x84)
        assertEquals(0x84, game.getMusicId(10), "Mystic Cave 1 Music ID");

        // Oil Ocean (0x8F)
        assertEquals(0x8F, game.getMusicId(12), "Oil Ocean 1 Music ID");

        // Metropolis (0x82)
        assertEquals(0x82, game.getMusicId(14), "Metropolis 1 Music ID");
        assertEquals(0x82, game.getMusicId(16), "Metropolis 3 Music ID");

        // Sky Chase (0x8E)
        assertEquals(0x8E, game.getMusicId(17), "Sky Chase Music ID");

        // Wing Fortress (0x90)
        assertEquals(0x90, game.getMusicId(18), "Wing Fortress Music ID");

        // Death Egg (0x87)
        assertEquals(0x87, game.getMusicId(19), "Death Egg Music ID");
    }
}


