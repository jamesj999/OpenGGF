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
    public void unknownZoneReturnsSharedOnlyPlan() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0xFF, 0);

        assertNotNull(plan);
        assertTrue("Expected at least 7 shared level art entries",
                plan.levelArt().size() >= 7);
        assertTrue("Expected no standalone art for unknown zone",
                plan.standaloneArt().isEmpty());
    }
}
