package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that AudioManager.resetState() clears all observable mutable fields:
 * audioProfile, soundMap, smpsLoader/dacData (via setRom), ringLeft, and donor state.
 *
 * No ROM or OpenGL required.
 */
public class TestAudioManagerResetState {

    private static final DacData EMPTY_DAC = new DacData(
            Collections.emptyMap(), Collections.emptyMap(), 288);

    private AudioManager am;
    private RingTrackingBackend backend;

    @BeforeEach
    public void setUp() {
        am = AudioManager.getInstance();
        am.resetState();
        backend = new RingTrackingBackend();
        am.setBackend(backend);
    }

    @AfterEach
    public void tearDown() {
        am.resetState();
    }

    @Test
    public void resetStateClearsAudioProfile() {
        am.setAudioProfile(new StubAudioProfile());
        assertNotNull(am.getAudioProfile(), "Precondition: audioProfile should be set");

        am.resetState();

        assertNull(am.getAudioProfile(), "audioProfile should be null after resetState()");
    }

    @Test
    public void resetStateResetsRingLeftToTrue() {
        // Advance ringLeft to false by playing a RING sound once (toggles trueâ†’false)
        am.setSoundMap(new EnumMap<>(GameSound.class));
        am.playSfx(GameSound.RING);
        assertTrue(backend.lastPlayedRingLeft, "Precondition: first ring should use RING_LEFT");

        // Second ring should use RING_RIGHT (ringLeft is now false)
        am.playSfx(GameSound.RING);
        assertFalse(backend.lastPlayedRingLeft, "Precondition: second ring should use RING_RIGHT");

        am.resetState();
        am.setBackend(backend);
        am.setSoundMap(new EnumMap<>(GameSound.class));

        // After reset, ringLeft is true again â€” first ring goes left
        am.playSfx(GameSound.RING);
        assertTrue(backend.lastPlayedRingLeft, "ringLeft should be reset to true after resetState()");
    }

    @Test
    public void resetStateClearsDonorAudio() {
        // Register a donor loader and sound binding
        StubSmpsLoader donorLoader = new StubSmpsLoader();
        am.registerDonorLoader("s2", donorLoader, EMPTY_DAC);
        am.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        am.resetState();
        am.setBackend(backend);

        // With empty sound map and cleared donor state, the sound falls through to
        // backend.playSfx(name) rather than routing through the donor loader
        am.setSoundMap(new EnumMap<>(GameSound.class));
        am.playSfx(GameSound.SPINDASH_CHARGE);

        assertEquals("SPINDASH_CHARGE", backend.lastFallbackName, "Donor bindings should be cleared â€” sound must fall through to name-based SFX");
        assertNull(backend.lastSmpsName, "No SMPS data should be played after donor state cleared");
    }

    @Test
    public void resetStateClearsSoundMap() {
        // Set up a sound map with a base loader so playSfx(int) would succeed
        StubSmpsLoader baseLoader = new StubSmpsLoader();
        baseLoader.sfxResults.put(0x90, new StubSmpsData("jump"));
        am.setAudioProfile(new StubAudioProfile(baseLoader));
        am.setRom(null);

        Map<GameSound, Integer> soundMap = new EnumMap<>(GameSound.class);
        soundMap.put(GameSound.JUMP, 0x90);
        am.setSoundMap(soundMap);

        am.resetState();
        am.setBackend(backend);

        // After reset: no soundMap, no smpsLoader â†’ falls through to fallback
        am.playSfx(GameSound.JUMP);

        assertEquals("JUMP", backend.lastFallbackName, "soundMap should be cleared â€” JUMP must fall through to name-based SFX");
        assertNull(backend.lastSmpsName, "No SMPS data should be played after reset clears smpsLoader");
    }

    @Test
    public void doubleResetDoesNotThrow() {
        am.resetState();
        // A second reset on an already-cleared instance should not throw
        am.resetState();
    }

    // --- Test doubles ---

    /**
     * Tracks which ring channel (LEFT/RIGHT) was last requested via playSfx(GameSound).
     * Also records fallback and SMPS play calls for other assertions.
     */
    private static class RingTrackingBackend extends NullAudioBackend {
        boolean lastPlayedRingLeft;
        String lastFallbackName;
        String lastSmpsName;

        @Override
        public void playSfx(String sfxName, float pitch) {
            if ("RING_LEFT".equals(sfxName)) {
                lastPlayedRingLeft = true;
            } else if ("RING_RIGHT".equals(sfxName)) {
                lastPlayedRingLeft = false;
            }
            lastFallbackName = sfxName;
            lastSmpsName = null;
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
            lastSmpsName = data.toString();
            lastFallbackName = null;
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch,
                                SmpsSequencerConfig config) {
            lastSmpsName = data.toString();
            lastFallbackName = null;
        }
    }

    private static class StubSmpsData extends AbstractSmpsData {
        final String name;

        StubSmpsData(String name) {
            super(new byte[0], 0);
            this.name = name;
        }

        @Override protected void parseHeader() {}
        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }

        @Override
        public String toString() { return name; }
    }

    private static class StubSmpsLoader implements SmpsLoader {
        final Map<Integer, AbstractSmpsData> sfxResults = new java.util.HashMap<>();

        @Override public AbstractSmpsData loadMusic(int musicId) { return null; }
        @Override public AbstractSmpsData loadSfx(int sfxId) { return sfxResults.get(sfxId); }
        @Override public AbstractSmpsData loadSfx(String sfxName) { return null; }
        @Override public DacData loadDacData() { return EMPTY_DAC; }
    }

    private static class StubAudioProfile implements GameAudioProfile {
        private final SmpsLoader loader;

        StubAudioProfile() { this.loader = new StubSmpsLoader(); }
        StubAudioProfile(SmpsLoader loader) { this.loader = loader; }

        @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
        @Override public SmpsSequencerConfig getSequencerConfig() { return null; }
        @Override public int getSpeedShoesOnCommandId() { return -1; }
        @Override public int getSpeedShoesOffCommandId() { return -1; }
        @Override public int getInvincibilityMusicId() { return -1; }
        @Override public int getExtraLifeMusicId() { return -1; }
        @Override public int getDrowningMusicId() { return -1; }
        @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    }
}


