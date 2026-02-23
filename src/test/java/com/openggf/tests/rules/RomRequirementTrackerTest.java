package com.openggf.tests.rules;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RomRequirementTrackerTest {
    private String originalProperty;

    @Before
    public void setUp() {
        originalProperty = System.getProperty(RomRequirementTracker.REQUIRED_ROMS_PROPERTY);
        System.clearProperty(RomRequirementTracker.REQUIRED_ROMS_PROPERTY);
        RomRequirementTracker.resetForTests();
    }

    @After
    public void tearDown() {
        if (originalProperty == null) {
            System.clearProperty(RomRequirementTracker.REQUIRED_ROMS_PROPERTY);
        } else {
            System.setProperty(RomRequirementTracker.REQUIRED_ROMS_PROPERTY, originalProperty);
        }
        RomRequirementTracker.resetForTests();
    }

    @Test
    public void parseRequiredRomsParsesCommaSeparatedValuesAndIgnoresUnknowns() {
        EnumSet<SonicGame> parsed = RomRequirementTracker.parseRequiredRoms("SONIC_1, SONIC_2,unknown, ,SONIC_3K");
        assertEquals(EnumSet.of(SonicGame.SONIC_1, SonicGame.SONIC_2, SonicGame.SONIC_3K), parsed);
    }

    @Test
    public void requireRomOrSkipSkipsAndTracksWhenGameNotStrictlyRequired() {
        try {
            RomRequirementTracker.requireRomOrSkip(SonicGame.SONIC_2, false, "missing");
            fail("Expected assumption to be violated");
        } catch (AssumptionViolatedException expected) {
            // expected
        }

        Map<SonicGame, Integer> snapshot = RomRequirementTracker.missingRomSkipCountsSnapshot();
        assertEquals(Integer.valueOf(1), snapshot.get(SonicGame.SONIC_2));
    }

    @Test
    public void requireRomOrSkipFailsWhenGameIsStrictlyRequired() {
        System.setProperty(RomRequirementTracker.REQUIRED_ROMS_PROPERTY, "SONIC_2");
        try {
            RomRequirementTracker.requireRomOrSkip(SonicGame.SONIC_2, false, "missing");
            fail("Expected strict requirement failure");
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage().contains("qa.requiredRoms"));
        }

        assertTrue(RomRequirementTracker.missingRomSkipCountsSnapshot().isEmpty());
    }
}
