package uk.co.jamesj999.sonic.audio.smps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SmpsSequencerConfig {

    public enum TempoMode {
        /** Sonic 2/3K style: accumulator += tempo; tick when >= modBase (overflow). */
        OVERFLOW,
        /** Sonic 1 style: countdown from tempo; when 0, extend all track durations by 1. Always tick. */
        TIMEOUT
    }

    private final Map<Integer, Integer> speedUpTempos;
    private final int tempoModBase;
    private final int[] fmChannelOrder;
    private final int[] psgChannelOrder;
    private final TempoMode tempoMode;
    private final Map<Integer, Integer> coordFlagParamOverrides;

    /**
     * Constructor with all options including tempo mode and coord flag overrides.
     */
    public SmpsSequencerConfig(
            Map<Integer, Integer> speedUpTempos,
            int tempoModBase,
            int[] fmChannelOrder,
            int[] psgChannelOrder,
            TempoMode tempoMode,
            Map<Integer, Integer> coordFlagParamOverrides) {
        Objects.requireNonNull(speedUpTempos, "speedUpTempos");
        Objects.requireNonNull(fmChannelOrder, "fmChannelOrder");
        Objects.requireNonNull(psgChannelOrder, "psgChannelOrder");
        Objects.requireNonNull(tempoMode, "tempoMode");
        this.speedUpTempos = Collections.unmodifiableMap(new HashMap<>(speedUpTempos));
        this.tempoModBase = tempoModBase;
        this.fmChannelOrder = Arrays.copyOf(fmChannelOrder, fmChannelOrder.length);
        this.psgChannelOrder = Arrays.copyOf(psgChannelOrder, psgChannelOrder.length);
        this.tempoMode = tempoMode;
        this.coordFlagParamOverrides = (coordFlagParamOverrides != null)
                ? Collections.unmodifiableMap(new HashMap<>(coordFlagParamOverrides))
                : Collections.emptyMap();
    }

    /**
     * Backward-compatible constructor. Defaults to OVERFLOW tempo mode and no coord flag overrides.
     */
    public SmpsSequencerConfig(
            Map<Integer, Integer> speedUpTempos,
            int tempoModBase,
            int[] fmChannelOrder,
            int[] psgChannelOrder) {
        this(speedUpTempos, tempoModBase, fmChannelOrder, psgChannelOrder,
                TempoMode.OVERFLOW, null);
    }

    public Map<Integer, Integer> getSpeedUpTempos() {
        return speedUpTempos;
    }

    public int getTempoModBase() {
        return tempoModBase;
    }

    public int[] getFmChannelOrder() {
        return Arrays.copyOf(fmChannelOrder, fmChannelOrder.length);
    }

    public int[] getPsgChannelOrder() {
        return Arrays.copyOf(psgChannelOrder, psgChannelOrder.length);
    }

    public TempoMode getTempoMode() {
        return tempoMode;
    }

    /**
     * Returns overrides for coordination flag parameter lengths.
     * Keys are flag commands (0xE0-0xFF), values are the param length for that flag.
     * Only flags that differ from the default S2 table need to be present.
     */
    public Map<Integer, Integer> getCoordFlagParamOverrides() {
        return coordFlagParamOverrides;
    }
}
