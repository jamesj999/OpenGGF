package uk.co.jamesj999.sonic.game.sonic1.audio;

import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        tempos.put(0x81, 0x07);  // GHZ
        tempos.put(0x82, 0x72);  // LZ
        tempos.put(0x83, 0x73);  // MZ
        tempos.put(0x84, 0x26);  // SLZ
        tempos.put(0x85, 0x15);  // SYZ
        tempos.put(0x86, 0x08);  // SBZ
        tempos.put(0x87, 0xFF);  // Invincibility
        tempos.put(0x88, 0x05);  // Extra Life
        SPEED_UP_TEMPOS = Collections.unmodifiableMap(tempos);

        // Sonic 1 coord flag differences: ED is 1 byte (0 params), EE is 1 byte (0 params)
        // S2 defaults: ED = 1 param, EE = 0 params. Only ED needs overriding.
        Map<Integer, Integer> coordOverrides = new HashMap<>();
        coordOverrides.put(0xED, 0);  // S1: ClearPush (no param) vs S2: IGNORE (1 param)

        CONFIG = new SmpsSequencerConfig(
                SPEED_UP_TEMPOS, TEMPO_MOD_BASE, FM_CHANNEL_ORDER, PSG_CHANNEL_ORDER,
                SmpsSequencerConfig.TempoMode.TIMEOUT, coordOverrides);
    }

    private Sonic1SmpsSequencerConfig() {
    }
}
