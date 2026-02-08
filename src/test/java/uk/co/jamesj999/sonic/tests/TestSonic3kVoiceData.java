package uk.co.jamesj999.sonic.tests;

import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencer;
import uk.co.jamesj999.sonic.audio.synth.VirtualSynthesizer;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import uk.co.jamesj999.sonic.game.sonic3k.audio.smps.Sonic3kSmpsData;
import uk.co.jamesj999.sonic.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Diagnostic tests for S3K voice (instrument) data correctness.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Global instrument table matches reference {@code InsSet.17D8.bin}</li>
 *   <li>SSG-EG values persist across {@code refreshInstrument()} calls</li>
 *   <li>SSG-EG values are cleared on voice change (EF command)</li>
 * </ol>
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kVoiceData {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private static final DacData EMPTY_DAC = new DacData(new HashMap<>(), new HashMap<>(), 297);

    /**
     * Verify that the loaded global instrument table matches the reference
     * {@code InsSet.17D8.bin} from the SMPS research pack.
     */
    @Test
    public void globalInstrumentTableMatchesReference() throws IOException {
        Path refPath = Path.of("docs/SMPS-rips/Sonic & Knuckles/InsSet.17D8.bin");
        org.junit.Assume.assumeTrue(
                "Reference InsSet.17D8.bin not available",
                Files.exists(refPath));

        Rom rom = romRule.rom();
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(rom);
        // Loading any music triggers Z80 driver decompression and table parsing
        loader.loadMusic(0x01); // AIZ1

        byte[] loaded = loader.getGlobalVoiceData();
        assertNotNull("Global voice data should be loaded", loaded);

        byte[] reference = Files.readAllBytes(refPath);
        assertTrue("Global voice data should have at least as many bytes as reference",
                loaded.length >= reference.length);

        // Compare voice-by-voice (25 bytes each)
        int voiceCount = reference.length / 25;
        for (int v = 0; v < voiceCount; v++) {
            int offset = v * 25;
            for (int b = 0; b < 25; b++) {
                int refByte = reference[offset + b] & 0xFF;
                int loadedByte = loaded[offset + b] & 0xFF;
                assertEquals(String.format("Voice %d byte %d (offset 0x%03X) mismatch",
                        v, b, offset + b), refByte, loadedByte);
            }
        }
    }

    // -----------------------------------------------------------------------
    // SSG-EG persistence tests (no ROM required for these — synthetic data)
    // -----------------------------------------------------------------------

    private static class FmWrite {
        final int port;
        final int reg;
        final int val;

        FmWrite(int port, int reg, int val) {
            this.port = port;
            this.reg = reg;
            this.val = val;
        }

        @Override
        public String toString() {
            return String.format("P%d R%02X V%02X", port, reg, val);
        }
    }

    private static class CaptureSynth extends VirtualSynthesizer {
        final List<FmWrite> fmWrites = new ArrayList<>();

        @Override
        public void writeFm(Object source, int port, int reg, int val) {
            fmWrites.add(new FmWrite(port, reg & 0xFF, val & 0xFF));
            super.writeFm(source, port, reg, val);
        }

        List<FmWrite> getSsgEgWrites() {
            List<FmWrite> result = new ArrayList<>();
            for (FmWrite w : fmWrites) {
                if (w.reg >= 0x90 && w.reg <= 0x9F) {
                    result.add(w);
                }
            }
            return result;
        }

        void clearCapture() {
            fmWrites.clear();
        }
    }

    /**
     * Verify that SSG-EG values set by FF 05 are restored after refreshInstrument().
     * This simulates the scenario where an SFX interrupts an FM channel and then
     * the music track is restored — refreshInstrument() must re-apply SSG-EG.
     */
    @Test
    public void ssgEgValuesRestoredAfterRefreshInstrument() {
        // FM track: load voice 0, set SSG-EG (FF 05 0A 0B 0C 0D), play a note, then stop
        byte[] fmTrack = {
                (byte) 0xEF, 0x00,                             // Load voice 0
                (byte) 0xFF, 0x05, 0x0A, 0x0B, 0x0C, 0x0D,   // SSG-EG: op1=0A, op2=0B, op3=0C, op4=0D
                (byte) 0x80, 0x10,                             // Rest for 0x10 ticks
                (byte) 0xF2                                     // Stop
        };
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);

        // Run a few frames to process the track through voice load + SSG-EG command
        short[] buffer = new short[4096];
        for (int i = 0; i < 5; i++) {
            seq.read(buffer);
        }

        // Find the FM track (channel != DAC)
        SmpsSequencer.Track fmTrackObj = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertNotNull("FM track should exist", fmTrackObj);

        // Verify SSG-EG values are stored in the track
        assertEquals("ssgEg[0] should be 0x0A", 0x0A, fmTrackObj.ssgEg[0]);
        assertEquals("ssgEg[1] should be 0x0B", 0x0B, fmTrackObj.ssgEg[1]);
        assertEquals("ssgEg[2] should be 0x0C", 0x0C, fmTrackObj.ssgEg[2]);
        assertEquals("ssgEg[3] should be 0x0D", 0x0D, fmTrackObj.ssgEg[3]);

        // Now simulate SFX restore: clear captures, call refreshInstrument()
        synth.clearCapture();
        seq.refreshInstrument(fmTrackObj);

        // Check that SSG-EG registers were re-written after setInstrument() cleared them
        List<FmWrite> ssgWrites = synth.getSsgEgWrites();

        // setInstrument() writes 0x00 to all 4 SSG-EG regs, then refreshInstrument()
        // restores non-zero values. We should see both the clear and the restore.
        // Extract the final values written to each SSG-EG register.
        Map<Integer, Integer> lastSsgValues = new HashMap<>();
        for (FmWrite w : ssgWrites) {
            lastSsgValues.put(w.reg, w.val);
        }

        // FM channel 1 = port 0, ch 1, so registers are 0x91, 0x95, 0x99, 0x9D
        int ch = fmTrackObj.channelId % 3;
        assertEquals("SSG-EG slot 0 should be restored to 0x0A",
                0x0A, (int) lastSsgValues.getOrDefault(0x90 + ch, 0));
        assertEquals("SSG-EG slot 1 should be restored to 0x0B",
                0x0B, (int) lastSsgValues.getOrDefault(0x94 + ch, 0));
        assertEquals("SSG-EG slot 2 should be restored to 0x0C",
                0x0C, (int) lastSsgValues.getOrDefault(0x98 + ch, 0));
        assertEquals("SSG-EG slot 3 should be restored to 0x0D",
                0x0D, (int) lastSsgValues.getOrDefault(0x9C + ch, 0));
    }

    /**
     * Verify that SSG-EG values are cleared when a new voice is loaded (EF command).
     * This prevents SSG-EG from bleeding between instruments.
     */
    @Test
    public void ssgEgClearedOnVoiceChange() {
        // FM track: load voice 0, set SSG-EG, then load voice 1 (which should clear SSG-EG)
        byte[] fmTrack = {
                (byte) 0xEF, 0x00,                             // Load voice 0
                (byte) 0xFF, 0x05, 0x0A, 0x0B, 0x0C, 0x0D,   // SSG-EG
                (byte) 0x80, 0x04,                             // Rest
                (byte) 0xEF, 0x01,                             // Load voice 1 — should clear SSG-EG
                (byte) 0x80, 0x04,                             // Rest
                (byte) 0xF2                                     // Stop
        };
        Sonic3kSmpsData smps = createMusicData(2, 0, fmTrack, null, null);
        CaptureSynth synth = new CaptureSynth();
        SmpsSequencer seq = new SmpsSequencer(smps, EMPTY_DAC, synth, Sonic3kSmpsSequencerConfig.CONFIG);

        // Run enough frames to process through voice change
        short[] buffer = new short[4096];
        for (int i = 0; i < 10; i++) {
            seq.read(buffer);
        }

        SmpsSequencer.Track fmTrackObj = findTrack(seq, SmpsSequencer.TrackType.FM);
        assertNotNull("FM track should exist", fmTrackObj);

        // After loading voice 1, SSG-EG should be cleared
        assertEquals("ssgEg[0] should be cleared after voice change", 0, fmTrackObj.ssgEg[0]);
        assertEquals("ssgEg[1] should be cleared after voice change", 0, fmTrackObj.ssgEg[1]);
        assertEquals("ssgEg[2] should be cleared after voice change", 0, fmTrackObj.ssgEg[2]);
        assertEquals("ssgEg[3] should be cleared after voice change", 0, fmTrackObj.ssgEg[3]);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static SmpsSequencer.Track findTrack(SmpsSequencer seq, SmpsSequencer.TrackType type) {
        for (SmpsSequencer.Track t : seq.getTracks()) {
            if (t.type == type) {
                return t;
            }
        }
        throw new AssertionError("Missing track type: " + type);
    }

    private static Sonic3kSmpsData createMusicData(int channels, int psgChannels,
            byte[] fmTrack, byte[] psgTrack, Map<Integer, byte[]> psgEnvelopes) {
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

        // Two local voices (25 bytes each)
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
        return smps;
    }

    private static void setLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
