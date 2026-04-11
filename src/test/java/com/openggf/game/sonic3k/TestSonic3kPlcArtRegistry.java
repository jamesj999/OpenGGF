package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestSonic3kPlcArtRegistry {

    @Test
    public void sharedLevelArtEntriesIncludeSpikesAndSprings() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 0);

        assertNotNull(plan);
        assertTrue(plan.levelArt().size() >= 7, "Expected at least 7 shared level art entries");

        boolean hasSpikes = plan.levelArt().stream()
                .anyMatch(e -> Sonic3kObjectArtKeys.SPIKES.equals(e.key()));
        assertTrue(hasSpikes, "Expected spikes entry in level art");
    }

    @Test
    public void aiz1PlanIncludesBadnikAndLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 0);
        assertEquals(9, plan.standaloneArt().size());

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
        assertEquals(9, plan.standaloneArt().size());
        boolean hasAiz2Rock = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.AIZ2_ROCK));
        assertTrue(hasAiz2Rock);
        // Act 2 should NOT have Act 1 tree
        boolean hasAiz1Tree = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.AIZ1_TREE));
        assertFalse(hasAiz1Tree);
    }

    @Test
    public void hcz1PlanHasBlastoidNotJawz() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x01, 0);
        assertNotNull(plan);
        assertEquals(18, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_LARGE_FAN)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_BLASTOID)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_JAWZ)));
    }

    @Test
    public void hcz2PlanHasJawzNotBlastoid() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x01, 1);
        assertNotNull(plan);
        assertEquals(18, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_LARGE_FAN)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_JAWZ)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_BLASTOID)));
    }

    @Test
    public void mgz1PlanHasMinibossAndDiagonalSpringOverride() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x02, 0);
        assertNotNull(plan);
        assertEquals(9, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_SPIKER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MANTIS)));

        // Diagonal spring should use MGZ/MHZ art tile
        Sonic3kPlcArtRegistry.LevelArtEntry diag = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL))
                .findFirst().orElse(null);
        assertNotNull(diag);
        assertEquals(0x0478, diag.artTileBase());
    }

    @Test
    public void mgz2PlanHasMantisNotMiniboss() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x02, 1);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MANTIS)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS)));
    }

    @Test
    public void cnzPlanHasFiveBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x03, 0);
        assertNotNull(plan);
        assertEquals(10, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_SPARKLE)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_BATBOT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_CLAMER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_CLAMER_SHOT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_BALLOON)));

        // CNZ Balloon should use palette 0
        Sonic3kPlcArtRegistry.StandaloneArtEntry balloon = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_BALLOON))
                .findFirst().orElse(null);
        assertNotNull(balloon);
        assertEquals(0, balloon.palette());
    }

    @Test
    public void fbzPlanOverridesSpikes() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x04, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_BLASTER)));

        // Spikes should use FBZ art tile
        Sonic3kPlcArtRegistry.LevelArtEntry spikes = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SPIKES))
                .findFirst().orElse(null);
        assertNotNull(spikes);
        assertEquals(0x0200, spikes.artTileBase());
    }

    @Test
    public void iczPlanHasCollapsingBridgeAndBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x05, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_SNOWDUST)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_STAR_POINTER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_PENGUINATOR)));

        // Penguinator should have DPLC
        Sonic3kPlcArtRegistry.StandaloneArtEntry penguin = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_PENGUINATOR))
                .findFirst().orElse(null);
        assertNotNull(penguin);
        assertTrue(penguin.dplcAddr() > 0);

        // Collapsing bridge level-art should still be present
        boolean hasBridge = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_ICZ));
        assertTrue(hasBridge);
    }

    @Test
    public void lbzPlanHasFourBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x06, 0);
        assertNotNull(plan);
        assertEquals(9, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SNALE_BLASTER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ORBINAUT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.RIBOT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CORKEY)));
    }

    @Test
    public void mhz1PlanHasFourBadniksNoArrow() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x07, 0);
        assertNotNull(plan);
        assertEquals(9, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MADMOLE)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CLUCKOID)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CLUCKOID_ARROW)));
    }

    @Test
    public void mhz2PlanHasCluckoidArrowAndDiagonalSpringOverride() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x07, 1);
        assertNotNull(plan);
        assertEquals(10, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CLUCKOID_ARROW)));

        // Diagonal spring should use MGZ/MHZ art tile
        Sonic3kPlcArtRegistry.LevelArtEntry diag = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL))
                .findFirst().orElse(null);
        assertNotNull(diag);
        assertEquals(0x0478, diag.artTileBase());
    }

    @Test
    public void sozPlanHasThreeBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x08, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SKORP)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SANDWORM)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ROCKN)));
    }

    @Test
    public void lrzPlanHasRocksAndBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x09, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.FIREWORM_SEGMENTS)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.IWAMODOKI)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.TOXOMISTER)));

        // Level-art rock should be act 1 variant
        boolean hasLrz1Rock = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.LRZ1_ROCK));
        assertTrue(hasLrz1Rock);
    }

    @Test
    public void sszPlanHasEggRobo() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x0A, 0);
        assertNotNull(plan);
        assertEquals(6, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SSZ_EGG_ROBO)));

        // EggRobo should use palette 0
        Sonic3kPlcArtRegistry.StandaloneArtEntry eggRobo = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SSZ_EGG_ROBO))
                .findFirst().orElse(null);
        assertNotNull(eggRobo);
        assertEquals(0, eggRobo.palette());
    }

    @Test
    public void dezPlanHasTwoBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x0B, 0);
        assertNotNull(plan);
        assertEquals(7, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SPIKEBONKER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CHAINSPIKE)));
    }

    @Test
    public void ddzPlanHasEggRobo() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x0C, 0);
        assertNotNull(plan);
        assertEquals(6, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.DDZ_EGG_ROBO)));
        Sonic3kPlcArtRegistry.StandaloneArtEntry ddzEggRobo = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DDZ_EGG_ROBO))
                .findFirst().orElse(null);
        assertNotNull(ddzEggRobo);
        assertEquals(0, ddzEggRobo.palette());
    }

    @Test
    public void pachinkoPlanHasBonusStageObjectArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x14, 0);
        assertNotNull(plan);

        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_BUMPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_TRIANGLE_BUMPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_FLIPPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_ENERGY_TRAP)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_INVISIBLE_UNKNOWN)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_PLATFORM)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_ITEM_ORB)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_GUMBALLS)));

        Sonic3kPlcArtRegistry.LevelArtEntry bumper = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_BUMPER))
                .findFirst().orElse(null);
        assertNotNull(bumper);
        assertEquals(Sonic3kConstants.ARTTILE_PACHINKO_MAIN, bumper.artTileBase());

        Sonic3kPlcArtRegistry.LevelArtEntry gumballs = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_GUMBALLS))
                .findFirst().orElse(null);
        assertNotNull(gumballs);
        assertEquals(Sonic3kConstants.ARTTILE_PACHINKO_GUMBALLS, gumballs.artTileBase());

        Sonic3kPlcArtRegistry.LevelArtEntry invisibleBeam = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_INVISIBLE_UNKNOWN))
                .findFirst().orElse(null);
        assertNotNull(invisibleBeam);
        assertEquals(Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x97, invisibleBeam.artTileBase());
    }

    @Test
    public void slotsPlanHasBonusStageObjectArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x15, 0);
        assertNotNull(plan);

        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_COLORED_WALL)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_GOAL)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_BUMPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_R_LABEL)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_PEPPERMINT)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_MACHINE_FACE)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_BONUS_CAGE)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_SPIKE_REWARD)));

        Sonic3kPlcArtRegistry.LevelArtEntry cage = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_BONUS_CAGE))
                .findFirst().orElse(null);
        assertNotNull(cage);
        assertEquals(Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x146, cage.artTileBase());
        assertEquals(Sonic3kConstants.MAP_SLOT_BONUS_CAGE_ADDR, cage.mappingAddr());
        assertEquals(S3kSpriteDataLoader.MappingFormat.STANDARD, cage.mappingFormat());

        Sonic3kPlcArtRegistry.LevelArtEntry spike = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_SPIKE_REWARD))
                .findFirst().orElse(null);
        assertNotNull(spike);
        assertEquals(Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x155, spike.artTileBase());
        assertEquals(Sonic3kConstants.MAP_SLOT_SPIKE_REWARD_ADDR, spike.mappingAddr());
        assertEquals(S3kSpriteDataLoader.MappingFormat.STANDARD, spike.mappingFormat());

        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_COLORED_WALL).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_GOAL).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_BUMPER).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_R_LABEL).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_PEPPERMINT).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_MACHINE_FACE).mappingFormat());

        Sonic3kPlcArtRegistry.StandaloneArtEntry ring = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_RING_STAGE))
                .findFirst().orElse(null);
        assertNotNull(ring);
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X, ring.mappingFormat());
    }

    private static Sonic3kPlcArtRegistry.LevelArtEntry findLevelArt(
            Sonic3kPlcArtRegistry.ZoneArtPlan plan, String key) {
        return plan.levelArt().stream()
                .filter(e -> e.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void allZonesReturnNonNullPlan() {
        // All 14 zone indices (0x00-0x0D) must return non-null plans for both acts
        for (int zone = 0x00; zone <= 0x0D; zone++) {
            for (int act = 0; act <= 1; act++) {
                Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, act);
                assertNotNull(plan, "Zone 0x" + Integer.toHexString(zone) + " act " + act + " returned null");
                assertNotNull(plan.standaloneArt());
                assertNotNull(plan.levelArt());
                // All zones should have at least the 7 shared level-art entries
                assertTrue(plan.levelArt().size() >= 7, "Zone 0x" + Integer.toHexString(zone) + " act " + act
                                + " has fewer than 7 level art entries");
            }
        }
    }

    @Test
    public void allPopulatedZonesHaveStandaloneEntries() {
        // Every zone except HPZ (0x0D) should have at least one standalone entry
        int[] populatedZones = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C};
        for (int zone : populatedZones) {
            Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, 0);
            assertFalse(plan.standaloneArt().isEmpty(), "Zone 0x" + Integer.toHexString(zone) + " should have standalone entries");
        }
    }

    @Test
    public void noDuplicateKeysInPlan() {
        // No plan should contain duplicate keys across standalone + level art
        for (int zone = 0x00; zone <= 0x0D; zone++) {
            for (int act = 0; act <= 1; act++) {
                Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, act);
                Set<String> keys = new HashSet<>();
                for (var e : plan.standaloneArt()) {
                    assertTrue(keys.add(e.key()), "Duplicate standalone key '" + e.key() + "' in zone 0x"
                            + Integer.toHexString(zone) + " act " + act);
                }
                for (var e : plan.levelArt()) {
                    assertTrue(keys.add(e.key()), "Duplicate level-art key '" + e.key() + "' in zone 0x"
                            + Integer.toHexString(zone) + " act " + act);
                }
            }
        }
    }

    @Test
    public void unknownZoneReturnsSharedOnlyPlan() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0xFF, 0);

        assertNotNull(plan);
        assertTrue(plan.levelArt().size() >= 7, "Expected at least 7 shared level art entries");
        assertEquals(5, plan.standaloneArt().size(), "Expected shared standalone art only for unknown zone");
    }
}


