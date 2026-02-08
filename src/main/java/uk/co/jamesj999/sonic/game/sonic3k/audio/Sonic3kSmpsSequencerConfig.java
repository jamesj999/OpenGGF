package uk.co.jamesj999.sonic.game.sonic3k.audio;

import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;

import java.util.Collections;

/**
 * SMPS sequencer configuration for Sonic 3 &amp; Knuckles.
 *
 * <p>From DefDrv.txt (S&amp;K final):
 * <ul>
 *   <li>PtrFmt = Z80 (relativePointers=false)</li>
 *   <li>TempoMode = OVERFLOW</li>
 *   <li>Tempo1Tick = DOTEMPO (tempoOnFirstTick=true)</li>
 *   <li>ModAlgo = Z80 (applyModOnNote=true, halveModSteps=true)</li>
 *   <li>VolMode = BIT7</li>
 *   <li>NoteOnPrevent = HOLD</li>
 *   <li>DelayFreq = KEEP</li>
 *   <li>PSG envelope 80 = RESET</li>
 *   <li>FadeOutSteps = 0x28, FadeOutDelay = 6</li>
 *   <li>FadeInSteps = 0x40, FadeInDelay = 2</li>
 *   <li>FMChnOrder = 16 0 1 2 4 5 6 (same as S2)</li>
 * </ul>
 *
 * <p>S3K has no speed-up tempo table; speed shoes use frame multiplier instead
 * (Z80 RAM 0x1C08). The speed-up tempos map is empty.
 */
public final class Sonic3kSmpsSequencerConfig {

    public static final int TEMPO_MOD_BASE = 0x100;
    public static final int[] FM_CHANNEL_ORDER = { 0x16, 0, 1, 2, 4, 5, 6 };
    public static final int[] PSG_CHANNEL_ORDER = { 0x80, 0xA0, 0xC0 };

    /** Pre-built sequencer config instance for S3K. */
    public static final SmpsSequencerConfig CONFIG;

    static {
        CONFIG = new SmpsSequencerConfig.Builder()
                .speedUpTempos(Collections.emptyMap())
                .tempoModBase(TEMPO_MOD_BASE)
                .fmChannelOrder(FM_CHANNEL_ORDER)
                .psgChannelOrder(PSG_CHANNEL_ORDER)
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .applyModOnNote(true)       // ModAlgo = Z80
                .halveModSteps(true)        // Z80 driver halves mod steps (srl a)
                .relativePointers(false)    // PtrFmt = Z80 (absolute addresses)
                .tempoOnFirstTick(true)     // Tempo1Tick = DOTEMPO
                .volMode(SmpsSequencerConfig.VolMode.BIT7)
                .psgEnvCmd80(SmpsSequencerConfig.PsgEnvCmd80.RESET)
                .noteOnPrevent(SmpsSequencerConfig.NoteOnPrevent.HOLD)
                .delayFreq(SmpsSequencerConfig.DelayFreq.KEEP)
                .coordFlagHandler(new Sonic3kCoordFlagHandler())
                .modAlgo(SmpsSequencerConfig.ModAlgo.MOD_Z80)
                .fadeOutDelay(6)            // FadeOutDelay = 6
                .fadeOutSteps(0x28)         // FadeOutSteps = 28h
                .fadeInSteps(0x40)          // FadeInSteps = 40h
                .fadeInDelay(2)             // FadeInDelay = 2
                .build();
    }

    private Sonic3kSmpsSequencerConfig() {
    }
}
