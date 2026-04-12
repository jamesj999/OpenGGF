package com.openggf.tools;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.LevelData;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.PlcParser;

import java.util.*;

/**
 * Sonic 3&amp;K object profile for the ObjectDiscoveryTool.
 *
 * <p>S3K uses two object pointer tables that remap many IDs by zone:
 * <ul>
 *   <li><b>S3KL</b> (SK Set 1): Zones 0-6 (AIZ through LBZ)</li>
 *   <li><b>SKL</b> (SK Set 2): Zones 7-13 (MHZ through DDZ)</li>
 * </ul>
 * Names, badnik IDs, and boss IDs are resolved per-level via the zone set.
 */
public class Sonic3kObjectProfile implements GameObjectProfile {

    private static final List<ObjectDiscoveryTool.LevelConfig> LEVELS = List.of(
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_ANGEL_ISLAND_1, "AIZ", "Angel Island Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_ANGEL_ISLAND_2, "AIZ", "Angel Island Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_HYDROCITY_1, "HCZ", "Hydrocity Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_HYDROCITY_2, "HCZ", "Hydrocity Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_MARBLE_GARDEN_1, "MGZ", "Marble Garden Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_MARBLE_GARDEN_2, "MGZ", "Marble Garden Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_CARNIVAL_NIGHT_1, "CNZ", "Carnival Night Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_CARNIVAL_NIGHT_2, "CNZ", "Carnival Night Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_FLYING_BATTERY_1, "FBZ", "Flying Battery Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_FLYING_BATTERY_2, "FBZ", "Flying Battery Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_ICECAP_1, "ICZ", "IceCap Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_ICECAP_2, "ICZ", "IceCap Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_LAUNCH_BASE_1, "LBZ", "Launch Base Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_LAUNCH_BASE_2, "LBZ", "Launch Base Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_MUSHROOM_HILL_1, "MHZ", "Mushroom Hill Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_MUSHROOM_HILL_2, "MHZ", "Mushroom Hill Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_SANDOPOLIS_1, "SOZ", "Sandopolis Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_SANDOPOLIS_2, "SOZ", "Sandopolis Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_LAVA_REEF_1, "LRZ", "Lava Reef Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_LAVA_REEF_2, "LRZ", "Lava Reef Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_SKY_SANCTUARY_1, "SSZ", "Sky Sanctuary Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_SKY_SANCTUARY_2, "SSZ", "Sky Sanctuary Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_DEATH_EGG_1, "DEZ", "Death Egg Zone", 1),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_DEATH_EGG_2, "DEZ", "Death Egg Zone", 2),
            new ObjectDiscoveryTool.LevelConfig(LevelData.S3K_DOOMSDAY, "DDZ", "The Doomsday Zone", 1)
    );

    // Shared objects implemented for both zone sets
    private static final Set<Integer> SHARED_IMPLEMENTED_IDS = Set.of(
            0x01, // Monitor
            0x02, // PathSwap
            0x04, // CollapsingPlatform
            0x05, // AIZLRZEMZRock
            0x07, // Spring
            0x08, // Spikes
            0x0D, // BreakableWall
            0x0E, // TwistedRamp
            0x0F, // CollapsingBridge
            0x26, // AutoSpin
            0x28, // InvisibleBlock
            0x2A, // CorkFloor
            0x2F, // StillSprite
            0x30, // AnimatedStillSprite
            0x33, // Button
            0x34, // StarPost
            0x3C, // Door
            0x51, // FloatingPlatform
            0x6C, // TensionBridge
            0x6A, // InvisibleHurtBlockH
            0x6B, // InvisibleHurtBlockV
            0x80, // HiddenMonitor
            0x85  // SSEntryRing
    );

    // S3KL-only implementations (zones 0-6: AIZ through LBZ)
    private static final Set<Integer> S3KL_IMPLEMENTED_IDS;
    // SKL-only implementations (zones 7-13: MHZ through DDZ)
    private static final Set<Integer> SKL_IMPLEMENTED_IDS;

    static {
        var s3kl = new HashSet<>(SHARED_IMPLEMENTED_IDS);
        s3kl.addAll(Set.of(
                0x03, // AIZHollowTree
                0x06, // AIZRideVine
                0x09, // AIZ1Tree
                0x0A, // AIZ1ZiplinePeg
                0x0C, // AIZGiantRideVine
                0x29, // AIZDisappearingFloor
                0x2B, // AIZFlippingBridge
                0x2C, // AIZCollapsingLogBridge
                0x2D, // AIZFallingLog
                0x2E, // AIZSpikedLog
                0x35, // AIZForegroundPlant
                0x36, // HCZBreakableBar
                0x37, // HCZWaterRush
                0x38, // HCZCGZFan
                0x3A, // HCZHandLauncher
                0x3B, // HCZWaterWall
                0x3E, // HCZConveyorBelt
                0x3F, // HCZConveyorSpike
                0x67, // HCZSnakeBlocks
                0x68, // HCZSpinningColumn
                0x69, // HCZTwistingLoop
                0x6D, // HCZWaterSplash
                0x6E, // HCZWaterDrop
                0x8C, // Bloominator
                0x8D, // Rhinobot
                0x8E, // MonkeyDude
                0x8F, // CaterKillerJr
                0x93, // Jawz
                0x94, // Blastoid
                0x95, // Buggernaut
                0x98, // Poindexter
                0x90, // AIZMinibossCutscene
                0x91, // AIZMiniboss
                0x92, // AIZEndBoss
                0x99, // HCZMiniboss
                0x9A  // HCZEndBoss
        ));
        S3KL_IMPLEMENTED_IDS = Set.copyOf(s3kl);

        // No SKL-specific objects implemented yet
        SKL_IMPLEMENTED_IDS = Set.copyOf(SHARED_IMPLEMENTED_IDS);
    }

    // S3KL badniks (SK Set 1, zones 0-6: AIZ through LBZ)
    private static final Set<Integer> S3KL_BADNIK_IDS = Set.of(
            0x8C, // Bloominator
            0x8D, // Rhinobot
            0x8E, // MonkeyDude
            0x8F, // CaterKillerJr
            0x93, // Jawz
            0x94, // Blastoid
            0x95, // Buggernaut
            0x96, // TurboSpiker
            0x97, // MegaChopper
            0x98, // Poindexter
            0x9B, // BubblesBadnik
            0x9C, // Spiker
            0x9D, // Mantis
            0x9E, // Tunnelbot
            0xA3, // Clamer
            0xA4, // Sparkle
            0xA5, // Batbot
            0xA8, // Blaster
            0xA9, // TechnoSqueek
            0xAD, // Penguinator
            0xAE, // StarPointer
            0xBE, // SnaleBlaster
            0xBF, // Ribot
            0xC0, // Orbinaut
            0xC1, // Corkey
            0xC2  // Flybot767
    );

    // SKL badniks (SK Set 2, zones 7-13: MHZ through DDZ)
    private static final Set<Integer> SKL_BADNIK_IDS = Set.of(
            0x8C, // Madmole
            0x8D, // Mushmeanie
            0x8E, // Dragonfly
            0x8F, // Butterdroid
            0x90, // Cluckoid
            0x94, // Skorp
            0x95, // Sandworm
            0x96, // Rockn
            0x99, // Fireworm
            0x9A, // Iwamodoki
            0x9B, // Toxomister
            0xA4, // Spikebonker
            0xA5  // Chainspike
    );

    // S3KL bosses (SK Set 1, zones 0-6)
    private static final Set<Integer> S3KL_BOSS_IDS = Set.of(
            0x90, // AIZMinibossCutscene
            0x91, // AIZMiniboss
            0x92, // AIZEndBoss
            0x99, // HCZMiniboss
            0x9A, // HCZEndBoss
            0x9F, // MGZMiniboss
            0xA0, // MGZ2DrillingRobotnik
            0xA1, // MGZEndBoss
            0xA2, // MGZEndBossKnux
            0xA6, // CNZMiniboss
            0xA7, // CNZEndBoss
            0xAA, // FBZMiniboss
            0xAB, // FBZ2Subboss
            0xAC, // FBZEndBoss
            0xBC, // ICZMiniboss
            0xBD, // ICZEndBoss
            0xC3, // LBZ1Robotnik
            0xC4, // LBZMinibossBox
            0xC5, // LBZMinibossBoxKnux
            0xC6, // LBZ2RobotnikShip
            0xC9, // LBZMiniboss
            0xCA, // LBZFinalBoss1
            0xCB, // LBZEndBoss
            0xCC, // LBZFinalBoss2
            0xCD  // LBZFinalBossKnux
    );

    // SKL bosses (SK Set 2, zones 7-13)
    private static final Set<Integer> SKL_BOSS_IDS = Set.of(
            0x91, // MHZMinibossTree
            0x92, // MHZMiniboss
            0x93, // MHZEndBoss
            0x97, // SOZMiniboss
            0x98, // SOZEndBoss
            0x9C, // LRZRockCrusher
            0x9D, // LRZMiniboss
            0x9E, // LRZ3Autoscroll
            0xA0, // EggRobo
            0xA1, // SSZGHZBoss
            0xA2, // SSZMTZBoss
            0xA3, // SSZEndBoss
            0xA6, // DEZMiniboss
            0xA7, // DEZEndBoss
            0xB6  // DDZEndBoss
    );

    /**
     * Maps Nemesis ROM addresses to object IDs that use that art.
     * Built from skdisasm PLC definitions (plreq ArtTile_X, ArtNem_Y entries).
     * Expand incrementally as more objects are identified.
     */
    private static final Map<Integer, Set<Integer>> NEM_ADDR_TO_OBJECTS = Map.ofEntries(
            // Universal objects (PLC_STD / PLC_STD2)
            Map.entry(Sonic3kConstants.ART_NEM_SPIKES_SPRINGS_ADDR, Set.of(0x07, 0x08)),  // Springs + Spikes
            Map.entry(Sonic3kConstants.ART_NEM_MONITORS_ADDR, Set.of(0x01)),               // Monitor
            // AIZ objects
            Map.entry(Sonic3kConstants.ART_NEM_AIZ_FALLING_LOG_ADDR, Set.of(0x03)),        // AIZHollowTree
            Map.entry(Sonic3kConstants.ART_NEM_AIZ_SWING_VINE_ADDR, Set.of(0x06)),         // AIZRideVine
            Map.entry(Sonic3kConstants.ART_NEM_AIZ_SLIDE_ROPE_ADDR, Set.of(0x0A)),         // AIZ1ZiplinePeg
            Map.entry(Sonic3kConstants.ART_NEM_AIZ_CORK_FLOOR_ADDR, Set.of(0x04)),         // CollapsingPlatform
            Map.entry(Sonic3kConstants.ART_NEM_AIZ_MISC1_ADDR, Set.of(0x05, 0x09))         // AIZLRZEMZRock, AIZ1Tree
    );

    private static final Map<String, List<ObjectDiscoveryTool.DynamicBoss>> DYNAMIC_BOSSES = Map.of();

    /** S3KL names built from the registry (SK Set 1). */
    private static final Map<Integer, List<String>> S3KL_NAMES = buildNames(S3kZoneSet.S3KL);
    /** SKL names built from the registry (SK Set 2). */
    private static final Map<Integer, List<String>> SKL_NAMES = buildNames(S3kZoneSet.SKL);

    private static Map<Integer, List<String>> buildNames(S3kZoneSet zoneSet) {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        Map<Integer, List<String>> map = new HashMap<>();
        for (int id = 0; id <= 0xFF; id++) {
            String name = registry.getPrimaryName(id, zoneSet);
            if (!name.startsWith("S3K_Obj_")) {
                map.put(id, List.of(name));
            }
        }
        return Map.copyOf(map);
    }

    /** Map S3K LevelData to zone/act indices for the pointer table. */
    static final Map<LevelData, int[]> LEVEL_ZONE_ACT = Map.ofEntries(
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

    private S3kZoneSet zoneSetForLevel(ObjectDiscoveryTool.LevelConfig level) {
        int[] za = LEVEL_ZONE_ACT.get(level.levelData());
        return S3kZoneSet.forZone(za[0]);
    }

    @Override public String gameName() { return "Sonic 3&K"; }
    @Override public String gameId() { return "s3k"; }
    @Override public String defaultRomPath() { return "Sonic and Knuckles & Sonic 3 (W) [!].gen"; }
    @Override public String outputFilename() { return "S3K_OBJECT_CHECKLIST.md"; }
    @Override public List<ObjectDiscoveryTool.LevelConfig> getLevels() { return LEVELS; }
    @Override public Set<Integer> getImplementedIds() { return S3KL_IMPLEMENTED_IDS; }
    @Override public Set<Integer> getImplementedIds(ObjectDiscoveryTool.LevelConfig level) {
        return zoneSetForLevel(level) == S3kZoneSet.SKL ? SKL_IMPLEMENTED_IDS : S3KL_IMPLEMENTED_IDS;
    }
    @Override public Map<String, List<ObjectDiscoveryTool.DynamicBoss>> getDynamicBosses() { return DYNAMIC_BOSSES; }

    @Override
    public Set<Integer> getBadnikIds() { return S3KL_BADNIK_IDS; }

    @Override
    public Set<Integer> getBadnikIds(ObjectDiscoveryTool.LevelConfig level) {
        return zoneSetForLevel(level) == S3kZoneSet.SKL ? SKL_BADNIK_IDS : S3KL_BADNIK_IDS;
    }

    @Override
    public Set<Integer> getBossIds() { return S3KL_BOSS_IDS; }

    @Override
    public Set<Integer> getBossIds(ObjectDiscoveryTool.LevelConfig level) {
        return zoneSetForLevel(level) == S3kZoneSet.SKL ? SKL_BOSS_IDS : S3KL_BOSS_IDS;
    }

    @Override
    public Map<Integer, List<String>> getObjectNames() { return S3KL_NAMES; }

    @Override
    public Map<Integer, List<String>> getObjectNames(ObjectDiscoveryTool.LevelConfig level) {
        return zoneSetForLevel(level) == S3kZoneSet.SKL ? SKL_NAMES : S3KL_NAMES;
    }

    @Override
    public List<PlcObjectMapping> getPlcObjectMappings(RomByteReader rom, ObjectDiscoveryTool.LevelConfig level) {
        int[] za = LEVEL_ZONE_ACT.get(level.levelData());
        // LevelLoadBlock index: zone * 2 + act
        int llbIndex = za[0] * 2 + za[1];
        int base = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR + llbIndex * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        // Extract PLC IDs from upper byte of first two longwords
        int plc1Id = (rom.readU32BE(base) >> 24) & 0xFF;
        int plc2Id = (rom.readU32BE(base + 4) >> 24) & 0xFF;

        Set<Integer> plcIds = new LinkedHashSet<>();
        plcIds.add(plc1Id);
        plcIds.add(plc2Id);

        List<PlcObjectMapping> mappings = new ArrayList<>();
        for (int plcId : plcIds) {
            PlcParser.PlcDefinition def = PlcParser.parse(rom, Sonic3kConstants.OFFS_PLC_ADDR, plcId);
            for (PlcParser.PlcEntry entry : def.entries()) {
                Set<Integer> objIds = NEM_ADDR_TO_OBJECTS.get(entry.romAddr());
                if (objIds != null && !objIds.isEmpty()) {
                    mappings.add(new PlcObjectMapping(plcId, entry.romAddr(), objIds));
                }
            }
        }
        return mappings;
    }

    @Override
    public List<ObjectSpawn> loadObjects(RomByteReader rom, ObjectDiscoveryTool.LevelConfig level) {
        int[] za = LEVEL_ZONE_ACT.get(level.levelData());
        return new Sonic3kObjectPlacement(rom).load(za[0], za[1]);
    }

    @Override
    public boolean isFinalAct(ObjectDiscoveryTool.LevelConfig level) {
        return switch (level.shortName()) {
            case "DDZ" -> level.act() == 1;
            default -> level.act() == 2;
        };
    }
}
