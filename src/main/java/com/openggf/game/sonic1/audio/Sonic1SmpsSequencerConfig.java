package com.openggf.game.sonic1.audio;

import com.openggf.audio.smps.SmpsSequencerConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SMPS sequencer configuration for Sonic 1.
 *
 * <p>Key differences from Sonic 2:
 * <ul>
 *   <li>Speed-up tempo table has only 8 entries (music IDs 0x81-0x88),
 *       covering zone themes + invincibility + extra life. The s1disasm notes
 *       that songs beyond the table will read into MusicIndex as fallback tempos.</li>
 *   <li>FM and PSG channel orders are the same as Sonic 2.</li>
 *   <li>Tempo algorithm uses the same TIMEOUT approach (counter decremented per
 *       frame, track update skipped when it wraps). TEMPO_MOD_BASE is 0x100.</li>
 * </ul>
 *
 * <p>Speed-up tempo values from s1disasm SpeedUpIndex (s1.sounddriver.asm).
 */
public final class Sonic1SmpsSequencerConfig {

    /**
     * Base value for the tempo modulo counter.
     * The sequencer decrements a counter each frame; when it wraps past zero
     * the track advances. Setting this to 0x100 matches the S1 TIMEOUT algorithm.
     */
    public static final int TEMPO_MOD_BASE = 0x100;

    /** FM channel assignment order: DAC(0x16), FM1-FM6. Same as S2. */
    public static final int[] FM_CHANNEL_ORDER = { 0x16, 0, 1, 2, 4, 5, 6 };

    /** PSG channel assignment order: PSG1(0x80), PSG2(0xA0), PSG3(0xC0). Same as S2. */
    public static final int[] PSG_CHANNEL_ORDER = { 0x80, 0xA0, 0xC0 };

    /**
     * Speed-up tempos map: music ID to sped-up main tempo byte.
     * Only 8 entries in Sonic 1 (0x81-0x88), from s1disasm SpeedUpIndex.
     */
    public static final Map<Integer, Integer> SPEED_UP_TEMPOS;

    /** Pre-built sequencer config instance. */
    public static final SmpsSequencerConfig CONFIG;

    static {
        Map<Integer, Integer> tempos = new HashMap<>();
        tempos.put(Sonic1Music.GHZ.id, 0x07);
        tempos.put(Sonic1Music.LZ.id, 0x72);
        tempos.put(Sonic1Music.MZ.id, 0x73);
        tempos.put(Sonic1Music.SLZ.id, 0x26);
        tempos.put(Sonic1Music.SYZ.id, 0x15);
        tempos.put(Sonic1Music.SBZ.id, 0x08);
        tempos.put(Sonic1Music.INVINCIBILITY.id, 0xFF);
        tempos.put(Sonic1Music.EXTRA_LIFE.id, 0x05);
        SPEED_UP_TEMPOS = Collections.unmodifiableMap(tempos);

        // Sonic 1 coord flag differences: ED is 1 byte (0 params), EE is 1 byte (0 params)
        // S2 defaults: ED = 1 param, EE = 0 params. Only ED needs overriding.
        Map<Integer, Integer> coordOverrides = new HashMap<>();
        coordOverrides.put(0xED, 0);  // S1: ClearPush (no param) vs S2: IGNORE (1 param)

        CONFIG = new SmpsSequencerConfig(
                SPEED_UP_TEMPOS, TEMPO_MOD_BASE, FM_CHANNEL_ORDER, PSG_CHANNEL_ORDER,
                SmpsSequencerConfig.TempoMode.TIMEOUT, coordOverrides,
                false,            // S1: don't apply modulation during note start (ModAlgo = 68k)
                false,            // S1: don't halve mod steps (68k driver has no srl a)
                Set.of(0xEE),
                true,             // S1: PC-relative pointers for F6/F7/F8
                true);            // S1: process tempo on first frame (DOTEMPO)
    }

    private Sonic1SmpsSequencerConfig() {
    }
}
