package com.openggf.game.sonic2.audio;

import com.openggf.audio.smps.SmpsSequencerConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Sonic2SmpsSequencerConfig {
    public static final int TEMPO_MOD_BASE = 0x100;
    public static final int[] FM_CHANNEL_ORDER = { 0x16, 0, 1, 2, 4, 5, 6 };
    public static final int[] PSG_CHANNEL_ORDER = { 0x80, 0xA0, 0xC0 };

    public static final Map<Integer, Integer> SPEED_UP_TEMPOS;
    public static final SmpsSequencerConfig CONFIG;

    static {
        Map<Integer, Integer> tempos = new HashMap<>();
        tempos.put(Sonic2Music.RESULTS_2P.id, 0x68);
        tempos.put(Sonic2Music.EMERALD_HILL.id, 0xBE);
        tempos.put(Sonic2Music.MYSTIC_CAVE_2P.id, 0xFF);
        tempos.put(Sonic2Music.OIL_OCEAN.id, 0xF0);
        tempos.put(Sonic2Music.METROPOLIS.id, 0xFF);
        tempos.put(Sonic2Music.HILL_TOP.id, 0xDE);
        tempos.put(Sonic2Music.AQUATIC_RUIN.id, 0xFF);
        tempos.put(Sonic2Music.CASINO_NIGHT_2P.id, 0xDD);
        tempos.put(Sonic2Music.CASINO_NIGHT.id, 0x68);
        tempos.put(Sonic2Music.DEATH_EGG.id, 0x80);
        tempos.put(Sonic2Music.MYSTIC_CAVE.id, 0xD6);
        tempos.put(Sonic2Music.EMERALD_HILL_2P.id, 0x7B);
        tempos.put(Sonic2Music.SKY_CHASE.id, 0x7B);
        tempos.put(Sonic2Music.CHEMICAL_PLANT.id, 0xFF);
        tempos.put(Sonic2Music.WING_FORTRESS.id, 0xA8);
        tempos.put(Sonic2Music.HIDDEN_PALACE.id, 0xFF);
        tempos.put(Sonic2Music.OPTIONS.id, 0x87);
        tempos.put(Sonic2Music.SPECIAL_STAGE.id, 0xFF);
        tempos.put(Sonic2Music.BOSS.id, 0xFF);
        tempos.put(Sonic2Music.FINAL_BOSS.id, 0xC9);
        tempos.put(Sonic2Music.ENDING.id, 0x97);
        tempos.put(Sonic2Music.SUPER_SONIC.id, 0xFF);
        tempos.put(Sonic2Music.INVINCIBILITY.id, 0xFF);
        tempos.put(Sonic2Music.EXTRA_LIFE.id, 0xCD);
        tempos.put(Sonic2Music.TITLE.id, 0xCD);
        tempos.put(Sonic2Music.ACT_CLEAR.id, 0xAA);
        tempos.put(Sonic2Music.GAME_OVER.id, 0xF2);
        tempos.put(Sonic2Music.CONTINUE.id, 0xDB);
        tempos.put(Sonic2Music.GOT_EMERALD.id, 0xD5);
        tempos.put(Sonic2Music.CREDITS.id, 0xF0);
        SPEED_UP_TEMPOS = Collections.unmodifiableMap(tempos);
        CONFIG = new SmpsSequencerConfig(SPEED_UP_TEMPOS, TEMPO_MOD_BASE, FM_CHANNEL_ORDER, PSG_CHANNEL_ORDER,
                SmpsSequencerConfig.TempoMode.OVERFLOW2, null);
    }

    private Sonic2SmpsSequencerConfig() {
    }
}
