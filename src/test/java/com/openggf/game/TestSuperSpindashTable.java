package com.openggf.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Super spindash speed table feature flag.
 * <p>S3K Super/Hyper forms use a higher speed table (sonic3k.asm:23743 word_11D04).
 * S1/S2 have no Super-specific spindash table.
 */
class TestSuperSpindashTable {

    @Test
    void s3k_hasSuperSpindashTable() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_3K;
        assertNotNull(fs.superSpindashSpeedTable(), "S3K should have Super spindash table");
        assertEquals(9, fs.superSpindashSpeedTable().length, "Table should have 9 entries");
    }

    @Test
    void s3k_superTableValues_matchRom() {
        // sonic3k.asm:23743 word_11D04
        short[] expected = {0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00};
        short[] actual = PhysicsFeatureSet.SONIC_3K.superSpindashSpeedTable();
        assertArrayEquals(expected, actual, "Super spindash table values");
    }

    @Test
    void s3k_normalTableValues_matchRom() {
        // sonic3k.asm:23733 word_11CF2 — same as S2
        short[] expected = {0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00};
        short[] actual = PhysicsFeatureSet.SONIC_3K.spindashSpeedTable();
        assertArrayEquals(expected, actual, "Normal spindash table values");
    }

    @Test
    void s2_noSuperSpindashTable() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_2;
        assertNull(fs.superSpindashSpeedTable(), "S2 should have no Super spindash table");
    }

    @Test
    void s1_noSuperSpindashTable() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_1;
        assertNull(fs.superSpindashSpeedTable(), "S1 should have no Super spindash table");
    }

    @Test
    void s1_noNormalSpindashTable() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_1;
        assertNull(fs.spindashSpeedTable(), "S1 should have no normal spindash table");
    }

    @Test
    void superTable_startsHigherThanNormal() {
        short[] normal = PhysicsFeatureSet.SONIC_3K.spindashSpeedTable();
        short[] superT = PhysicsFeatureSet.SONIC_3K.superSpindashSpeedTable();
        assertTrue(superT[0] > normal[0],
                "Super table first entry ($B00) should be higher than normal ($800)");
        assertTrue(superT[8] > normal[8],
                "Super table last entry ($F00) should be higher than normal ($C00)");
    }
}


