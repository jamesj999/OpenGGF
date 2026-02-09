package uk.co.jamesj999.sonic.game.sonic2.audio;

import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic2.audio.smps.Sonic2SmpsLoader;
public class Sonic2AudioProfile implements GameAudioProfile {
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
    public int getSfxPriority(int soundId) {
        return Sonic2SmpsConstants.getSfxPriority(soundId);
    }
}
