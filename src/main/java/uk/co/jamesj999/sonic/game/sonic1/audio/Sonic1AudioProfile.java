package uk.co.jamesj999.sonic.game.sonic1.audio;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic1.audio.smps.Sonic1SmpsLoader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sonic 1 audio profile. Provides SMPS loader, sequencer config, and
 * game-specific sound ID mappings for the Sonic 1 68000 sound driver.
 */
public class Sonic1AudioProfile implements GameAudioProfile {

    // -----------------------------------------------------------------------
    // Sonic 1 SFX IDs (from s1disasm Constants.asm, verified against labels)
    // -----------------------------------------------------------------------
    public static final int SFX_JUMP = 0xA0;        // sfx_Jump
    public static final int SFX_LAMPPOST = 0xA1;     // sfx_Lamppost
    public static final int SFX_DEATH = 0xA3;        // sfx_Death (hurt/death sound)
    public static final int SFX_SKID = 0xA4;         // sfx_Skid
    public static final int SFX_HIT_SPIKES = 0xA6;   // sfx_HitSpikes
    public static final int SFX_PUSH = 0xA7;         // sfx_Push
    public static final int SFX_SS_GOAL = 0xA8;      // sfx_SSGoal
    public static final int SFX_SS_ITEM = 0xA9;      // sfx_SSItem
    public static final int SFX_SPLASH = 0xAA;       // sfx_Splash
    public static final int SFX_HIT_BOSS = 0xAC;     // sfx_HitBoss
    public static final int SFX_BUBBLE = 0xAD;       // sfx_Bubble
    public static final int SFX_SHIELD = 0xAF;       // sfx_Shield
    public static final int SFX_DROWN = 0xB2;        // sfx_Drown
    public static final int SFX_BUMPER = 0xB4;       // sfx_Bumper
    public static final int SFX_RING = 0xB5;         // sfx_Ring
    public static final int SFX_SPIKES_MOVE = 0xB6;  // sfx_SpikesMove
    public static final int SFX_ROLL = 0xBE;         // sfx_Roll
    public static final int SFX_BREAK_ITEM = 0xC1;   // sfx_BreakItem
    public static final int SFX_WARNING = 0xC2;      // sfx_Warning (air countdown ding)
    public static final int SFX_RING_LOSS = 0xC6;    // sfx_RingLoss
    public static final int SFX_CASH = 0xC5;          // sfx_Cash (tally complete / "ker-ching")
    public static final int SFX_SPRING = 0xCC;       // sfx_Spring
    public static final int SFX_SWITCH = 0xCD;       // sfx_Switch (tally tick / blip)
    public static final int SFX_RING_LEFT = 0xCE;    // sfx_RingLeft (stereo ring pan)
    public static final int SFX_SIGNPOST = 0xCF;     // sfx_Signpost

    // -----------------------------------------------------------------------
    // Sonic 1 Music IDs (from s1disasm Constants.asm)
    // -----------------------------------------------------------------------
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

    /**
     * GameSound to Sonic 1 SFX ID mappings.
     * Only sounds that have a Sonic 1 equivalent are mapped.
     * Sonic 1 has no spindash, CNZ-specific sounds, or casino bonus sounds.
     */
    private static final Map<GameSound, Integer> SOUND_MAP;

    static {
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, SFX_JUMP);
        map.put(GameSound.RING, SFX_RING);
        map.put(GameSound.RING_LEFT, SFX_RING_LEFT);
        // Sonic 1 uses sfx_Ring (0xB5) for both ears; sfx_RingLeft (0xCE)
        // handles the left-pan variant. There is no separate right-pan SFX.
        map.put(GameSound.RING_RIGHT, SFX_RING);
        map.put(GameSound.RING_SPILL, SFX_RING_LOSS);
        map.put(GameSound.ROLLING, SFX_ROLL);
        map.put(GameSound.SKID, SFX_SKID);
        map.put(GameSound.HURT, SFX_DEATH);
        map.put(GameSound.HURT_SPIKE, SFX_HIT_SPIKES);
        map.put(GameSound.DROWN, SFX_DROWN);
        map.put(GameSound.BADNIK_HIT, SFX_HIT_BOSS);
        map.put(GameSound.CHECKPOINT, SFX_LAMPPOST);
        map.put(GameSound.SPRING, SFX_SPRING);
        map.put(GameSound.BUMPER, SFX_BUMPER);
        map.put(GameSound.SPLASH, SFX_SPLASH);
        map.put(GameSound.AIR_DING, SFX_WARNING);
        // GameSound.SPINDASH_CHARGE - no S1 equivalent (spindash not in original S1)
        // GameSound.SPINDASH_RELEASE - no S1 equivalent
        // GameSound.BONUS_BUMPER - CNZ-specific, not in S1
        // GameSound.LARGE_BUMPER - CNZ-specific, not in S1
        // GameSound.FLIPPER - CNZ-specific, not in S1
        // GameSound.CNZ_LAUNCH - CNZ-specific, not in S1
        // GameSound.CNZ_ELEVATOR - CNZ-specific, not in S1
        // GameSound.SLOW_SMASH - special stage bomb, not in S1
        // GameSound.ERROR - no direct S1 equivalent
        // GameSound.CASINO_BONUS - CNZ-specific, not in S1
        SOUND_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public SmpsLoader createSmpsLoader(Rom rom) {
        return new Sonic1SmpsLoader(rom);
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return Sonic1SmpsSequencerConfig.CONFIG;
    }

    @Override
    public int getSpeedShoesOnCommandId() {
        return Sonic1SmpsConstants.CMD_SPEED_UP;
    }

    @Override
    public int getSpeedShoesOffCommandId() {
        return Sonic1SmpsConstants.CMD_SLOW_DOWN;
    }

    @Override
    public int getInvincibilityMusicId() {
        return MUS_INVINCIBILITY;
    }

    @Override
    public int getExtraLifeMusicId() {
        return MUS_EXTRA_LIFE;
    }

    @Override
    public boolean isMusicOverride(int musicId) {
        return musicId == MUS_INVINCIBILITY || musicId == MUS_EXTRA_LIFE;
    }

    @Override
    public boolean handleSystemCommand(int soundId, AudioManager manager) {
        if (soundId == Sonic1SmpsConstants.CMD_FADE_OUT) {
            manager.fadeOutMusic();
            return true;
        } else if (soundId == Sonic1SmpsConstants.CMD_STOP_ALL) {
            manager.stopMusic();
            return true;
        } else if (soundId == Sonic1SmpsConstants.CMD_SEGA) {
            // SEGA PCM sample - only used on title screen, not yet implemented
            return true;
        }
        return false;
    }

    /**
     * Returns the GameSound to SFX ID mapping for Sonic 1.
     * Used by the game class to configure AudioManager's sound map.
     *
     * @return unmodifiable map of GameSound enum values to Sonic 1 SFX IDs
     */
    public Map<GameSound, Integer> getSoundMap() {
        return SOUND_MAP;
    }

    /**
     * Look up sound priority for a given sound ID using the Sonic 1 priority table.
     *
     * @param soundId any sound ID in the 0x81-0xE4 range
     * @return priority value from the ROM priority table
     */
    public int getSfxPriority(int soundId) {
        return Sonic1SmpsConstants.getSfxPriority(soundId);
    }
}
