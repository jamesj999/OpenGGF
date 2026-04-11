package com.openggf.tests;

import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.LevelArtEntry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.TensionBridgeObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kTensionBridgeObject {

    @Test
    void registryCreatesTensionBridgeInstance() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.TENSION_BRIDGE, 0x0C, 0x00, false, 0));
        assertInstanceOf(TensionBridgeObjectInstance.class, instance);
    }

    @Test
    void objectUsesExpectedSolidParams() {
        // Subtype 0x8C: bit 7 = collapse variant, effective count = 0x0C = 12 segments
        TensionBridgeObjectInstance instance = new TensionBridgeObjectInstance(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.TENSION_BRIDGE, 0x8C, 0x00, false, 0));
        SolidObjectParams params = instance.getSolidParams();
        // ROM: halfWidth = segCount * 8, offsetX = -8, offsetY = -8 (surface offset)
        assertEquals(12 * 8, params.halfWidth());
        assertEquals(0, params.airHalfHeight());
        assertEquals(0, params.groundHalfHeight());
        assertEquals(-8, params.offsetX());
        assertEquals(-8, params.offsetY());
        assertTrue(instance.isTopSolidOnly());
        assertEquals(4, instance.getPriorityBucket());
    }

    @Test
    void artRegistryProvidesZoneSpecificBridgeSheets() {
        LevelArtEntry hcz = findLevelEntry(Sonic3kPlcArtRegistry.getPlan(0x01, 0).levelArt(),
                Sonic3kObjectArtKeys.TENSION_BRIDGE_HCZ);
        LevelArtEntry icz = findLevelEntry(Sonic3kPlcArtRegistry.getPlan(0x05, 0).levelArt(),
                Sonic3kObjectArtKeys.TENSION_BRIDGE_ICZ);
        LevelArtEntry lrz = findLevelEntry(Sonic3kPlcArtRegistry.getPlan(0x09, 0).levelArt(),
                Sonic3kObjectArtKeys.TENSION_BRIDGE_LRZ);

        assertEquals(Sonic3kConstants.MAP_TENSION_BRIDGE_ADDR, hcz.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_HCZ_TENSION_BRIDGE, hcz.artTileBase());
        assertEquals(2, hcz.palette());

        assertEquals(Sonic3kConstants.MAP_ICZ_TENSION_BRIDGE_ADDR, icz.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_ICZ_MISC1, icz.artTileBase());
        assertEquals(2, icz.palette());

        assertEquals(Sonic3kConstants.MAP_TENSION_BRIDGE_ADDR, lrz.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_LRZ_TENSION_BRIDGE, lrz.artTileBase());
        assertEquals(3, lrz.palette());
    }

    @Test
    void profileMarksTensionBridgeImplemented() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.TENSION_BRIDGE));
    }

    private static LevelArtEntry findLevelEntry(List<LevelArtEntry> entries, String key) {
        return entries.stream()
                .filter(entry -> key.equals(entry.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing art entry: " + key));
    }
}


