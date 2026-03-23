package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioManager {
    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static AudioManager instance;
    private AudioBackend backend;
    private SmpsLoader smpsLoader;
    private DacData dacData;
    private Map<GameSound, Integer> soundMap;
    private GameAudioProfile audioProfile;
    private boolean ringLeft = true;

    // Donor audio overlay: secondary SFX path for cross-game feature donation
    private final Map<String, SmpsLoader> donorLoaders = new HashMap<>();
    private final Map<String, DacData> donorDacData = new HashMap<>();
    private final Map<String, SmpsSequencerConfig> donorConfigs = new HashMap<>();
    private final Map<GameSound, DonorSfxBinding> donorSoundBindings = new EnumMap<>(GameSound.class);

    private record DonorSfxBinding(String gameId, int sfxId) {}

    private AudioManager() {
        // Default to NullBackend
        backend = new NullAudioBackend();
    }

    public static synchronized AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public AudioBackend getBackend() {
        return backend;
    }

    public void setBackend(AudioBackend backend) {
        if (this.backend != null) {
            this.backend.destroy();
        }
        this.backend = backend;
        try {
            this.backend.init();
            this.backend.setAudioProfile(audioProfile);
            LOGGER.info("AudioBackend initialized: " + backend.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize AudioBackend", e);
            this.backend = new NullAudioBackend();
        }
    }

    public void setAudioProfile(GameAudioProfile audioProfile) {
        this.audioProfile = audioProfile;
        if (backend != null) {
            backend.setAudioProfile(audioProfile);
        }
    }

    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    public void setRom(Rom rom) {
        if (audioProfile == null) {
            this.smpsLoader = null;
            this.dacData = null;
            return;
        }
        this.smpsLoader = audioProfile.createSmpsLoader(rom);
        this.dacData = smpsLoader != null ? smpsLoader.loadDacData() : null;
    }

    public void setSoundMap(Map<GameSound, Integer> soundMap) {
        this.soundMap = soundMap;
    }

    public void resetRingSound() {
        ringLeft = true;
    }

    public void playMusic(int musicId) {
        if (audioProfile != null) {
            if (audioProfile.handleSystemCommand(musicId, this)) {
                return;
            }
            if (musicId == audioProfile.getSpeedShoesOnCommandId()) {
                if (audioProfile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    backend.setSpeedMultiplier(audioProfile.getSpeedMultiplierValue());
                } else {
                    backend.setSpeedShoes(true);
                }
                return;
            } else if (musicId == audioProfile.getSpeedShoesOffCommandId()) {
                if (audioProfile.getSpeedMode() == GameAudioProfile.SpeedMode.FRAME_MULTIPLY) {
                    backend.setSpeedMultiplier(1);
                } else {
                    backend.setSpeedShoes(false);
                }
                return;
            }
        }

        if (smpsLoader != null) {
            AbstractSmpsData data = smpsLoader.loadMusic(musicId);
            if (data != null) {
                backend.playSmps(data, dacData);
                return;
            }
        }
        backend.playMusic(musicId);
    }

    public void playSfx(String sfxName) {
        playSfx(sfxName, 1.0f);
    }

    public void playSfx(String sfxName, float pitch) {
        if (smpsLoader != null) {
            AbstractSmpsData sfx = smpsLoader.loadSfx(sfxName);
            if (sfx != null) {
                backend.playSfxSmps(sfx, dacData, pitch);
                return;
            }
        }
        backend.playSfx(sfxName, pitch);
    }

    public void playSfx(GameSound sound) {
        playSfx(sound, 1.0f);
    }

    public void playSfx(GameSound sound, float pitch) {
        if (sound == GameSound.RING) {
            playSfx(ringLeft ? GameSound.RING_LEFT : GameSound.RING_RIGHT, pitch);
            ringLeft = !ringLeft;
            return;
        }

        boolean played = false;
        if (soundMap != null && soundMap.containsKey(sound)) {
            played = playSfx(soundMap.get(sound), pitch);
        }
        if (!played) {
            DonorSfxBinding binding = donorSoundBindings.get(sound);
            if (binding != null) {
                SmpsLoader loader = donorLoaders.get(binding.gameId());
                DacData dData = donorDacData.get(binding.gameId());
                if (loader != null && dData != null) {
                    AbstractSmpsData sfx = loader.loadSfx(binding.sfxId());
                    if (sfx != null) {
                        SmpsSequencerConfig donorConfig = donorConfigs.get(binding.gameId());
                        if (donorConfig != null) {
                            backend.playSfxSmps(sfx, dData, pitch, donorConfig);
                        } else {
                            backend.playSfxSmps(sfx, dData, pitch);
                        }
                        played = true;
                    }
                }
            }
        }
        if (!played) {
            backend.playSfx(sound.name(), pitch);
        }
    }

    public boolean playSfx(int sfxId) {
        return playSfx(sfxId, 1.0f);
    }

    public boolean playSfx(int sfxId, float pitch) {
        if (smpsLoader != null) {
            AbstractSmpsData sfx = smpsLoader.loadSfx(sfxId);
            if (sfx != null) {
                backend.playSfxSmps(sfx, dacData, pitch);
                return true;
            }
        }
        return false;
    }

    /**
     * Plays an SFX from a donor game's SMPS loader with the donor's sequencer config.
     * Used for cross-game SFX that aren't in the base game's sound map (e.g., S3K
     * Super Sonic transformation sound).
     *
     * @param donorGameId the donor game identifier (e.g., "s3k")
     * @param sfxId the SFX ID in the donor game's format
     */
    public void playDonorSfx(String donorGameId, int sfxId) {
        SmpsLoader loader = donorLoaders.get(donorGameId);
        DacData dData = donorDacData.get(donorGameId);
        if (loader != null && dData != null) {
            AbstractSmpsData sfx = loader.loadSfx(sfxId);
            if (sfx != null) {
                SmpsSequencerConfig config = donorConfigs.get(donorGameId);
                if (config != null) {
                    backend.playSfxSmps(sfx, dData, 1.0f, config);
                } else {
                    backend.playSfxSmps(sfx, dData, 1.0f);
                }
            }
        }
    }

    public void update() {
        backend.update();
    }

    /**
     * Plays music from a donor game's SMPS loader with the donor's sequencer config.
     * Used for cross-game Super Sonic music (e.g., S3K invincibility in an S2 base game).
     *
     * @param donorGameId the donor game identifier (e.g., "s3k")
     * @param musicId the music ID in the donor game's format
     */
    public void playDonorMusic(String donorGameId, int musicId) {
        SmpsLoader loader = donorLoaders.get(donorGameId);
        DacData dData = donorDacData.get(donorGameId);
        if (loader != null && dData != null) {
            AbstractSmpsData data = loader.loadMusic(musicId);
            if (data != null) {
                SmpsSequencerConfig config = donorConfigs.get(donorGameId);
                // forceOverride=true: the base game's audioProfile won't recognize
                // donor music IDs, so force the override path to push zone music
                // onto the stack for restoration when Super Sonic ends.
                backend.playSmps(data, dData, config, true);
            }
        }
    }

    public void endMusicOverride(int musicId) {
        backend.endMusicOverride(musicId);
    }

    /**
     * Change the music dividing timing (tempo).
     * ROM: Change_Music_Tempo. Lower values = faster playback.
     *
     * @param newDividingTiming the new dividing timing value
     */
    public void changeMusicTempo(int newDividingTiming) {
        if (backend != null) {
            backend.changeMusicTempo(newDividingTiming);
        }
    }

    /**
     * Stops all playing SFX without affecting music.
     * Clears both SFX sequencers in the active music driver and the standalone SFX stream.
     */
    public void stopAllSfx() {
        if (backend != null) {
            backend.stopAllSfx();
        }
    }

    /**
     * Stops all music and sound playback.
     * Used when exiting special stages or changing game modes.
     */
    public void stopMusic() {
        if (backend != null) {
            backend.stopPlayback();
        }
    }

    /**
     * Fade out the currently playing music using ROM default timing.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * <p>ROM uses fadeOutMusic() in these situations (for future implementation):
     * <ul>
     *   <li>Special stage entry (s2.asm:6540) - IMPLEMENTED</li>
     *   <li>Special stage checkpoint fail (Obj5A, s2.asm:71358, 71878) - IMPLEMENTED</li>
     *   <li>Level entry - before entering a level with title card (s2.asm:4757) - IMPLEMENTED</li>
     *   <li>Boss area triggers - when approaching end-of-act boss fights
     *       (EHZ:20404, MTZ:20512, HTZ:21230, HPZ:21332, ARZ:21421, MCZ:21529, OOZ:21613, CNZ:21760)</li>
     *   <li>Title screen - starting new game (s2.asm:4526)</li>
     *   <li>Demo playback - before playing a demo (s2.asm:4581)</li>
     *   <li>WFZ/DEZ boss setup (s2.asm:77011, 80751)</li>
     *   <li>Ending sequence - final boss defeated, going to credits (s2.asm:82064, 82525)</li>
     * </ul>
     */
    public void fadeOutMusic() {
        // ROM default: 0x28 (40) steps, delay of 3 frames between steps
        fadeOutMusic(0x28, 3);
    }

    /**
     * Fade out the currently playing music over time.
     * ROM equivalent: MusID_FadeOut (0xF9) / zFadeOutMusic.
     * Does not affect SFX - only music channels fade.
     *
     * @param steps total number of volume steps (ROM default: 0x28 = 40)
     * @param delay frames between each volume step (ROM default: 3)
     */
    public void fadeOutMusic(int steps, int delay) {
        if (backend != null) {
            backend.fadeOutMusic(steps, delay);
        }
    }

    /**
     * Registers a donor SmpsLoader and DacData for cross-game SFX playback.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData) {
        donorLoaders.put(gameId, loader);
        this.donorDacData.put(gameId, dacData);
    }

    /**
     * Registers a donor SmpsLoader, DacData, and SmpsSequencerConfig for cross-game SFX playback.
     * The config will be passed to the backend so the donor SFX uses the correct driver settings.
     */
    public void registerDonorLoader(String gameId, SmpsLoader loader, DacData dacData,
                                    SmpsSequencerConfig config) {
        donorLoaders.put(gameId, loader);
        this.donorDacData.put(gameId, dacData);
        if (config != null) {
            donorConfigs.put(gameId, config);
        }
    }

    /**
     * Registers a donor sound binding so that a GameSound missing from the
     * base game's sound map will be routed through the specified donor loader.
     */
    public void registerDonorSound(GameSound sound, String gameId, int sfxId) {
        donorSoundBindings.put(sound, new DonorSfxBinding(gameId, sfxId));
    }

    /**
     * Clears all donor audio state (loaders, DAC data, and sound bindings).
     */
    public void clearDonorAudio() {
        donorLoaders.clear();
        donorDacData.clear();
        donorConfigs.clear();
        donorSoundBindings.clear();
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Used by TestEnvironment to prevent state leaking between tests
     * (e.g. Sonic 1 SMPS loader contaminating Sonic 2 tests).
     */
    public void resetState() {
        if (backend != null) {
            backend.stopPlayback();
        }
        this.smpsLoader = null;
        this.dacData = null;
        this.soundMap = null;
        this.audioProfile = null;
        this.ringLeft = true;
        clearDonorAudio();
    }

    public void destroy() {
        if (backend != null) {
            backend.destroy();
        }
    }

    /**
     * Pauses audio playback. Called when the game window is minimized or loses focus.
     */
    public void pause() {
        if (backend != null) {
            backend.pause();
        }
    }

    /**
     * Resumes audio playback after being paused.
     */
    public void resume() {
        if (backend != null) {
            backend.resume();
        }
    }
}
