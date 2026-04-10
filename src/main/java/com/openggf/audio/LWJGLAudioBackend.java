package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.EngineServices;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.ALC11.*;
import static org.lwjgl.openal.SOFTHRTF.*;

public class LWJGLAudioBackend implements AudioBackend {
    private static final Logger LOGGER = Logger.getLogger(LWJGLAudioBackend.class.getName());

    private final Object streamLock = new Object();
    private final SonicConfigurationService configService;

    private long device;
    private long context;

    private final Map<String, Integer> buffers = new HashMap<>();
    private final List<Integer> sfxSources = new ArrayList<>();
    private int musicSource = -1;

    private AudioStream currentStream;
    private AudioStream sfxStream;
    private int[] streamBuffers;
    private static final int STREAM_BUFFER_COUNT = 3;
    private static final int STREAM_BUFFER_SIZE = 1024;
    // Pre-allocated buffers for fillBuffer() to avoid per-call allocations (~43 times/sec)
    private final short[] streamData = new short[STREAM_BUFFER_SIZE * 2];
    private final short[] sfxStreamData = new short[STREAM_BUFFER_SIZE * 2];
    private int deviceSampleRate = 48000;  // Default fallback, updated in init()
    // Reusable DirectBuffer to avoid allocation in fillBuffer() hot path
    private ShortBuffer directShortBuffer;
    private SmpsSequencer currentSmps;
    private SmpsDriver smpsDriver;

    private static class MusicState {
        final AudioStream stream;
        final SmpsSequencer smps;
        final SmpsDriver driver;
        final int musicId;

        MusicState(AudioStream stream, SmpsSequencer smps, SmpsDriver driver, int musicId) {
            this.stream = stream;
            this.smps = smps;
            this.driver = driver;
            this.musicId = musicId;
        }
    }

    private final Deque<MusicState> musicStack = new ArrayDeque<>();
    private int currentMusicId = -1;
    private volatile boolean pendingRestore = false;
    private volatile boolean sfxBlocked = false;  // Block SFX during override jingle/fade-in (ROM: 1upPlaying, FadeInFlag)

    // Fallback mappings
    private final Map<Integer, String> musicFallback = new HashMap<>();
    private final Map<String, String> sfxFallback = new HashMap<>();

    // Mute/Solo State
    private final boolean[] fmUserMutes = new boolean[6];
    private final boolean[] fmUserSolos = new boolean[6];
    private final boolean[] psgUserMutes = new boolean[4];
    private final boolean[] psgUserSolos = new boolean[4];

    private boolean speedShoesEnabled = false;
    private GameAudioProfile audioProfile;
    private SmpsSequencerConfig smpsConfig;

    public LWJGLAudioBackend() {
        this(com.openggf.game.RuntimeManager.getEngineServices().configuration());
    }

    public LWJGLAudioBackend(SonicConfigurationService configService) {
        this.configService = Objects.requireNonNull(configService, "configService");
        // Initialize fallback mappings
        // SFX
        sfxFallback.put("JUMP", "sfx/jump.wav");
        sfxFallback.put("RING", "sfx/ring.wav");
        sfxFallback.put("SPINDASH", "sfx/spindash.wav");
        sfxFallback.put("SKID", "sfx/skid.wav");
    }

    @Override
    public void setAudioProfile(GameAudioProfile profile) {
        this.audioProfile = profile;
        this.smpsConfig = profile != null ? profile.getSequencerConfig() : null;
    }

    @Override
    public void init() {
        try {
            // Open default device
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) {
                throw new RuntimeException("Could not open ALC device");
            }

            // Request 48000 Hz sample rate explicitly and disable HRTF for clean stereo output
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer contextAttribs = stack.ints(
                    ALC_FREQUENCY, 48000,
                    ALC_HRTF_SOFT, ALC_FALSE,  // Disable HRTF processing
                    0  // Terminate list
                );
                context = alcCreateContext(device, contextAttribs);
            }
            if (context == 0) {
                throw new RuntimeException("Could not create ALC context");
            }

            alcMakeContextCurrent(context);

            // Create capabilities (required for LWJGL OpenAL)
            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            AL.createCapabilities(alcCaps);

