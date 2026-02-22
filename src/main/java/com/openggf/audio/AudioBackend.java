package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencerConfig;

public interface AudioBackend {
    void init();

    void setAudioProfile(GameAudioProfile profile);

    /**
     * Plays music by ID (potentially loading from ROM or fallback map).
     * 
     * @param musicId The music ID from the ROM/LevelData.
     */
    void playMusic(int musicId);

    void playSmps(AbstractSmpsData data, DacData dacData);

    void playSfxSmps(AbstractSmpsData data, DacData dacData);

    void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch);

    /**
     * Plays an SFX with an explicit sequencer config override.
     * Used for donor (cross-game) SFX that need a different driver configuration
     * than the base game's.
     */
    default void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch,
                             SmpsSequencerConfig config) {
        playSfxSmps(data, dacData, pitch);
    }

    /**
     * Plays a sound effect by name (mapped to a WAV file).
     * 
     * @param sfxName The name of the SFX (e.g., "JUMP", "RING").
     */
    void playSfx(String sfxName);

    void playSfx(String sfxName, float pitch);

    /**
     * Stops any active music/streaming playback.
     */
    void stopPlayback();

    /**
     * Stops all playing SFX without affecting music.
     * Clears both SFX sequencers in the active music driver and the standalone SFX stream.
     */
    void stopAllSfx();

    /**
     * Fade out the currently playing music over time.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * @param steps total number of volume steps (ROM default: 0x28 = 40)
     * @param delay frames between each volume step (ROM default: 3)
     */
    void fadeOutMusic(int steps, int delay);

    void toggleMute(ChannelType type, int channel);

    void toggleSolo(ChannelType type, int channel);

    boolean isMuted(ChannelType type, int channel);

    boolean isSoloed(ChannelType type, int channel);

    void setSpeedShoes(boolean enabled);

    /**
     * Set the speed multiplier on the music sequencer (S3K speed shoes).
     * A multiplier of 1 means normal speed, >1 means extra tick calls per frame.
     */
    default void setSpeedMultiplier(int multiplier) {
        // Default no-op; backends that support S3K override this.
    }

    void restoreMusic();

    /**
     * Ends a temporary music override (e.g., invincibility). If the override is
     * currently playing, it should restore the previous track. If it is queued
     * beneath another override, it should be removed so it does not resume.
     *
     * @param musicId The music ID to end.
     */
    void endMusicOverride(int musicId);

    void update();

    void destroy();

    /**
     * Pauses audio playback. Called when the game window is minimized or loses focus.
     * Audio should be suspended but state preserved so it can resume seamlessly.
     */
    void pause();

    /**
     * Resumes audio playback after being paused.
     * Called when the game window is restored or regains focus.
     */
    void resume();
}
