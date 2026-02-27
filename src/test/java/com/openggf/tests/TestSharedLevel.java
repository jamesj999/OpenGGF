package com.openggf.tests;

import com.openggf.level.Level;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link SharedLevel} reusable level loading utility.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestSharedLevel {

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();
    private static SharedLevel shared;

    @BeforeClass
    public static void loadLevel() throws Exception {
        shared = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (shared != null) {
            shared.dispose();
            shared = null;
        }
    }

    @Test
    public void levelIsLoaded() {
        assertNotNull("SharedLevel.level() should not be null after load", shared.level());
    }

    @Test
    public void levelHasValidBounds() {
        Level level = shared.level();
        assertTrue("Level maxX should be > 0, was " + level.getMaxX(), level.getMaxX() > 0);
    }

    @Test
    public void gameAndZoneAreStored() {
        assertEquals(SonicGame.SONIC_2, shared.game());
        assertEquals(ZONE_EHZ, shared.zone());
        assertEquals(ACT_1, shared.act());
    }
}