            // Verify the actual frequency (may differ from request)
            deviceSampleRate = alcGetInteger(device, ALC_FREQUENCY);
            if (deviceSampleRate <= 0) {
                deviceSampleRate = 48000;  // Use our requested rate as fallback
            }

            if (alGetError() != AL_NO_ERROR) {
                throw new RuntimeException("AL Error during init");
            }

            // Log HRTF status
            if (ALC.getCapabilities().ALC_SOFT_HRTF) {
                int hrtfStatus = alcGetInteger(device, ALC_HRTF_STATUS_SOFT);
                String hrtfStatusStr = switch (hrtfStatus) {
                    case ALC_HRTF_DISABLED_SOFT -> "Disabled";
                    case ALC_HRTF_ENABLED_SOFT -> "Enabled";
                    case ALC_HRTF_DENIED_SOFT -> "Denied";
                    case ALC_HRTF_REQUIRED_SOFT -> "Required";
                    case ALC_HRTF_HEADPHONES_DETECTED_SOFT -> "Headphones Detected";
                    case ALC_HRTF_UNSUPPORTED_FORMAT_SOFT -> "Unsupported Format";
                    default -> "Unknown (" + hrtfStatus + ")";
                };
                LOGGER.info("HRTF Status: " + hrtfStatusStr);
            }

            // Log device info
            String deviceName = alcGetString(device, ALC_DEVICE_SPECIFIER);
            int monoSources = alcGetInteger(device, ALC_MONO_SOURCES);
            int stereoSources = alcGetInteger(device, ALC_STEREO_SOURCES);
            LOGGER.info("OpenAL Device: " + deviceName);
            LOGGER.info("Mono sources: " + monoSources + ", Stereo sources: " + stereoSources);

            LOGGER.info("LWJGL OpenAL Initialized. Device sample rate: " + deviceSampleRate + " Hz, Buffer Size: " + STREAM_BUFFER_SIZE);

            // Preload SFX
            for (String sfxPath : sfxFallback.values()) {
                loadWav(sfxPath);
            }

            // Generate music source
            musicSource = alGenSources();

