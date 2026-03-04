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
import java.util.Set;

import static org.junit.Assert.*;

public class TestSmpsDriver {

    // A spy driver that records writes
    static class SpyDriver extends SmpsDriver {
        List<String> log = new ArrayList<>();
        List<Integer> rawPsgWrites = new ArrayList<>();

        @Override
        public void render(short[] buffer) {
            // No-op for synth rendering
        }

        @Override
        public void writeFm(Object source, int port, int reg, int val) {
            super.writeFm(source, port, reg, val);
        }

        @Override
        protected void writeRawPsg(int val) {
            rawPsgWrites.add(val);
            super.writeRawPsg(val);
        }

        // Helper to check FM lock via reflection
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

        // Helper to check PSG lock via reflection
        public Object getPsgLock(int channel) {
            try {
                java.lang.reflect.Field f = SmpsDriver.class.getDeclaredField("psgLocks");
                f.setAccessible(true);
                SmpsSequencer[] locks = (SmpsSequencer[]) f.get(this);
                return locks[channel];
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        public int getSequencerCount() {
            try {
                java.lang.reflect.Field f = SmpsDriver.class.getDeclaredField("sequencers");
                f.setAccessible(true);
                return ((List<SmpsSequencer>) f.get(this)).size();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        public int getSfxSequencerCount() {
            try {
                java.lang.reflect.Field f = SmpsDriver.class.getDeclaredField("sfxSequencers");
                f.setAccessible(true);
                return ((Set<SmpsSequencer>) f.get(this)).size();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Create a Track via reflection (constructor is package-private). */
    private static SmpsSequencer.Track createTrack(SmpsSequencer.TrackType type, int channelId) {
        try {
            var ctor = SmpsSequencer.Track.class.getDeclaredConstructor(
                    int.class, SmpsSequencer.TrackType.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(0, type, channelId);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    @Test
    public void testSfxChannelConflictKillsOldTrack() {
        SpyDriver driver = new SpyDriver();
        AbstractSmpsData dummyDataA = new Sonic2SmpsData(new byte[100]);
        dummyDataA.setId(0xE7); // DrawbridgeMove
        AbstractSmpsData dummyDataB = new Sonic2SmpsData(new byte[100]);
        dummyDataB.setId(0xCD); // BLIP - different ID so same-ID dedup doesn't fire
        DacData dummyDac = new DacData(new HashMap<>(), new HashMap<>());

        // SFX-A uses PSG channel 2 (PSG3)
        SmpsSequencer sfxA = new SmpsSequencer(dummyDataA, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        sfxA.addTrack(createTrack(SmpsSequencer.TrackType.PSG, 2));

        // SFX-B also uses PSG channel 2 (PSG3)
        SmpsSequencer sfxB = new SmpsSequencer(dummyDataB, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        sfxB.addTrack(createTrack(SmpsSequencer.TrackType.PSG, 2));

        // Add SFX-A, give it the PSG2 lock via a write
        driver.addSequencer(sfxA, true);
        driver.writePsg(sfxA, 0x80 | (2 << 5) | 0x00); // latch PSG3
        assertEquals("SFX-A should hold PSG2 lock", sfxA, driver.getPsgLock(2));
        assertEquals(1, driver.getSequencerCount());

        // Add SFX-B on same channel - should kill SFX-A's track
        driver.addSequencer(sfxB, true);

        // SFX-A's track should be deactivated
        assertFalse("SFX-A's PSG2 track should be deactivated",
                sfxA.getTracks().get(0).active);
        // SFX-A's lock should be released (SFX-B hasn't written yet)
        assertNull("PSG2 lock should be released after conflict resolution",
                driver.getPsgLock(2));
        // SFX-A should be removed entirely (all tracks inactive)
        assertEquals("SFX-A should be removed (all tracks dead)", 1, driver.getSequencerCount());
        assertEquals("Only SFX-B in sfxSequencers", 1, driver.getSfxSequencerCount());
    }

    @Test
    public void testPsg3SfxSilencesNoiseChannel() {
        SpyDriver driver = new SpyDriver();
        AbstractSmpsData dummyDataA = new Sonic2SmpsData(new byte[100]);
        dummyDataA.setId(0xE7); // old SFX on PSG3
        AbstractSmpsData dummyDataB = new Sonic2SmpsData(new byte[100]);
        dummyDataB.setId(0xCD); // new SFX replacing it on PSG3
        DacData dummyDac = new DacData(new HashMap<>(), new HashMap<>());

        // Old SFX on PSG3 (channel 2)
        SmpsSequencer sfxOld = new SmpsSequencer(dummyDataA, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        sfxOld.addTrack(createTrack(SmpsSequencer.TrackType.PSG, 2));
        driver.addSequencer(sfxOld, true);
        // Give it the PSG2 lock
        driver.writePsg(sfxOld, 0x80 | (2 << 5) | 0x00);

        // New SFX also on PSG3 (channel 2) - should trigger noise silencing
        SmpsSequencer sfxNew = new SmpsSequencer(dummyDataB, dummyDac, driver, Sonic2SmpsSequencerConfig.CONFIG);
        sfxNew.addTrack(createTrack(SmpsSequencer.TrackType.PSG, 2));

        driver.rawPsgWrites.clear();
        driver.addSequencer(sfxNew, true);

        // ROM lines 2221-2228: replacing PSG3 SFX should silence both tone2 and noise
        assertTrue("Should silence PSG3 (0xDF)",
                driver.rawPsgWrites.contains(0xDF));
        assertTrue("Should silence noise channel (0xFF)",
                driver.rawPsgWrites.contains(0xFF));
    }
}
