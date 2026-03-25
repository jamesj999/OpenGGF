package com.openggf.game;

import com.openggf.level.LevelData;

import java.util.List;

/**
 * Base implementation of {@link ZoneRegistry} providing the five methods
 * that are identical across all game modules: zone count, act count,
 * zone name lookup, per-zone level data, and the full zone list.
 *
 * <p>Subclasses supply the zone data and zone names via the constructor,
 * then implement {@link #getStartPosition(int, int)} (game-specific
 * fallback coordinates) and {@link #getMusicId(int, int)} (S1/S2 use
 * a per-zone 1-D array; S3K uses a per-act 2-D array).
 */
public abstract class AbstractZoneRegistry implements ZoneRegistry {

    protected final List<List<LevelData>> zones;
    protected final String[] zoneNames;

    /**
     * @param zones     outer list = zones, inner list = acts
     * @param zoneNames display names, one per zone
     */
    protected AbstractZoneRegistry(List<List<LevelData>> zones, String[] zoneNames) {
        this.zones = zones;
        this.zoneNames = zoneNames;
    }

    @Override
    public int getZoneCount() {
        return zones.size();
    }

    @Override
    public int getActCount(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= zones.size()) {
            return 0;
        }
        return zones.get(zoneIndex).size();
    }

    @Override
    public String getZoneName(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= zoneNames.length) {
            return "UNKNOWN";
        }
        return zoneNames[zoneIndex];
    }

    @Override
    public List<LevelData> getLevelDataForZone(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= zones.size()) {
            return List.of();
        }
        return zones.get(zoneIndex);
    }

    @Override
    public List<List<LevelData>> getAllZones() {
        return zones;
    }
}
