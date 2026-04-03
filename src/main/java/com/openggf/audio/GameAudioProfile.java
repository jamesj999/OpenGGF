package com.openggf.audio;

import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;

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

    /**
     * Returns the drowning countdown music ID for this game.
     *
     * <p>Examples:
     * <ul>
     *   <li>Sonic 1: 0x92</li>
     *   <li>Sonic 2: 0xDC</li>
     *   <li>Sonic 3&amp;K: 0x31</li>
     * </ul>
     *
     * @return music ID to play when underwater air reaches the countdown threshold
     */
    int getDrowningMusicId();

    /** Returns the Super Sonic music ID, or -1 if not applicable. */
    default int getSuperSonicMusicId() {
        return -1;
    }

    default boolean isMusicOverride(int musicId) {
        return musicId == getInvincibilityMusicId()
                || musicId == getExtraLifeMusicId()
                || musicId == getSuperSonicMusicId();
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
     * Returns true if the SFX ID is a continuous SFX (cfx_*).
     *
     * <p>Continuous SFX (S3K: 0xBC-0xDB) use a special looping mechanism in the
     * Z80 sound driver. When re-triggered while already playing, the driver
     * extends playback instead of restarting, producing seamless sustained sound.
     * The 0xFC coord flag ({@code cfLoopContinuousSFX}) checks the extension flag
     * to decide whether to loop or stop.
     */
    default boolean isContinuousSfx(int sfxId) {
        return false;
    }

    /**
     * Returns true if the SFX ID is a "special" SFX class for this game profile.
     *
     * <p>Some drivers (notably Sonic 1 68k) route special SFX through dedicated
     * tracks with different override rules than normal SFX.
     */
    default boolean isSpecialSfx(int soundId) {
        return false;
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
