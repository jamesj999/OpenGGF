package uk.co.jamesj999.sonic.audio;

import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencerConfig;
import uk.co.jamesj999.sonic.data.Rom;

import java.util.Map;

public interface GameAudioProfile {

    /** How speed shoes affect music playback. */
    enum SpeedMode {
        /** S1/S2: swap to a faster tempo value from the speed-up table. */
        TEMPO_SWAP,
        /** S3K: multiply frame ticks (music updates multiple times per frame). */
        FRAME_MULTIPLY
    }

    SmpsLoader createSmpsLoader(Rom rom);

    SmpsSequencerConfig getSequencerConfig();

    int getSpeedShoesOnCommandId();

    int getSpeedShoesOffCommandId();

    int getInvincibilityMusicId();

    int getExtraLifeMusicId();

    default boolean isMusicOverride(int musicId) {
        return musicId == getInvincibilityMusicId() || musicId == getExtraLifeMusicId();
    }

    /**
     * Returns true if SFX should be completely blocked during this music.
     * In the original ROM, only the 1-up jingle sets 1upPlaying flag which blocks SFX.
     * Invincibility music does NOT block SFX - you can still hear rings, jumps, etc.
     */
    default boolean isSfxBlockingMusic(int musicId) {
        return musicId == getExtraLifeMusicId();
    }

    /**
     * Returns the priority for a given sound ID. Higher values = higher priority.
     * Used for SFX channel arbitration.
     */
    default int getSfxPriority(int soundId) {
        return 0x70; // Default priority
    }

    /**
     * Handle a game-specific system command (e.g., fade out, stop all).
     * Called early in {@code AudioManager.playMusic()} dispatch.
     *
     * @param soundId the sound/command ID
     * @param manager the AudioManager instance for executing the command
     * @return true if the command was handled, false if it should continue normal dispatch
     */
    default boolean handleSystemCommand(int soundId, AudioManager manager) {
        return false;
    }

    /** How speed shoes affect music playback. Default: TEMPO_SWAP (S1/S2). */
    default SpeedMode getSpeedMode() {
        return SpeedMode.TEMPO_SWAP;
    }

    /** S3K speed multiplier value. Default 0x08 means ~1.25x speed. */
    default int getSpeedMultiplierValue() {
        return 0x08;
    }

    /**
     * Returns the GameSound to game-specific SFX ID mapping.
     * Used by the game class to configure AudioManager's sound dispatch.
     *
     * @return unmodifiable map of GameSound enum values to native SFX IDs
     */
    Map<GameSound, Integer> getSoundMap();
}
