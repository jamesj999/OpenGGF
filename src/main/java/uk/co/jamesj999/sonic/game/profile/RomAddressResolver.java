package uk.co.jamesj999.sonic.game.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Singleton resolver for ROM addresses with layered resolution priority:
 * <ol>
 *   <li><strong>Profile</strong> - values from a {@link RomProfile} (highest priority)</li>
 *   <li><strong>Scanned</strong> - values discovered by runtime ROM scanning</li>
 *   <li><strong>Defaults</strong> - hardcoded fallback values from constants classes</li>
 * </ol>
 *
 * <p>Profile values always win over scanned and default values. Scanned values
 * fill gaps not covered by the profile and override defaults. Default values
 * are the final fallback.</p>
 *
 * <p>Keys are composite strings in the form {@code "category.name"}, e.g.
 * {@code "level.LEVEL_LAYOUT_INDEX_ADDR"}.</p>
 */
public class RomAddressResolver {

    private static final Logger logger = Logger.getLogger(RomAddressResolver.class.getName());

    private static RomAddressResolver instance;

    /** Resolved address values keyed by "category.name". */
    private final Map<String, Integer> resolved = new LinkedHashMap<>();

    /** Source tracking keyed by "category.name" -> "profile"|"scanned"|"default". */
    private final Map<String, String> sources = new LinkedHashMap<>();

    /** Zone slot mappings from the profile. */
    private final Map<Integer, ZoneMapping> zoneMappings = new LinkedHashMap<>();

    /** All default keys seen during initialize, used for report generation. */
    private final Map<String, Integer> allDefaults = new LinkedHashMap<>();

    /** Profile keys loaded during initialize. */
    private final Map<String, Integer> profileKeys = new LinkedHashMap<>();

    private RomAddressResolver() {
    }

    /**
     * Returns the singleton instance, creating it if necessary.
     */
    public static synchronized RomAddressResolver getInstance() {
        if (instance == null) {
            instance = new RomAddressResolver();
        }
        return instance;
    }

    /**
     * Resets the singleton instance. For testing only.
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * Initializes the resolver with a profile and default addresses.
     * Profile entries take highest priority; defaults fill remaining gaps.
     *
     * @param profile  the ROM profile (may be null if no profile is available)
     * @param defaults default addresses keyed by "category.name"
     */
    public void initialize(RomProfile profile, Map<String, Integer> defaults) {
        resolved.clear();
        sources.clear();
        zoneMappings.clear();
        allDefaults.clear();
        profileKeys.clear();

        // Store all default keys for report tracking
        if (defaults != null) {
            allDefaults.putAll(defaults);
        }

        // Layer 1: Load profile addresses (highest priority)
        if (profile != null) {
            for (Map.Entry<String, Map<String, AddressEntry>> categoryEntry : profile.getAddresses().entrySet()) {
                String category = categoryEntry.getKey();
                for (Map.Entry<String, AddressEntry> addressEntry : categoryEntry.getValue().entrySet()) {
                    String key = category + "." + addressEntry.getKey();
                    int value = addressEntry.getValue().value();
                    resolved.put(key, value);
                    sources.put(key, "profile");
                    profileKeys.put(key, value);
                }
            }

            // Load zone mappings
            if (profile.getZones() != null) {
                zoneMappings.putAll(profile.getZones());
            }
        }

        // Layer 2 (scanned) is added later via addScannedAddresses()

        // Layer 3: Load defaults (lowest priority - only fill gaps)
        if (defaults != null) {
            for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
                if (!resolved.containsKey(entry.getKey())) {
                    resolved.put(entry.getKey(), entry.getValue());
                    sources.put(entry.getKey(), "default");
                }
            }
        }

