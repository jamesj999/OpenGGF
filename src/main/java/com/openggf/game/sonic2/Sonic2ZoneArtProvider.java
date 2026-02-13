package com.openggf.game.sonic2;

import com.openggf.game.ZoneArtProvider;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonic 2-specific implementation of ZoneArtProvider.
 * Maps object IDs to zone-specific art addresses, palettes, and compression types.
 */
public class Sonic2ZoneArtProvider implements ZoneArtProvider {

    @Override
    public ObjectArtConfig getObjectArt(int objectId, int zoneId) {
        return switch (objectId) {
            case Sonic2ObjectIds.GENERIC_PLATFORM_B -> getPlatformArt(zoneId);
            case Sonic2ObjectIds.BREAKABLE_BLOCK -> getBreakableBlockArt(zoneId);
            default -> null;
        };
    }

    @Override
    public List<ArtLoadRequest> getZoneArt(int zoneId, int actId) {
        List<ArtLoadRequest> requests = new ArrayList<>();

        // Add zone-specific platform art if applicable
        ObjectArtConfig platformArt = getPlatformArt(zoneId);
        if (platformArt != null) {
            requests.add(new ArtLoadRequest(
                    "Platform_" + zoneId,
                    platformArt.artAddress(),
                    platformArt.palette(),
                    platformArt.compression()));
        }

        // Add zone-specific breakable block art if applicable
        ObjectArtConfig breakableBlockArt = getBreakableBlockArt(zoneId);
        if (breakableBlockArt != null) {
            requests.add(new ArtLoadRequest(
                    "BreakableBlock_" + zoneId,
                    breakableBlockArt.artAddress(),
                    breakableBlockArt.palette(),
                    breakableBlockArt.compression()));
        }

        return requests;
    }

    /**
     * Gets the art configuration for CPZ/OOZ/WFZ moving platforms (Object 0x19).
     * Each zone uses different art and potentially different palettes.
     */
    private ObjectArtConfig getPlatformArt(int zoneId) {
        if (zoneId == Sonic2Constants.ZONE_OIL_OCEAN) {
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_OOZ_ELEVATOR_ADDR, 3);
        } else if (zoneId == Sonic2Constants.ZONE_WING_FORTRESS) {
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_WFZ_PLATFORM_ADDR, 1);
        } else if (zoneId == Sonic2Constants.ZONE_CHEMICAL_PLANT) {
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR, 3);
        } else {
            // Default to CPZ art for other zones (object may not appear but provides fallback)
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR, 3);
        }
    }

    /**
     * Gets the art configuration for Breakable Block / Rock (Object 0x32).
     * HTZ uses rock art (palette 2), CPZ uses metal block art (palette 3).
     */
    private ObjectArtConfig getBreakableBlockArt(int zoneId) {
        if (zoneId == Sonic2Constants.ZONE_HTZ) {
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_HTZ_ROCK_ADDR, 2);
        } else if (zoneId == Sonic2Constants.ZONE_CHEMICAL_PLANT) {
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_CPZ_METAL_BLOCK_ADDR, 3);
        } else {
            // Default to CPZ art for other zones (object may not appear but provides fallback)
            return ObjectArtConfig.nemesis(Sonic2Constants.ART_NEM_CPZ_METAL_BLOCK_ADDR, 3);
        }
    }
}
