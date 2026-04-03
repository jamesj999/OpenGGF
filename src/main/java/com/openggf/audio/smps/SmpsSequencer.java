package com.openggf.audio.smps;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioStream;
import com.openggf.audio.synth.Synthesizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class SmpsSequencer implements AudioStream, CoordFlagContext {
    private static final Logger LOGGER = Logger.getLogger(SmpsSequencer.class.getName());
    private final AbstractSmpsData smpsData;
    private AbstractSmpsData fallbackVoiceData;
    private final byte[] data;
    private final Synthesizer synth;
    private final SmpsSequencerConfig config;
    private final DacData dacData;
    private final int tempoModBase;
    private final List<Track> tracks = new ArrayList<>();

    public enum Region {
        NTSC(60.0), PAL(50.0);

        public final double frameRate;

        Region(double frameRate) {
            this.frameRate = frameRate;
        }
    }

    private Region region = Region.NTSC;
    private boolean speedShoes = false;
    private boolean sfxMode = false;
    private int normalTempo;
    private int commData = 0; // Communication byte (E2)
    private boolean fm6DacOff = false;
    private int maxTicks = Integer.MAX_VALUE;
    private float pitch = 1.0f;
    private int sfxPriority = 0x70; // Default SFX priority (Z80 driver uses 0x70 as common)
    private boolean specialSfx = false; // Driver-specific "special SFX" class (e.g. S1 0xD0+)
    private boolean isSfx = false; // Cached SFX status for performance (set by SmpsDriver.addSequencer)
    private int psgLatchChannel = -1; // Cached PSG latch channel for performance (set by SmpsDriver.writePsg)
    private int speedMultiplier = 1; // S3K: zTempoSpeedup value (0/1=off, 8=speed shoes)
    private int speedupTimeout = 0;  // S3K: zSpeedupTimeout countdown for double-update

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setSampleRate(double sampleRate) {
        if (sampleRate > 0.0) {
            this.sampleRate = sampleRate;
            this.samplesPerFrame = sampleRate / region.frameRate;
        }
    }

    public void setSfxPriority(int priority) {
        this.sfxPriority = priority;
    }

    public int getSfxPriority() {
        return sfxPriority;
    }

    public void setSpecialSfx(boolean specialSfx) {
        this.specialSfx = specialSfx;
    }

    public boolean isSpecialSfx() {
        return specialSfx;
    }

    /**
     * Mark this sequencer as SFX. Called by SmpsDriver when adding the sequencer.
     * This cached flag eliminates HashSet lookups in the hot path.
     */
    public void setIsSfx(boolean isSfx) {
        this.isSfx = isSfx;
    }

    /**
     * Returns true if this sequencer is playing SFX (not music).
     * Uses a cached field for O(1) lookup instead of HashSet.contains().
     */
    public boolean isSfx() {
        return isSfx;
    }

    /**
     * Set the cached PSG latch channel. Called by SmpsDriver.writePsg().
     * This eliminates HashMap lookups in the hot path.
     */
    public void setPsgLatchChannel(int channel) {
        this.psgLatchChannel = channel;
    }

    /**
     * Get the cached PSG latch channel.
     * @return the latch channel (0-3), or -1 if not latched
     */
    public int getPsgLatchChannel() {
        return psgLatchChannel;
    }

    /**
     * Set a callback to be invoked when a fade-in completes.
     * Used by JOALAudioBackend to clear sfxBlocked flag after override music restoration.
     */
    public void setOnFadeComplete(Runnable callback) {
        this.onFadeComplete = callback;
    }

    private static class FadeState {
        int steps;
        int delayInit;
        int delayCounter;
        int addFm;
        int addPsg;
        boolean active;
        boolean fadeOut; // true = Fade Out, false = Fade In
    }

    private final FadeState fadeState = new FadeState();
    private Runnable onFadeComplete;

    private double sampleRate = 44100.0;
    // Base tempo weight is game/driver-specific (configured externally).
    private double samplesPerFrame = 44100.0 / 60.0;
    private double sampleCounter = 0;
    private int tempoWeight;
    private int tempoAccumulator;
    private int dividingTiming = 1;
    private boolean primed;

    // Scratch buffer for read() to avoid per-sample allocations
    private final short[] scratchSample = new short[1];

    // Speed-up tempos and channel orders are game/driver-specific (configurable).

    // DEF_FMFREQ_68K - S1/S2 68K driver (FMBaseNote = B, FMBaseOctave = -1)
    private static final int[] FNUM_TABLE_68K = {
            606, 644, 683, 723, 766, 813, 860, 911, 965, 1023, 1084, 1148
    };
    // DEF_FMFREQ_Z80 - S3K Z80 driver (FMBaseNote = C, FMBaseOctave = 0)
    private static final int[] FNUM_TABLE_Z80 = {
            644, 683, 723, 766, 813, 860, 911, 965, 1023, 1084, 1148, 1216
    };
    // SMPSPlay DEF_PSGFREQ_68K table (register values). Slice from DEF_PSGFREQ_PRE
    // starting at index 12 (count 70).
    private static final int[] PSG_FREQ_TABLE_68K = {
            0x356, 0x326, 0x2F9, 0x2CE, 0x2A5, 0x280, 0x25C, 0x23A, 0x21A, 0x1FB, 0x1DF, 0x1C4,
            0x1AB, 0x193, 0x17D, 0x167, 0x153, 0x140, 0x12E, 0x11D, 0x10D, 0x0FE, 0x0EF, 0x0E2,
            0x0D6, 0x0C9, 0x0BE, 0x0B4, 0x0A9, 0x0A0, 0x097, 0x08F, 0x087, 0x07F, 0x078, 0x071,
            0x06B, 0x065, 0x05F, 0x05A, 0x055, 0x050, 0x04B, 0x047, 0x043, 0x040, 0x03C, 0x039,
            0x036, 0x033, 0x030, 0x02D, 0x02B, 0x028, 0x026, 0x024, 0x022, 0x020, 0x01F, 0x01D,
            0x01B, 0x01A, 0x018, 0x017, 0x016, 0x015, 0x013, 0x012, 0x011, 0x010
    };
    // SMPSPlay DEF_PSGFREQ_Z80_T2 table used by S3K (DefDrv: PSGFreqs=DEF_Z80_T2).
    private static final int[] PSG_FREQ_TABLE_Z80_T2 = {
            0x3FF, 0x3FF, 0x3FF, 0x3FF, 0x3FF, 0x3FF, 0x3FF, 0x3FF, 0x3FF, 0x3F7, 0x3BE, 0x388,
            0x356, 0x326, 0x2F9, 0x2CE, 0x2A5, 0x280, 0x25C, 0x23A, 0x21A, 0x1FB, 0x1DF, 0x1C4,
            0x1AB, 0x193, 0x17D, 0x167, 0x153, 0x140, 0x12E, 0x11D, 0x10D, 0x0FE, 0x0EF, 0x0E2,
            0x0D6, 0x0C9, 0x0BE, 0x0B4, 0x0A9, 0x0A0, 0x097, 0x08F, 0x087, 0x07F, 0x078, 0x071,
            0x06B, 0x065, 0x05F, 0x05A, 0x055, 0x050, 0x04B, 0x047, 0x043, 0x040, 0x03C, 0x039,
            0x036, 0x033, 0x030, 0x02D, 0x02B, 0x028, 0x026, 0x024, 0x022, 0x020, 0x01F, 0x01D,
            0x01B, 0x01A, 0x018, 0x017, 0x016, 0x015, 0x013, 0x012, 0x011, 0x010, 0x000, 0x000
    };

    // Carrier bitmask per YM2612 algorithm in YM operator order (Op1, Op2, Op3, Op4).
    // Algo output mask from SMPSPlay is in slot/register order (40/44/48/4C = slots 1/2/3/4).
    // Our SMPS operator order is Op1, Op3, Op2, Op4, so we convert masks once here.
    private static final int[] ALGO_OUT_MASK_SLOT = { 0x08, 0x08, 0x08, 0x08, 0x0C, 0x0E, 0x0E, 0x0F };
    private static final int[] ALGO_OUT_MASK = toSmpsOrderMask(ALGO_OUT_MASK_SLOT);

    private static int[] toSmpsOrderMask(int[] slotMasks) {
        int[] out = new int[slotMasks.length];
        for (int i = 0; i < slotMasks.length; i++) {
            int mask = slotMasks[i];
            int smps = 0;
            if ((mask & 0x01) != 0) smps |= 1; // slot1 -> op1
            if ((mask & 0x04) != 0) smps |= 1 << 1; // slot3 -> op3
            if ((mask & 0x02) != 0) smps |= 1 << 2; // slot2 -> op2
            if ((mask & 0x08) != 0) smps |= 1 << 3; // slot4 -> op4
            out[i] = smps;
        }
        return out;
    }

    public enum TrackType {
        FM, PSG, DAC
    }

    public static class Track {
        public int pos;
        public TrackType type;
        public int channelId;
        public int duration;
        public int note;
        public boolean active = true;
        public boolean overridden = false; // Set if SFX stole the channel
        public int rawDuration;
        public int scaledDuration;
        public int fill; // note-off shortening in ticks
        public int keyOffset; // signed semitone displacement (E9)
        public int volumeOffset; // attenuation applied to TL (FM) or volume (PSG)
        public boolean tieNext; // E7 prevents next attack
        public int pan = 0xC0; // default L+R bits set for YM (E0)
        public int ams = 0;
        public int fms = 0;
        public byte[] voiceData; // last loaded voice
        // Scratch buffer for voice data modification (avoids allocation in refreshInstrument)
        public final byte[] voiceScratch = new byte[25];
        public int voiceId;
        public int baseFnum;
        public int baseBlock;
        public int[] loopCounters = new int[8]; // Increased from 4 to reduce runtime reallocation
        public int loopTarget = -1;
        // Z80 driver: Stack shares space with loop counters, grows down from offset 0x2A.
        // No hard limit but collision possible after ~5 calls. Using 16 for safety margin.
        public final int[] returnStack = new int[16];
        public int returnSp = 0;
        public int dividingTiming = 1;
        // Modulation (F0)
        public int modDelay;
        public int modDelayInit;
        public int modRate;
        public int modDelta;
        public int modSteps;
        public int modStepsFull;
        public int modRateCounter;
        public int modStepCounter;
        public short modAccumulator;
        public int modCurrentDelta;
        public boolean modEnabled;
        public boolean customModEnabled;
        public int detune;
        public int modEnvId;
        public byte[] modEnvData;
        public int modEnvPos;
        public int modEnvMult;
        public int modEnvCache;
        public boolean modEnvHold;
        public boolean rawFreqMode;
        public int rawFrequency;
        public int instrumentId;
        public boolean noiseMode;
        public int psgNoiseParam;
        public int decayOffset;
        public int decayTimer;
        // PSG Volume Envelope
        public byte[] envData;
        public int envPos;
        public int envValue;
        public boolean envHold;
        public boolean envAtRest;
        // S3K FF 06: FM volume envelope (envelope ID + operator mask).
        public byte[] fmVolEnvData;
        public int fmVolEnvPos;
        public int fmVolEnvValue;
        public boolean fmVolEnvHold;
        public int fmVolEnvOpMask;
        public boolean forceRefresh;
        // SSG-EG per-operator state (S3K FF 05). Preserved across refreshInstrument() calls
        // because setInstrument() unconditionally clears SSG-EG registers (0x90-0x9C).
        public final int[] ssgEg = new int[4];
        // DAC mute state for fade-in
        public boolean dacMuted;

        // Mutable result fields for stepCustomModulation() – avoids per-tick allocation
        boolean modStepInEffect;
        boolean modStepChanged;
        int modStepDelta;

        // Mutable result fields for stepModEnvelope() – avoids per-tick allocation
        boolean modEnvStepInEffect;
        boolean modEnvStepChanged;
        int modEnvStepDelta;

        Track(int pos, TrackType type, int channelId) {
            this.pos = pos;
            this.type = type;
            this.channelId = channelId;
        }
    }

    public SmpsSequencer(AbstractSmpsData smpsData, DacData dacData, SmpsSequencerConfig config) {
        this(smpsData, dacData, new VirtualSynthesizer(), config);
    }

    public SmpsSequencer(AbstractSmpsData smpsData, DacData dacData, Synthesizer synth,
            SmpsSequencerConfig config) {
        this.smpsData = smpsData;
        this.data = smpsData.getData();
        this.synth = synth;
        this.config = Objects.requireNonNull(config, "config");
        this.tempoModBase = this.config.getTempoModBase();
        this.dacData = dacData;
        this.synth.setDacData(dacData);

        // Enable DAC (YM2612 Reg 2B = 0x80)
        synth.writeFm(this, 0, 0x2B, 0x80);

        dividingTiming = smpsData.getDividingTiming();
        if (dividingTiming == 0) {
            dividingTiming = 1;
        }
        normalTempo = smpsData.getTempo();

        // Initialize Region and Tempo
        setRegion(Region.NTSC);

        int z80Start = smpsData.getZ80StartAddress();

        if (smpsData instanceof SmpsSfxData sfxData) {
            initSfxTracks(sfxData, z80Start);
            setSfxMode(true);
            return;
        }

        int[] fmPointers = smpsData.getFmPointers();
        int[] psgPointers = smpsData.getPsgPointers();

        // FM tracks mapping
        int[] fmOrder = config.getFmChannelOrder();
        int[] psgOrder = config.getPsgChannelOrder();

        for (int i = 0; i < fmPointers.length; i++) {
            int chnVal = (i < fmOrder.length) ? fmOrder[i] : -1;

            // 0x16 or 0x10 is DAC
            if (chnVal == 0x16 || chnVal == 0x10) {
                // DAC Track
                int ptr = relocate(fmPointers[i], z80Start);
                if (ptr >= 0 && ptr < data.length) {
                    Track t = new Track(ptr, TrackType.DAC, 5); // DAC uses channel 5 (FM6) slot
                    t.dividingTiming = dividingTiming;
                    tracks.add(t);
                }
                continue;
            }

            // FM Channel
            int linearCh = mapFmChannel(chnVal);
            if (linearCh >= 0) {
                int ptr = relocate(fmPointers[i], z80Start);
                if (ptr < 0 || ptr >= data.length) {
                    continue;
                }
                Track t = new Track(ptr, TrackType.FM, linearCh);
                int[] fmKeys = smpsData.getFmKeyOffsets();
                int[] fmVols = smpsData.getFmVolumeOffsets();
                if (i < fmKeys.length) {
                    t.keyOffset = (byte) fmKeys[i];
                }
                if (i < fmVols.length) {
                    t.volumeOffset = fmVols[i];
                }
                t.dividingTiming = dividingTiming;
                loadVoice(t, 0); // default instrument
                tracks.add(t);
            }
        }

        // PSG tracks mapping
        for (int i = 0; i < psgPointers.length; i++) {
            int ptr = relocate(psgPointers[i], z80Start);
            if (ptr < 0 || ptr >= data.length) {
                continue;
            }

            int chnVal = (i < psgOrder.length) ? psgOrder[i] : -1;
            int linearCh = mapPsgChannel(chnVal);
            if (linearCh < 0) {
                // Fallback for extra channels (like Noise if mapped linearly)
                linearCh = i;
            }

            Track t = new Track(ptr, TrackType.PSG, linearCh);
            int[] psgKeys = smpsData.getPsgKeyOffsets();
            int[] psgVols = smpsData.getPsgVolumeOffsets();
            int[] psgMods = smpsData.getPsgModEnvs();
            int[] psgInsts = smpsData.getPsgInstruments();
            if (i < psgKeys.length) {
                t.keyOffset = (byte) psgKeys[i];
            }
            if (i < psgVols.length) {
                t.volumeOffset = psgVols[i];
            }
            if (i < psgMods.length) {
                t.modEnvId = psgMods[i];
                if (t.modEnvId != 0) {
                    t.modEnvData = smpsData.getModEnvelope(t.modEnvId);
                    t.modEnabled = t.modEnvData != null;
                }
            }
            if (i < psgInsts.length) {
                t.instrumentId = psgInsts[i];
                loadPsgEnvelope(t, t.instrumentId);
            }
            t.dividingTiming = dividingTiming;
            tracks.add(t);
        }
    }

    @Override
    public AbstractSmpsData getSmpsData() {
        return smpsData;
    }

    public DacData getDacData() {
        return dacData;
    }

    /**
     * Optional: provide another SMPS data set (usually the currently playing music)
     * to supply instrument voices if this sequence has no local voice table.
     */
    public void setFallbackVoiceData(AbstractSmpsData fallbackVoiceData) {
        this.fallbackVoiceData = fallbackVoiceData;
    }

    /**
     * Force-silence a hardware channel that was previously owned by this sequencer.
     * Used by the driver when releasing SFX locks so stray tones don't linger
     * if there is no music track to immediately rewrite the channel.
     * <p>
     * For FM channels, this matches ROM behavior (zSetMaxRelRate + zFMSilenceChannel):
     * - Set D1L/RR to 0xFF for all operators (fastest release)
     * - Set TL to 0x7F for all operators (max attenuation)
     * - Key off
     * This ensures the envelope is fully silenced before music refreshes the channel,
     * preventing corrupted first samples when music resumes.
     */
    public void forceSilence(TrackType type, int channelId) {
        if (type == TrackType.FM) {
            int port = (channelId < 3) ? 0 : 1;
            int ch = channelId % 3;
            int chVal = (port == 0) ? ch : (ch + 4);

            // ROM: zSetMaxRelRate - Set D1L/RR to 0xFF (fastest release) for all operators
            // Register 0x80-0x8F: D1L/RR (Sustain Level / Release Rate)
            // Operator register offsets: 0x00 (Op1), 0x08 (Op3), 0x04 (Op2), 0x0C (Op4)
            int[] opOffsets = {0x00, 0x08, 0x04, 0x0C};
            for (int opOffset : opOffsets) {
                synth.writeFm(this, port, 0x80 + opOffset + ch, 0xFF);
            }

            // ROM: zFMSilenceChannel - Set TL to 0x7F (max attenuation) for all operators
            // Register 0x40-0x4F: TL (Total Level)
            for (int opOffset : opOffsets) {
                synth.writeFm(this, port, 0x40 + opOffset + ch, 0x7F);
            }

            // Key off
            synth.writeFm(this, 0, 0x28, chVal);

            if (channelId == 5) {
                // If this was DAC (FM6), stop DAC playback too.
                synth.stopDac(this);
            }
        } else if (type == TrackType.PSG) {
            int ch = Math.max(0, Math.min(3, channelId));
            synth.writePsg(this, 0x80 | (ch << 5) | (1 << 4) | 0x0F); // volume -> silence
        } else if (type == TrackType.DAC) {
            synth.stopDac(this);
        }
    }

    public void setRegion(Region region) {
        this.region = region;
        this.samplesPerFrame = sampleRate / region.frameRate;
        calculateTempo();
    }

    public void setSpeedShoes(boolean active) {
        this.speedShoes = active;
        calculateTempo();
    }

    public void setFm6DacOff(boolean active) {
        this.fm6DacOff = active;
    }

    public void setSfxMode(boolean active) {
        this.sfxMode = active;
        int div = smpsData.getDividingTiming();
        if (smpsData instanceof SmpsSfxData sfxData) {
            div = sfxData.getTickMultiplier();
        }
        if (div == 0) {
            div = 1;
        }
        if (active) {
            updateDividingTiming(div);
        } else {
            updateDividingTiming(smpsData.getDividingTiming());
        }
        // SFX tick every tempo frame; keep frame pacing tied to region to avoid
        // double-speed playback.
        this.samplesPerFrame = sampleRate / region.frameRate;
        calculateTempo();

        // Safety: cap SFX to a reasonable tick budget so bad data doesn't hang forever.
        if (active) {
            this.maxTicks = 2048;
        } else {
            this.maxTicks = Integer.MAX_VALUE;
        }
    }

    public void setChannelOverridden(TrackType type, int channelId, boolean overridden) {
        for (Track t : tracks) {
            if (t.type == type && t.channelId == channelId) {
                boolean wasOverridden = t.overridden;
                t.overridden = overridden;
                if (wasOverridden && !overridden) {
                    if (!t.active)
                        continue;

                    // Channel released from SFX, restore instrument and volume
                    refreshInstrument(t);
                    if (t.type == TrackType.PSG) {
                        refreshVolume(t);
                    }
                    if (t.type == TrackType.FM) {
                        applyFmPanAmsFms(t);
                        // Ensure channel is keyed-off after restore to prevent clicks/pops.
                        // The music track's note was interrupted by SFX; it should remain
                        // silent until the next note event naturally keys-on.
                        int hwCh = t.channelId;
                        int port = (hwCh < 3) ? 0 : 1;
                        int ch = hwCh % 3;
                        int chVal = (port == 0) ? ch : (ch + 4);
                        synth.writeFm(this, 0, 0x28, chVal);
                    }
                    if (t.duration > 0) {
                        restoreFrequency(t);
                    }
                }
            }
        }
    }

    private void restoreFrequency(Track t) {
        if (t.type == TrackType.PSG) {
            boolean noiseUsesTone2 = t.noiseMode && t.channelId == 2 && (t.psgNoiseParam & 0x03) == 0x03;
            boolean writeToneFreq = t.channelId < 3 && (!t.noiseMode || noiseUsesTone2);

            if (writeToneFreq) {
                int reg = t.baseFnum + t.modAccumulator + t.detune;
                if (pitch != 1.0f) {
                    reg = (int) (reg / pitch);
                }
                reg = normalizePsgPeriod(reg);

                int data = reg & 0xF;
                int ch = t.channelId;
                synth.writePsg(this, 0x80 | (ch << 5) | (0) | data);
                synth.writePsg(this, (reg >> 4) & 0x3F);
            }

            if (t.noiseMode) {
                synth.writePsg(this, 0xE0 | (t.psgNoiseParam & 0x0F));
            }
            return;
        }

        if (t.type != TrackType.FM)
            return;

        int packed = (t.baseBlock << 11) | t.baseFnum;
        packed += t.modAccumulator + t.detune;

        if (pitch != 1.0f) {
            int b = (packed >> 11) & 7;
            int f = packed & 0x7FF;
            f = (int) (f * pitch);
            while (f > 0x7FF && b < 7) {
                f >>= 1;
                b++;
            }
            packed = (b << 11) | (f & 0x7FF);
        }

        int block = (packed >> 11) & 7;
        int fnum = packed & 0x7FF;

        int hwCh = t.channelId;
        int port = (hwCh < 3) ? 0 : 1;
        int ch = (hwCh % 3);
        writeFmFreq(port, ch, fnum, block);
    }

    public List<Track> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    /** Adds a track to this sequencer's track list. */
    public void addTrack(Track track) {
        tracks.add(track);
    }

    private void calculateTempo() {
        if (sfxMode) {
            this.tempoWeight = config.getTempoModBase(); // 0x100: Tick every frame
            return;
        }

        int base = normalTempo;

        if (speedShoes) {
            base = config.getSpeedUpTempos().getOrDefault(smpsData.getId(), base);
        }

        double multiplier = 1.0;
        if (region == Region.PAL && !smpsData.isPalSpeedupDisabled()) {
            multiplier = 1.2; // Compensate 50Hz by speeding up music
        }

        int weighted = (int) (base * multiplier);
        if (weighted > 0xFF)
            weighted = 0xFF;

        this.tempoWeight = weighted;

        // For TIMEOUT mode, initialize the countdown accumulator to the tempo value
        if (config.getTempoMode() == SmpsSequencerConfig.TempoMode.TIMEOUT && tempoAccumulator == 0) {
            tempoAccumulator = tempoWeight;
        }
    }

    private int mapFmChannel(int val) {
        return switch (val) {
            case 0 -> 0; // FM1
            case 1 -> 1; // FM2
            case 2 -> 2; // FM3
            case 4 -> 3; // FM4
            case 5 -> 4; // FM5
            case 6 -> 5; // FM6
            default -> -1;
        };
    }

    private int mapPsgChannel(int val) {
        return switch (val) {
            case 0x80 -> 0;
            case 0xA0 -> 1;
            case 0xC0 -> 2;
            default -> -1;
        };
    }

    private void initSfxTracks(SmpsSfxData sfxData, int z80Start) {
        int tickMult = sfxData.getTickMultiplier();
        if (tickMult <= 0) {
            tickMult = 1;
        }
        updateDividingTiming(tickMult);

        for (SmpsSfxData.SmpsSfxTrack entry : sfxData.getTrackEntries()) {
            int ptr = relocate(entry.pointer(), z80Start);
            if (ptr < 0 || ptr >= data.length) {
                continue;
            }

            int chnVal = entry.channelMask();
            TrackType type;
            int linearCh;

            if (chnVal == 0x16 || chnVal == 0x10) {
                type = TrackType.DAC;
                linearCh = 5;
            } else {
                int fmCh = mapFmChannel(chnVal);
                if (fmCh >= 0) {
                    type = TrackType.FM;
                    linearCh = fmCh;
                } else {
                    int psgCh = mapPsgChannel(chnVal);
                    if (psgCh < 0) {
                        continue;
                    }
                    type = TrackType.PSG;
                    linearCh = psgCh;
                }
            }

            Track t = new Track(ptr, type, linearCh);
            t.keyOffset = (byte) entry.transpose();
            t.volumeOffset = entry.volume();
            t.dividingTiming = tickMult;
            if (type == TrackType.FM) {
                // SFX should not inherit music state; center pan/AMS/FMS and preload voice 0 if
                // available.
                t.pan = 0xC0;
                t.ams = 0;
                t.fms = 0;
                loadVoice(t, 0);
                applyFmPanAmsFms(t);
            }
            tracks.add(t);
        }
    }

    private int relocate(int ptr, int z80Start) {
        if (ptr == 0)
            return -1;
        // Many Sonic 2 SMPS blobs use file-relative offsets already.
        if (ptr >= 0 && ptr < data.length) {
            return ptr;
        }
        if (z80Start > 0) {
            int offset = ptr - z80Start;
            if (offset >= 0 && offset < data.length) {
                return offset;
            }
        }
        return -1;
    }

    @Override
    public int read(short[] buffer) {
        if (!primed) {
            if (config.isTempoOnFirstTick()) {
                if (tempoWeight != 0) {
                    processTempoFrame(); // S1/S3K: process tempo on first frame (DOTEMPO)
                } else {
                    // Tempo-0 songs (e.g. S3K Title Screen) need an unconditional first tick
                    // so their FF 00 (TEMPO_SET) command can execute and set the real tempo.
                    tick();
                }
            } else {
                if (tempoWeight != 0) {
                    tick(); // S2: skip tempo on first frame (PlayMusic)
                }
            }
            primed = true;
        }

        if (tempoWeight == 0 && config.getTempoMode() == SmpsSequencerConfig.TempoMode.OVERFLOW2) {
            return buffer.length;
        }

        for (int i = 0; i < buffer.length; i++) {
            advance(1.0);
            if (synth instanceof VirtualSynthesizer) {
                ((VirtualSynthesizer) synth).render(scratchSample);
            }
            buffer[i] = scratchSample[0];
        }
        return buffer.length;
    }

    public void advance(double samples) {
        sampleCounter += samples;
        while (sampleCounter >= samplesPerFrame) {
            sampleCounter -= samplesPerFrame;
            processTempoFrame();
        }
    }

    /**
     * Advance by multiple samples at once. More efficient than calling advance(1) repeatedly.
     * Processes tempo frames as needed.
     *
     * @param samples Number of samples to advance
     */
    public void advanceBatch(int samples) {
        sampleCounter += samples;
        while (sampleCounter >= samplesPerFrame) {
            sampleCounter -= samplesPerFrame;
            processTempoFrame();
        }
    }

    /**
     * Calculate the number of samples until the next tempo frame boundary.
     * This determines the maximum safe batch size that won't cross a tempo event.
     *
     * @return Number of samples until next tempo frame, or Integer.MAX_VALUE if no tempo
     */
    public int getSamplesUntilNextTempoFrame() {
        if (tempoWeight == 0 || samplesPerFrame <= 0) {
            return Integer.MAX_VALUE;
        }
        double remaining = samplesPerFrame - sampleCounter;
        if (remaining <= 0) {
            return 0;
        }
        return (int) Math.ceil(remaining);
    }

    private void tick() {
        for (Track t : tracks) {
            if (!t.active)
                continue;
            // Note: In SMPS, overridden tracks continue to process (tick) in the
            // background,
            // but their output is blocked (or overwritten) by the SFX.
            // SmpsDriver blocks the writes if locked.

            if (t.duration > 0) {
                t.duration--;

                if (t.fill > 0 && (t.scaledDuration - t.duration) >= t.fill && !t.tieNext
                        && t.type != TrackType.DAC) {
                    stopNote(t);
                }

                if (t.duration > 0) {
                    // Z80 driver order: PSG envelope (zPSGUpdateVolFX) THEN modulation (zDoModulation)
                    if (t.type == TrackType.PSG) {
                        processPsgEnvelope(t);
                    } else if (t.type == TrackType.FM) {
                        processFmVolEnvelope(t);
                    }
                    // Skip modulation if track is at rest (0x80). Matches Z80 driver zDoModulation check.
                    if ((t.type == TrackType.FM || t.type == TrackType.PSG) && t.modEnabled && t.note != 0x80) {
                        applyModulation(t);
                    }
                    continue;
                }
            }

            while (t.duration == 0 && t.active) {
                if (t.pos >= data.length) {
                    t.active = false;
                    break;
                }

                int cmd = data[t.pos] & 0xFF;

                if (cmd >= 0xE0) {
                    t.pos++;
                    handleFlag(t, cmd);
                    // Re-check bounds after handleFlag as it may have modified t.pos
                    if (t.pos < 0 || t.pos >= data.length) {
                        if (t.active) { // Only stop if still supposedly active
                            t.active = false;
                        }
                        break;
                    }
                } else if (t.rawFreqMode) {
                    if (t.pos + 1 >= data.length) {
                        t.active = false;
                        break;
                    }
                    int freq = (data[t.pos] & 0xFF) | ((data[t.pos + 1] & 0xFF) << 8);
                    t.pos += 2;
                    if (freq != 0) {
                        freq = (freq + t.keyOffset) & 0xFFFF;
                    }
                    t.rawFrequency = freq;
                    t.note = (freq == 0) ? 0x80 : 0x81;
                    if (t.pos < data.length) {
                        int next = data[t.pos] & 0xFF;
                        if (next < 0x80) {
                            setDuration(t, next);
                            t.pos++;
                        } else {
                            reuseDuration(t);
                        }
                    } else {
                        reuseDuration(t);
                    }
                    playRawFrequency(t);
                    break;
                } else if (cmd >= 0x80) {
                    t.pos++;
                    t.note = cmd;
                    if (t.pos < data.length) {
                        int next = data[t.pos] & 0xFF;
                        if (next < 0x80) {
                            setDuration(t, next);
                            t.pos++;
                        } else {
                            reuseDuration(t);
                        }
                    }
                    playNote(t);
                    break;
                } else {
                    t.pos++;
                    // 0x00 is not a valid SMPS command/note in standard mode.
                    if (cmd == 0x00) {
                        t.active = false;
                        break;
                    }
                    setDuration(t, cmd);
                    playNote(t);
                    break;
                }
            }

            if (!t.active) {
                stopNote(t);
            }
        }
    }

    private void processTempoFrame() {
        if (tempoWeight == 0 && config.getTempoMode() == SmpsSequencerConfig.TempoMode.OVERFLOW2) {
            return;
        }
        if (config.getTempoMode() == SmpsSequencerConfig.TempoMode.TIMEOUT) {
            // S1 style: always tick, but periodically extend track durations
            // SFX bypass tempo extension entirely - they just tick every frame
            if (!sfxMode) {
                tempoAccumulator--;
                if (tempoAccumulator <= 0) {
                    tempoAccumulator = tempoWeight;
                    // Extend all active music track durations by 1
                    for (Track t : tracks) {
                        if (t.active && t.duration > 0) {
                            t.duration++;
                        }
                    }
                }
            }
            processFade();
            tick();
            if (sfxMode) {
                maxTicks--;
                if (maxTicks <= 0) {
                    for (Track t : tracks) {
                        t.active = false;
                        stopNote(t);
                    }
                }
            }
        } else if (config.getTempoMode() == SmpsSequencerConfig.TempoMode.OVERFLOW2) {
            // S2: tick when accumulator overflows. Higher tempo = more ticks = faster.
            tempoAccumulator += tempoWeight;
            if (tempoAccumulator >= tempoModBase) {
                tempoAccumulator -= tempoModBase;
                processFade();
                tick();
                for (int m = 1; m < speedMultiplier; m++) {
                    processFade();
                    tick();
                }
                if (sfxMode) {
                    maxTicks--;
                    if (maxTicks <= 0) {
                        for (Track t : tracks) {
                            t.active = false;
                            stopNote(t);
                        }
                    }
                }
            }
        } else {
            // S3K OVERFLOW: tick when accumulator does NOT overflow. Higher tempo = more skips = slower.
            // SFX bypass: Z80 driver processes SFX every frame (zUpdateSFXTracks),
            // independent of music tempo. Without this, tempoWeight=tempoModBase
            // causes overflow every frame → SFX never ticks.
            if (sfxMode) {
                processFade();
                tick();
                maxTicks--;
                if (maxTicks <= 0) {
                    for (Track t : tracks) {
                        t.active = false;
                        stopNote(t);
                    }
                }
            } else {
                tempoAccumulator += tempoWeight;
                if (tempoAccumulator >= tempoModBase) {
                    tempoAccumulator -= tempoModBase;
                    // Overflow → skip this frame (delay)
                } else {
                    // No overflow → tick normally
                    processFade();
                    tick();
                }
                // S3K speed-up: timeout-based double update (ROM: zDoSpeedUp)
                // zTempoSpeedup=N → one extra zUpdateMusic every (N+1) frames.
                if (speedMultiplier > 1) {
                    if (speedupTimeout <= 0) {
                        speedupTimeout = speedMultiplier;
                        // Re-run tempo processing for the extra update
                        tempoAccumulator += tempoWeight;
                        if (tempoAccumulator >= tempoModBase) {
                            tempoAccumulator -= tempoModBase;
                        } else {
                            processFade();
                            tick();
                        }
                    } else {
                        speedupTimeout--;
                    }
                }
            }
        }
    }

    private void processFade() {
        if (!fadeState.active) {
            return;
        }

        // ROM: Check if fade counter is already 0 BEFORE processing
        // This happens after all steps have been applied
        if (fadeState.steps == 0) {
            if (fadeState.fadeOut) {
                // Stop all tracks
                for (Track t : tracks) {
                    t.active = false;
                    stopNote(t);
                }
            } else {
                // Fade In complete - unmute DAC tracks
                for (Track t : tracks) {
                    if (t.type == TrackType.DAC) {
                        t.dacMuted = false;
                    }
                }
                // Notify listener that fade-in is complete (e.g., to unblock SFX)
                Runnable callback = onFadeComplete;
                if (callback != null) {
                    callback.run();
                }
            }
            fadeState.active = false;
            return;
        }

        // ROM: Check delay counter, decrement and return if not yet 0
        if (fadeState.delayCounter > 0) {
            fadeState.delayCounter--;
            return;
        }

        // ROM: Decrement fade counter and apply volume change
        fadeState.steps--;
        fadeState.delayCounter = fadeState.delayInit;

        int dir = fadeState.fadeOut ? 1 : -1;

        for (Track t : tracks) {
            if (!t.active)
                continue;
            // Skip DAC tracks - they don't have volume control
            if (t.type == TrackType.DAC)
                continue;

            int add = (t.type == TrackType.PSG) ? fadeState.addPsg : fadeState.addFm;
            int change = add * dir;

            int prevOffset = t.volumeOffset;
            t.volumeOffset += change;
            if (fadeState.fadeOut) {
                // SMPSPlay smps.c:3467-3476 - clamp on signed overflow (bit-7 toggle)
                if ((t.volumeOffset & 0x80) != 0 && (prevOffset & 0x80) == 0) {
                    t.volumeOffset = 0x7F;
                }
            }
            refreshVolume(t);
        }
    }

    // handleFlag and other private methods...
    private void handleFlag(Track t, int cmd) {
        // Delegate to game-specific handler first (e.g., S3K coord flags)
        CoordFlagHandler handler = config.getCoordFlagHandler();
        if (handler != null && handler.handleFlag(this, t, cmd)) {
            return;
        }

        switch (cmd) {
            case 0xF2: // Stop
                t.active = false;
                stopNote(t);
                break;
            case 0xE3: // Return
                handleReturn(t);
                break;
            case 0xF6: // Jump
                handleJump(t);
                break;
            case 0xF7: // Loop
                handleLoop(t);
                break;
            case 0xF8: // Call
                handleCall(t);
                break;
            case 0xF9: // SND_OFF
                handleSndOff(t);
                break;
            case 0xF0: // Modulation
                handleModulation(t);
                break;
            case 0xF1: // Modulation on
                t.customModEnabled = true;
                t.modEnabled = true;
                break;
            case 0xE0: // Pan
                setPanAmsFms(t);
                break;
            case 0xE1: // Detune
                setDetune(t);
                break;
            case 0xE2: // Set Communication (E2 xx)
                if (t.pos < data.length) {
                    commData = data[t.pos++] & 0xFF;
                }
                break;
            case 0xE4: // Fade in (Stop Track / Fade In)
                handleFadeIn(t);
                break;
            case 0xFD: // Custom Fade Out command for testing/internal use
                handleFadeOut(t);
                break;
            case 0xE5: // Tick multiplier
                setTrackDividingTiming(t);
                break;
            case 0xE6: // Volume
                setVolumeOffset(t);
                break;
            case 0xE7: // Tie next
                t.tieNext = true;
                break;
            case 0xE8: // Note fill
                setFill(t);
                break;
            case 0xE9: // Key displacement
                setKeyOffset(t);
                break;
            case 0xEC: // PSG volume
                setPsgVolume(t);
                break;
            case 0xF3: // PSG Noise
                setPsgNoise(t);
                break;
            case 0xF4: // Modulation off
                clearModulation(t);
                break;
            case 0xF5: // PSG instrument
                if (t.pos < data.length) {
                    int insId = data[t.pos++] & 0xFF;
                    t.instrumentId = insId;
                    loadPsgEnvelope(t, insId);
                }
                break;
            case 0xEF:
                // Set Voice
                if (t.pos < data.length) {
                    int voiceId = data[t.pos++] & 0xFF;
                    loadVoice(t, voiceId);
                }
                break;
            case 0xEA:
                // Set main tempo
                if (t.pos < data.length) {
                    normalTempo = data[t.pos++] & 0xFF;
                    calculateTempo();
                    // Parity: EA (Tempo Set) resets the tempo accumulator/counter to the new tempo value
                    tempoAccumulator = tempoWeight;
                }
                break;
            case 0xEB:
                // Set dividing timing
                if (t.pos < data.length) {
                    int newDividingTiming = data[t.pos++] & 0xFF;
                    updateDividingTiming(newDividingTiming);
                }
                break;
            default:
                // Check for game-specific TRK_END flags (e.g., S1 0xEE = stop track)
                if (!config.getExtraTrkEndFlags().isEmpty() && config.getExtraTrkEndFlags().contains(cmd)) {
                    t.active = false;
                    stopNote(t);
                    break;
                }
                int params = flagParamLength(cmd);
                int advance = Math.min(params, data.length - t.pos);
                t.pos += advance;
                break;
        }
    }

    // Pre-computed flag parameter length table for SMPS commands 0xE0-0xFF
    // Lookup table is faster than switch statement in hot path
    // Index = cmd - 0xE0 (range 0-31)
    private static final byte[] FLAG_PARAM_LENGTH = new byte[32];
    static {
        // 1-parameter commands
        FLAG_PARAM_LENGTH[0xE0 - 0xE0] = 1; // Pan
        FLAG_PARAM_LENGTH[0xE1 - 0xE0] = 1; // Detune
        FLAG_PARAM_LENGTH[0xE2 - 0xE0] = 1; // Set Communication
        FLAG_PARAM_LENGTH[0xE5 - 0xE0] = 1; // Tick multiplier
        FLAG_PARAM_LENGTH[0xE6 - 0xE0] = 1; // Volume
        FLAG_PARAM_LENGTH[0xE8 - 0xE0] = 1; // Note fill
        FLAG_PARAM_LENGTH[0xE9 - 0xE0] = 1; // Key displacement
        FLAG_PARAM_LENGTH[0xEA - 0xE0] = 1; // Set main tempo
        FLAG_PARAM_LENGTH[0xEB - 0xE0] = 1; // Set dividing timing
        FLAG_PARAM_LENGTH[0xEC - 0xE0] = 1; // PSG volume
        FLAG_PARAM_LENGTH[0xED - 0xE0] = 1; // (unused but keep for safety)
        FLAG_PARAM_LENGTH[0xEF - 0xE0] = 1; // Set Voice
        FLAG_PARAM_LENGTH[0xF3 - 0xE0] = 1; // PSG Noise
        FLAG_PARAM_LENGTH[0xF5 - 0xE0] = 1; // PSG instrument

        // 2-parameter commands
        FLAG_PARAM_LENGTH[0xF6 - 0xE0] = 2; // Jump
        FLAG_PARAM_LENGTH[0xF8 - 0xE0] = 2; // Call
        FLAG_PARAM_LENGTH[0xFD - 0xE0] = 2; // Custom Fade Out

        // 4-parameter commands
        FLAG_PARAM_LENGTH[0xF0 - 0xE0] = 4; // Modulation
        FLAG_PARAM_LENGTH[0xF7 - 0xE0] = 4; // Loop

        // 0-parameter commands (E3, E4, E7, EE, F1, F2, F4, F9) are already 0 from array initialization
    }

    private int flagParamLength(int cmd) {
        if (cmd >= 0xE0 && cmd <= 0xFF) {
            // Delegate to game-specific handler first
            CoordFlagHandler handler = config.getCoordFlagHandler();
            if (handler != null) {
                int len = handler.flagParamLength(cmd);
                if (len >= 0) {
                    return len;
                }
            }
            // Check for game-specific overrides first (e.g., S1 ED/EE differ from S2)
            Map<Integer, Integer> overrides = config.getCoordFlagParamOverrides();
            if (!overrides.isEmpty()) {
                Integer override = overrides.get(cmd);
                if (override != null) {
                    return override;
                }
            }
            return FLAG_PARAM_LENGTH[cmd - 0xE0];
        }
        return 0;
    }

    private void handleFadeOut(Track t) {
        if (t.pos + 2 <= data.length) {
            fadeState.steps = data[t.pos++] & 0xFF;
            fadeState.delayInit = data[t.pos++] & 0xFF;
            fadeState.addFm = 1;
            fadeState.addPsg = 1;
            fadeState.delayCounter = fadeState.delayInit;
            fadeState.active = true;
            fadeState.fadeOut = true;
        }
    }

    @Override
    public int getCommData() {
        return commData;
    }

    private int readPointer(Track t) {
        if (t.pos + 2 > data.length)
            return 0;
        int ptr = smpsData.read16(t.pos);
        t.pos += 2;
        return ptr;
    }

    /**
     * Read a jump/loop/call pointer from the track data, handling both S1 (PC-relative)
     * and S2 (absolute Z80) addressing modes.
     *
     * <p>S1 68k: In-stream pointers (F6/F7/F8) use {@code dc.w loc-*-1}, meaning the
     * raw 16-bit value is a signed offset from (ptrWordOffset + 1).
     *
     * <p>S2 Z80: Pointers are absolute Z80 addresses, resolved via {@link #relocate}.
     */
    @Override
    public int readJumpPointer(Track t) {
        if (config.isRelativePointers()) {
            // S1 68k: PC-relative from (ptrAddr + 1)
            int ptrOffset = t.pos;
            int raw = smpsData.read16(t.pos);
            t.pos += 2;
            // Interpret as signed 16-bit
            int target = ptrOffset + 1 + (short) raw;
            return (target >= 0 && target < data.length) ? target : -1;
        } else {
            // S2 Z80: absolute address, needs relocate
            int ptr = readPointer(t);
            return relocate(ptr, smpsData.getZ80StartAddress());
        }
    }

    private void handleJump(Track t) {
        int newPos = readJumpPointer(t);
        if (newPos != -1) {
            t.pos = newPos;
        } else {
            t.active = false;
        }
    }

    private void handleLoop(Track t) {
        if (t.pos + 2 <= data.length) {
            int index = data[t.pos++] & 0xFF;
            int count = data[t.pos++] & 0xFF;
            int newPos = readJumpPointer(t);
            if (newPos == -1) {
                t.active = false;
                return;
            }
            if (count == 0) {
                t.pos = newPos;
                return;
            }
            if (index >= t.loopCounters.length) {
                // Cap at 256 entries (max possible index from a single byte)
                int newSize = Math.min(256, Math.max(t.loopCounters.length * 2, index + 1));
                if (index >= newSize) {
                    // Index exceeds maximum SMPS loop nesting; skip this loop command
                    return;
                }
                int[] newCounters = new int[newSize];
                System.arraycopy(t.loopCounters, 0, newCounters, 0, t.loopCounters.length);
                t.loopCounters = newCounters;
            }

            if (t.loopCounters[index] == 0) {
                t.loopCounters[index] = count;
            }
            if (t.loopCounters[index] > 0) {
                t.loopCounters[index]--;
                if (t.loopCounters[index] > 0) {
                    t.pos = newPos;
                }
            }
        }
    }

    private void handleCall(Track t) {
        int newPos = readJumpPointer(t);
        if (newPos == -1 || t.returnSp >= t.returnStack.length) {
            t.active = false;
            return;
        }
        t.returnStack[t.returnSp++] = t.pos;
        t.pos = newPos;
    }

    private void handleReturn(Track t) {
        if (t.returnSp > 0) {
            t.pos = t.returnStack[--t.returnSp];
        } else {
            t.active = false;
        }
    }

    private void handleSndOff(Track t) {
        // SMPSPlay CF_SND_OFF: Only writes to specific operators' release rates (Op 2
        // and 4).
        // Confirmed via SMPSPlay src/Engine/smps_commands.c that it does NOT write
        // Total Level (TL).
        // It does NOT stop the track (active=false) or explicitly stop the note.

        if (t.type == TrackType.FM) {
            int hwCh = t.channelId;
            int port = (hwCh < 3) ? 0 : 1;
            int ch = (hwCh % 3);

            // Write 0x0F to 0x88 + ch (Op 2) and 0x8C + ch (Op 4)
            // 0x80 register: SL/RR. 0x0F means SL=0, RR=15 (Max Release).
            synth.writeFm(this, port, 0x88 + ch, 0x0F);
            synth.writeFm(this, port, 0x8C + ch, 0x0F);

            // Mark track for instrument refresh on next note to undo SL/RR changes
            t.forceRefresh = true;
        }
    }

    private void handleModulation(Track t) {
        if (t.pos + 4 <= data.length) {
            t.modDelayInit = data[t.pos++] & 0xFF;
            t.modDelay = t.modDelayInit;
            int rate = data[t.pos++] & 0xFF;
            t.modRate = (rate == 0) ? 256 : rate;
            t.modDelta = data[t.pos++];
            int steps = data[t.pos++] & 0xFF;
            t.modStepsFull = steps;
            // Z80 driver (S2): srl a (halve). 68k driver (S1): no halving.
            t.modSteps = config.isHalveModSteps() ? steps / 2 : steps;

            t.modRateCounter = t.modRate;
            t.modStepCounter = t.modSteps;
            t.modAccumulator = 0;
            t.modCurrentDelta = t.modDelta;
            t.customModEnabled = true;
            t.modEnvId = 0;
            t.modEnvData = null;
            t.modEnvPos = 0;
            t.modEnvMult = 0;
            t.modEnvCache = 0;
            t.modEnvHold = false;
            t.modEnabled = true;
        }
    }

    @Override
    public void clearModulation(Track t) {
        t.customModEnabled = false;
        t.modEnabled = false;
        t.modEnvId = 0;
        t.modEnvData = null;
        t.modEnvPos = 0;
        t.modEnvMult = 0;
        t.modEnvCache = 0;
        t.modEnvHold = false;
        t.modAccumulator = 0;
    }

    private void resetModEnvelopeState(Track t) {
        if (t.modEnvId == 0) {
            return;
        }
        if (t.modEnvData == null) {
            t.modEnvData = smpsData.getModEnvelope(t.modEnvId);
        }
        t.modEnvPos = 0;
        t.modEnvMult = 0;
        t.modEnvCache = 0;
        t.modEnvHold = false;
        t.modEnabled = t.customModEnabled || t.modEnvData != null;
    }

    private void setPanAmsFms(Track t) {
        if (t.pos < data.length) {
            int val = data[t.pos++] & 0xFF;
            t.pan = ((val & 0x80) != 0 ? 0x80 : 0) | ((val & 0x40) != 0 ? 0x40 : 0);
            t.ams = (val >> 4) & 0x3;
            t.fms = val & 0x7;
            applyFmPanAmsFms(t);
        }
    }

    private void setVolumeOffset(Track t) {
        if (t.pos < data.length) {
            t.volumeOffset += (byte) data[t.pos++];
            refreshVolume(t);
        }
    }

    private void setFill(Track t) {
        if (t.pos < data.length) {
            t.fill = data[t.pos++] & 0xFF;
        }
    }

    private void setKeyOffset(Track t) {
        if (t.pos < data.length) {
            t.keyOffset = wrapSignedByte(t.keyOffset + (byte) data[t.pos++]);
        }
    }

    private static int wrapSignedByte(int value) {
        return (byte) value;
    }

    private void setPsgNoise(Track t) {
        if (t.pos < data.length) {
            int val = data[t.pos++] & 0x0F;
            t.noiseMode = true;
            t.psgNoiseParam = val;
            synth.writePsg(this, 0xE0 | (val & 0x0F));
        }
    }

    private void setPsgVolume(Track t) {
        if (t.pos < data.length) {
            t.volumeOffset += (byte) data[t.pos++];
            refreshVolume(t);
        }
    }

    private void setTempoWeight(int newTempo) {
        normalTempo = newTempo & 0xFF;
        calculateTempo();
    }

    private void setTrackDividingTiming(Track t) {
        if (t.pos < data.length) {
            t.dividingTiming = data[t.pos++] & 0xFF;
        }
    }

    @Override
    public void updateDividingTiming(int newDividingTiming) {
        dividingTiming = newDividingTiming;
        for (Track track : tracks) {
            track.dividingTiming = newDividingTiming;
        }
    }

    private void setDuration(Track track, int rawDuration) {
        track.rawDuration = rawDuration;
        int scaled = scaleDuration(track, rawDuration);
        track.scaledDuration = scaled;
        track.duration = scaled;
    }

    private void reuseDuration(Track track) {
        if (track.rawDuration == 0) {
            track.rawDuration = 1;
        }
        setDuration(track, track.rawDuration);
    }

    private int scaleDuration(Track track, int rawDuration) {
        int factor = track.dividingTiming;
        int scaled = rawDuration * factor;
        if (scaled == 0) {
            return 65536; // Emulate SMPS wrap-around behavior (0 ticks -> 65536 ticks)
        }
        return scaled;
    }

    private int[] getFmFreqTable() {
        if (config.getVolMode() == SmpsSequencerConfig.VolMode.BIT7) {
            return FNUM_TABLE_Z80;
        }
        return FNUM_TABLE_68K;
    }

    private int[] getPsgFreqTable() {
        if (config.getVolMode() == SmpsSequencerConfig.VolMode.BIT7) {
            return PSG_FREQ_TABLE_Z80_T2;
        }
        return PSG_FREQ_TABLE_68K;
    }

    private boolean shouldPreventNoteAttack(Track t) {
        // We model the driver's note-on-prevent bit with tieNext.
        // On SMPS Z80 this is the HOLD bit, and on SMPS 68k this maps to AT-REST.
        return t.tieNext;
    }

    private void resetTrackedFrequency(Track t) {
        if (t.type == TrackType.FM) {
            t.baseFnum = 0;
            t.baseBlock = 0;
        } else if (t.type == TrackType.PSG) {
            t.baseFnum = 0x3FF;
        }
    }

    @Override
    public void loadVoice(Track t, int voiceId) {
        byte[] voice = smpsData.getVoice(voiceId);
        if (voice == null && fallbackVoiceData != null) {
            voice = fallbackVoiceData.getVoice(voiceId);
        }
        if (voice != null) {
            t.voiceData = voice;
            t.voiceId = voiceId;
            // Clear SSG-EG state: new voice may not use SSG-EG, and the song's
            // coordination flags (FF 05) will re-set it if needed.
            Arrays.fill(t.ssgEg, 0);
            // ROM parity: when a note is being sustained (tieNext / HOLD),
            // skip refreshInstrument to avoid the key-off in setInstrument().
            // Continuous SFX (cfx_*) loop back through smpsSetvoice on every
            // iteration while the note is tied; re-keying would kill the sound.
            // The Z80 driver's zSetVoice does not key-off mid-sustain.
            if (!t.tieNext) {
                refreshInstrument(t);
            }
        }
    }

    private void playNote(Track t) {
        boolean preventAttack = shouldPreventNoteAttack(t);

        if (t.note == 0x80) {
            if (t.type != TrackType.DAC) {
                stopNote(t);
            }
            if (config.getDelayFreq() == SmpsSequencerConfig.DelayFreq.RESET) {
                resetTrackedFrequency(t);
            }
            t.tieNext = false;
            return;
        }

        if (t.forceRefresh) {
            refreshInstrument(t);
            t.forceRefresh = false;
        }

        if (!preventAttack) {
            resetModEnvelopeState(t);
        }

        if (t.type == TrackType.DAC) {
            // Skip DAC playback if muted during fade-in
            if (!t.dacMuted) {
                synth.playDac(this, t.note);
            }
            t.tieNext = false;
            return;
        }

        int baseNoteOffset = (t.type == TrackType.PSG) ? smpsData.getPsgBaseNoteOffset() : smpsData.getBaseNoteOffset();
        int n = t.note - 0x81 + t.keyOffset + baseNoteOffset;

        if (t.type == TrackType.FM) {
            // Match SMPSPlay/GetNote FM note indexing behavior.
            int fmNote = n & 0xFF;
            if (baseNoteOffset == 1) {
                fmNote &= 0x7F;
            }
            int octave = fmNote / 12;
            int noteIdx = fmNote % 12;

            int hwCh = t.channelId;
            int port = (hwCh < 3) ? 0 : 1;
            int ch = (hwCh % 3);

            int fnum = getFmFreqTable()[noteIdx];
            int block = octave;

            block &= 7;

            t.baseFnum = fnum;
            t.baseBlock = block;

            if (t.customModEnabled && !preventAttack) {
                t.modDelay = t.modDelayInit;
                t.modRateCounter = t.modRate;
                t.modStepCounter = t.modSteps;
                t.modAccumulator = 0;
                t.modCurrentDelta = t.modDelta;
            }

            int packed = (block << 11) | fnum;
            packed += t.detune;

            if (pitch != 1.0f) {
                int b = (packed >> 11) & 7;
                int f = packed & 0x7FF;
                f = (int) (f * pitch);
                while (f > 0x7FF && b < 7) {
                    f >>= 1;
                    b++;
                }
                packed = (b << 11) | (f & 0x7FF);
            }

            block = (packed >> 11) & 7;
            fnum = packed & 0x7FF;

            int chVal = (port == 0) ? ch : (ch + 4); // YM2612 0x28: bit2 selects upper port

            // SMPSPlay DoNoteOn: skip KEY_OFF and KEY_ON when tieNext (HOLD) is set.
            // This allows smpsNoAttack (E7) to work correctly for both music and SFX.
            if (!preventAttack) {
                // [not in driver] turn DAC off when playing a note on FM6
                if (fm6DacOff && hwCh == 5) {
                    synth.writeFm(this, 0, 0x2B, 0x00);
                }

                synth.writeFm(this, 0, 0x28, chVal); // Key Off before frequency change
            }

            writeFmFreq(port, ch, fnum, block);
            applyFmPanAmsFms(t);
            // S2 (ModAlgo 68k_a) applies modulation before note-on; S1 (ModAlgo 68k) does not.
            if (t.modEnabled && config.isApplyModOnNote()) {
                applyModulation(t);
            }

            if (!preventAttack) {
                synth.writeFm(this, 0, 0x28, 0xF0 | chVal); // Key On after latching frequency/pan
                LOGGER.fine("FM KEY ON: chVal=" + Integer.toHexString(chVal) + " port=" + port + " fnum="
                        + Integer.toHexString(fnum) + " block=" + block + " note=" + Integer.toHexString(t.note));
            }

        } else {
            // Table choice comes from driver config: S1/S2 use DEF_68K, S3K uses DEF_Z80_T2.
            int[] psgFreqTable = getPsgFreqTable();
            int psgNote = n;
            if (psgNote < 0)
                psgNote = 0;
            if (psgNote >= psgFreqTable.length)
                psgNote = psgFreqTable.length - 1;
            int reg = psgFreqTable[psgNote];
            t.baseFnum = reg;

            reg += t.detune;

            if (pitch != 1.0f) {
                reg = (int) (reg / pitch);
            }
            reg = normalizePsgPeriod(reg);

            boolean noiseUsesTone2 = t.noiseMode && t.channelId == 2 && (t.psgNoiseParam & 0x03) == 0x03;
            boolean writeToneFreq = t.channelId < 3 && (!t.noiseMode || noiseUsesTone2);

            if (writeToneFreq) {
                int data = reg & 0xF;
                int ch = t.channelId;
                synth.writePsg(this, 0x80 | (ch << 5) | (0) | data);
                synth.writePsg(this, (reg >> 4) & 0x3F);
                // baseFnum stores detune-free period; modulation applies detune dynamically.
            }

            if (t.customModEnabled && !preventAttack) {
                t.modDelay = t.modDelayInit;
                t.modRateCounter = t.modRate;
                t.modStepCounter = t.modSteps;
                t.modAccumulator = 0;
                t.modCurrentDelta = t.modDelta;
            }

            // S2 (ModAlgo 68k_a) applies modulation before PSG volume write; S1 (ModAlgo 68k) does not.
            if (t.modEnabled && config.isApplyModOnNote()) {
                applyModulation(t);
            }

        }

        if (!preventAttack) {
            t.decayOffset = 0;
            t.decayTimer = 0;
            t.envPos = 0;
            t.envHold = false;
            t.envAtRest = false;
            if (t.envData != null && t.envData.length > 0) {
                int val = t.envData[0] & 0xFF;
                if (val < 0x80) {
                    t.envValue = val;
                    t.envPos = 1;
                }
            } else {
                t.envData = null;
                t.envValue = 0;
            }

            if (t.type == TrackType.PSG) {
                refreshVolume(t); // Apply the first envelope step immediately on note start
            }
            if (t.type == TrackType.FM && t.fmVolEnvData != null) {
                t.fmVolEnvPos = 0;
                t.fmVolEnvValue = 0;
                t.fmVolEnvHold = false;
                refreshVolume(t);
            }
        }
        t.tieNext = false;
    }

    private void playRawFrequency(Track t) {
        boolean preventAttack = shouldPreventNoteAttack(t);
        int freq = t.rawFrequency & 0xFFFF;

        if (freq == 0) {
            stopNote(t);
            if (config.getDelayFreq() == SmpsSequencerConfig.DelayFreq.RESET) {
                resetTrackedFrequency(t);
            }
            t.tieNext = false;
            return;
        }

        if (!preventAttack) {
            resetModEnvelopeState(t);
        }

        if (t.type == TrackType.FM) {
            int packed = freq + t.detune;
            int block = (packed >> 11) & 0x7;
            int fnum = packed & 0x7FF;
            t.baseFnum = fnum;
            t.baseBlock = block;

            if (t.customModEnabled && !preventAttack) {
                t.modDelay = t.modDelayInit;
                t.modRateCounter = t.modRate;
                t.modStepCounter = t.modSteps;
                t.modAccumulator = 0;
                t.modCurrentDelta = t.modDelta;
            }

            int hwCh = t.channelId;
            int port = (hwCh < 3) ? 0 : 1;
            int ch = (hwCh % 3);
            int chVal = (port == 0) ? ch : (ch + 4);
            if (!preventAttack) {
                synth.writeFm(this, 0, 0x28, chVal);
            }
            writeFmFreq(port, ch, fnum, block);
            applyFmPanAmsFms(t);
            if (t.modEnabled && config.isApplyModOnNote()) {
                applyModulation(t);
            }
            if (!preventAttack) {
                synth.writeFm(this, 0, 0x28, 0xF0 | chVal);
            }
            if (!preventAttack && t.fmVolEnvData != null) {
                t.fmVolEnvPos = 0;
                t.fmVolEnvValue = 0;
                t.fmVolEnvHold = false;
                refreshVolume(t);
            }
        } else if (t.type == TrackType.PSG) {
            t.baseFnum = freq;

            int reg = freq + t.detune;
            reg = normalizePsgPeriod(reg);

            boolean noiseUsesTone2 = t.noiseMode && t.channelId == 2 && (t.psgNoiseParam & 0x03) == 0x03;
            boolean writeToneFreq = t.channelId < 3 && (!t.noiseMode || noiseUsesTone2);
            if (writeToneFreq) {
                int ch = t.channelId;
                synth.writePsg(this, 0x80 | (ch << 5) | (reg & 0x0F));
                synth.writePsg(this, (reg >> 4) & 0x3F);
            }

            if (t.customModEnabled && !preventAttack) {
                t.modDelay = t.modDelayInit;
                t.modRateCounter = t.modRate;
                t.modStepCounter = t.modSteps;
                t.modAccumulator = 0;
                t.modCurrentDelta = t.modDelta;
            }
            if (t.modEnabled && config.isApplyModOnNote()) {
                applyModulation(t);
            }

            if (!preventAttack) {
                t.envPos = 0;
                t.envHold = false;
                t.envAtRest = false;
                t.decayOffset = 0;
                t.decayTimer = 0;
                refreshVolume(t);
            }
        }

        t.tieNext = false;
    }

    private int getPitchSlideFreq(int freq) {
        // The Z80 SMPS driver does NOT have any pitch slide wrapping logic.
        // It directly adds modulation/detune to the frequency and lets the
        // hardware handle any overflow. Previous wrapping code here was
        // causing incorrect octave jumps during modulation (e.g., Gloop SFX).
        return freq;
    }

    @Override
    public void stopNote(Track t) {
        if (t.type == TrackType.FM) {
            int hwCh = t.channelId;
            int port = (hwCh < 3) ? 0 : 1;
            int ch = hwCh % 3;
            int chVal = (port == 0) ? ch : (ch + 4);
            synth.writeFm(this, 0, 0x28, chVal); // Key On/Off is always on Port 0
        } else if (t.type == TrackType.DAC) {
            synth.stopDac(this);
        } else {
            if (t.channelId <= 3) {
                if (t.noiseMode && t.channelId == 2) {
                    synth.writePsg(this, 0x80 | (3 << 5) | (1 << 4) | 0x0F);
                } else {
                    synth.writePsg(this, 0x80 | (t.channelId << 5) | (1 << 4) | 0x0F);
                }
            }
        }
    }

    @Override
    public boolean isComplete() {
        for (Track t : tracks) {
            if (t.active)
                return false;
        }
        return true;
    }

    @Override
    public void refreshVolume(Track t) {
        if (t.type == TrackType.FM) {
            updateFmTotalLevel(t);
        } else if (t.type == TrackType.PSG) {
            if (t.envAtRest) {
                return;
            }
            int vol = 0x0F;
            if (t.note != 0x80) {
                vol = Math.min(0x0F, Math.max(0, t.volumeOffset + t.envValue));
            }
            int ch = t.channelId;
            if (t.noiseMode && ch == 2) {
                ch = 3;
            }
            if (ch <= 3) {
                synth.writePsg(this, 0x80 | (ch << 5) | (1 << 4) | vol);
            }
        }
    }

    private void updateFmTotalLevel(Track t) {
        if (t.voiceData == null) {
            return;
        }
        boolean hasTl = t.voiceData.length >= 25;
        if (!hasTl) {
            return;
        }
        int algo = t.voiceData[0] & 0x07;
        int[] tlIdx = { 21, 23, 22, 24 };
        int mask;
        if (config.getVolMode() == SmpsSequencerConfig.VolMode.BIT7) {
            // S3K: carrier operators identified by bit 7 set in TL bytes
            mask = 0;
            for (int op = 0; op < 4; op++) {
                int idx = tlIdx[op];
                if (idx < t.voiceData.length && (t.voiceData[idx] & 0x80) != 0) {
                    mask |= (1 << op);
                }
            }
        } else {
            mask = ALGO_OUT_MASK[algo];
        }

        int hwCh = t.channelId;
        int port = (hwCh < 3) ? 0 : 1;
        int ch = hwCh % 3;

        for (int op = 0; op < 4; op++) {
            if ((mask & (1 << op)) == 0) {
                continue;
            }
            int idx = tlIdx[op];
            if (idx >= t.voiceData.length) {
                continue;
            }
            int tl = computeFmTotalLevel(t, t.voiceData[idx] & 0x7F, op);
            synth.writeFm(this, port, 0x40 + (op * 4) + ch, tl);
        }
    }

    private int computeFmTotalLevel(Track t, int baseTl, int op) {
        int tl = baseTl + t.volumeOffset;
        if (t.fmVolEnvData != null && (t.fmVolEnvOpMask & (1 << op)) != 0) {
            tl += t.fmVolEnvValue;
        }
        return tl & 0x7F; // wrap like the Z80 interpreter (7-bit)
    }

    @Override
    public void loadPsgEnvelope(Track t, int id) {
        byte[] env = smpsData.getPsgEnvelope(id);
        if (env != null) {
            t.envData = env;
            t.envPos = 0;
            t.envHold = false;
            t.envAtRest = false;
            t.envValue = 0;
        } else {
            t.envData = null;
            t.envValue = 0;
        }
    }

    private void processPsgEnvelope(Track t) {
        if (t.envData == null || t.envHold)
            return;

        // Loop to handle envelope commands that may require immediate progression
        while (true) {
            if (t.envPos >= t.envData.length) {
                t.envHold = true;
                t.envAtRest = true;
                return;
            }
            int val = t.envData[t.envPos] & 0xFF;
            t.envPos++;

            if (val < 0x80) {
                t.envValue = val;
                refreshVolume(t);
                return;
            } else {
                if (val == 0x80) {
                    if (config.getPsgEnvCmd80() == SmpsSequencerConfig.PsgEnvCmd80.RESET) {
                        // S3K: reset envelope to start (loop from beginning)
                        t.envPos = 0;
                        continue;
                    }
                    // S1/S2: HOLD (Sonic 2 driver definition)
                    t.envHold = true;
                    t.envAtRest = true;
                    return;
                } else if (val == 0x81) {
                    // HOLD
                    t.envHold = true;
                    t.envAtRest = true;
                    return;
                } else if (val == 0x82) {
                    // LOOP xx - next byte is target index
                    if (t.envPos < t.envData.length) {
                        t.envPos = t.envData[t.envPos] & 0xFF;
                        continue;
                    } else {
                        t.envHold = true;
                        t.envAtRest = true;
                        return;
                    }
                } else if (val == 0x84) {
                    // CHGMULT xx - not modeled; consume parameter to stay in sync
                    if (t.envPos < t.envData.length) {
                        t.envPos++; // skip multiplier byte
                        continue;
                    } else {
                        t.envHold = true;
                        t.envAtRest = true;
                        return;
                    }
                } else if (val == 0x83) {
                    // STOP
                    t.envHold = true;
                    t.envValue = 0x0F; // Silence
                    t.envAtRest = true;
                    refreshVolume(t);
                    stopNote(t);
                    return;
                } else {
                    // Unknown/Other: Treat as HOLD
                    t.envHold = true;
                    t.envAtRest = true;
                    return;
                }
            }
        }
    }

    private void processFmVolEnvelope(Track t) {
        if (t.fmVolEnvData == null || t.fmVolEnvHold) {
            return;
        }

        while (true) {
            if (t.fmVolEnvPos >= t.fmVolEnvData.length) {
                t.fmVolEnvHold = true;
                return;
            }
            int val = t.fmVolEnvData[t.fmVolEnvPos] & 0xFF;
            t.fmVolEnvPos++;

            if (val < 0x80) {
                t.fmVolEnvValue = val;
                refreshVolume(t);
                return;
            }

            if (val == 0x80) {
                t.fmVolEnvPos = 0;
                continue;
            }
            if (val == 0x81) {
                t.fmVolEnvHold = true;
                return;
            }
            if (val == 0x82) {
                if (t.fmVolEnvPos < t.fmVolEnvData.length) {
                    t.fmVolEnvPos = t.fmVolEnvData[t.fmVolEnvPos] & 0xFF;
                    continue;
                }
                t.fmVolEnvHold = true;
                return;
            }
            if (val == 0x84) {
                if (t.fmVolEnvPos < t.fmVolEnvData.length) {
                    t.fmVolEnvPos++;
                    continue;
                }
                t.fmVolEnvHold = true;
                return;
            }

            // STOP/unknown: hold at max attenuation.
            t.fmVolEnvHold = true;
            t.fmVolEnvValue = 0x7F;
            refreshVolume(t);
            return;
        }
    }

    @Override
    public void refreshInstrument(Track t) {
        if (t.type != TrackType.FM || t.voiceData == null) {
            return;
        }
        // Use scratch buffer instead of allocating new array each call
        int copyLen = Math.min(t.voiceData.length, t.voiceScratch.length);
        System.arraycopy(t.voiceData, 0, t.voiceScratch, 0, copyLen);
        byte[] voice = t.voiceScratch;
        boolean hasTl = t.voiceData.length >= 25;
        // SMPS (S2) stores TL at the end of the 25-byte blob (bytes 21-24).
        int tlBase = hasTl ? 21 : -1;
        if (tlBase >= 0) {
            int algo = voice[0] & 0x07;
            int[] opMap = { 0, 2, 1, 3 };
            int mask;
            if (config.getVolMode() == SmpsSequencerConfig.VolMode.BIT7) {
                // S3K: carrier operators identified by bit 7 set in TL bytes
                mask = 0;
                for (int op = 0; op < 4; op++) {
                    int idx = tlBase + opMap[op];
                    if (idx < voice.length && (voice[idx] & 0x80) != 0) {
                        mask |= (1 << op);
                    }
                }
            } else {
                mask = ALGO_OUT_MASK[algo];
            }

            boolean bit7Mode = config.getVolMode() == SmpsSequencerConfig.VolMode.BIT7;
            for (int op = 0; op < 4; op++) {
                if ((mask & (1 << op)) != 0) {
                    int idx = tlBase + opMap[op];
                    int bit7 = bit7Mode ? (voice[idx] & 0x80) : 0; // preserve carrier marker in S3K BIT7 mode only
                    int tl = computeFmTotalLevel(t, voice[idx] & 0x7F, op);
                    voice[idx] = (byte) (tl | bit7);
                }
            }
        }
        synth.setInstrument(this, t.channelId, voice);

        // Restore SSG-EG values that setInstrument() cleared (registers 0x90-0x9C).
        // S3K sets SSG-EG via coordination flag FF 05; without restoring them here,
        // every voice refresh (SFX restore, fade, forceRefresh) resets the looping
        // envelope shapes that fundamentally define instrument character.
        boolean hasSsgEg = false;
        for (int v : t.ssgEg) {
            if (v != 0) { hasSsgEg = true; break; }
        }
        if (hasSsgEg) {
            int port = (t.channelId < 3) ? 0 : 1;
            int ch = t.channelId % 3;
            for (int slot = 0; slot < 4; slot++) {
                if (t.ssgEg[slot] != 0) {
                    synth.writeFm(this, port, 0x90 + slot * 4 + ch, t.ssgEg[slot]);
                }
            }
        }
    }

    private void applyFmPanAmsFms(Track t) {
        if (t.type != TrackType.FM)
            return;
        int hwCh = t.channelId;
        int port = (hwCh < 3) ? 0 : 1;
        int ch = (hwCh % 3);
        int reg = 0xB4 + ch;
        int val = (t.pan & 0xC0) | ((t.ams & 0x3) << 4) | (t.fms & 0x7);
        synth.writeFm(this, port, reg, val);
    }

    private void writeFmFreq(int port, int ch, int fnum, int block) {
        int valA4 = (block << 3) | ((fnum >> 8) & 0x7);
        int valA0 = fnum & 0xFF;
        synth.writeFm(this, port, 0xA4 + ch, valA4);
        synth.writeFm(this, port, 0xA0 + ch, valA0);
        // Note: Key On is handled by playNote(), NOT here.
        // The original Z80 driver's zFMUpdateFreq only writes frequency registers.
        // Adding Key On here caused re-keying during rests, breaking SFX like 0xAD.
    }

    private void applyModulation(Track t) {
        if (!t.modEnabled)
            return;
        // Match original Z80 driver behavior: skip modulation when track is at rest.
        // From s2.sounddriver.asm zDoModulation: "bit 1,(ix+zTrack.PlaybackControl) / ret nz"
        if (t.note == 0x80)  // 0x80 = rest note
            return;

        stepCustomModulation(t);
        stepModEnvelope(t);
        if (!t.modStepInEffect && !t.modEnvStepInEffect) {
            t.modEnabled = false;
            return;
        }

        int freqDelta = t.modStepDelta + t.modEnvStepDelta;
        boolean changed = t.modStepChanged || t.modEnvStepChanged;
        if (!changed) {
            return;
        }

        if (t.type == TrackType.FM) {
            int packed = getPacked(t, freqDelta);

            int block = (packed >> 11) & 7;
            int fnum = packed & 0x7FF;

            int hwCh = t.channelId;
            int port = (hwCh < 3) ? 0 : 1;
            int ch = (hwCh % 3);
            writeFmFreq(port, ch, fnum, block);
        } else if (t.type == TrackType.PSG && t.channelId < 3) {
            boolean noiseUsesTone2 = t.noiseMode && t.channelId == 2 && (t.psgNoiseParam & 0x03) == 0x03;
            if (!t.noiseMode || noiseUsesTone2) {
                int reg = t.baseFnum + freqDelta + t.detune;
                if (pitch != 1.0f) {
                    reg = (int) (reg / pitch);
                }
                reg = normalizePsgPeriod(reg);

                int data = reg & 0xF;
                int ch = t.channelId;
                synth.writePsg(this, 0x80 | (ch << 5) | data);
                synth.writePsg(this, (reg >> 4) & 0x3F);
            }
        }
    }

    private void stepCustomModulation(Track t) {
        if (!t.customModEnabled) {
            t.modStepInEffect = false;
            t.modStepChanged = false;
            t.modStepDelta = 0;
            return;
        }

        if (t.modDelay > 0) {
            t.modDelay--;
            t.modStepInEffect = true;
            t.modStepChanged = false;
            t.modStepDelta = t.modAccumulator;
            return;
        }

        if (t.modRateCounter > 0) {
            t.modRateCounter--;
        }

        if (t.modRateCounter == 0) {
            t.modRateCounter = t.modRate;

            if (config.getModAlgo() == SmpsSequencerConfig.ModAlgo.MOD_Z80) {
                // S3K (MODALGO_Z80): post-decrement with 8-bit wrap, then check.
                // dec (ix+zModStepCount) ; jr nz,.no_reversal
                t.modStepCounter = (t.modStepCounter - 1) & 0xFF;
                if (t.modStepCounter == 0) {
                    t.modStepCounter = t.modStepsFull; // reload from RAW (ModData[0x03])
                    t.modCurrentDelta = -t.modCurrentDelta;
                    t.modStepInEffect = true;
                    t.modStepChanged = false;
                    t.modStepDelta = t.modAccumulator;
                    return;
                }
            } else {
                // S1/S2 (MODALGO_68K): pre-check, then decrement.
                // ld a,(ix+zModStepCount) ; or a ; jr nz,.calcfreq
                if (t.modStepCounter == 0) {
                    t.modStepCounter = t.modStepsFull; // reload from RAW (ModData[0x03])
                    t.modCurrentDelta = -t.modCurrentDelta;
                    t.modStepInEffect = true;
                    t.modStepChanged = false;
                    t.modStepDelta = t.modAccumulator;
                    return;
                }
                t.modStepCounter--;
            }

            t.modAccumulator += t.modCurrentDelta;
            t.modAccumulator = (short) t.modAccumulator; // 16-bit signed wrap
            t.modStepInEffect = true;
            t.modStepChanged = true;
            t.modStepDelta = t.modAccumulator;
            return;
        }

        t.modStepInEffect = true;
        t.modStepChanged = false;
        t.modStepDelta = t.modAccumulator;
    }

    private void stepModEnvelope(Track t) {
        if (t.modEnvId == 0 || t.modEnvData == null || t.modEnvData.length == 0) {
            t.modEnvStepInEffect = false;
            t.modEnvStepChanged = false;
            t.modEnvStepDelta = 0;
            return;
        }

        if (t.modEnvHold) {
            t.modEnvStepInEffect = true;
            t.modEnvStepChanged = false;
            t.modEnvStepDelta = t.modEnvCache;
            return;
        }

        int safety = 0;
        while (safety++ < 512) {
            if (t.modEnvPos >= t.modEnvData.length) {
                t.modEnvPos = 0;
            }

            int value = t.modEnvData[t.modEnvPos] & 0xFF;
            t.modEnvPos++;

            if (value < 0x80) {
                int envVal = (byte) value;
                int multiplier = t.modEnvMult + 1; // S3K DefDrv: EnvMult = Z80
                t.modEnvCache = (short) (envVal * multiplier);
                t.modEnvStepInEffect = true;
                t.modEnvStepChanged = true;
                t.modEnvStepDelta = t.modEnvCache;
                return;
            }

            switch (value) {
                case 0x80: // RESET
                    t.modEnvPos = 0;
                    continue;
                case 0x81: // HOLD
                case 0x83: // VOLSTOP_MODHOLD => HOLD for modulation envelopes
                    t.modEnvPos--;
                    t.modEnvHold = true;
                    t.modEnvStepInEffect = true;
                    t.modEnvStepChanged = false;
                    t.modEnvStepDelta = t.modEnvCache;
                    return;
                case 0x82: // LOOP xx
                    if (t.modEnvPos < t.modEnvData.length) {
                        t.modEnvPos = t.modEnvData[t.modEnvPos] & 0xFF;
                        continue;
                    }
                    t.modEnvHold = true;
                    t.modEnvStepInEffect = true;
                    t.modEnvStepChanged = false;
                    t.modEnvStepDelta = t.modEnvCache;
                    return;
                case 0x84: // CHG_MULT xx
                    if (t.modEnvPos < t.modEnvData.length) {
                        t.modEnvMult = (t.modEnvMult + (t.modEnvData[t.modEnvPos] & 0xFF)) & 0xFF;
                        t.modEnvPos++;
                        continue;
                    }
                    t.modEnvHold = true;
                    t.modEnvStepInEffect = true;
                    t.modEnvStepChanged = false;
                    t.modEnvStepDelta = t.modEnvCache;
                    return;
                default:
                    t.modEnvHold = true;
                    t.modEnvStepInEffect = true;
                    t.modEnvStepChanged = false;
                    t.modEnvStepDelta = t.modEnvCache;
                    return;
            }
        }

        t.modEnvHold = true;
        t.modEnvStepInEffect = true;
        t.modEnvStepChanged = false;
        t.modEnvStepDelta = t.modEnvCache;
    }

    private int getPacked(Track t, int modulationDelta) {
        int packed = (t.baseBlock << 11) | t.baseFnum;
        packed += modulationDelta + t.detune;

        packed = getPitchSlideFreq(packed);

        if (pitch != 1.0f) {
            int b = (packed >> 11) & 7;
            int f = packed & 0x7FF;
            f = (int) (f * pitch);
            while (f > 0x7FF && b < 7) {
                f >>= 1;
                b++;
            }
            packed = (b << 11) | (f & 0x7FF);
        }
        return packed;
    }

    private static int normalizePsgPeriod(int reg) {
        int period = reg & 0x3FF;
        return period == 0 ? 1 : period;
    }

    private void setDetune(Track t) {
        if (t.pos < data.length) {
            t.detune = data[t.pos++];
        }
    }

    private void handleFadeIn(Track t) {
        // E4 is "Fade in to previous song" in Sonic 2.
        // It's used at the end of the 1-up jingle.
        // This command should stop ALL tracks in this sequence and restore the previous
        // music.
        for (Track track : tracks) {
            track.active = false;
            stopNote(track);
        }
        AudioManager.getInstance().getBackend().restoreMusic();
    }

    public void triggerFadeIn(int steps, int delay) {
        // Start a fade in from current volume (silence) to normal
        fadeState.steps = steps;
        fadeState.delayInit = delay;
        fadeState.addFm = 1;
        fadeState.addPsg = 1;
        // ROM: FadeInDelay is NOT initialized, so first step happens immediately
        fadeState.delayCounter = 0;
        fadeState.active = true;
        fadeState.fadeOut = false; // Fade IN

        // Add steps to existing volumeOffset (attenuate by 'steps'), then fade
        // decreases it.
        for (Track track : tracks) {
            // For DAC, mute during fade-in (no volume control available)
            if (track.type == TrackType.DAC) {
                track.dacMuted = true;
                stopNote(track);
                continue;
            }
            track.volumeOffset += steps;
            refreshVolume(track);
        }
    }

    /**
     * Trigger a music fade-out. ROM equivalent: zFadeOutMusic.
     * Gradually increases volume attenuation over 'steps' frames with 'delay' frames between each step.
     * DAC track is stopped immediately (no volume control available).
     *
     * @param steps total number of volume steps (ROM default: 0x28 = 40)
     * @param delay frames between each volume step (ROM default: 3)
     */
    public void triggerFadeOut(int steps, int delay) {
        if (steps <= 0) {
            return;
        }
        fadeState.steps = steps;
        fadeState.delayInit = delay;
        fadeState.addFm = 1;
        fadeState.addPsg = 1;
        fadeState.delayCounter = delay;
        fadeState.active = true;
        fadeState.fadeOut = true;

        // Stop DAC track immediately (can't fade it) - matches ROM zFadeOutMusic
        for (Track track : tracks) {
            if (track.type == TrackType.DAC) {
                track.active = false;
                stopNote(track);
            }
        }
    }

    /**
     * Refresh all FM voice settings after being paused/restored.
     * This reloads instruments and pan/ams/fms settings to the hardware.
     */
    public void refreshAllVoices() {
        for (Track t : tracks) {
            if (!t.active)
                continue;
            if (t.type == TrackType.FM) {
                refreshInstrument(t);
                applyFmPanAmsFms(t);
            } else if (t.type == TrackType.PSG) {
                refreshVolume(t);
            }
        }
    }

    public Synthesizer getSynthesizer() {
        return synth;
    }

    // -----------------------------------------------------------------------
    // CoordFlagContext implementation (remaining methods)
    // -----------------------------------------------------------------------

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public SmpsSequencerConfig getConfig() {
        return config;
    }

    @Override
    public void setNormalTempo(int tempo) {
        this.normalTempo = tempo;
    }

    @Override
    public int getNormalTempo() {
        return normalTempo;
    }

    @Override
    public void recalculateTempo() {
        calculateTempo();
    }

    @Override
    public void triggerFadeIn() {
        // Parameterless version: use config defaults
        triggerFadeIn(config.getFadeInSteps(), config.getFadeInDelay());
    }

    @Override
    public void setCommData(int value) {
        this.commData = value;
    }

    @Override
    public void writeFm(int port, int reg, int value) {
        synth.writeFm(this, port, reg, value);
    }

    @Override
    public void writePsg(int value) {
        synth.writePsg(this, value);
    }

    @Override
    public void playDac(int noteId) {
        synth.playDac(this, noteId);
    }

    @Override
    public void stopDac() {
        synth.stopDac(this);
    }

    // --- Continuous SFX (delegate to SmpsDriver if available) ---

    @Override
    public boolean isContinuousSfxFlagSet() {
        return (synth instanceof SmpsDriver d) && d.isContinuousSfxFlagSet();
    }

    @Override
    public void clearContinuousSfxId() {
        if (synth instanceof SmpsDriver d) d.clearContinuousSfxId();
    }

    @Override
    public void clearContinuousSfxFlag() {
        if (synth instanceof SmpsDriver d) d.clearContinuousSfxFlag();
    }

    @Override
    public boolean decrementContSfxLoopCnt() {
        return (synth instanceof SmpsDriver d) ? d.decrementContSfxLoopCnt() : true;
    }

    // -----------------------------------------------------------------------
    // Speed multiplier (S3K speed shoes)
    // -----------------------------------------------------------------------

    public void setSpeedMultiplier(int multiplier) {
        this.speedMultiplier = Math.max(1, multiplier);
        if (this.speedMultiplier <= 1) {
            this.speedupTimeout = 0;
        }
    }

    public int getSpeedMultiplier() {
        return speedMultiplier;
    }

    public DebugState debugState() {
        DebugState state = new DebugState();
        state.tempoWeight = tempoWeight;
        state.dividingTiming = dividingTiming;
        for (Track t : tracks) {
            DebugTrack dt = new DebugTrack();
            dt.type = t.type;
            dt.channelId = t.channelId;
            dt.active = t.active;
            dt.duration = t.duration;
            dt.rawDuration = t.rawDuration;
            dt.note = t.note;
            dt.voiceId = t.voiceId;
            dt.volumeOffset = t.volumeOffset;
            dt.keyOffset = t.keyOffset;
            dt.pan = t.pan;
            dt.ams = t.ams;
            dt.fms = t.fms;
            dt.envValue = t.envValue;
            dt.tieNext = t.tieNext;
            dt.modEnabled = t.modEnabled;
            dt.modAccumulator = t.modAccumulator;
            dt.detune = t.detune;
            dt.decayOffset = t.decayOffset;
            dt.loopCounter = (t.loopCounters != null && t.loopCounters.length > 0) ? t.loopCounters[0] : 0;
            dt.position = t.pos;
            dt.fill = t.fill;
            state.tracks.add(dt);
        }
        return state;
    }

    public static class DebugState {
        public int tempoWeight;
        public int dividingTiming;
        public final List<DebugTrack> tracks = new ArrayList<>();
    }

    public static class DebugTrack {
        public TrackType type;
        public int channelId;
        public boolean active;
        public boolean overridden;
        public int duration;
        public int rawDuration;
        public int note;
        public int voiceId;
        public int volumeOffset;
        public int envValue;
        public int keyOffset;
        public int pan;
        public int ams;
        public int fms;
        public boolean tieNext;
        public boolean modEnabled;
        public short modAccumulator;
        public int detune;
        public int decayOffset;
        public int loopCounter;
        public int position;
        public int fill;
    }
}