        logResolutionSummary();
    }

    /**
     * Adds addresses discovered by runtime ROM scanning. Scanned values fill gaps
     * not covered by the profile but DO override default values.
     *
     * @param scanned scanned addresses keyed by "category.name"
     */
    public void addScannedAddresses(Map<String, Integer> scanned) {
        if (scanned == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : scanned.entrySet()) {
            String key = entry.getKey();
            // Scanned does NOT override profile values, but DOES override defaults
            if (profileKeys.containsKey(key)) {
                continue;
            }
            resolved.put(key, entry.getValue());
            sources.put(key, "scanned");
        }
    }

    /**
     * Resolves an address by category and name.
     *
     * @param category the address category (e.g. "level", "audio")
     * @param name     the address name within the category
     * @return the resolved address value, or -1 if not found
     */
    public int getAddress(String category, String name) {
        return getAddress(category, name, -1);
    }

    /**
     * Resolves an address by category and name, returning a caller-provided default
     * if the address is not found in any resolution layer.
     *
     * @param category     the address category (e.g. "level", "audio")
     * @param name         the address name within the category
     * @param defaultValue the value to return if the address is not resolved
     * @return the resolved address value, or {@code defaultValue} if not found
     */
    public int getAddress(String category, String name, int defaultValue) {
        String key = category + "." + name;
        Integer value = resolved.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Convenience method for level addresses.
     *
     * @param name the address name
     * @return the resolved address value, or -1 if not found
     */
    public int getLevelAddress(String name) {
        return getAddress("level", name);
    }

    /**
     * Convenience method for level addresses with a default.
     *
     * @param name         the address name
     * @param defaultValue the value to return if the address is not resolved
     * @return the resolved address value, or {@code defaultValue} if not found
     */
    public int getLevelAddress(String name, int defaultValue) {
        return getAddress("level", name, defaultValue);
    }

    /**
     * Convenience method for audio addresses.
     *
     * @param name the address name
     * @return the resolved address value, or -1 if not found
     */
    public int getAudioAddress(String name) {
        return getAddress("audio", name);
    }

    /**
     * Convenience method for art addresses.
     *
     * @param name the address name
     * @return the resolved address value, or -1 if not found
     */
    public int getArtAddress(String name) {
        return getAddress("art", name);
    }

    /**
     * Convenience method for collision addresses.
     *
     * @param name the address name
     * @return the resolved address value, or -1 if not found
     */
    public int getCollisionAddress(String name) {
        return getAddress("collision", name);
    }

    /**
     * Convenience method for palette addresses.
     *
     * @param name the address name
     * @return the resolved address value, or -1 if not found
     */
    public int getPaletteAddress(String name) {
        return getAddress("palette", name);
    }

    /**
     * Looks up the behavior mapping string for a zone slot ID.
     *
     * @param zoneId the zone slot index
     * @return the behavior mapping string, or null if the zone is not mapped
     *         or has a null behavior mapping
     */
    public String getZoneBehavior(int zoneId) {
        ZoneMapping mapping = zoneMappings.get(zoneId);
        return mapping != null ? mapping.behaviorMapping() : null;
    }

    /**
     * Returns a report of resolution statistics.
     *
     * @return the resolution report
     */
    public ResolutionReport getReport() {
        int fromProfile = 0;
        int fromScanned = 0;
        int fromDefaults = 0;

        for (String source : sources.values()) {
            switch (source) {
                case "profile" -> fromProfile++;
                case "scanned" -> fromScanned++;
                case "default" -> fromDefaults++;
            }
        }

        // Missing = default keys that have no resolution in `resolved`
        int missing = 0;
        for (String key : allDefaults.keySet()) {
            if (!resolved.containsKey(key)) {
                missing++;
            }
        }

        return new ResolutionReport(fromProfile, fromScanned, fromDefaults, missing);
    }

    private void logResolutionSummary() {
        ResolutionReport report = getReport();
        logger.info(() -> String.format(
                "ROM address resolution: %d from profile, %d from defaults, %d missing (total expected: %d)",
                report.fromProfile(), report.fromDefaults(), report.missing(), report.totalExpected()
        ));
    }
}
