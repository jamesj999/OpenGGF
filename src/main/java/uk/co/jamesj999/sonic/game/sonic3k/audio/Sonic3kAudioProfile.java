package uk.co.jamesj999.sonic.game.sonic3k.audio;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic3k.audio.smps.Sonic3kSmpsLoader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sonic 3 &amp; Knuckles audio profile.
 *
 * <p>Key differences from S1/S2:
 * <ul>
 *   <li>Speed shoes use FRAME_MULTIPLY mode (Z80 RAM 0x1C08) instead of tempo swap.</li>
 *   <li>S3K coordination flags are entirely different from S2 (pluggable handler).</li>
 *   <li>Music data is bank-switched raw data (not Saxman compressed).</li>
 *   <li>DAC samples use DPCM compression with bank switching.</li>
 * </ul>
 */
public class Sonic3kAudioProfile implements GameAudioProfile {

    private static final Map<GameSound, Integer> SOUND_MAP;

    static {
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, Sonic3kSfx.JUMP.id);
        map.put(GameSound.RING_LEFT, Sonic3kSfx.RING_LEFT.id);
        map.put(GameSound.RING_RIGHT, Sonic3kSfx.RING_RIGHT.id);
        map.put(GameSound.RING_SPILL, Sonic3kSfx.RING_LOSS.id);
        map.put(GameSound.SPINDASH_CHARGE, Sonic3kSfx.SPINDASH.id);
        map.put(GameSound.SPINDASH_RELEASE, Sonic3kSfx.SPINDASH.id);
        map.put(GameSound.SKID, Sonic3kSfx.SKID.id);
        map.put(GameSound.HURT, Sonic3kSfx.SPIKE_HIT.id);
        map.put(GameSound.HURT_SPIKE, Sonic3kSfx.SPIKE_HIT.id);
        map.put(GameSound.BADNIK_HIT, Sonic3kSfx.BOSS_HIT.id);
        map.put(GameSound.CHECKPOINT, Sonic3kSfx.STARPOST.id);
        map.put(GameSound.SPRING, Sonic3kSfx.SPRING.id);
        map.put(GameSound.BUMPER, Sonic3kSfx.BUMPER.id);
        map.put(GameSound.ROLLING, Sonic3kSfx.ROLL.id);
        map.put(GameSound.SPLASH, Sonic3kSfx.SPLASH.id);
        map.put(GameSound.DROWN, Sonic3kSfx.DROWN.id);
        map.put(GameSound.AIR_DING, Sonic3kSfx.AIR_DING.id);
        map.put(GameSound.FIRE_SHIELD, Sonic3kSfx.FIRE_SHIELD.id);
        map.put(GameSound.LIGHTNING_SHIELD, Sonic3kSfx.LIGHTNING_SHIELD.id);
        map.put(GameSound.BUBBLE_SHIELD, Sonic3kSfx.BUBBLE_SHIELD.id);
        map.put(GameSound.FIRE_ATTACK, Sonic3kSfx.FIRE_ATTACK.id);
        map.put(GameSound.LIGHTNING_ATTACK, Sonic3kSfx.ELECTRIC_ATTACK.id);
        map.put(GameSound.BUBBLE_ATTACK, Sonic3kSfx.BUBBLE_ATTACK.id);
        map.put(GameSound.INSTA_SHIELD, Sonic3kSfx.INSTA_SHIELD.id);
        SOUND_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public SmpsLoader createSmpsLoader(Rom rom) {
        return new Sonic3kSmpsLoader(rom);
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return Sonic3kSmpsSequencerConfig.CONFIG;
    }

    @Override
    public int getSpeedShoesOnCommandId() {
        return Sonic3kSmpsConstants.CMD_SPEED_UP;
    }

    @Override
    public int getSpeedShoesOffCommandId() {
        return Sonic3kSmpsConstants.CMD_SLOW_DOWN;
    }

    @Override
    public int getInvincibilityMusicId() {
        return Sonic3kMusic.INVINCIBILITY.id;
    }

    @Override
    public int getExtraLifeMusicId() {
        return Sonic3kMusic.EXTRA_LIFE.id;
    }

    @Override
    public int getDrowningMusicId() {
        return Sonic3kMusic.DROWNING.id;
    }

    @Override
    public int getSuperSonicMusicId() {
        return Sonic3kMusic.INVINCIBILITY.id;
    }

    @Override
    public boolean handleSystemCommand(int soundId, AudioManager manager) {
        if (soundId == Sonic3kSmpsConstants.CMD_FADE_OUT) {
            // S3K: FadeOutSteps = 0x28, FadeOutDelay = 6
            manager.fadeOutMusic(0x28, 6);
            return true;
        } else if (soundId == Sonic3kSmpsConstants.CMD_STOP_ALL) {
            manager.stopMusic();
            return true;
        } else if (soundId == Sonic3kSmpsConstants.CMD_SEGA) {
            // SEGA PCM sample - not yet implemented
            return true;
        }
        return false;
    }

    @Override
    public SpeedMode getSpeedMode() {
        return SpeedMode.FRAME_MULTIPLY;
    }

    @Override
    public int getSpeedMultiplierValue() {
        return 0x08; // Standard speed shoes: 125% speed
    }

    @Override
    public Map<GameSound, Integer> getSoundMap() {
        return SOUND_MAP;
    }
}
