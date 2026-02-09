package uk.co.jamesj999.sonic.tools;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectPlacement;
import uk.co.jamesj999.sonic.level.LevelData;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.DynamicBoss;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.LevelConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sonic 3&amp;K object profile for the ObjectDiscoveryTool.
 */
public class Sonic3kObjectProfile implements GameObjectProfile {

    private static final List<LevelConfig> LEVELS = List.of(
            new LevelConfig(LevelData.S3K_ANGEL_ISLAND_1, "AIZ", "Angel Island Zone", 1),
            new LevelConfig(LevelData.S3K_ANGEL_ISLAND_2, "AIZ", "Angel Island Zone", 2),
            new LevelConfig(LevelData.S3K_HYDROCITY_1, "HCZ", "Hydrocity Zone", 1),
            new LevelConfig(LevelData.S3K_HYDROCITY_2, "HCZ", "Hydrocity Zone", 2),
            new LevelConfig(LevelData.S3K_MARBLE_GARDEN_1, "MGZ", "Marble Garden Zone", 1),
            new LevelConfig(LevelData.S3K_MARBLE_GARDEN_2, "MGZ", "Marble Garden Zone", 2),
            new LevelConfig(LevelData.S3K_CARNIVAL_NIGHT_1, "CNZ", "Carnival Night Zone", 1),
            new LevelConfig(LevelData.S3K_CARNIVAL_NIGHT_2, "CNZ", "Carnival Night Zone", 2),
            new LevelConfig(LevelData.S3K_FLYING_BATTERY_1, "FBZ", "Flying Battery Zone", 1),
            new LevelConfig(LevelData.S3K_FLYING_BATTERY_2, "FBZ", "Flying Battery Zone", 2),
            new LevelConfig(LevelData.S3K_ICECAP_1, "ICZ", "IceCap Zone", 1),
            new LevelConfig(LevelData.S3K_ICECAP_2, "ICZ", "IceCap Zone", 2),
            new LevelConfig(LevelData.S3K_LAUNCH_BASE_1, "LBZ", "Launch Base Zone", 1),
            new LevelConfig(LevelData.S3K_LAUNCH_BASE_2, "LBZ", "Launch Base Zone", 2),
            new LevelConfig(LevelData.S3K_MUSHROOM_HILL_1, "MHZ", "Mushroom Hill Zone", 1),
            new LevelConfig(LevelData.S3K_MUSHROOM_HILL_2, "MHZ", "Mushroom Hill Zone", 2),
            new LevelConfig(LevelData.S3K_SANDOPOLIS_1, "SOZ", "Sandopolis Zone", 1),
            new LevelConfig(LevelData.S3K_SANDOPOLIS_2, "SOZ", "Sandopolis Zone", 2),
            new LevelConfig(LevelData.S3K_LAVA_REEF_1, "LRZ", "Lava Reef Zone", 1),
            new LevelConfig(LevelData.S3K_LAVA_REEF_2, "LRZ", "Lava Reef Zone", 2),
            new LevelConfig(LevelData.S3K_SKY_SANCTUARY_1, "SSZ", "Sky Sanctuary Zone", 1),
            new LevelConfig(LevelData.S3K_SKY_SANCTUARY_2, "SSZ", "Sky Sanctuary Zone", 2),
            new LevelConfig(LevelData.S3K_DEATH_EGG_1, "DEZ", "Death Egg Zone", 1),
            new LevelConfig(LevelData.S3K_DEATH_EGG_2, "DEZ", "Death Egg Zone", 2),
            new LevelConfig(LevelData.S3K_DOOMSDAY, "DDZ", "The Doomsday Zone", 1)
    );

    // No S3K objects implemented yet
    private static final Set<Integer> IMPLEMENTED_IDS = Set.of();

    // Known badnik IDs from skdisasm Obj_ labels
    private static final Set<Integer> BADNIK_IDS = Set.of(
            0x01, // MonkeyDude (AIZ)
            0x02, // Caterkiller Jr. (MGZ)
            0x03, // Rhinobot (AIZ)
            0x04, // Bloominator (AIZ/MGZ)
            0x50, // Jawz (HCZ)
            0x51, // Buggernaut (MGZ)
            0x52, // TurboSpiker (HCZ)
            0x53, // MegaChopper (AIZ)
            0x54, // Pointdexter (HCZ)
            0x55, // Blaster (CNZ)
            0x56, // Sparkle (ICZ)
            0x58, // Batbot (CNZ)
            0x59, // Orbinaut (LBZ)
            0x5A, // Ribot (FBZ)
            0x5E, // Mushmeanie (MHZ)
            0x61, // Sandworm (SOZ)
            0x63, // Skorp (SOZ)
            0x65  // Fireworm (LRZ)
    );

    private static final Set<Integer> BOSS_IDS = Set.of();

    private static final Map<String, List<DynamicBoss>> DYNAMIC_BOSSES = Map.of();

    /** Known S3K object names from skdisasm Obj_ labels. */
    private static final Map<Integer, List<String>> OBJECT_NAMES = buildNames();

