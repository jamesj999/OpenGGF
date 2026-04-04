package com.openggf.tests;

import org.junit.Test;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic1.Sonic1ZoneRegistry;
import com.openggf.game.sonic2.Sonic2ZoneRegistry;

import static org.junit.Assert.*;

/**
 * TODO #31 -- End-game loop prevention in LevelManager.advanceToNextLevel().
 *
 * <p>In {@code LevelManager.advanceToNextLevel()} (LevelManager.java:2527),
 * the code currently wraps zone index back to 0 when all zones are complete:
 * <pre>
 *   if (currentZone >= levels.size()) {
 *       LOGGER.info("All zones complete!");
 *       currentZone = 0; // Loop back for now - TODO: end game sequence
 *   }
 * </pre>
 *
 * <p>This is a placeholder that should be replaced with proper end-game
 * sequence handling (credits, ending screen, etc.) per game.
 *
 * <p>Expected zone progression order per game:
 * <ul>
 *   <li>Sonic 1: GHZ -> LZ -> MZ -> SLZ -> SYZ -> SBZ -> FZ -> END</li>
 *   <li>Sonic 2: EHZ -> CPZ -> ARZ -> CNZ -> HTZ -> MCZ -> OOZ -> MTZ -> SCZ -> WFZ -> DEZ -> END</li>
 *   <li>Sonic 3&K: AIZ -> HCZ -> MGZ -> CNZ -> ICZ -> LBZ -> MHZ -> FBZ -> SOZ -> LRZ -> HPZ -> SSZ -> DEZ -> DDZ -> END</li>
 * </ul>
 */
public class TestTodo31_EndGameLoopPrevention {

    @Test
    public void testSonic2ZoneProgressionOrder() {
        Sonic2ZoneRegistry registry = Sonic2ZoneRegistry.getInstance();

        // Sonic 2 has exactly 11 zones
        assertEquals("Sonic 2 has 11 zones", 11, registry.getZoneCount());

        // Verify the zone order matches the original game
        String[] expectedOrder = {
                "EMERALD HILL",     // Zone 0
                "CHEMICAL PLANT",   // Zone 1
                "AQUATIC RUIN",     // Zone 2
                "CASINO NIGHT",     // Zone 3
                "HILL TOP",         // Zone 4
                "MYSTIC CAVE",      // Zone 5
                "OIL OCEAN",        // Zone 6
                "METROPOLIS",       // Zone 7
                "SKY CHASE",        // Zone 8
                "WING FORTRESS",    // Zone 9
                "DEATH EGG"         // Zone 10
        };

        for (int i = 0; i < expectedOrder.length; i++) {
            assertEquals("Zone " + i + " name", expectedOrder[i], registry.getZoneName(i));
        }
    }

    @Test
    public void testSonic2ActCounts() {
        Sonic2ZoneRegistry registry = Sonic2ZoneRegistry.getInstance();

        // Most zones have 2 acts; Metropolis has 3; Sky Chase, Wing Fortress, Death Egg have 1
        assertEquals("EHZ has 2 acts", 2, registry.getActCount(0));
        assertEquals("CPZ has 2 acts", 2, registry.getActCount(1));
        assertEquals("ARZ has 2 acts", 2, registry.getActCount(2));
        assertEquals("CNZ has 2 acts", 2, registry.getActCount(3));
        assertEquals("HTZ has 2 acts", 2, registry.getActCount(4));
        assertEquals("MCZ has 2 acts", 2, registry.getActCount(5));
        assertEquals("OOZ has 2 acts", 2, registry.getActCount(6));
        assertEquals("MTZ has 3 acts", 3, registry.getActCount(7));
        assertEquals("SCZ has 1 act", 1, registry.getActCount(8));
        assertEquals("WFZ has 1 act", 1, registry.getActCount(9));
        assertEquals("DEZ has 1 act", 1, registry.getActCount(10));
    }

    @Test
    public void testSonic2TotalLevelCount() {
        Sonic2ZoneRegistry registry = Sonic2ZoneRegistry.getInstance();

        // Total levels: 7*2 + 3 + 1 + 1 + 1 = 20
        int totalLevels = 0;
        for (int z = 0; z < registry.getZoneCount(); z++) {
            totalLevels += registry.getActCount(z);
        }
        assertEquals("Sonic 2 has 20 total levels", 20, totalLevels);
    }

