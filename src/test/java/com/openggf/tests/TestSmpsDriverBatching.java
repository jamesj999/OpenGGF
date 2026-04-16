package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class TestSmpsDriverBatching {

    @Test
    public void sampleAccurateAndHybridReadModesRenderIdenticallyForSteadyFmSequence() {
        short[] sampleAccurate = renderSteadyFmSequence(SmpsDriver.ReadMode.SAMPLE_ACCURATE);
        short[] hybrid = renderSteadyFmSequence(SmpsDriver.ReadMode.HYBRID);

        assertArrayEquals(sampleAccurate, hybrid);
    }

    private static short[] renderSteadyFmSequence(SmpsDriver.ReadMode readMode) {
        SmpsDriver driver = newDriver(readMode);
        driver.addSequencer(newSequencer(driver), false);

        short[] buffer = new short[256];
        driver.read(buffer);
        return buffer;
    }

    private static SmpsDriver newDriver(SmpsDriver.ReadMode readMode) {
        SmpsDriver driver = new SmpsDriver();
        driver.setReadModeForTesting(readMode);
        return driver;
    }

    private static SmpsSequencer newSequencer(SmpsDriver driver) {
        byte[] data = new byte[32];
        data[2] = 2;
        data[4] = 1;
        data[5] = (byte) 0x80;
        data[10] = 0x14;
        data[11] = 0x00;
        data[0x14] = (byte) 0x81;
        data[0x15] = 0x10;
        data[0x16] = (byte) 0xF2;

        AbstractSmpsData smps = new Sonic2SmpsData(data);
        return new SmpsSequencer(smps, new DacData(new HashMap<>(), new HashMap<>()),
                driver, AudioManager.getInstance(), Sonic2SmpsSequencerConfig.CONFIG);
    }
}
