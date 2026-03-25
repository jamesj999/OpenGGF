package com.openggf.game.sonic2.audio;

import com.openggf.audio.AbstractAudioProfile;
import com.openggf.audio.GameSound;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class Sonic2AudioProfile extends AbstractAudioProfile {

    private static final Map<GameSound, Integer> SOUND_MAP;

    static {
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, Sonic2Sfx.JUMP.id);
        map.put(GameSound.RING_LEFT, Sonic2Sfx.RING_LEFT.id);
        map.put(GameSound.RING_RIGHT, Sonic2Sfx.RING_RIGHT.id);
        map.put(GameSound.RING_SPILL, Sonic2Sfx.RING_SPILL.id);
        map.put(GameSound.SPINDASH_CHARGE, Sonic2Sfx.SPINDASH_CHARGE.id);
        map.put(GameSound.SPINDASH_RELEASE, Sonic2Sfx.SPINDASH_RELEASE.id);
        map.put(GameSound.SKID, Sonic2Sfx.SKIDDING.id);
        map.put(GameSound.HURT, Sonic2Sfx.HURT.id);
        map.put(GameSound.HURT_SPIKE, Sonic2Sfx.HURT_BY_SPIKES.id);
        map.put(GameSound.DROWN, Sonic2Sfx.DROWN.id);
        map.put(GameSound.BADNIK_HIT, Sonic2Sfx.EXPLOSION.id);
        map.put(GameSound.CHECKPOINT, Sonic2Sfx.CHECKPOINT.id);
        map.put(GameSound.SPRING, Sonic2Sfx.SPRING.id);
        map.put(GameSound.BUMPER, Sonic2Sfx.BUMPER.id);
        map.put(GameSound.BONUS_BUMPER, Sonic2Sfx.BONUS_BUMPER.id);
        map.put(GameSound.LARGE_BUMPER, Sonic2Sfx.LARGE_BUMPER.id);
        map.put(GameSound.FLIPPER, Sonic2Sfx.FLIPPER.id);
        map.put(GameSound.CNZ_LAUNCH, Sonic2Sfx.CNZ_LAUNCH.id);
        map.put(GameSound.CNZ_ELEVATOR, Sonic2Sfx.CNZ_ELEVATOR.id);
        map.put(GameSound.ROLLING, Sonic2Sfx.ROLL.id);
        map.put(GameSound.ERROR, Sonic2Sfx.ERROR.id);
        map.put(GameSound.SPLASH, Sonic2Sfx.SPLASH.id);
        map.put(GameSound.AIR_DING, Sonic2Sfx.WATER_WARNING.id);
        map.put(GameSound.SLOW_SMASH, Sonic2Sfx.SLOW_SMASH.id);
        map.put(GameSound.CASINO_BONUS, Sonic2Sfx.CASINO_BONUS.id);
        map.put(GameSound.OIL_SLIDE, Sonic2Sfx.OIL_SLIDE.id);
        SOUND_MAP = Collections.unmodifiableMap(map);
    }

    public Sonic2AudioProfile() {
        super(SOUND_MAP);
    }

    @Override
    public SmpsLoader createSmpsLoader(Rom rom) {
        return new Sonic2SmpsLoader(rom);
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return Sonic2SmpsSequencerConfig.CONFIG;
    }

    @Override
    public int getSpeedShoesOnCommandId() {
        return Sonic2SmpsConstants.CMD_SPEED_UP;
    }

    @Override
    public int getSpeedShoesOffCommandId() {
        return Sonic2SmpsConstants.CMD_SLOW_DOWN;
    }

    @Override
    public int getInvincibilityMusicId() {
        return Sonic2Music.INVINCIBILITY.id;
    }

    @Override
    public int getExtraLifeMusicId() {
        return Sonic2Music.EXTRA_LIFE.id;
    }

    @Override
    public int getDrowningMusicId() {
        return Sonic2Music.UNDERWATER.id;
    }

    @Override
    public int getSuperSonicMusicId() {
        return Sonic2Music.SUPER_SONIC.id;
    }

    @Override
    public int getSfxPriority(int soundId) {
        return Sonic2SmpsConstants.getSfxPriority(soundId);
    }
}
