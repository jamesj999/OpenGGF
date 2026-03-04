package com.openggf.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pure-Java WAV file decoder. Parses standard RIFF/WAVE PCM files
 * without requiring javax.sound.sampled, making it compatible with
 * GraalVM native-image builds.
 */
public class WavDecoder {

    public final int channels;
    public final int sampleRate;
    public final int bitsPerSample;
    public final byte[] data;

    private WavDecoder(int channels, int sampleRate, int bitsPerSample, byte[] data) {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.data = data;
    }

    /**
     * Decodes a WAV file from the given input stream.
     *
     * @param is the input stream containing WAV data
     * @return a WavDecoder with parsed audio parameters and PCM data
     * @throws IOException if the stream is not a valid WAV file or cannot be read
     */
    public static WavDecoder decode(InputStream is) throws IOException {
        byte[] all = is.readAllBytes();
        ByteBuffer buf = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        if (buf.remaining() < 12) {
            throw new IOException("WAV file too short for RIFF header");
        }
        int riffTag = buf.getInt();
        if (riffTag != 0x46464952) { // "RIFF" in little-endian
            throw new IOException("Not a RIFF file");
        }
        buf.getInt(); // file size - 8
        int waveTag = buf.getInt();
        if (waveTag != 0x45564157) { // "WAVE"
            throw new IOException("Not a WAVE file");
        }

        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        byte[] pcmData = null;

        // Parse chunks
        while (buf.remaining() >= 8) {
            int chunkId = buf.getInt();
            int chunkSize = buf.getInt();

            if (chunkId == 0x20746D66) { // "fmt "
                if (chunkSize < 16) {
                    throw new IOException("fmt chunk too small");
                }
                int audioFormat = buf.getShort() & 0xFFFF;
                if (audioFormat != 1) {
                    throw new IOException("Only PCM format (1) is supported, got: " + audioFormat);
                }
                channels = buf.getShort() & 0xFFFF;
                sampleRate = buf.getInt();
                buf.getInt();  // byte rate
                buf.getShort(); // block align
                bitsPerSample = buf.getShort() & 0xFFFF;
                // Skip any extra fmt bytes
                int extra = chunkSize - 16;
                if (extra > 0) {
                    buf.position(buf.position() + extra);
                }
            } else if (chunkId == 0x61746164) { // "data"
                pcmData = new byte[chunkSize];
                buf.get(pcmData);
            } else {
                // Skip unknown chunk
                buf.position(buf.position() + chunkSize);
            }
        }

        if (pcmData == null) {
            throw new IOException("No data chunk found in WAV file");
        }
        if (channels == 0) {
            throw new IOException("No fmt chunk found in WAV file");
        }

        return new WavDecoder(channels, sampleRate, bitsPerSample, pcmData);
    }
}
