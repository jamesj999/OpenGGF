package uk.co.jamesj999.sonic.game.sonic3k.audio;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic3k.audio.smps.Sonic3kSmpsLoader;

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
    public boolean isMusicOverride(int musicId) {
        return musicId == Sonic3kMusic.INVINCIBILITY.id
                || musicId == Sonic3kMusic.EXTRA_LIFE.id;
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
}
