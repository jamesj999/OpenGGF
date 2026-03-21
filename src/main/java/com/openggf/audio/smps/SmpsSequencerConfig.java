package com.openggf.audio.smps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SmpsSequencerConfig {

    public enum TempoMode {
        /** S3K: accumulator overflow → skip (delay). Tick on non-overflow. Higher tempo = slower. */
        OVERFLOW,
        /** S2: accumulator overflow → tick. Skip on non-overflow. Higher tempo = faster. */
        OVERFLOW2,
        /** S1: countdown from tempo; when 0, extend all track durations by 1. Always tick. */
        TIMEOUT
    }

    /** How carrier operators are determined for volume scaling. */
    public enum VolMode {
        /** S1/S2: carrier mask derived from algorithm number via ALGO_OUT_MASK table. */
        ALGO,
        /** S3K: carrier operators identified by bit 7 set in the TL byte of the voice data. */
        BIT7
    }

    /** Behavior of PSG envelope command byte 0x80. */
    public enum PsgEnvCmd80 {
        /** S1/S2: hold the envelope at current level (stop advancing). */
        HOLD,
        /** S3K: reset the envelope index to 0 (loop from start). */
        RESET
    }

    /** How note-on is prevented during ties/holds. */
    public enum NoteOnPrevent {
        /** S1/S2: prevented when note is REST (0x80). */
        REST,
        /** S3K: prevented when HOLD flag is set. */
        HOLD
    }

    /** What happens to frequency during rests/delays. */
    public enum DelayFreq {
        /** S1/S2: frequency is reset on rest. */
        RESET,
        /** S3K: frequency persists through rests. */
        KEEP
    }

    /** Modulation stepping algorithm. */
    public enum ModAlgo {
        /** S1/S2 (MODALGO_68K): pre-check step counter, then decrement. Reload from raw data. */
        MOD_68K,
        /** S3K (MODALGO_Z80): post-decrement with 8-bit wrap, then check. Reload from raw data. */
        MOD_Z80
    }

    // -----------------------------------------------------------------------
    // Default constants shared across all three games (S1, S2, S3K)
    // -----------------------------------------------------------------------

    /** Default tempo modulation base (0x100). Same for S1, S2, and S3K. */
    public static final int DEFAULT_TEMPO_MOD_BASE = 0x100;

    /** Default FM channel order: DAC(0x16), FM1-FM6. Same for S1, S2, and S3K. */
    public static final int[] DEFAULT_FM_CHANNEL_ORDER = { 0x16, 0, 1, 2, 4, 5, 6 };

    /** Default PSG channel order: PSG1(0x80), PSG2(0xA0), PSG3(0xC0). Same for S1, S2, and S3K. */
    public static final int[] DEFAULT_PSG_CHANNEL_ORDER = { 0x80, 0xA0, 0xC0 };

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

    // --- S3K-specific config fields ---
    private final VolMode volMode;
    private final PsgEnvCmd80 psgEnvCmd80;
    private final NoteOnPrevent noteOnPrevent;
    private final DelayFreq delayFreq;
    private final CoordFlagHandler coordFlagHandler;
    private final ModAlgo modAlgo;
    private final int fadeOutDelay;
    private final int fadeOutSteps;
    private final int fadeInSteps;
    private final int fadeInDelay;

    /**
     * Private constructor used by the Builder. All fields are set here.
     */
    private SmpsSequencerConfig(Builder b) {
        this.speedUpTempos = Collections.unmodifiableMap(new HashMap<>(b.speedUpTempos));
        this.tempoModBase = b.tempoModBase;
        this.fmChannelOrder = Arrays.copyOf(b.fmChannelOrder, b.fmChannelOrder.length);
        this.psgChannelOrder = Arrays.copyOf(b.psgChannelOrder, b.psgChannelOrder.length);
        this.tempoMode = b.tempoMode;
        this.coordFlagParamOverrides = (b.coordFlagParamOverrides != null)
                ? Collections.unmodifiableMap(new HashMap<>(b.coordFlagParamOverrides))
                : Collections.emptyMap();
        this.applyModOnNote = b.applyModOnNote;
        this.halveModSteps = b.halveModSteps;
        this.extraTrkEndFlags = (b.extraTrkEndFlags != null)
                ? Collections.unmodifiableSet(b.extraTrkEndFlags)
                : Collections.emptySet();
        this.relativePointers = b.relativePointers;
        this.tempoOnFirstTick = b.tempoOnFirstTick;
        this.volMode = b.volMode;
        this.psgEnvCmd80 = b.psgEnvCmd80;
        this.noteOnPrevent = b.noteOnPrevent;
        this.delayFreq = b.delayFreq;
        this.coordFlagHandler = b.coordFlagHandler;
        this.modAlgo = b.modAlgo;
        this.fadeOutDelay = b.fadeOutDelay;
        this.fadeOutSteps = b.fadeOutSteps;
        this.fadeInSteps = b.fadeInSteps;
        this.fadeInDelay = b.fadeInDelay;
    }

    /**
     * Constructor with all options including tempo mode, coord flag overrides, and modulation settings.
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated(forRemoval = true)
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
        // Defaults for S3K-specific fields (S1/S2 compatible)
        this.volMode = VolMode.ALGO;
        this.psgEnvCmd80 = PsgEnvCmd80.HOLD;
        this.noteOnPrevent = NoteOnPrevent.REST;
        this.delayFreq = DelayFreq.RESET;
        this.coordFlagHandler = null;
        this.modAlgo = ModAlgo.MOD_68K;
        this.fadeOutDelay = 3;
        this.fadeOutSteps = 0x28;
        this.fadeInSteps = 0x28;
        this.fadeInDelay = 2;
    }

    /**
     * Constructor without modulation/track-end overrides. Defaults to S2 behavior.
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated(forRemoval = true)
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
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated(forRemoval = true)
    public SmpsSequencerConfig(
            Map<Integer, Integer> speedUpTempos,
            int tempoModBase,
            int[] fmChannelOrder,
            int[] psgChannelOrder) {
        this(speedUpTempos, tempoModBase, fmChannelOrder, psgChannelOrder,
                TempoMode.OVERFLOW2, null);
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

    /** Volume mode: ALGO (S1/S2) or BIT7 (S3K). */
    public VolMode getVolMode() {
        return volMode;
    }

    /** PSG envelope 0x80 command behavior: HOLD (S1/S2) or RESET (S3K). */
    public PsgEnvCmd80 getPsgEnvCmd80() {
        return psgEnvCmd80;
    }

    /** Note-on prevention mode: REST (S1/S2) or HOLD (S3K). */
    public NoteOnPrevent getNoteOnPrevent() {
        return noteOnPrevent;
    }

    /** Delay frequency behavior: RESET (S1/S2) or KEEP (S3K). */
    public DelayFreq getDelayFreq() {
        return delayFreq;
    }

    /** Game-specific coordination flag handler, or null for default S2 handling. */
    public CoordFlagHandler getCoordFlagHandler() {
        return coordFlagHandler;
    }

    /** Modulation stepping algorithm: MOD_68K (S1/S2) or MOD_Z80 (S3K). */
    public ModAlgo getModAlgo() {
        return modAlgo;
    }

    /** Fade-out inter-step delay in frames. S1/S2: 3, S3K: 6. */
    public int getFadeOutDelay() {
        return fadeOutDelay;
    }

    /** Fade-out total step count. S1/S2: 0x28, S3K: 0x28. */
    public int getFadeOutSteps() {
        return fadeOutSteps;
    }

    /** Fade-in total step count. S1/S2: 0x28, S3K: 0x40. */
    public int getFadeInSteps() {
        return fadeInSteps;
    }

    /** Fade-in inter-step delay in frames. S1/S2: 2, S3K: 2. */
    public int getFadeInDelay() {
        return fadeInDelay;
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    /**
     * Builder for SmpsSequencerConfig with S2-compatible defaults.
     * Use this for S3K and other configs that need the new fields.
     */
    public static final class Builder {
        // Required (defaults reference shared constants)
        private Map<Integer, Integer> speedUpTempos = Collections.emptyMap();
        private int tempoModBase = DEFAULT_TEMPO_MOD_BASE;
        private int[] fmChannelOrder = DEFAULT_FM_CHANNEL_ORDER;
        private int[] psgChannelOrder = DEFAULT_PSG_CHANNEL_ORDER;

        // S2-compatible defaults
        private TempoMode tempoMode = TempoMode.OVERFLOW2;
        private Map<Integer, Integer> coordFlagParamOverrides = null;
        private boolean applyModOnNote = true;
        private boolean halveModSteps = true;
        private Set<Integer> extraTrkEndFlags = null;
        private boolean relativePointers = false;
        private boolean tempoOnFirstTick = false;

        // S3K-specific defaults (S2 compatible)
        private VolMode volMode = VolMode.ALGO;
        private PsgEnvCmd80 psgEnvCmd80 = PsgEnvCmd80.HOLD;
        private NoteOnPrevent noteOnPrevent = NoteOnPrevent.REST;
        private DelayFreq delayFreq = DelayFreq.RESET;
        private CoordFlagHandler coordFlagHandler = null;
        private ModAlgo modAlgo = ModAlgo.MOD_68K;
        private int fadeOutDelay = 3;
        private int fadeOutSteps = 0x28;
        private int fadeInSteps = 0x28;
        private int fadeInDelay = 2;

        public Builder speedUpTempos(Map<Integer, Integer> val) { speedUpTempos = val; return this; }
        public Builder tempoModBase(int val) { tempoModBase = val; return this; }
        public Builder fmChannelOrder(int[] val) { fmChannelOrder = val; return this; }
        public Builder psgChannelOrder(int[] val) { psgChannelOrder = val; return this; }
        public Builder tempoMode(TempoMode val) { tempoMode = val; return this; }
        public Builder coordFlagParamOverrides(Map<Integer, Integer> val) { coordFlagParamOverrides = val; return this; }
        public Builder applyModOnNote(boolean val) { applyModOnNote = val; return this; }
        public Builder halveModSteps(boolean val) { halveModSteps = val; return this; }
        public Builder extraTrkEndFlags(Set<Integer> val) { extraTrkEndFlags = val; return this; }
        public Builder relativePointers(boolean val) { relativePointers = val; return this; }
        public Builder tempoOnFirstTick(boolean val) { tempoOnFirstTick = val; return this; }
        public Builder volMode(VolMode val) { volMode = val; return this; }
        public Builder psgEnvCmd80(PsgEnvCmd80 val) { psgEnvCmd80 = val; return this; }
        public Builder noteOnPrevent(NoteOnPrevent val) { noteOnPrevent = val; return this; }
        public Builder delayFreq(DelayFreq val) { delayFreq = val; return this; }
        public Builder coordFlagHandler(CoordFlagHandler val) { coordFlagHandler = val; return this; }
        public Builder modAlgo(ModAlgo val) { modAlgo = val; return this; }
        public Builder fadeOutDelay(int val) { fadeOutDelay = val; return this; }
        public Builder fadeOutSteps(int val) { fadeOutSteps = val; return this; }
        public Builder fadeInSteps(int val) { fadeInSteps = val; return this; }
        public Builder fadeInDelay(int val) { fadeInDelay = val; return this; }

        public SmpsSequencerConfig build() {
            Objects.requireNonNull(speedUpTempos, "speedUpTempos");
            Objects.requireNonNull(fmChannelOrder, "fmChannelOrder");
            Objects.requireNonNull(psgChannelOrder, "psgChannelOrder");
            Objects.requireNonNull(tempoMode, "tempoMode");
            return new SmpsSequencerConfig(this);
        }
    }
}