    @Test
    public void testSonic2FinalZoneIsDeathEgg() {
        Sonic2ZoneRegistry registry = Sonic2ZoneRegistry.getInstance();

        // The last zone should be Death Egg
        int lastZone = registry.getZoneCount() - 1;
        assertEquals("Last zone is DEATH EGG", "DEATH EGG", registry.getZoneName(lastZone));
    }

    @Test
    public void testSonic1ZoneProgressionOrder() {
        Sonic1ZoneRegistry registry = Sonic1ZoneRegistry.getInstance();

        // Sonic 1 has 8 zones (6 main + Final Zone + Ending)
        assertEquals("Sonic 1 has 8 zones", 8, registry.getZoneCount());

        String[] expectedOrder = {
                "GREEN HILL",     // Zone 0
                "MARBLE",         // Zone 1
                "SPRING YARD",    // Zone 2
                "LABYRINTH",      // Zone 3
                "STAR LIGHT",     // Zone 4
                "SCRAP BRAIN",    // Zone 5
                "FINAL",          // Zone 6
                "ENDING"          // Zone 7
        };

        for (int i = 0; i < expectedOrder.length; i++) {
            assertEquals("Zone " + i + " name", expectedOrder[i], registry.getZoneName(i));
        }
    }

    @Test
    public void testSonic1FinalZoneHasSingleAct() {
        Sonic1ZoneRegistry registry = Sonic1ZoneRegistry.getInstance();
        // Final Zone is zone 6 (second-to-last), has 1 act
        assertEquals("Final Zone has 1 act", 1, registry.getActCount(6));
        assertEquals("Zone 6 is Final Zone", "FINAL", registry.getZoneName(6));
        // Ending (zone 7) has 2 variants (flowers / no emeralds)
        assertEquals("Ending has 2 acts", 2, registry.getActCount(7));
        assertEquals("Zone 7 is Ending", "ENDING", registry.getZoneName(7));
    }

    @Test
    public void testZoneIndexBoundsAfterFinalZone() {
        Sonic2ZoneRegistry registry = Sonic2ZoneRegistry.getInstance();

        // After the final zone, currentZone would exceed the zone count.
        // The TODO at LevelManager:2527 wraps to 0 instead of triggering end-game.
        int zoneAfterFinal = registry.getZoneCount(); // would be 11
        assertTrue("Zone index after final zone exceeds zone count",
                zoneAfterFinal >= registry.getZoneCount());

        // Out-of-bounds zone name returns "UNKNOWN" (not crash)
        assertEquals("Out-of-bounds zone returns UNKNOWN",
                "UNKNOWN", registry.getZoneName(zoneAfterFinal));
    }

    /**
     * Verifies that all ZoneRegistry boundary methods return safe defaults
     * for out-of-bounds zone indices (at, beyond, and negative). This is the
     * behavior that advanceToNextLevel() would encounter if it did not wrap.
     */
    @Test
    public void testZoneIndexBeyondFinalIsHandledGracefully() {
        assertBoundaryBehavior("Sonic 2", Sonic2ZoneRegistry.getInstance());
        assertBoundaryBehavior("Sonic 1", Sonic1ZoneRegistry.getInstance());
    }

    private void assertBoundaryBehavior(String gameName, ZoneRegistry registry) {
        int count = registry.getZoneCount();
        assertTrue(gameName + " must have at least one zone", count > 0);

        // Indices at and beyond the final zone
        int[] outOfBounds = { count, count + 1, count + 100 };
        for (int idx : outOfBounds) {
            assertEquals(gameName + " getZoneName(" + idx + ") should return UNKNOWN",
                    "UNKNOWN", registry.getZoneName(idx));
            assertEquals(gameName + " getActCount(" + idx + ") should return 0",
                    0, registry.getActCount(idx));
            assertTrue(gameName + " getLevelDataForZone(" + idx + ") should return empty list",
                    registry.getLevelDataForZone(idx).isEmpty());
        }

        // Negative index
        assertEquals(gameName + " getZoneName(-1) should return UNKNOWN",
                "UNKNOWN", registry.getZoneName(-1));
        assertEquals(gameName + " getActCount(-1) should return 0",
                0, registry.getActCount(-1));
        assertTrue(gameName + " getLevelDataForZone(-1) should return empty list",
                registry.getLevelDataForZone(-1).isEmpty());
    }

}
