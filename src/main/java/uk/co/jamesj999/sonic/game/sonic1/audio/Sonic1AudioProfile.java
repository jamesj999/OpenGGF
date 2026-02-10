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

    /**
     * GameSound to Sonic 1 SFX ID mappings.
     * Only sounds that have a Sonic 1 equivalent are mapped.
     * Sonic 1 has no spindash, CNZ-specific sounds, or casino bonus sounds.
     */
    private static final Map<GameSound, Integer> SOUND_MAP;

    static {
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, Sonic1Sfx.JUMP.id);
        map.put(GameSound.RING_LEFT, Sonic1Sfx.RING_LEFT.id);
        map.put(GameSound.RING_RIGHT, Sonic1Sfx.RING.id);
        map.put(GameSound.RING_SPILL, Sonic1Sfx.RING_LOSS.id);
        map.put(GameSound.ROLLING, Sonic1Sfx.ROLL.id);
        map.put(GameSound.SKID, Sonic1Sfx.SKID.id);
        map.put(GameSound.HURT, Sonic1Sfx.DEATH.id);
        map.put(GameSound.HURT_SPIKE, Sonic1Sfx.SPIKE_HIT.id);
        map.put(GameSound.DROWN, Sonic1Sfx.DROWN.id);
        map.put(GameSound.BADNIK_HIT, Sonic1Sfx.HIT_BOSS.id);
        map.put(GameSound.CHECKPOINT, Sonic1Sfx.LAMPPOST.id);
        map.put(GameSound.SPRING, Sonic1Sfx.SPRING.id);
        map.put(GameSound.BUMPER, Sonic1Sfx.BUMPER.id);
        map.put(GameSound.SPLASH, Sonic1Sfx.SPLASH.id);
        map.put(GameSound.AIR_DING, Sonic1Sfx.WARNING.id);
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
        return Sonic1Music.INVINCIBILITY.id;
    }

    @Override
    public int getExtraLifeMusicId() {
        return Sonic1Music.EXTRA_LIFE.id;
    }

    @Override
    public boolean isMusicOverride(int musicId) {
        return musicId == Sonic1Music.INVINCIBILITY.id || musicId == Sonic1Music.EXTRA_LIFE.id;
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
