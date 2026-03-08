package com.openggf.game.sonic3k;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestSonic3kPlcArtRegistry {

    @Test
    public void sharedLevelArtEntriesIncludeSpikesAndSprings() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 0);

        assertNotNull(plan);
        assertTrue("Expected at least 7 shared level art entries",
                plan.levelArt().size() >= 7);

        boolean hasSpikes = plan.levelArt().stream()
                .anyMatch(e -> Sonic3kObjectArtKeys.SPIKES.equals(e.key()));
        assertTrue("Expected spikes entry in level art", hasSpikes);
    }

    @Test
    public void aiz1PlanIncludesBadnikAndLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 0);
        assertEquals(3, plan.standaloneArt().size());

        // Verify Bloominator
        Sonic3kPlcArtRegistry.StandaloneArtEntry bloominator = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.BLOOMINATOR))
                .findFirst().orElse(null);
        assertNotNull(bloominator);

        // Verify Rhinobot has DPLC
        Sonic3kPlcArtRegistry.StandaloneArtEntry rhinobot = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.RHINOBOT))
                .findFirst().orElse(null);
        assertNotNull(rhinobot);
        assertTrue(rhinobot.dplcAddr() > 0);

        // 7 shared + 3 shared AIZ + 4 act-1 specific = 14
        assertTrue(plan.levelArt().size() > 7);
    }

    @Test
    public void aiz2PlanHasAct2SpecificEntries() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 1);
        assertEquals(3, plan.standaloneArt().size());
        boolean hasAiz2Rock = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.AIZ2_ROCK));
        assertTrue(hasAiz2Rock);
        // Act 2 should NOT have Act 1 tree
        boolean hasAiz1Tree = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.AIZ1_TREE));
        assertFalse(hasAiz1Tree);
    }

    @Test
    public void unknownZoneReturnsSharedOnlyPlan() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0xFF, 0);

        assertNotNull(plan);
        assertTrue("Expected at least 7 shared level art entries",
                plan.levelArt().size() >= 7);
        assertTrue("Expected no standalone art for unknown zone",
                plan.standaloneArt().isEmpty());
    }
}
