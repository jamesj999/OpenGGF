package uk.co.jamesj999.sonic.game.sonic1.audio.smps;

import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.SmpsSfxData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SMPS 68k SFX header parser for Sonic 1.
 *
 * <p>S1 SFX header layout (from s1disasm):
 * <pre>
 *   0x00-0x01: Voice table pointer (16-bit BE, relative to SFX start)
 *   0x02:      Tick multiplier (dividing timing)
 *   0x03:      Track count
 *   0x04+:     Track headers (6 bytes each):
 *              [0] = playback control (usually 0x80)
 *              [1] = channel ID (0x02=FM3, 0x04=FM4, 0x05=FM5, 0x80=PSG1, 0xA0=PSG2, 0xC0=PSG3)
 *              [2-3] = data pointer (16-bit BE, relative to SFX start)
 *              [4] = transpose
 *              [5] = volume
 * </pre>
 *
 * <p>This is structurally identical to S2's SFX format except pointers are
 * big-endian instead of little-endian, and they are relative to the SFX start
 * rather than absolute Z80 addresses.
 */
public class Sonic1SfxData extends AbstractSmpsData implements SmpsSfxData {

    public static class TrackEntry implements SmpsSfxTrack {
        public final int playbackFlags;
        public final int channelMask;
        public final int transpose;
        public final int volume;
        public final int pointer;

        TrackEntry(int playbackFlags, int channelMask, int transpose, int volume, int pointer) {
            this.playbackFlags = playbackFlags;
            this.channelMask = channelMask;
            this.transpose = transpose;
            this.volume = volume;
            this.pointer = pointer;
        }

        @Override
        public int channelMask() {
            return channelMask;
        }

        @Override
        public int pointer() {
            return pointer;
        }

        @Override
        public int transpose() {
            return transpose;
        }

        @Override
        public int volume() {
            return volume;
        }
    }

    private List<TrackEntry> tracks = new ArrayList<>();
    private int tickMultiplier = 1;
    private byte[][] psgEnvelopes;

    public Sonic1SfxData(byte[] data, int z80StartAddress) {
        super(data, z80StartAddress);
    }

    public void setPsgEnvelopes(byte[][] psgEnvelopes) {
        this.psgEnvelopes = psgEnvelopes;
    }

    @Override
    public List<TrackEntry> getTrackEntries() {
        if (tracks == null || tracks.isEmpty()) {
            parseHeader();
        }
        return Collections.unmodifiableList(tracks);
    }

    @Override
    public int getTickMultiplier() {
        return tickMultiplier;
    }

    @Override
    protected void parseHeader() {
        if (tracks == null) {
            tracks = new ArrayList<>();
        } else {
            tracks.clear();
        }
        if (data.length < 4) {
            return;
        }

        // Voice pointer: 16-bit BE relative to SFX start
        int rawVoicePtr = read16(0);
        this.voicePtr = rawVoicePtr; // Already relative to data[0]

        this.tickMultiplier = data[2] & 0xFF;
        if (tickMultiplier == 0) {
            tickMultiplier = 1;
        }
        this.dividingTiming = tickMultiplier;
        this.tempo = 0; // SFX tick every frame

        int trackCount = data[3] & 0xFF;
        int pos = 4;
        int fmCount = 0;
        int psgCount = 0;
        boolean hasDac = false;

        // Track header layout (6 bytes each):
        // [0]=flags, [1]=channel ID, [2-3]=data pointer (BE), [4]=transpose, [5]=volume
        for (int i = 0; i < trackCount && pos + 5 < data.length; i++, pos += 6) {
            int flags = data[pos] & 0xFF;
            int channelId = data[pos + 1] & 0xFF;
            int ptr = read16(pos + 2); // BE relative pointer
            int transpose = (byte) data[pos + 4];
            int volume = (byte) data[pos + 5];

            tracks.add(new TrackEntry(flags, channelId, transpose, volume, ptr));

            if (channelId == 0x06) {
                // DAC channel in S1 (FM6/DAC)
                hasDac = true;
            } else if ((channelId & 0x80) != 0) {
                psgCount++;
            } else {
                fmCount++;
            }
        }

        this.channels = fmCount + (hasDac ? 1 : 0);
        this.psgChannels = psgCount;
    }

    @Override
    public byte[] getVoice(int voiceId) {
        int ptr = voicePtr;
        if (ptr == 0) {
            return null;
        }

        // S1 voice pointer is relative to SFX start (offset 0 in data[])
        int offset = ptr;
        if (offset < 0 || offset >= data.length) {
            return null;
        }

        int stride = 25;
        offset += (voiceId * stride);

        if (offset < 0 || offset + stride > data.length) {
            return null;
        }

        // S1 voices are already in the engine's expected operator order (1,3,2,4)
        byte[] voice = new byte[stride];
        System.arraycopy(data, offset, voice, 0, stride);
        return voice;
    }

    @Override
    public byte[] getPsgEnvelope(int id) {
        if (psgEnvelopes != null && id >= 0 && id < psgEnvelopes.length) {
            return psgEnvelopes[id];
        }
        return null;
    }

    @Override
    public int read16(int offset) {
        if (offset + 1 >= data.length) {
            return 0;
        }
        // Big Endian (68k byte order)
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    @Override
    public int getBaseNoteOffset() {
        return 0; // Sonic 1 uses base note C
    }

    @Override
    public int getPsgBaseNoteOffset() {
        return 0; // PSG base note C
    }
}
