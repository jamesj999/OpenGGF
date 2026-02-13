package uk.co.jamesj999.sonic.game.profile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility that creates {@link RomProfile} instances from flat offset maps
 * (e.g. from constants classes) and converts them to resolver-keyed format.
 *
 * <p>Each constant name is auto-categorized by prefix to match the categories
 * used by {@link RomAddressResolver}. This enables generating shipped profiles
 * from hardcoded constants and providing defaults to the resolver.</p>
 */
public class ProfileGenerator {

    /**
     * Creates a {@link RomProfile} from a flat map of constant names to values.
     * Each entry is auto-categorized by name prefix. All entries receive
     * "verified" confidence. Metadata is set with generatedBy="shipped" and
     * complete=true.
     *
     * @param name     human-readable profile name (e.g. "Sonic 2 REV01")
     * @param game     game identifier (e.g. "sonic2")
     * @param checksum ROM checksum string
     * @param offsets  flat map of constant names to ROM offset values
     * @return a fully-populated RomProfile
     */
    public RomProfile generateFromOffsets(String name, String game, String checksum,
                                          Map<String, Integer> offsets) {
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata(name, game, checksum, "shipped", true));

        for (Map.Entry<String, Integer> entry : offsets.entrySet()) {
            String fieldName = entry.getKey();
            String category = categorize(fieldName);
            profile.putAddress(category, fieldName, new AddressEntry(entry.getValue(), "verified"));
        }

        return profile;
    }

    /**
     * Converts a flat offset map to resolver-keyed format where keys become
     * "category.name" (e.g. "level.LEVEL_LAYOUT_DIR_ADDR_LOC").
     *
     * @param offsets flat map of constant names to ROM offset values
     * @return map with composite "category.name" keys suitable for
     *         {@link RomAddressResolver#initialize(RomProfile, Map)}
     */
    public static Map<String, Integer> toResolverDefaults(Map<String, Integer> offsets) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : offsets.entrySet()) {
            String fieldName = entry.getKey();
            String category = categorize(fieldName);
            result.put(category + "." + fieldName, entry.getValue());
        }
        return result;
    }

    /**
     * Categorizes a field name into a profile category based on its prefix.
     *
     * <p>Categorization rules (checked in order, first match wins):</p>
     * <ul>
     *   <li>{@code SONIC_ANIM_*}, {@code TAILS_ANIM_*} &rarr; "art"</li>
     *   <li>{@code ART_*}, {@code MAP_*}, {@code DPLC_*} &rarr; "art"</li>
     *   <li>{@code ANIM_PAT_*}, {@code ANI_*} &rarr; "animation"</li>
     *   <li>{@code LEVEL_*}, {@code DEFAULT_LEVEL_*}, {@code BG_SCROLL_*},
     *       {@code RING_*}, {@code START_LOC_*} &rarr; "level"</li>
     *   <li>{@code COLLISION_*}, {@code SOLID_*}, {@code ALT_COLLISION_*} &rarr; "collision"</li>
     *   <li>{@code MUSIC_*}, {@code SFX_*}, {@code SOUND_*}, {@code DAC_*},
     *       {@code Z80_*}, {@code PSG_*}, {@code SPEED_UP_*}, {@code SEGA_SOUND_*} &rarr; "audio"</li>
     *   <li>{@code PAL_*}, {@code CYCLING_PAL_*} &rarr; "palette"</li>
     *   <li>Any name containing {@code PALETTE} &rarr; "palette"</li>
     *   <li>{@code OBJECT_*}, {@code OBJ_POS_*}, {@code TOUCH_*} &rarr; "objects"</li>
     *   <li>Everything else &rarr; "misc"</li>
     * </ul>
     *
     * @param fieldName the constant field name to categorize
     * @return the category string
     */
    static String categorize(String fieldName) {
        // Character animation data addresses (must check before generic prefixes)
        if (fieldName.startsWith("SONIC_ANIM_") || fieldName.startsWith("TAILS_ANIM_")) {
            return "art";
        }

        // Art / mapping / DPLC data
        if (fieldName.startsWith("ART_") || fieldName.startsWith("MAP_") || fieldName.startsWith("DPLC_")) {
            return "art";
        }

        // Animation pattern scripts
        if (fieldName.startsWith("ANIM_PAT_") || fieldName.startsWith("ANI_")) {
            return "animation";
        }

        // Level layout, sizes, scrolling, rings, start locations
        if (fieldName.startsWith("LEVEL_") || fieldName.startsWith("DEFAULT_LEVEL_")
                || fieldName.startsWith("BG_SCROLL_") || fieldName.startsWith("RING_")
                || fieldName.startsWith("START_LOC_")) {
            return "level";
        }

        // Collision data
        if (fieldName.startsWith("COLLISION_") || fieldName.startsWith("SOLID_")
                || fieldName.startsWith("ALT_COLLISION_")) {
            return "collision";
        }

        // Audio: music, SFX, sound driver, DAC, Z80, PSG, speed-up, SEGA sound
        if (fieldName.startsWith("MUSIC_") || fieldName.startsWith("SFX_")
                || fieldName.startsWith("SOUND_") || fieldName.startsWith("DAC_")
                || fieldName.startsWith("Z80_") || fieldName.startsWith("PSG_")
                || fieldName.startsWith("SPEED_UP_") || fieldName.startsWith("SEGA_SOUND_")) {
            return "audio";
        }

        // Palette: PAL_ prefix, CYCLING_PAL_ prefix
        if (fieldName.startsWith("PAL_") || fieldName.startsWith("CYCLING_PAL_")) {
            return "palette";
        }

        // Palette: any name containing PALETTE (e.g. SONIC_PALETTE_ADDR)
        if (fieldName.contains("PALETTE")) {
            return "palette";
        }

        // Object placement and touch response
        if (fieldName.startsWith("OBJECT_") || fieldName.startsWith("OBJ_POS_")
                || fieldName.startsWith("TOUCH_")) {
            return "objects";
        }

        return "misc";
    }
}
