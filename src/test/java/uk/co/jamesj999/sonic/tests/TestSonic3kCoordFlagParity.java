package uk.co.jamesj999.sonic.tests;

import org.junit.Test;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencer;
import uk.co.jamesj999.sonic.audio.synth.VirtualSynthesizer;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import uk.co.jamesj999.sonic.game.sonic3k.audio.smps.Sonic3kSmpsData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestSonic3kCoordFlagParity {

    private static final DacData EMPTY_DAC = new DacData(new HashMap<>(), new HashMap<>(), 297);

    private static class FmWrite {
        final int port;
        final int reg;
        final int val;

        FmWrite(int port, int reg, int val) {
            this.port = port;
            this.reg = reg;
            this.val = val;
        }
    }

    private static class CaptureSynth extends VirtualSynthesizer {
        final List<FmWrite> fmWrites = new ArrayList<>();
        final List<Integer> psgWrites = new ArrayList<>();

        @Override
        public void writeFm(Object source, int port, int reg, int val) {
            fmWrites.add(new FmWrite(port, reg & 0xFF, val & 0xFF));
            super.writeFm(source, port, reg, val);
        }

        @Override
        public void writePsg(Object source, int val) {
            psgWrites.add(val & 0xFF);
            super.writePsg(source, val);
        }
    }

    @Test
    public void e5UsesSecondParamAsFmVolumeDeltaAndDoesNotLoadVoice() {
        byte[] fmTrack = {
                (byte) 0xEF, 0x00,       // Load voice 0
                (byte) 0xE5, 0x7F, 0x01, // E5: first byte ignored, second byte = +1 volume
                (byte) 0xF2              // Stop
        };
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track fm = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertEquals("E5 must not switch instrument on S3K", 0, fm.voiceId);
        assertEquals("E5 must apply second byte as FM volume delta", 1, fm.volumeOffset);
    }

    @Test
    public void edSetsTransposeToParamMinus40() {
        byte[] fmTrack = {
                (byte) 0xED, 0x41, // key offset = +1
                (byte) 0xF2
        };
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track fm = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertEquals(1, fm.keyOffset);
    }

    @Test
    public void e4AppliesS3kAbsoluteVolumeScalingForFmAndPsg() {
        byte[] fmTrack = {
                (byte) 0xE4, 0x00, // FM -> 0x7F attenuation
                (byte) 0xF2
        };
        byte[] psgTrack = {
                (byte) 0xE4, 0x78, // PSG -> 0x00 attenuation
                (byte) 0xF2
        };
        Sonic3kSmpsData smps = createMusicData(2, 1, fmTrack, psgTrack, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track fm = findTrack(seq, SmpsSequencer.TrackType.FM);
        SmpsSequencer.Track psg = findTrack(seq, SmpsSequencer.TrackType.PSG);
        assertEquals(0x7F, fm.volumeOffset);
        assertEquals(0x00, psg.volumeOffset);
    }

    @Test
    public void ecUsesUnsignedClipBehaviorForPsgVolume() {
        byte[] psgTrack = {
                (byte) 0xEC, (byte) 0xFF, // 0 + (-1) -> 0xFF -> clipped to 0x0F
                (byte) 0xF2
        };
        Sonic3kSmpsData smps = createMusicData(2, 1, new byte[] { (byte) 0xF2 }, psgTrack, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track psg = findTrack(seq, SmpsSequencer.TrackType.PSG);
        assertEquals(0x0F, psg.volumeOffset);
    }

    @Test
    public void f3SetResetTogglesNoiseMode() {
        byte[] psgTrack = {
                (byte) 0xF3, (byte) 0xE7, // set noise
                (byte) 0xF3, 0x00,        // reset noise
                (byte) 0xF2
        };
        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(2, 1, new byte[] { (byte) 0xF2 }, psgTrack, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track psg = findTrack(seq, SmpsSequencer.TrackType.PSG);
        assertEquals(0, psg.psgNoiseParam);
        assertTrue("Expected PSG noise reset write", synth.psgWrites.contains(0xFF));
    }

    @Test
    public void fdRawFreqReadsWordAndAppliesRawFrequency() {
        byte[] fmTrack = {
                (byte) 0xFD, 0x01, // raw frequency mode on
                (byte) 0x84, 0x02, // packed frequency = 0x0284
                0x01,              // duration
                (byte) 0xF2
        };
        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        boolean wroteA4 = false;
        boolean wroteA0 = false;
        for (FmWrite w : synth.fmWrites) {
            if (w.reg == 0xA4 && w.val == 0x02) {
                wroteA4 = true;
            }
            if (w.reg == 0xA0 && w.val == 0x84) {
                wroteA0 = true;
            }
        }
        assertTrue("Raw frequency mode should write A4 from raw word", wroteA4);
        assertTrue("Raw frequency mode should write A0 from raw word", wroteA0);
    }

    @Test
    public void s3kUsesDefZ80Type2PsgTable() {
        byte[] psgTrack = {
                (byte) 0x81, 0x01, // first note
                (byte) 0xF2
        };
        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(1, 1, null, psgTrack, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        // DEF_Z80_T2 first entry is 0x3FF, so channel 0 writes should include 0x8F then 0x3F.
        assertTrue(synth.psgWrites.contains(0x8F));
        assertTrue(synth.psgWrites.contains(0x3F));
    }

    @Test
    public void psgNegativeTransposeClampsToLowestNoteInsteadOfSkipping() {
        byte[] psgTrack = {
                (byte) 0xFB, (byte) 0xFF, // transpose -1
                (byte) 0x81, 0x01,        // first note should clamp to lowest table entry
                (byte) 0xF2
        };
        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(1, 1, null, psgTrack, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        assertTrue("PSG note should still write frequency after negative transpose", synth.psgWrites.contains(0x8F));
        assertTrue("Lowest S3K PSG entry should write high byte 0x3F", synth.psgWrites.contains(0x3F));
    }

    @Test
    public void psgDetuneUnderflowWrapsTo10BitPeriod() {
        byte[] psgTrack = {
                (byte) 0xE1, (byte) 0xFF, // detune -1
                (byte) 0xD4, 0x01,        // high note -> base period 0x000 in DEF_Z80_T2 tail
                (byte) 0xF2
        };
        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(1, 1, null, psgTrack, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        assertTrue("Underflow should wrap to 0x3FF low nibble", synth.psgWrites.contains(0x8F));
        assertTrue("Underflow should wrap to 0x3FF high bits", synth.psgWrites.contains(0x3F));
    }

    @Test
    public void fbTransposeAddWrapsAsSignedByte() {
        byte[] fmTrack = {
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0,
                (byte) 0xFB, (byte) 0xF0, // 9 * (-16) = -144 -> wraps to +112
                (byte) 0xF2
        };
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track fm = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertEquals("S3K transpose add must wrap as signed byte", 112, fm.keyOffset);
    }

    @Test
    public void edTransposeSetWrapsAsSignedByte() {
        byte[] fmTrack = {
                (byte) 0xED, (byte) 0xFF, // 0xFF - 0x40 = 0xBF -> signed -65
                (byte) 0xF2
        };
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[20000]);

        SmpsSequencer.Track fm = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertEquals("S3K transpose set must wrap as signed byte", -65, fm.keyOffset);
    }

    @Test
    public void globalVoicePointerUsesGlobalVoiceTableInZ80AddressedSongs() {
        byte[] songData = new byte[0x1900];
        setLe16(songData, 0x00, 0x17D8); // Global voice table pointer in S3K Z80 RAM
        // If incorrectly treated as local, this marker would be returned.
        songData[0x17D8] = 0x11;

        Sonic3kSmpsData smps = new Sonic3kSmpsData(songData, 0x8000);
        byte[] globalVoices = new byte[25 * 2];
        globalVoices[0] = 0x55;
        smps.setGlobalVoiceData(globalVoices);

        byte[] voice = smps.getVoice(0);
        assertNotNull(voice);
        assertEquals("Expected global voice data, not in-song blob data", 0x55, voice[0] & 0xFF);
    }

    @Test
    public void localVoicePointerStillWorksForOffsetBasedData() {
        byte[] songData = new byte[0x200];
        setLe16(songData, 0x00, 0x0100);
        songData[0x0100] = 0x33;

        Sonic3kSmpsData smps = new Sonic3kSmpsData(songData, 0);
        byte[] globalVoices = new byte[25];
        globalVoices[0] = 0x77;
        smps.setGlobalVoiceData(globalVoices);

        byte[] voice = smps.getVoice(0);
        assertNotNull(voice);
        assertEquals("Offset-based data should keep using local voices", 0x33, voice[0] & 0xFF);
    }

    @Test
    public void ff06LoadsFmVolumeEnvelopeState() {
        byte[] fmTrack = {
                (byte) 0xFF, 0x06, 0x01, 0x01, // env id 1, op mask 1
                (byte) 0x81, 0x02,
                (byte) 0xF2
        };
        Map<Integer, byte[]> psgEnvs = new HashMap<>();
        psgEnvs.put(1, new byte[] { 0x03, (byte) 0x81 });
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, psgEnvs);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, new CaptureSynth(), Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        SmpsSequencer.Track fm = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertTrue("FM vol env should be loaded by FF 06", fm.fmVolEnvData != null && fm.fmVolEnvData.length > 0);
        assertEquals(0x01, fm.fmVolEnvOpMask);
    }

    @Test
    public void f4ModEnvelopeAppliesPitchDeltaOnPsg() {
        byte[] psgTrack = {
                (byte) 0xF4, 0x01, // load modulation envelope 1
                (byte) 0x90, 0x04,
                (byte) 0xF2
        };
        Map<Integer, byte[]> modEnvs = new HashMap<>();
        modEnvs.put(1, new byte[] { 0x01, (byte) 0x81 }); // +1 then HOLD

        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(1, 1, null, psgTrack, null, modEnvs);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        SmpsSequencer.Track psg = findTrack(seq, SmpsSequencer.TrackType.PSG);
        assertEquals("Mod envelope delta should be cached", 1, psg.modEnvCache);
        assertTrue("PSG pitch write should include modulation-adjusted low nibble", synth.psgWrites.contains(0x8F));
    }

    @Test
    public void modEnvelopeChangeMultiplierUsesZ80Formula() {
        byte[] psgTrack = {
                (byte) 0xF4, 0x01,
                (byte) 0x92, 0x04, // base period 0x280 in DEF_Z80_T2
                (byte) 0xF2
        };
        Map<Integer, byte[]> modEnvs = new HashMap<>();
        // 84 02 => mult = 2, Z80 formula uses (mult + 1), then value 01 => delta 3.
        modEnvs.put(1, new byte[] { (byte) 0x84, 0x02, 0x01, (byte) 0x81 });

        CaptureSynth synth = new CaptureSynth();
        Sonic3kSmpsData smps = createMusicData(1, 1, null, psgTrack, null, modEnvs);
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);
        seq.read(new short[25000]);

        SmpsSequencer.Track psg = findTrack(seq, SmpsSequencer.TrackType.PSG);
        assertEquals("CHG_MULT should affect modulation delta via Z80 (mult+1)", 3, psg.modEnvCache);
        assertTrue("PSG frequency should reflect +3 modulation delta", synth.psgWrites.contains(0x83));
    }

    private static Sonic3kSmpsData createMusicData(int channels, int psgChannels, byte[] fmTrack, byte[] psgTrack,
            Map<Integer, byte[]> psgEnvelopes) {
        return createMusicData(channels, psgChannels, fmTrack, psgTrack, psgEnvelopes, null);
    }

    private static Sonic3kSmpsData createMusicData(int channels, int psgChannels, byte[] fmTrack, byte[] psgTrack,
            Map<Integer, byte[]> psgEnvelopes, Map<Integer, byte[]> modEnvelopes) {
        byte[] data = new byte[0x240];
        setLe16(data, 0x00, 0x100); // voice table pointer
        data[0x02] = (byte) channels;
        data[0x03] = (byte) psgChannels;
        data[0x04] = 0x01; // dividing timing
        data[0x05] = (byte) 0x80; // tempo

        // FM/DAC entries (4 bytes each): ptr, transpose, volume
        for (int i = 0; i < channels; i++) {
            int off = 0x06 + (i * 4);
            if (i == 0) {
                setLe16(data, off, 0x80); // DAC track
                data[0x80] = (byte) 0xF2;
            } else if (i == 1 && fmTrack != null) {
                setLe16(data, off, 0x90);
                System.arraycopy(fmTrack, 0, data, 0x90, fmTrack.length);
            } else {
                setLe16(data, off, 0);
            }
        }

        int psgBase = 0x06 + (channels * 4);
        for (int i = 0; i < psgChannels; i++) {
            int off = psgBase + (i * 6);
            if (i == 0 && psgTrack != null) {
                setLe16(data, off, 0xA0);
                System.arraycopy(psgTrack, 0, data, 0xA0, psgTrack.length);
            } else {
                setLe16(data, off, 0);
            }
            data[off + 2] = 0;
            data[off + 3] = 0;
            data[off + 4] = 0;
            data[off + 5] = 0;
        }

        // Two local voices so EF/E5 bugs are observable.
        for (int i = 0; i < 25; i++) {
            data[0x100 + i] = 0x00;
            data[0x100 + 25 + i] = 0x20;
        }
        data[0x100] = 0x00;
        data[0x100 + 25] = 0x00;

        Sonic3kSmpsData smps = new Sonic3kSmpsData(data, 0);
        if (psgEnvelopes != null) {
            smps.setPsgEnvelopes(psgEnvelopes);
        }
        if (modEnvelopes != null) {
            smps.setModEnvelopes(modEnvelopes);
        }
        return smps;
    }

    private static SmpsSequencer.Track findTrack(SmpsSequencer seq, SmpsSequencer.TrackType type) {
        for (SmpsSequencer.Track t : seq.getTracks()) {
            if (t.type == type) {
                return t;
            }
        }
        throw new AssertionError("Missing track type: " + type);
    }

    private static void setLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