    private static Map<Integer, List<String>> buildNames() {
        Map<Integer, List<String>> map = new HashMap<>();
        // AIZ objects
        map.put(0x01, List.of("MonkeyDude"));
        map.put(0x02, List.of("CaterkillerJr"));
        map.put(0x03, List.of("Rhinobot"));
        map.put(0x04, List.of("Bloominator"));
        // Common objects
        map.put(0x0D, List.of("Signpost"));
        map.put(0x18, List.of("Platform"));
        map.put(0x22, List.of("CollapsingPlatform"));
        map.put(0x25, List.of("Ring"));
        map.put(0x26, List.of("Monitor"));
        map.put(0x2F, List.of("Spring"));
        map.put(0x36, List.of("Spikes"));
        map.put(0x3E, List.of("EggPrison"));
        // HCZ objects
        map.put(0x50, List.of("Jawz"));
        map.put(0x51, List.of("Buggernaut"));
        map.put(0x52, List.of("TurboSpiker"));
        map.put(0x53, List.of("MegaChopper"));
        map.put(0x54, List.of("Pointdexter"));
        // CNZ objects
        map.put(0x55, List.of("Blaster"));
        map.put(0x56, List.of("Sparkle"));
        map.put(0x58, List.of("Batbot"));
        // LBZ/FBZ objects
        map.put(0x59, List.of("Orbinaut"));
        map.put(0x5A, List.of("Ribot"));
        // MHZ objects
        map.put(0x5E, List.of("Mushmeanie"));
        // SOZ objects
        map.put(0x61, List.of("Sandworm"));
        map.put(0x63, List.of("Skorp"));
        // LRZ objects
        map.put(0x65, List.of("Fireworm"));
        // Misc common
        map.put(0x06, List.of("Spiral"));
        map.put(0x07, List.of("Bumper"));
        map.put(0x09, List.of("LayerSwitcher"));
        map.put(0x0A, List.of("Bridge"));
        map.put(0x11, List.of("SwingingPlatform"));
        map.put(0x12, List.of("SpindashDust", "Splash"));
        map.put(0x14, List.of("Starpost"));
        map.put(0x1F, List.of("BreakableWall"));
        return Map.copyOf(map);
    }

    /** Map S3K LevelData to zone/act indices for the pointer table. */
    private static final Map<LevelData, int[]> LEVEL_ZONE_ACT = Map.ofEntries(
            Map.entry(LevelData.S3K_ANGEL_ISLAND_1, new int[]{0, 0}),
            Map.entry(LevelData.S3K_ANGEL_ISLAND_2, new int[]{0, 1}),
            Map.entry(LevelData.S3K_HYDROCITY_1, new int[]{1, 0}),
            Map.entry(LevelData.S3K_HYDROCITY_2, new int[]{1, 1}),
            Map.entry(LevelData.S3K_MARBLE_GARDEN_1, new int[]{2, 0}),
            Map.entry(LevelData.S3K_MARBLE_GARDEN_2, new int[]{2, 1}),
            Map.entry(LevelData.S3K_CARNIVAL_NIGHT_1, new int[]{3, 0}),
            Map.entry(LevelData.S3K_CARNIVAL_NIGHT_2, new int[]{3, 1}),
            Map.entry(LevelData.S3K_FLYING_BATTERY_1, new int[]{4, 0}),
            Map.entry(LevelData.S3K_FLYING_BATTERY_2, new int[]{4, 1}),
            Map.entry(LevelData.S3K_ICECAP_1, new int[]{5, 0}),
            Map.entry(LevelData.S3K_ICECAP_2, new int[]{5, 1}),
            Map.entry(LevelData.S3K_LAUNCH_BASE_1, new int[]{6, 0}),
            Map.entry(LevelData.S3K_LAUNCH_BASE_2, new int[]{6, 1}),
            Map.entry(LevelData.S3K_MUSHROOM_HILL_1, new int[]{7, 0}),
            Map.entry(LevelData.S3K_MUSHROOM_HILL_2, new int[]{7, 1}),
            Map.entry(LevelData.S3K_SANDOPOLIS_1, new int[]{8, 0}),
            Map.entry(LevelData.S3K_SANDOPOLIS_2, new int[]{8, 1}),
            Map.entry(LevelData.S3K_LAVA_REEF_1, new int[]{9, 0}),
            Map.entry(LevelData.S3K_LAVA_REEF_2, new int[]{9, 1}),
            Map.entry(LevelData.S3K_SKY_SANCTUARY_1, new int[]{10, 0}),
            Map.entry(LevelData.S3K_SKY_SANCTUARY_2, new int[]{10, 1}),
            Map.entry(LevelData.S3K_DEATH_EGG_1, new int[]{11, 0}),
            Map.entry(LevelData.S3K_DEATH_EGG_2, new int[]{11, 1}),
            Map.entry(LevelData.S3K_DOOMSDAY, new int[]{12, 0})
    );

    @Override public String gameName() { return "Sonic 3&K"; }
    @Override public String gameId() { return "s3k"; }
    @Override public String defaultRomPath() { return "Sonic and Knuckles & Sonic 3 (W) [!].gen"; }
    @Override public String outputFilename() { return "S3K_OBJECT_CHECKLIST.md"; }
    @Override public List<LevelConfig> getLevels() { return LEVELS; }
    @Override public Set<Integer> getImplementedIds() { return IMPLEMENTED_IDS; }
    @Override public Set<Integer> getBadnikIds() { return BADNIK_IDS; }
    @Override public Set<Integer> getBossIds() { return BOSS_IDS; }
    @Override public Map<String, List<DynamicBoss>> getDynamicBosses() { return DYNAMIC_BOSSES; }
    @Override public Map<Integer, List<String>> getObjectNames() { return OBJECT_NAMES; }

    @Override
    public List<ObjectSpawn> loadObjects(RomByteReader rom, LevelConfig level) {
        int[] za = LEVEL_ZONE_ACT.get(level.levelData());
        return new Sonic3kObjectPlacement(rom).load(za[0], za[1]);
    }

    @Override
    public boolean isFinalAct(LevelConfig level) {
        return switch (level.shortName()) {
            case "DDZ" -> level.act() == 1;
            default -> level.act() == 2;
        };
    }
}
