package com.openggf.tests;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;

import org.junit.Test;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.DacData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class TestSmpsDriver {

    // A spy driver that records writes
    static class SpyDriver extends SmpsDriver {
        List<String> log = new ArrayList<>();

        @Override
        public void render(short[] buffer) {
            // No-op for synth rendering
        }

        @Override
        public void writeFm(Object source, int port, int reg, int val) {
            super.writeFm(source, port, reg, val);
            // Log if the write passed through
            // We can check internal state or just log here.
            // SmpsDriver calls super.writeFm if allowed.
            // But SmpsDriver doesn't call a 'hardware' write, it updates internal state.
            // We need to override the *base* VirtualSynthesizer methods to detect if the write happened?
            // SmpsDriver extends VirtualSynthesizer.
            // But we can't easily spy on "super.super.writeFm".
            // However, SmpsDriver calls `super.writeFm`. We can override it in this SpyDriver?
            // No, `super` in SpyDriver refers to SmpsDriver.
            // We need to know if SmpsDriver decided to proceed.
            // SmpsDriver logic: if (allowed) super.writeFm().
            // So if we override `writeFm` in SpyDriver, we are overriding the logic itself!
            // Wait, SmpsDriver IS the class under test.
            // We can't override the method we are testing.

            // We can inspect the state of the synthesizer AFTER the write?
            // VirtualSynthesizer has registers.
            // But they are private/protected?
            // VirtualSynthesizer registers are private.

            // However, we can use a callback/listener if available? No.

            // Alternative: SmpsDriver allows checking fmLocks?
            // No, private.

            // We can rely on the return value? writeFm is void.

            // Let's use reflection to check fmLocks?
            // Or add a getter/protected visibility for testing.
            // I'll try reflection.
        }

        // Helper to check lock via reflection
        public Object getFmLock(int channel) {
            try {
                java.lang.reflect.Field f = SmpsDriver.class.getDeclaredField("fmLocks");
                f.setAccessible(true);
                SmpsSequencer[] locks = (SmpsSequencer[]) f.get(this);
                return locks[channel];
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testSfxFighting() {
        SpyDriver driver = new SpyDriver();
        AbstractSmpsData dummyData = new Sonic2SmpsData(new byte[100]);
        DacData dummyDac = new DacData(new HashMap<>(), new HashMap<>());

        // Create two sequencers (SFX)
        SmpsSequencer sfx1 = new SmpsSequencer(dummyData, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer sfx2 = new SmpsSequencer(dummyData, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);

        driver.addSequencer(sfx1, true);
        driver.addSequencer(sfx2, true);

        // sfx1 writes to FM channel 0 (Reg 0xA4, 0xA0 -> Channel 0)
        // Reg mapping: 0xA0..0xA2 -> Ch 0..2.
        driver.writeFm(sfx1, 0, 0xA0, 0x10);

        // Assert sfx1 has lock
        assertEquals("SFX1 should have lock on Ch 0", sfx1, driver.getFmLock(0));

        // sfx2 writes to FM channel 0
        driver.writeFm(sfx2, 0, 0xA0, 0x20);

        // Equal priority: newer SFX (sfx2) should steal the lock from sfx1
        assertEquals("SFX2 should steal lock on Ch 0 (equal priority, newer wins)", sfx2, driver.getFmLock(0));

        // sfx1 writes again
        driver.writeFm(sfx1, 0, 0xA0, 0x11);

        // sfx1 writes again but sfx2 still holds the lock (equal priority, sfx2 is newer)
        assertEquals("SFX2 should still hold lock on Ch 0", sfx2, driver.getFmLock(0));
    }

    @Test
    public void testNormalSfxStealsFromSpecialSfx() {
        SpyDriver driver = new SpyDriver();
        AbstractSmpsData dummyData = new Sonic2SmpsData(new byte[100]);
        DacData dummyDac = new DacData(new HashMap<>(), new HashMap<>());

        SmpsSequencer special = new SmpsSequencer(dummyData, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        special.setSfxPriority(0x80); // S1-style special/non-storing
        special.setSpecialSfx(true);

        SmpsSequencer normal = new SmpsSequencer(dummyData, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        normal.setSfxPriority(0x70);
        normal.setSpecialSfx(false);

        driver.addSequencer(special, true);
        driver.addSequencer(normal, true);

        driver.writeFm(special, 0, 0xA0, 0x10);
        assertEquals("Special SFX should initially own Ch 0", special, driver.getFmLock(0));

        driver.writeFm(normal, 0, 0xA0, 0x20);
        assertEquals("Normal SFX should steal Ch 0 from special SFX", normal, driver.getFmLock(0));
    }

    @Test
    public void testSpecialSfxDoesNotStealFromNormalSfx() {
        SpyDriver driver = new SpyDriver();
        AbstractSmpsData dummyData = new Sonic2SmpsData(new byte[100]);
        DacData dummyDac = new DacData(new HashMap<>(), new HashMap<>());

        SmpsSequencer normal = new SmpsSequencer(dummyData, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        normal.setSfxPriority(0x70);
        normal.setSpecialSfx(false);

        SmpsSequencer special = new SmpsSequencer(dummyData, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        special.setSfxPriority(0x80); // Higher numeric priority, but special class
        special.setSpecialSfx(true);

        driver.addSequencer(normal, true);
        driver.addSequencer(special, true);

        driver.writeFm(normal, 0, 0xA0, 0x10);
        assertEquals("Normal SFX should initially own Ch 0", normal, driver.getFmLock(0));

        driver.writeFm(special, 0, 0xA0, 0x20);
        assertEquals("Special SFX should not steal Ch 0 from normal SFX", normal, driver.getFmLock(0));
    }
}
