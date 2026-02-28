package com.openggf.audio.smps;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DacData {
    public final Map<Integer, byte[]> samples;
    public final Map<Integer, DacEntry> mapping; // NoteID -> Entry
    public final int baseCycles; // Game-specific DAC base cycles (S1=301, S2=288, S3K=297)

    public DacData(Map<Integer, byte[]> samples, Map<Integer, DacEntry> mapping) {
        this(samples, mapping, 288); // Default to S2 value for backwards compatibility
    }

    public DacData(Map<Integer, byte[]> samples, Map<Integer, DacEntry> mapping, int baseCycles) {
        this.samples = Collections.unmodifiableMap(new HashMap<>(samples));
        this.mapping = Collections.unmodifiableMap(new HashMap<>(mapping));
        this.baseCycles = baseCycles;
    }

    public static class DacEntry {
        public final int sampleId;
        public final int rate;

        public DacEntry(int sampleId, int rate) {
            this.sampleId = sampleId;
            this.rate = rate;
        }
    }
}
