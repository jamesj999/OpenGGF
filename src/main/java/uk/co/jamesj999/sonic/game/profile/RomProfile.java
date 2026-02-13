package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level container for a ROM profile. Holds metadata, categorized ROM addresses,
 * and zone slot mappings. Jackson-serializable for JSON persistence.
 */
public class RomProfile {

    @JsonProperty("profile")
    private ProfileMetadata metadata;

    @JsonProperty("addresses")
    private Map<String, Map<String, AddressEntry>> addresses = new LinkedHashMap<>();

    @JsonProperty("zones")
    private Map<Integer, ZoneMapping> zones = new LinkedHashMap<>();

    public ProfileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ProfileMetadata metadata) {
        this.metadata = metadata;
    }

    public Map<String, Map<String, AddressEntry>> getAddresses() {
        return addresses;
    }

    public void setAddresses(Map<String, Map<String, AddressEntry>> addresses) {
        this.addresses = addresses;
    }

    public Map<Integer, ZoneMapping> getZones() {
        return zones;
    }

    public void setZones(Map<Integer, ZoneMapping> zones) {
        this.zones = zones;
    }

    /**
     * Look up an address entry by category and name.
     *
     * @param category address category (e.g. "level", "audio")
     * @param name     address name within the category
     * @return the entry, or null if the category or name is not present
     */
    public AddressEntry getAddress(String category, String name) {
        Map<String, AddressEntry> categoryMap = addresses.get(category);
        if (categoryMap == null) {
            return null;
        }
        return categoryMap.get(name);
    }

    /**
     * Store an address entry under a category. Creates the category if it does not exist.
     *
     * @param category address category (e.g. "level", "audio")
     * @param name     address name within the category
     * @param entry    the address entry to store
     */
    public void putAddress(String category, String name, AddressEntry entry) {
        addresses.computeIfAbsent(category, k -> new LinkedHashMap<>()).put(name, entry);
    }

    /**
     * Look up a zone mapping by zone slot ID.
     *
     * @param zoneId the zone slot index
     * @return the mapping, or null if not present
     */
    public ZoneMapping getZoneMapping(int zoneId) {
        return zones.get(zoneId);
    }

    /**
     * Store a zone mapping for a zone slot ID.
     *
     * @param zoneId  the zone slot index
     * @param mapping the zone mapping to store
     */
    public void putZoneMapping(int zoneId, ZoneMapping mapping) {
        zones.put(zoneId, mapping);
    }

    /**
     * Count the total number of address entries across all categories.
     *
     * @return total address count
     */
    public int addressCount() {
        return addresses.values().stream().mapToInt(Map::size).sum();
    }
}
