package uk.co.jamesj999.sonic.game.sonic1.audio;

import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.data.Rom;

/**
 * Sonic 1 audio profile. Stub for now - audio loading will be implemented later.
 * The engine handles null SmpsLoader gracefully.
 */
public class Sonic1AudioProfile implements GameAudioProfile {
    // Sonic 1 SFX IDs (from s1disasm Constants.asm)
    public static final int SFX_JUMP = 0xA0;
    public static final int SFX_LAMPPOST = 0xA1;
    public static final int SFX_HIT_SPIKES = 0xA3;
    public static final int SFX_SKID = 0xA4;
    public static final int SFX_SPLASH = 0xAA;
    public static final int SFX_RING = 0xB5;
    public static final int SFX_SPRING = 0xB1;
    public static final int SFX_HURT = 0xA3;
    public static final int SFX_RING_LOSS = 0xA6;

    // Sonic 1 Music IDs
    public static final int MUS_GHZ = 0x81;
    public static final int MUS_LZ = 0x82;
    public static final int MUS_MZ = 0x83;
    public static final int MUS_SLZ = 0x84;
    public static final int MUS_SYZ = 0x85;
    public static final int MUS_SBZ = 0x86;
    public static final int MUS_INVINCIBILITY = 0x87;
    public static final int MUS_EXTRA_LIFE = 0x88;
    public static final int MUS_SPECIAL_STAGE = 0x89;
    public static final int MUS_TITLE = 0x8A;
    public static final int MUS_ENDING = 0x8B;
    public static final int MUS_BOSS = 0x8C;
    public static final int MUS_FZ = 0x8D;
    public static final int MUS_GOT_THROUGH = 0x8E;
    public static final int MUS_GAME_OVER = 0x8F;
    public static final int MUS_CONTINUE = 0x90;
    public static final int MUS_CREDITS = 0x91;
    public static final int MUS_DROWNING = 0x92;
    public static final int MUS_CHAOS_EMERALD = 0x93;

    // Speed shoes: Sonic 1 doesn't have a separate speed shoes command like S2.
    // Instead it plays a specific music track. Use placeholder values.
    public static final int CMD_SPEED_UP = 0xE0;
    public static final int CMD_SLOW_DOWN = 0xE4;

    @Override
    public SmpsLoader createSmpsLoader(Rom rom) {
        // S1 audio loading will be implemented later
        return null;
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return null;
    }

    @Override
    public int getSpeedShoesOnCommandId() {
        return CMD_SPEED_UP;
    }

    @Override
    public int getSpeedShoesOffCommandId() {
        return CMD_SLOW_DOWN;
    }

    @Override
    public int getInvincibilityMusicId() {
        return MUS_INVINCIBILITY;
    }

    @Override
    public int getExtraLifeMusicId() {
        return MUS_EXTRA_LIFE;
    }
}
