package com.openggf.audio;

import java.util.Map;

/**
 * Shared base for game audio profiles. Extracts the common
 * {@link #handleSystemCommand} dispatch pattern (fade-out, stop-all, SEGA PCM)
 * and the immutable sound-map accessor.
 *
 * <p>Subclasses supply game-specific command IDs via {@link #getFadeOutCommandId()},
 * {@link #getStopAllCommandId()}, and {@link #getSegaCommandId()}.  Games that
 * do not use system commands (e.g. Sonic 2, where fade/stop are called directly)
 * can leave the defaults ({@code -1}) so the template returns {@code false}.
 *
 * <p>The fade-out action is delegated to {@link #executeFadeOut(AudioManager)} so
 * that each game can supply its own step/delay parameters.
 */
public abstract class AbstractAudioProfile implements GameAudioProfile {

    private final Map<GameSound, Integer> soundMap;

    protected AbstractAudioProfile(Map<GameSound, Integer> soundMap) {
        this.soundMap = soundMap;
    }

    // ------------------------------------------------------------------
    // System-command template
    // ------------------------------------------------------------------

    /**
     * Returns the sound ID that triggers a music fade-out, or {@code -1} if
     * this game does not route fade-out through {@code playMusic()} dispatch.
     */
    protected int getFadeOutCommandId() {
        return -1;
    }

    /**
     * Returns the sound ID that stops all audio, or {@code -1} if this game
     * does not route stop-all through {@code playMusic()} dispatch.
     */
    protected int getStopAllCommandId() {
        return -1;
    }

    /**
     * Returns the sound ID that triggers the SEGA PCM jingle, or {@code -1}
     * if not applicable.
     */
    protected int getSegaCommandId() {
        return -1;
    }

    /**
     * Executes the fade-out action. Override to supply game-specific
     * step/delay parameters (e.g. S3K uses 0x28 steps, delay 6).
     * The default calls {@link AudioManager#fadeOutMusic()} which uses
     * the S1/S2 ROM defaults (0x28 steps, delay 3).
     */
    protected void executeFadeOut(AudioManager manager) {
        manager.fadeOutMusic();
    }

    /**
     * Template implementation of system-command dispatch.  Checks the
     * sound ID against fade-out, stop-all, and SEGA command IDs and
     * delegates to the appropriate {@link AudioManager} method.
     *
     * <p>If all three command IDs are {@code -1} (the default), this
     * method always returns {@code false}, matching the behaviour of
     * the {@link GameAudioProfile} interface default.
     */
    @Override
    public boolean handleSystemCommand(int soundId, AudioManager manager) {
        if (soundId == getFadeOutCommandId() && getFadeOutCommandId() != -1) {
            executeFadeOut(manager);
            return true;
        } else if (soundId == getStopAllCommandId() && getStopAllCommandId() != -1) {
            manager.stopMusic();
            return true;
        } else if (soundId == getSegaCommandId() && getSegaCommandId() != -1) {
            // SEGA PCM sample - only used on title screen, not yet implemented
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Sound map
    // ------------------------------------------------------------------

    @Override
    public Map<GameSound, Integer> getSoundMap() {
        return soundMap;
    }
}
