package uk.co.jamesj999.sonic.audio.smps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final boolean applyModOnNote;
    private final boolean halveModSteps;
    private final Set<Integer> extraTrkEndFlags;
    private final boolean relativePointers; // S1: true (68k PC-relative), S2: false (Z80 absolute)
    private final boolean tempoOnFirstTick; // S1: true (DOTEMPO), S2: false (PlayMusic)

    /**
     * Constructor with all options including tempo mode, coord flag overrides, and modulation settings.
     */
    public SmpsSequencerConfig(
            Map<Integer, Integer> speedUpTempos,
            int tempoModBase,
            int[] fmChannelOrder,
            int[] psgChannelOrder,
            TempoMode tempoMode,
            Map<Integer, Integer> coordFlagParamOverrides,
            boolean applyModOnNote,
            boolean halveModSteps,
            Set<Integer> extraTrkEndFlags,
            boolean relativePointers,
            boolean tempoOnFirstTick) {
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
        this.applyModOnNote = applyModOnNote;
        this.halveModSteps = halveModSteps;
        this.extraTrkEndFlags = (extraTrkEndFlags != null)
                ? Collections.unmodifiableSet(extraTrkEndFlags)
                : Collections.emptySet();
        this.relativePointers = relativePointers;
        this.tempoOnFirstTick = tempoOnFirstTick;
    }

    /**
     * Constructor without modulation/track-end overrides. Defaults to S2 behavior.
     */
    public SmpsSequencerConfig(
            Map<Integer, Integer> speedUpTempos,
            int tempoModBase,
            int[] fmChannelOrder,
            int[] psgChannelOrder,
            TempoMode tempoMode,
            Map<Integer, Integer> coordFlagParamOverrides) {
        this(speedUpTempos, tempoModBase, fmChannelOrder, psgChannelOrder,
                tempoMode, coordFlagParamOverrides, true, true, null, false, false);
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

    /**
     * Whether to apply modulation during note start (playNote).
     * S2 (ModAlgo 68k_a): true. S1 (ModAlgo 68k): false.
     */
    public boolean isApplyModOnNote() {
        return applyModOnNote;
    }

    /**
     * Whether to halve the modulation step count on load.
     * Z80 driver (S2): true (srl a). 68k driver (S1): false.
     */
    public boolean isHalveModSteps() {
        return halveModSteps;
    }

    /**
     * Returns coordination flag commands that should stop the track (TRK_END).
     * S1: includes 0xEE. S2: empty (0xEE is IGNORE/no-op).
     */
    public Set<Integer> getExtraTrkEndFlags() {
        return extraTrkEndFlags;
    }

    /**
     * Whether in-stream pointers (F6 Jump, F7 Loop, F8 Call) use PC-relative addressing.
     * S1 (68k): true — pointer value is signed offset from (ptrAddr + 1).
     * S2 (Z80): false — pointer value is absolute Z80 address, resolved via relocate().
     */
    public boolean isRelativePointers() {
        return relativePointers;
    }

    /**
     * Whether to process tempo on the very first frame.
     * S1 (DOTEMPO): true — first frame goes through processTempoFrame().
     * S2 (PlayMusic): false — first frame calls tick() directly, bypassing tempo.
     */
    public boolean isTempoOnFirstTick() {
        return tempoOnFirstTick;
    }
}
