package com.openggf.tests;

import org.junit.After;
import org.junit.Test;
import com.openggf.control.InputHandler;
import com.openggf.audio.AudioBackend;
import com.openggf.audio.AudioManager;
import com.openggf.audio.ChannelType;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.titlescreen.TitleScreenManager;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

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
            if (data != null && data.getId() == Sonic2Sfx.SPARKLE.id) {
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
        assumeNotNull("Sonic 2 ROM not available — skipping test", romFile);
        Rom rom = new Rom();
        assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));

        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
        AbstractSmpsData titleMusic = loader.loadMusic(Sonic2Music.TITLE.id);
        assertNotNull("Title music should load from ROM", titleMusic);
    }

    @Test
    public void testSparkleSfxCompletesQuickly() {
        File romFile = RomTestUtils.ensureRomAvailable();
        assumeNotNull("Sonic 2 ROM not available — skipping test", romFile);
        Rom rom = new Rom();
        assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));

        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
        AbstractSmpsData sparkle = loader.loadSfx(Sonic2Sfx.SPARKLE.id);
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
        assumeNotNull("Sonic 2 ROM not available — skipping test", romFile);
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
