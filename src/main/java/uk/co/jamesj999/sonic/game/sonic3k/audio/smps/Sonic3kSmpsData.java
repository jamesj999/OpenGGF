package uk.co.jamesj999.sonic.game.sonic3k.audio.smps;

import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;

import java.util.Map;

/**
 * SMPS Z80 Type 2 music data parser for Sonic 3 &amp; Knuckles.
 *
 * <p>S3K header layout is identical to Sonic 2 (Z80 Type 2):
 * <pre>
 *   Offset 0x00: word  - Voice pointer (16-bit LE, Z80 address)
 *   Offset 0x02: byte  - FM channel count (includes DAC as first channel)
 *   Offset 0x03: byte  - PSG channel count
 *   Offset 0x04: byte  - Dividing timing
 *   Offset 0x05: byte  - Tempo value
 *   Offset 0x06+: DAC/FM track entries (4 bytes each):
 *     +0: word  - Data pointer (LE, Z80 address)
 *     +2: byte  - Transpose
 *     +3: byte  - Volume
 *   Then PSG channel entries (6 bytes each):
 *     +0: word  - Data pointer (LE, Z80 address)
 *     +2: byte  - Transpose
 *     +3: byte  - Volume
 *     +4: byte  - Modulation envelope
 *     +5: byte  - PSG volume envelope index
 * </pre>
 *
 * <p>Key differences from Sonic 2:
 * <ul>
 *   <li>Base note is C (offset 0) per DefDrv.txt: {@code FMBaseNote = C}</li>
 *   <li>InsMode=DEFAULT: voice operator order is Op4,Op3,Op2,Op1 (same as S1).
 *       {@link #getVoice(int)} swaps the middle two bytes in each 4-byte group
 *       to convert to the engine's expected S2 format (Op4,Op2,Op3,Op1).</li>
 *   <li>Some songs use a global instrument table rather than per-song voices.</li>
 * </ul>
 */
public class Sonic3kSmpsData extends AbstractSmpsData {

    private Map<Integer, byte[]> psgEnvelopes;
    private byte[] globalVoiceData;

    public Sonic3kSmpsData(byte[] data) {
        this(data, 0);
    }

    public Sonic3kSmpsData(byte[] data, int z80StartAddress) {
        super(data, z80StartAddress);
    }

    public void setPsgEnvelopes(Map<Integer, byte[]> psgEnvelopes) {
        this.psgEnvelopes = psgEnvelopes;
    }

    /**
     * Sets the global instrument table data.
     * Some S3K songs reference voices from this shared table rather than
     * from a per-song voice pointer.
     */
    public void setGlobalVoiceData(byte[] globalVoiceData) {
        this.globalVoiceData = globalVoiceData;
    }

    @Override
    protected void parseHeader() {
        if (data.length < 8) {
            return;
        }

        this.voicePtr = read16(0);
        this.channels = data[2] & 0xFF;
        this.psgChannels = data[3] & 0xFF;
        this.dividingTiming = data[4] & 0xFF;
        this.tempo = data[5] & 0xFF;
        this.dacPointer = read16(6);

        int fmStart = 0x06;
        this.fmPointers = new int[channels];
        this.fmKeyOffsets = new int[channels];
        this.fmVolumeOffsets = new int[channels];
        int offset = fmStart;
        for (int i = 0; i < channels; i++) {
            if (offset + 1 < data.length) {
                this.fmPointers[i] = read16(offset);
                this.fmKeyOffsets[i] = (byte) data[offset + 2];
                this.fmVolumeOffsets[i] = (byte) data[offset + 3];
            }
            offset += 4;
        }

        this.psgPointers = new int[psgChannels];
        this.psgKeyOffsets = new int[psgChannels];
        this.psgVolumeOffsets = new int[psgChannels];
        this.psgModEnvs = new int[psgChannels];
        this.psgInstruments = new int[psgChannels];
        for (int i = 0; i < psgChannels; i++) {
            if (offset + 5 < data.length) {
                this.psgPointers[i] = read16(offset);
                this.psgKeyOffsets[i] = (byte) data[offset + 2];
                this.psgVolumeOffsets[i] = (byte) data[offset + 3];
                this.psgModEnvs[i] = data[offset + 4] & 0xFF;
                this.psgInstruments[i] = data[offset + 5] & 0xFF;
            }
            offset += 6;
        }
    }

    @Override
    public byte[] getVoice(int voiceId) {
        int ptr = voicePtr;
        if (ptr == 0) {
            // Try global voice table if per-song pointer is absent
            return getGlobalVoice(voiceId);
        }

        int offset = -1;
        if (ptr >= 0 && ptr < data.length) {
            offset = ptr;
        } else if (z80StartAddress > 0) {
            int rel = ptr - z80StartAddress;
            if (rel >= 0 && rel < data.length) {
                offset = rel;
            }
        }

        if (offset < 0) {
            // Fall back to global voice table
            return getGlobalVoice(voiceId);
        }

        int stride = 25;
        offset += (voiceId * stride);

        if (offset < 0 || offset + stride > data.length) {
            return getGlobalVoice(voiceId);
        }

        byte[] voice = new byte[stride];
        System.arraycopy(data, offset, voice, 0, stride);

        // S3K InsMode=DEFAULT: operator order is Op4,Op3,Op2,Op1 per group.
        // Engine expects S2 order: Op4,Op2,Op3,Op1.
        // Swap positions [g+1] and [g+2] in each 4-byte group.
        for (int g = 1; g < 25; g += 4) {
            byte tmp = voice[g + 1];
            voice[g + 1] = voice[g + 2];
            voice[g + 2] = tmp;
        }
        return voice;
    }

    @Override
    public byte[] getPsgEnvelope(int id) {
        if (psgEnvelopes != null && psgEnvelopes.containsKey(id)) {
            return psgEnvelopes.get(id);
        }
        return null;
    }

    @Override
    public int read16(int offset) {
        if (offset + 1 >= data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8); // Little Endian
    }

    @Override
    public int getBaseNoteOffset() {
        return 0; // S3K uses base note C (DefDrv.txt: FMBaseNote = C)
    }

    @Override
    public int getPsgBaseNoteOffset() {
        return 0; // PSG base note C
    }

    /**
     * Look up a voice from the global instrument table.
     */
    private byte[] getGlobalVoice(int voiceId) {
        if (globalVoiceData == null) {
            return null;
        }

        int stride = 25;
        int offset = voiceId * stride;
        if (offset < 0 || offset + stride > globalVoiceData.length) {
            return null;
        }

        byte[] voice = new byte[stride];
        System.arraycopy(globalVoiceData, offset, voice, 0, stride);

        // Same operator order swap as per-song voices
        for (int g = 1; g < 25; g += 4) {
            byte tmp = voice[g + 1];
            voice[g + 1] = voice[g + 2];
            voice[g + 2] = tmp;
        }
        return voice;
    }
}