            // Pre-allocate reusable DirectBuffer to avoid per-fillBuffer allocations
            if (directShortBuffer != null) {
                MemoryUtil.memFree(directShortBuffer);
            }
            directShortBuffer = MemoryUtil.memAllocShort(STREAM_BUFFER_SIZE * 2);

        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "LWJGL OpenAL Init failed", t);
            throw new RuntimeException(t);
        }
    }

    @Override
    public void playMusic(int musicId) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Requesting Music ID: " + Integer.toHexString(musicId));
        }
        stopStream(); // Stop any running stream
        clearMusicStack();
        currentMusicId = -1;

        // Try fallback map first
        String filename = musicFallback.get(musicId);
        if (filename == null) {
            // Default naming convention
            filename = "music/" + Integer.toHexString(musicId).toUpperCase() + ".wav";
        }

        playWav(filename, musicSource, true);
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData) {
        int musicId = data.getId();
        boolean isOverride = audioProfile != null && audioProfile.isMusicOverride(musicId);
        if (isOverride) {
            // ROM behavior: only 1-up jingle (isSfxBlockingMusic) kills active SFX.
            // Non-blocking overrides (invincibility, Super Sonic) let SFX continue.
            if (audioProfile.isSfxBlockingMusic(musicId)) {
                synchronized (streamLock) {
                    if (smpsDriver != null) {
                        smpsDriver.stopAllSfx();
                    }
                    if (sfxStream instanceof SmpsDriver sfxDriver) {
                        sfxDriver.stopAll();
                    }
                    sfxStream = null;
                }
                sfxBlocked = true;
            }
            // Only push state if current music is NOT an override (e.g., not already playing 1up jingle).
            boolean currentIsOverride = audioProfile != null && audioProfile.isMusicOverride(currentMusicId);
            if (!currentIsOverride) {
                pushCurrentState();
            }

            // Just disconnect the current driver from the source without stopping/clearing it.
            alSourceStop(musicSource);
            alSourcei(musicSource, AL_BUFFER, 0);
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            stopStream();
            // Stop music source if playing wav
            alSourceStop(musicSource);
            clearMusicStack();
            // Clean up standalone SFX stream - stopStream() only handles currentStream/smpsDriver,
            // but SFX played before any music was active use a separate sfxStream SmpsDriver.
            // Without this, the sfxStream persists and keeps rendering into fillBuffer() indefinitely.
            synchronized (streamLock) {
                if (sfxStream instanceof SmpsDriver sfxDriver) {
                    sfxDriver.stopAll();
                }
                sfxStream = null;
            }
        }

        smpsDriver = new SmpsDriver(getSmpsOutputRate());

        // Configure Region
        String regionStr = configService.getString(SonicConfiguration.REGION);
        if ("PAL".equalsIgnoreCase(regionStr)) {
            smpsDriver.setRegion(SmpsSequencer.Region.PAL);
        } else {
            smpsDriver.setRegion(SmpsSequencer.Region.NTSC);
        }

        boolean dacInterpolate = configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE);
        smpsDriver.setDacInterpolate(dacInterpolate);
        smpsDriver.setOutputSampleRate(getSmpsOutputRate());
        applyPsgNoiseConfig(smpsDriver);

        boolean fm6DacOff = configService.getBoolean(SonicConfiguration.FM6_DAC_OFF);

        SmpsSequencer seq = new SmpsSequencer(data, dacData, smpsDriver, requireSmpsConfig());
        seq.setSampleRate(smpsDriver.getOutputSampleRate());
        seq.setSpeedShoes(speedShoesEnabled);
        seq.setFm6DacOff(fm6DacOff);
        // Music is the primary voice source for SFX fallback
        seq.setFallbackVoiceData(data);
        smpsDriver.addSequencer(seq, false);
        currentSmps = seq;
        currentMusicId = musicId;

        updateSynthesizerConfig();
        synchronized (streamLock) {
            currentStream = smpsDriver;
        }
        startStream();
    }

    @Override
    public void playSmps(AbstractSmpsData data, DacData dacData,
                         SmpsSequencerConfig config, boolean forceOverride) {
        SmpsSequencerConfig effectiveConfig = (config != null) ? config : requireSmpsConfig();

        int musicId = data.getId();
        boolean isOverride = forceOverride
                || (audioProfile != null && audioProfile.isMusicOverride(musicId));
        if (isOverride) {
            boolean sfxBlocking = audioProfile != null && audioProfile.isSfxBlockingMusic(musicId);
            // ROM: only the 1-up jingle (isSfxBlockingMusic) kills active SFX.
            // Non-blocking overrides (invincibility, Super Sonic) let SFX continue.
            if (sfxBlocking) {
                synchronized (streamLock) {
                    if (smpsDriver != null) {
                        smpsDriver.stopAllSfx();
                    }
                    if (sfxStream instanceof SmpsDriver sfxDriver) {
                        sfxDriver.stopAll();
                    }
                    sfxStream = null;
                }
                sfxBlocked = true;
            }
            boolean currentIsOverride = audioProfile != null && audioProfile.isMusicOverride(currentMusicId);
            if (!currentIsOverride) {
                pushCurrentState();
            }
            alSourceStop(musicSource);
            alSourcei(musicSource, AL_BUFFER, 0);
            currentStream = null;
            currentSmps = null;
            smpsDriver = null;
        } else {
            stopStream();
            alSourceStop(musicSource);
            clearMusicStack();
            synchronized (streamLock) {
                if (sfxStream instanceof SmpsDriver sfxDriver) {
                    sfxDriver.stopAll();
                }
                sfxStream = null;
            }
        }

        smpsDriver = new SmpsDriver(getSmpsOutputRate());

        String regionStr = configService.getString(SonicConfiguration.REGION);
        if ("PAL".equalsIgnoreCase(regionStr)) {
            smpsDriver.setRegion(SmpsSequencer.Region.PAL);
        } else {
            smpsDriver.setRegion(SmpsSequencer.Region.NTSC);
        }

        boolean dacInterpolate = configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE);
        smpsDriver.setDacInterpolate(dacInterpolate);
        smpsDriver.setOutputSampleRate(getSmpsOutputRate());
        applyPsgNoiseConfig(smpsDriver);

        boolean fm6DacOff = configService.getBoolean(SonicConfiguration.FM6_DAC_OFF);

        SmpsSequencer seq = new SmpsSequencer(data, dacData, smpsDriver, effectiveConfig);
        seq.setSampleRate(smpsDriver.getOutputSampleRate());
        seq.setSpeedShoes(speedShoesEnabled);
        seq.setFm6DacOff(fm6DacOff);
        seq.setFallbackVoiceData(data);
        smpsDriver.addSequencer(seq, false);
        currentSmps = seq;
        currentMusicId = musicId;

        updateSynthesizerConfig();
        synchronized (streamLock) {
            currentStream = smpsDriver;
        }
        startStream();
    }

    @Override
    public void playSfxSmps(AbstractSmpsData data, DacData dacData) {
        playSfxSmps(data, dacData, 1.0f);
    }

    @Override
    public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
        playSfxSmps(data, dacData, pitch, null);
    }

    @Override
    public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch,
                             SmpsSequencerConfig config) {
        // ROM behavior: completely block SFX during override jingle and fade-in period
        if (sfxBlocked) {
            return;
        }

        SmpsSequencerConfig effectiveConfig = (config != null) ? config : requireSmpsConfig();

        boolean dacInterpolate = configService.getBoolean(SonicConfiguration.DAC_INTERPOLATE);
        boolean fm6DacOff = configService.getBoolean(SonicConfiguration.FM6_DAC_OFF);

        // Look up SFX priority from game-specific audio profile
        int sfxPriority = (audioProfile != null) ? audioProfile.getSfxPriority(data.getId()) : 0x70;
        boolean specialSfx = (audioProfile != null) && audioProfile.isSpecialSfx(data.getId());

        // --- Continuous SFX detection (Z80: zPlaySound_Bankswitch lines 1937-1965) ---
        // If this SFX is continuous (S3K >= 0xBC) and the same one is already playing,
        // extend playback (set the flag) instead of restarting from scratch.
        boolean isContinuous = (audioProfile != null) && audioProfile.isContinuousSfx(data.getId());
        int contTrackCount = data.getChannels() + data.getPsgChannels();
        if (isContinuous) {
            SmpsDriver targetDriver = null;
            if (smpsDriver != null && currentStream == smpsDriver) {
                targetDriver = smpsDriver;
            } else {
                synchronized (streamLock) {
                    if (sfxStream instanceof SmpsDriver) {
                        targetDriver = (SmpsDriver) sfxStream;
                    }
                }
            }
            if (targetDriver != null && targetDriver.extendContinuousSfx(data.getId(), contTrackCount)) {
                return; // Extended existing playback — no new sequencer needed
            }
        }

        if (smpsDriver != null && currentStream == smpsDriver) {
            // Mix into current driver
            if (isContinuous) {
                smpsDriver.startContinuousSfx(data.getId(), contTrackCount);
            }
            SmpsSequencer seq = new SmpsSequencer(data, dacData, smpsDriver, effectiveConfig);
            seq.setSampleRate(smpsDriver.getOutputSampleRate());
            seq.setFm6DacOff(fm6DacOff);
            seq.setSfxMode(true);
            seq.setPitch(pitch);
            seq.setSfxPriority(sfxPriority);
            seq.setSpecialSfx(specialSfx);
            if (currentSmps != null) {
                seq.setFallbackVoiceData(currentSmps.getSmpsData());
            }
            smpsDriver.addSequencer(seq, true);
        } else {
            // Standalone SFX driver
            synchronized (streamLock) {
                SmpsDriver sfxDriver;
                if (sfxStream instanceof SmpsDriver) {
                    sfxDriver = (SmpsDriver) sfxStream;
                } else {
                    sfxDriver = new SmpsDriver(getSmpsOutputRate());
                    sfxDriver.setDacInterpolate(dacInterpolate);
                    sfxStream = sfxDriver;
                }
                sfxDriver.setOutputSampleRate(getSmpsOutputRate());
                applyPsgNoiseConfig(sfxDriver);
                if (isContinuous) {
                    sfxDriver.startContinuousSfx(data.getId(), contTrackCount);
                }
                SmpsSequencer seq = new SmpsSequencer(data, dacData, sfxDriver, effectiveConfig);
                seq.setSampleRate(sfxDriver.getOutputSampleRate());
                seq.setFm6DacOff(fm6DacOff);
                seq.setSfxMode(true);
                seq.setPitch(pitch);
                seq.setSfxPriority(sfxPriority);
                seq.setSpecialSfx(specialSfx);
                if (currentSmps != null) {
                    seq.setFallbackVoiceData(currentSmps.getSmpsData());
                }
                sfxDriver.addSequencer(seq, true);
            }
        }

        // Ensure stream is running
        int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
        if (queued == 0) {
            alSourceStop(musicSource);
            alSourcei(musicSource, AL_BUFFER, 0);
            startStream();
        }
    }

    private void startStream() {
        if (streamBuffers == null) {
            streamBuffers = new int[STREAM_BUFFER_COUNT];
            for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
                streamBuffers[i] = alGenBuffers();
            }
        }

        for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
            fillBuffer(streamBuffers[i]);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer bufferIds = stack.mallocInt(STREAM_BUFFER_COUNT);
            for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
                bufferIds.put(i, streamBuffers[i]);
            }
            alSourceQueueBuffers(musicSource, bufferIds);
        }
        alSourcePlay(musicSource);
    }

    private void stopStream() {
        if (musicSource >= 0) {
            alSourceStop(musicSource);
            int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
            while (queued > 0) {
                alSourceUnqueueBuffers(musicSource);
                queued--;
            }
            alSourcei(musicSource, AL_BUFFER, 0);
        }

        currentStream = null;
        currentSmps = null;
        if (smpsDriver != null) {
            smpsDriver.stopAll();
            smpsDriver = null;
        }
        currentMusicId = -1;
    }

    private void updateStream() {
        // Check for pending music restoration (deferred from E4 handler)
        if (pendingRestore) {
            pendingRestore = false;
            doRestoreMusic();
            return;
        }

        boolean hasStream;
        synchronized (streamLock) {
            hasStream = currentStream != null || sfxStream != null;
        }
        if (hasStream) {
            int state = alGetSourcei(musicSource, AL_SOURCE_STATE);
            int processed = alGetSourcei(musicSource, AL_BUFFERS_PROCESSED);

            while (processed > 0) {
                int bufferId = alSourceUnqueueBuffers(musicSource);
                fillBuffer(bufferId);
                alSourceQueueBuffers(musicSource, bufferId);
                processed--;
            }

            // Check state again
            state = alGetSourcei(musicSource, AL_SOURCE_STATE);
            if (state != AL_PLAYING) {
                alSourcePlay(musicSource);
            }
        }
    }

    @Override
    public void restoreMusic() {
        // Defer actual restoration to next updateStream cycle to avoid
        // modifying buffers while they're being rendered
        if (!musicStack.isEmpty()) {
            pendingRestore = true;
        }
    }

    private void doRestoreMusic() {
        MusicState savedState = musicStack.pollFirst();
        if (savedState == null || savedState.stream == null || savedState.smps == null
                || savedState.driver == null) {
            return;
        }

        // Stop the current (invincibility/extra-life) music stream
        alSourceStop(musicSource);

        // Unqueue ALL buffers (both processed and queued) to avoid OpenAL errors
        int queued = alGetSourcei(musicSource, AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) {
            alSourceUnqueueBuffers(musicSource);
        }

        // Stop the current (non-saved) smps driver
        if (smpsDriver != null && smpsDriver != savedState.driver) {
            smpsDriver.stopAll();
        }

        // Restore saved state
        synchronized (streamLock) {
            currentStream = savedState.stream;
            currentSmps = savedState.smps;
            smpsDriver = savedState.driver;
            currentMusicId = savedState.musicId;
        }

        if (currentSmps != null) {
            // Restore speed shoes state to the saved sequencer
            currentSmps.setSpeedShoes(speedShoesEnabled);
            currentSmps.refreshAllVoices();
            // ROM: only the 1-up jingle (sfxBlocked/FadeInFlag) fades in on restore.
            // Non-blocking overrides (invincibility, Super Sonic) resume at full volume.
            if (sfxBlocked) {
                currentSmps.setOnFadeComplete(() -> sfxBlocked = false);
                currentSmps.triggerFadeIn();
            }
        }

        startStream();
    }

    private void fillBuffer(int bufferId) {
        int sampleRate;
        synchronized (streamLock) {
            // Clear and reuse pre-allocated buffer
            Arrays.fill(streamData, (short) 0);
            if (currentStream != null) {
                currentStream.read(streamData);
            }

            if (sfxStream != null) {
                Arrays.fill(sfxStreamData, (short) 0);
                sfxStream.read(sfxStreamData);

                for (int i = 0; i < streamData.length; i++) {
                    int mixed = streamData[i] + sfxStreamData[i];
                    if (mixed > Short.MAX_VALUE)
                        mixed = Short.MAX_VALUE;
                    if (mixed < Short.MIN_VALUE)
                        mixed = Short.MIN_VALUE;
                    streamData[i] = (short) mixed;
                }

                if (sfxStream.isComplete()) {
                    sfxStream = null;
                }
            }

            sampleRate = (int) Math.round(getStreamSampleRate());
        }

        // Keep DirectBuffer/OpenAL operations outside lock to minimize contention
        directShortBuffer.clear();
        directShortBuffer.put(streamData);
        directShortBuffer.flip();
        alBufferData(bufferId, AL_FORMAT_STEREO16, directShortBuffer, sampleRate);
    }

    private double getSmpsOutputRate() {
        boolean internalRate = configService.getBoolean(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT);
        // Use device's native sample rate to avoid OpenAL resampling - our BlipResampler handles it
        return internalRate ? Ym2612Chip.getInternalRate() : deviceSampleRate;
    }

    private void applyPsgNoiseConfig(SmpsDriver driver) {
        boolean everyToggle = configService.getBoolean(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE);
        driver.setPsgNoiseShiftOnEveryToggle(everyToggle);
    }

    private double getStreamSampleRate() {
        double rate = deviceSampleRate;  // Use device rate as fallback to match getSmpsOutputRate()
        synchronized (streamLock) {
            SmpsDriver musicDriver = (currentStream instanceof SmpsDriver driver) ? driver : null;
            SmpsDriver sfxDriver = (sfxStream instanceof SmpsDriver driver) ? driver : null;
            if (musicDriver != null) {
                rate = musicDriver.getOutputSampleRate();
            } else if (sfxDriver != null) {
                rate = sfxDriver.getOutputSampleRate();
            }
            if (musicDriver != null && sfxDriver != null) {
                double sfxRate = sfxDriver.getOutputSampleRate();
                if (Math.abs(rate - sfxRate) > 1e-6) {
                    LOGGER.warning("Audio stream sample rate mismatch: music=" + rate + " sfx=" + sfxRate);
                }
            }
        }
        return rate;
    }

    /**
     * Returns a debug snapshot of the current SMPS sequencer if one is playing.
     */
    public SmpsSequencer.DebugState getDebugState() {
        synchronized (streamLock) {
            return currentSmps != null ? currentSmps.debugState() : null;
        }
    }

    @Override
    public void playSfx(String sfxName) {
        playSfx(sfxName, 1.0f);
    }

    @Override
    public void playSfx(String sfxName, float pitch) {
        String filename = sfxFallback.get(sfxName);
        if (filename != null) {
            int source = alGenSources();
            sfxSources.add(source);
            playWav(filename, source, false, pitch);
        } else {
            LOGGER.fine("SFX not found in fallback map: " + sfxName);
        }
    }

    @Override
    public void stopPlayback() {
        stopStream();
        alSourceStop(musicSource);
        alSourcei(musicSource, AL_BUFFER, 0);
        synchronized (streamLock) {
            currentStream = null;
            currentSmps = null;
            currentMusicId = -1;
            clearMusicStack();
            // Also stop any playing SFX to prevent them persisting across level transitions
            if (sfxStream instanceof SmpsDriver sfxDriver) {
                sfxDriver.stopAll();
            }
            sfxStream = null;
        }
        // Stop and cleanup WAV-based SFX sources
        for (int source : sfxSources) {
            alSourceStop(source);
            alDeleteSources(source);
        }
        sfxSources.clear();
    }

    @Override
    public void fadeOutMusic(int steps, int delay) {
        // Fade only music, not SFX - delegated to the music sequencer
        if (currentSmps != null) {
            currentSmps.triggerFadeOut(steps, delay);
        }
    }

    @Override
    public void endMusicOverride(int musicId) {
        if (currentSmps != null && currentMusicId == musicId) {
            restoreMusic();
            return;
        }
        removeSavedOverride(musicId);
    }

    @Override
    public void toggleMute(ChannelType type, int channel) {
        switch (type) {
            case FM:
            case DAC:
                if (channel >= 0 && channel < 6) {
                    fmUserMutes[channel] = !fmUserMutes[channel];
                }
                break;
            case PSG:
                if (channel >= 0 && channel < 4) {
                    psgUserMutes[channel] = !psgUserMutes[channel];
                }
                break;
        }
        updateSynthesizerConfig();
    }

    @Override
    public void toggleSolo(ChannelType type, int channel) {
        switch (type) {
            case FM:
            case DAC:
                if (channel >= 0 && channel < 6) {
                    fmUserSolos[channel] = !fmUserSolos[channel];
                }
                break;
            case PSG:
                if (channel >= 0 && channel < 4) {
                    psgUserSolos[channel] = !psgUserSolos[channel];
                }
                break;
        }
        updateSynthesizerConfig();
    }

    @Override
    public boolean isMuted(ChannelType type, int channel) {
        return switch (type) {
            case FM, DAC -> (channel >= 0 && channel < 6) && fmUserMutes[channel];
            case PSG -> (channel >= 0 && channel < 4) && psgUserMutes[channel];
        };
    }

    @Override
    public boolean isSoloed(ChannelType type, int channel) {
        return switch (type) {
            case FM, DAC -> (channel >= 0 && channel < 6) && fmUserSolos[channel];
            case PSG -> (channel >= 0 && channel < 4) && psgUserSolos[channel];
        };
    }

    @Override
    public void setSpeedShoes(boolean enabled) {
        this.speedShoesEnabled = enabled;
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.setSpeedShoes(enabled);
            }
        }
    }

    @Override
    public void setSpeedMultiplier(int multiplier) {
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.setSpeedMultiplier(multiplier);
            }
        }
    }

    @Override
    public void changeMusicTempo(int newDividingTiming) {
        synchronized (streamLock) {
            if (currentSmps != null) {
                currentSmps.updateDividingTiming(newDividingTiming);
            }
        }
    }

    private void updateSynthesizerConfig() {
        if (currentSmps == null || currentSmps.getSynthesizer() == null)
            return;
        var synth = currentSmps.getSynthesizer();

        boolean anyFmSolo = false;
        for (boolean s : fmUserSolos)
            if (s)
                anyFmSolo = true;

        boolean anyPsgSolo = false;
        for (boolean s : psgUserSolos)
            if (s)
                anyPsgSolo = true;

        boolean anySolo = anyFmSolo || anyPsgSolo;

        for (int i = 0; i < 6; i++) {
            boolean soloed = fmUserSolos[i];
            boolean muted = fmUserMutes[i];
            if (soloed)
                muted = false;
            else if (anySolo)
                muted = true;
            synth.setFmMute(i, muted);
        }

        for (int i = 0; i < 4; i++) {
            boolean soloed = psgUserSolos[i];
            boolean muted = psgUserMutes[i];
            if (soloed)
                muted = false;
            else if (anySolo)
                muted = true;
            synth.setPsgMute(i, muted);
        }
    }

    private SmpsSequencerConfig requireSmpsConfig() {
        if (smpsConfig == null) {
            throw new IllegalStateException("SMPS sequencer config not set");
        }
        return smpsConfig;
    }

    private void pushCurrentState() {
        if (currentStream == null || currentSmps == null || smpsDriver == null) {
            return;
        }
        musicStack.push(new MusicState(currentStream, currentSmps, smpsDriver, currentMusicId));
    }

    private void clearMusicStack() {
        musicStack.clear();
        pendingRestore = false;
        sfxBlocked = false;  // Unblock SFX when stack is cleared (e.g., level transition)
    }

    private boolean removeSavedOverride(int musicId) {
        if (musicStack.isEmpty()) {
            return false;
        }
        for (Iterator<MusicState> iterator = musicStack.iterator(); iterator.hasNext();) {
            MusicState state = iterator.next();
            if (state.musicId == musicId) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void playWav(String resourcePath, int source, boolean loop) {
        playWav(resourcePath, source, loop, 1.0f);
    }

    private void playWav(String resourcePath, int source, boolean loop, float pitch) {
        try {
            // Check if buffer exists
            if (!buffers.containsKey(resourcePath)) {
                loadWav(resourcePath);
            }

            Integer buffer = buffers.get(resourcePath);
            if (buffer != null) {
                alSourceStop(source);
                alSourcei(source, AL_BUFFER, buffer);
                alSourcei(source, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
                alSourcef(source, AL_PITCH, pitch);
                alSourcePlay(source);
            } else {
                LOGGER.fine("Could not load buffer for: " + resourcePath);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to play WAV: " + resourcePath + " - " + e.getMessage());
        }
    }

    private void loadWav(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.fine("Audio resource not found: " + resourcePath);
                return;
            }

            WavDecoder wav = WavDecoder.decode(is);

            int alFormat;
            if (wav.channels == 1) {
                alFormat = (wav.bitsPerSample == 8) ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16;
            } else {
                alFormat = (wav.bitsPerSample == 8) ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16;
            }

            ByteBuffer bufferData = MemoryUtil.memAlloc(wav.data.length);
            bufferData.put(wav.data);
            bufferData.flip();

            int buf = alGenBuffers();
            alBufferData(buf, alFormat, bufferData, wav.sampleRate);

            MemoryUtil.memFree(bufferData);

            buffers.put(resourcePath, buf);
        } catch (Exception e) {
            LOGGER.warning("Error loading WAV " + resourcePath + ": " + e.getMessage());
        }
    }

    @Override
    public void update() {
        updateStream();

        // Cleanup stopped sources
        Iterator<Integer> it = sfxSources.iterator();
        while (it.hasNext()) {
            int src = it.next();
            int state = alGetSourcei(src, AL_SOURCE_STATE);
            if (state == AL_STOPPED) {
                alDeleteSources(src);
                it.remove();
            }
        }
    }

    @Override
    public void destroy() {
        // Free the pre-allocated buffer
        if (directShortBuffer != null) {
            MemoryUtil.memFree(directShortBuffer);
            directShortBuffer = null;
        }

        // Delete all buffers
        for (int bufferId : buffers.values()) {
            alDeleteBuffers(bufferId);
        }
        buffers.clear();

        // Delete stream buffers
        if (streamBuffers != null) {
            for (int bufferId : streamBuffers) {
                alDeleteBuffers(bufferId);
            }
            streamBuffers = null;
        }

        // Delete sources
        if (musicSource >= 0) {
            alDeleteSources(musicSource);
            musicSource = -1;
        }
        for (int source : sfxSources) {
            alDeleteSources(source);
        }
        sfxSources.clear();

        // Destroy context and close device
        if (context != 0) {
            alcDestroyContext(context);
            context = 0;
        }
        if (device != 0) {
            alcCloseDevice(device);
            device = 0;
        }
    }

    @Override
    public void stopAllSfx() {
        // Stop SFX sequencers in the active music driver (mixed into currentStream)
        if (smpsDriver != null) {
            smpsDriver.stopAllSfx();
        }
        // Stop standalone SFX stream (used when SFX played before any music started)
        synchronized (streamLock) {
            if (sfxStream instanceof SmpsDriver sfxDriver) {
                sfxDriver.stopAll();
            }
            sfxStream = null;
        }
    }

    @Override
    public void pause() {
        if (musicSource >= 0) {
            alSourcePause(musicSource);
        }
        for (int src : sfxSources) {
            alSourcePause(src);
        }
    }

    @Override
    public void resume() {
        if (musicSource >= 0) {
            int state = alGetSourcei(musicSource, AL_SOURCE_STATE);
            if (state == AL_PAUSED) {
                alSourcePlay(musicSource);
            }
        }
        for (int src : sfxSources) {
            int state = alGetSourcei(src, AL_SOURCE_STATE);
            if (state == AL_PAUSED) {
                alSourcePlay(src);
            }
        }
    }
}
