package uk.co.jamesj999.sonic.tests;

import org.junit.After;
import org.junit.Test;
import uk.co.jamesj999.sonic.Control.InputHandler;
import uk.co.jamesj999.sonic.audio.AudioBackend;
import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.ChannelType;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.NullAudioBackend;
import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencer;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2AudioProfile;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import uk.co.jamesj999.sonic.game.sonic2.audio.smps.Sonic2SmpsLoader;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.titlescreen.TitleScreenManager;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestTitleScreenAudioRegression {
    @After
    public void tearDown() {
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        TitleScreenManager.getInstance().reset();
    }

    private static final class CountingBackend implements AudioBackend {
        int musicPlayCalls = 0;
        int sparkleSfxCalls = 0;

        @Override
        public void init() {
        }

        @Override
        public void setAudioProfile(GameAudioProfile profile) {
        }

        @Override
        public void playMusic(int musicId) {
            musicPlayCalls++;
        }

        @Override
        public void playSmps(AbstractSmpsData data, DacData dacData) {
            musicPlayCalls++;
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData) {
            playSfxSmps(data, dacData, 1.0f);
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
            if (data != null && data.getId() == Sonic2AudioConstants.SFX_SPARKLE) {
                sparkleSfxCalls++;
            }
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

    @Test
    public void testTitleMusicLoadsFromRom() {
        File romFile = RomTestUtils.ensureRomAvailable();
        Rom rom = new Rom();
        assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));

        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
        AbstractSmpsData titleMusic = loader.loadMusic(Sonic2AudioConstants.MUS_TITLE);
        assertNotNull("Title music should load from ROM", titleMusic);
    }

    @Test
    public void testSparkleSfxCompletesQuickly() {
        File romFile = RomTestUtils.ensureRomAvailable();
        Rom rom = new Rom();
        assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));

        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
        AbstractSmpsData sparkle = loader.loadSfx(Sonic2AudioConstants.SFX_SPARKLE);
        assertNotNull("Sparkle SFX should load from ROM", sparkle);

        DacData dacData = loader.loadDacData();
        SmpsSequencer seq = new SmpsSequencer(sparkle, dacData, Sonic2SmpsSequencerConfig.CONFIG);
        seq.setSfxMode(true);

        int ticks = 0;
        while (!seq.isComplete() && ticks < 1000) {
            // Slightly above one 60 Hz frame at 44.1 kHz to guarantee one tick.
            seq.advance(750);
            ticks++;
        }

        assertTrue("Sparkle SFX should complete in under ~4 seconds", ticks < 240);
    }

    @Test
    public void testTitleScreenTriggersSparkleAndMusicExpectedCounts() {
        File romFile = RomTestUtils.ensureRomAvailable();
        Rom rom = new Rom();
        assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));

        AudioManager audioManager = AudioManager.getInstance();
        CountingBackend backend = new CountingBackend();
        audioManager.setBackend(backend);
        audioManager.setAudioProfile(new Sonic2AudioProfile());
        audioManager.setRom(rom);

        TitleScreenManager title = TitleScreenManager.getInstance();
        title.initialize();

        InputHandler input = new InputHandler();
        for (int i = 0; i < 600; i++) {
            title.update(input);
            input.update();
        }

        assertEquals("Title music should be triggered once", 1, backend.musicPlayCalls);
        assertEquals("Title sparkle SFX should trigger exactly 10 times (init + 9 positions)", 10, backend.sparkleSfxCalls);
    }
}
