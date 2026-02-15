package uk.co.jamesj999.sonic.sprites.playable;

import org.junit.After;
import org.junit.Test;
import uk.co.jamesj999.sonic.audio.AudioBackend;
import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.ChannelType;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.NullAudioBackend;
import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1AudioProfile;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2AudioProfile;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Music;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kAudioProfile;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kMusic;
import uk.co.jamesj999.sonic.level.LevelManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestDrowningControllerMusicSelection {

    @After
    public void tearDown() {
        AudioManager audioManager = AudioManager.getInstance();
        audioManager.setBackend(new NullAudioBackend());
        audioManager.resetState();
        LevelManager.getInstance().resetState();
    }

    @Test
    public void usesSonic1DrowningMusicWhenSonic1ProfileIsActive() {
        assertDrowningMusicId(new Sonic1AudioProfile(), Sonic1Music.DROWNING.id);
    }

    @Test
    public void usesSonic2UnderwaterMusicWhenSonic2ProfileIsActive() {
        assertDrowningMusicId(new Sonic2AudioProfile(), Sonic2Music.UNDERWATER.id);
    }

    @Test
    public void usesSonic3kDrowningMusicWhenSonic3kProfileIsActive() {
        assertDrowningMusicId(new Sonic3kAudioProfile(), Sonic3kMusic.DROWNING.id);
    }

    private void assertDrowningMusicId(GameAudioProfile profile, int expectedMusicId) {
        AudioManager audioManager = AudioManager.getInstance();
        CapturingBackend backend = new CapturingBackend();
        audioManager.setBackend(backend);
        audioManager.setAudioProfile(profile);
        LevelManager.getInstance().resetState();

        DrowningController controller = new DrowningController(new Sonic("test", (short) 0, (short) 0));

        // Air starts at 30 and drowning music triggers when air event runs at exactly 12.
        // That is 19 air events: 30..12 inclusive.
        int updatesToTriggerMusic = (30 - 12 + 1) * 60;
        for (int i = 0; i < updatesToTriggerMusic; i++) {
            controller.update();
        }

        assertTrueMusicTriggeredExactlyOnce(expectedMusicId, backend);
        assertTrue("Controller should flag drowning music as active", controller.isDrowningMusicPlaying());
    }

    private void assertTrueMusicTriggeredExactlyOnce(int expectedMusicId, CapturingBackend backend) {
        assertEquals("Drowning music should be triggered exactly once", 1, backend.musicPlayCalls);
        assertEquals("Incorrect drowning music ID selected", expectedMusicId, backend.lastMusicId);
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
