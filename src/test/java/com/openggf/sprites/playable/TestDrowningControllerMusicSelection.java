package com.openggf.sprites.playable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.openggf.audio.AudioBackend;
import com.openggf.audio.AudioManager;
import com.openggf.audio.ChannelType;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.level.LevelManager;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDrowningControllerMusicSelection {

    @AfterEach
    void tearDown() {
        AudioManager audioManager = AudioManager.getInstance();
        audioManager.setBackend(new NullAudioBackend());
        audioManager.resetState();
        RuntimeManager.destroyCurrent();
    }

    static Stream<Arguments> drowningMusicProvider() {
        return Stream.of(
                Arguments.of(new Sonic1AudioProfile(), Sonic1Music.DROWNING.id, "Sonic 1"),
                Arguments.of(new Sonic2AudioProfile(), Sonic2Music.UNDERWATER.id, "Sonic 2"),
                Arguments.of(new Sonic3kAudioProfile(), Sonic3kMusic.DROWNING.id, "Sonic 3K")
        );
    }

    @ParameterizedTest(name = "{2} drowning music")
    @MethodSource("drowningMusicProvider")
    void drowningMusicMatchesProfile(GameAudioProfile profile, int expectedMusicId, String label) {
        AudioManager audioManager = AudioManager.getInstance();
        CapturingBackend backend = new CapturingBackend();
        audioManager.setBackend(backend);
        audioManager.setAudioProfile(profile);
        GameRuntime runtime = RuntimeManager.createGameplay();
        LevelManager levelManager = runtime.getLevelManager();
        levelManager.resetState();

        DrowningController controller = new DrowningController(new Sonic("test", (short) 0, (short) 0));

        // Air starts at 30 and drowning music triggers when air event runs at exactly 12.
        // That is 19 air events: 30..12 inclusive.
        int updatesToTriggerMusic = (30 - 12 + 1) * 60;
        for (int i = 0; i < updatesToTriggerMusic; i++) {
            controller.update();
        }

        assertEquals(1, backend.musicPlayCalls,
                "Drowning music should be triggered exactly once");
        assertEquals(expectedMusicId, backend.lastMusicId,
                "Incorrect drowning music ID selected");
        assertTrue(controller.isDrowningMusicPlaying(),
                "Controller should flag drowning music as active");
    }

    private static final class CapturingBackend implements AudioBackend {
        int musicPlayCalls = 0;
        int lastMusicId = -1;

        @Override
        public void init() {
        }

        @Override
        public void setAudioProfile(GameAudioProfile profile) {
        }

        @Override
        public void playMusic(int musicId) {
            musicPlayCalls++;
            lastMusicId = musicId;
        }

        @Override
        public void playSmps(AbstractSmpsData data, DacData dacData) {
            musicPlayCalls++;
            if (data != null) {
                lastMusicId = data.getId();
            }
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData) {
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
        }

        @Override
        public void playSfx(String sfxName) {
        }

        @Override
        public void playSfx(String sfxName, float pitch) {
        }

        @Override
        public void stopPlayback() {
        }

        @Override
        public void stopAllSfx() {
        }

        @Override
        public void fadeOutMusic(int steps, int delay) {
        }

        @Override
        public void toggleMute(ChannelType type, int channel) {
        }

        @Override
        public void toggleSolo(ChannelType type, int channel) {
        }

        @Override
        public boolean isMuted(ChannelType type, int channel) {
            return false;
        }

        @Override
        public boolean isSoloed(ChannelType type, int channel) {
            return false;
        }

        @Override
        public void setSpeedShoes(boolean enabled) {
        }

        @Override
        public void setSpeedMultiplier(int multiplier) {
        }

        @Override
        public void restoreMusic() {
        }

        @Override
        public void endMusicOverride(int musicId) {
        }

        @Override
        public void update() {
        }

        @Override
        public void destroy() {
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }
    }
}
