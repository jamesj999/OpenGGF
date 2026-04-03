package com.openggf.game.sonic3k.audio;

import com.openggf.audio.AbstractAudioProfile;
import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;

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
public class Sonic3kAudioProfile extends AbstractAudioProfile {

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
        map.put(GameSound.HURT, Sonic3kSfx.DEATH.id);
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
        map.put(GameSound.GRAB, Sonic3kSfx.GRAB.id);
        map.put(GameSound.GLIDE_LAND, Sonic3kSfx.GLIDE_LAND.id);
        SOUND_MAP = Collections.unmodifiableMap(map);
    }

    public Sonic3kAudioProfile() {
        super(SOUND_MAP);
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
    protected int getFadeOutCommandId() {
        return Sonic3kSmpsConstants.CMD_FADE_OUT;
    }

    @Override
    protected int getStopAllCommandId() {
        return Sonic3kSmpsConstants.CMD_STOP_ALL;
    }

    @Override
    protected int getSegaCommandId() {
        return Sonic3kSmpsConstants.CMD_SEGA;
    }

    /** S3K fade-out uses delay 6 instead of the S1/S2 default delay 3. */
    @Override
    protected void executeFadeOut(AudioManager manager) {
        manager.fadeOutMusic(0x28, 6);
    }

    @Override
    public boolean isContinuousSfx(int sfxId) {
        // ROM: sfx__FirstContinuous = 0xBC (sfx_SlideSkidLoud)
        return sfxId >= Sonic3kSfx.SLIDE_SKID_LOUD.id;
    }

    @Override
    public SpeedMode getSpeedMode() {
        return SpeedMode.FRAME_MULTIPLY;
    }

    @Override
    public int getSpeedMultiplierValue() {
        return 0x08; // Standard speed shoes: 125% speed
    }
}
