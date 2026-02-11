package uk.co.jamesj999.sonic.tools;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1ObjectPlacement;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.level.LevelData;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.DynamicBoss;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.LevelConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sonic 1 object profile for the ObjectDiscoveryTool.
 */
public class Sonic1ObjectProfile implements GameObjectProfile {

    private static final List<LevelConfig> LEVELS = List.of(
            new LevelConfig(LevelData.S1_GREEN_HILL_1, "GHZ", "Green Hill Zone", 1),
            new LevelConfig(LevelData.S1_GREEN_HILL_2, "GHZ", "Green Hill Zone", 2),
            new LevelConfig(LevelData.S1_GREEN_HILL_3, "GHZ", "Green Hill Zone", 3),
            new LevelConfig(LevelData.S1_LABYRINTH_1, "LZ", "Labyrinth Zone", 1),
            new LevelConfig(LevelData.S1_LABYRINTH_2, "LZ", "Labyrinth Zone", 2),
            new LevelConfig(LevelData.S1_LABYRINTH_3, "LZ", "Labyrinth Zone", 3),
            new LevelConfig(LevelData.S1_MARBLE_1, "MZ", "Marble Zone", 1),
            new LevelConfig(LevelData.S1_MARBLE_2, "MZ", "Marble Zone", 2),
            new LevelConfig(LevelData.S1_MARBLE_3, "MZ", "Marble Zone", 3),
            new LevelConfig(LevelData.S1_STAR_LIGHT_1, "SLZ", "Star Light Zone", 1),
            new LevelConfig(LevelData.S1_STAR_LIGHT_2, "SLZ", "Star Light Zone", 2),
            new LevelConfig(LevelData.S1_STAR_LIGHT_3, "SLZ", "Star Light Zone", 3),
            new LevelConfig(LevelData.S1_SPRING_YARD_1, "SYZ", "Spring Yard Zone", 1),
            new LevelConfig(LevelData.S1_SPRING_YARD_2, "SYZ", "Spring Yard Zone", 2),
            new LevelConfig(LevelData.S1_SPRING_YARD_3, "SYZ", "Spring Yard Zone", 3),
            new LevelConfig(LevelData.S1_SCRAP_BRAIN_1, "SBZ", "Scrap Brain Zone", 1),
            new LevelConfig(LevelData.S1_SCRAP_BRAIN_2, "SBZ", "Scrap Brain Zone", 2),
            new LevelConfig(LevelData.S1_SCRAP_BRAIN_3, "SBZ", "Scrap Brain Zone", 3),
            new LevelConfig(LevelData.S1_FINAL_ZONE, "FZ", "Final Zone", 1)
    );

    // IDs with registered factories in Sonic1ObjectRegistry
    private static final Set<Integer> IMPLEMENTED_IDS = Set.of(
            Sonic1ObjectIds.LAMPPOST,
            Sonic1ObjectIds.SIGNPOST,
            Sonic1ObjectIds.MONITOR,
            Sonic1ObjectIds.RING,
            Sonic1ObjectIds.SPIKED_POLE_HELIX,
            Sonic1ObjectIds.SWINGING_PLATFORM,
            Sonic1ObjectIds.PLATFORM,
            Sonic1ObjectIds.COLLAPSING_LEDGE,
            Sonic1ObjectIds.ROCK,
            Sonic1ObjectIds.BREAKABLE_WALL,
            Sonic1ObjectIds.EDGE_WALLS,
            Sonic1ObjectIds.MZ_BRICK,
            Sonic1ObjectIds.BRIDGE,
            Sonic1ObjectIds.SCENERY,
            Sonic1ObjectIds.SPIKES,
            Sonic1ObjectIds.SPRING,
            Sonic1ObjectIds.BUZZ_BOMBER,
            Sonic1ObjectIds.CHOPPER,
            Sonic1ObjectIds.CRABMEAT,
            Sonic1ObjectIds.MOTOBUG,
            Sonic1ObjectIds.NEWTRON,
            Sonic1ObjectIds.CATERKILLER,
            Sonic1ObjectIds.BATBRAIN,
            Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM,
            Sonic1ObjectIds.MZ_GLASS_BLOCK,
            Sonic1ObjectIds.CHAINED_STOMPER,
            Sonic1ObjectIds.BURNING_GRASS,
            Sonic1ObjectIds.SMASH_BLOCK,
            Sonic1ObjectIds.LAVA_TAG,
            Sonic1ObjectIds.WATERFALL_SOUND,
            Sonic1ObjectIds.GIANT_RING,
            Sonic1ObjectIds.GHZ_BOSS,
            Sonic1ObjectIds.EGG_PRISON,
            Sonic1ObjectIds.HIDDEN_BONUS
    );

    private static final Set<Integer> BADNIK_IDS = Set.of(
            Sonic1ObjectIds.CRABMEAT,
            Sonic1ObjectIds.BUZZ_BOMBER,
            Sonic1ObjectIds.CHOPPER,
            Sonic1ObjectIds.JAWS,
            Sonic1ObjectIds.BURROBOT,
            Sonic1ObjectIds.MOTOBUG,
            Sonic1ObjectIds.NEWTRON,
            Sonic1ObjectIds.YADRIN,
            Sonic1ObjectIds.BATBRAIN,
            Sonic1ObjectIds.BOMB,
            Sonic1ObjectIds.ORBINAUT,
            Sonic1ObjectIds.CATERKILLER
    );

    private static final Set<Integer> BOSS_IDS = Set.of(
            Sonic1ObjectIds.GHZ_BOSS
    );

    private static final Map<String, List<DynamicBoss>> DYNAMIC_BOSSES = Map.of();

    /** Object names from Sonic1ObjectRegistry.getPrimaryName() switch cases. */
    private static final Map<Integer, List<String>> OBJECT_NAMES = buildNames();

    private static Map<Integer, List<String>> buildNames() {
        Map<Integer, List<String>> map = new HashMap<>();
        map.put(Sonic1ObjectIds.SONIC, List.of("Sonic"));
        map.put(Sonic1ObjectIds.SIGNPOST, List.of("Signpost"));
        map.put(Sonic1ObjectIds.BRIDGE, List.of("Bridge"));
        map.put(Sonic1ObjectIds.SPIKED_POLE_HELIX, List.of("SpikedPoleHelix"));
        map.put(Sonic1ObjectIds.SWINGING_PLATFORM, List.of("SwingingPlatform"));
        map.put(Sonic1ObjectIds.PLATFORM, List.of("Platform"));
        map.put(Sonic1ObjectIds.COLLAPSING_LEDGE, List.of("CollapsingLedge"));
        map.put(Sonic1ObjectIds.SCENERY, List.of("Scenery"));
        map.put(Sonic1ObjectIds.CRABMEAT, List.of("Crabmeat"));
        map.put(Sonic1ObjectIds.BUZZ_BOMBER, List.of("BuzzBomber"));
        map.put(Sonic1ObjectIds.BUZZ_BOMBER_MISSILE, List.of("BuzzBomberMissile"));
        map.put(Sonic1ObjectIds.MISSILE_DISSOLVE, List.of("MissileDissolve"));
        map.put(Sonic1ObjectIds.RING, List.of("Ring"));
        map.put(Sonic1ObjectIds.MONITOR, List.of("Monitor"));
        map.put(Sonic1ObjectIds.CHOPPER, List.of("Chopper"));
        map.put(Sonic1ObjectIds.JAWS, List.of("Jaws"));
        map.put(Sonic1ObjectIds.BURROBOT, List.of("Burrobot"));
        map.put(Sonic1ObjectIds.SPIKES, List.of("Spikes"));
        map.put(Sonic1ObjectIds.ROCK, List.of("Rock"));
        map.put(Sonic1ObjectIds.BREAKABLE_WALL, List.of("BreakableWall"));
        map.put(Sonic1ObjectIds.GHZ_BOSS, List.of("GHZBoss"));
        map.put(Sonic1ObjectIds.EGG_PRISON, List.of("EggPrison"));
        map.put(Sonic1ObjectIds.MOTOBUG, List.of("Motobug"));
        map.put(Sonic1ObjectIds.SPRING, List.of("Spring"));
        map.put(Sonic1ObjectIds.EDGE_WALLS, List.of("EdgeWalls"));
        map.put(Sonic1ObjectIds.MZ_BRICK, List.of("MzBrick"));
        map.put(Sonic1ObjectIds.NEWTRON, List.of("Newtron"));
        map.put(Sonic1ObjectIds.BUMPER, List.of("Bumper"));
        map.put(Sonic1ObjectIds.BOSS_BALL, List.of("BossBall"));
        map.put(Sonic1ObjectIds.WATERFALL_SOUND, List.of("WaterfallSound"));
        map.put(Sonic1ObjectIds.GIANT_RING, List.of("GiantRing"));
        map.put(Sonic1ObjectIds.YADRIN, List.of("Yadrin"));
        map.put(Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM, List.of("MzLargeGrassyPlatform"));
        map.put(Sonic1ObjectIds.MZ_GLASS_BLOCK, List.of("MzGlassBlock"));
        map.put(Sonic1ObjectIds.SMASH_BLOCK, List.of("SmashBlock"));
        map.put(Sonic1ObjectIds.CHAINED_STOMPER, List.of("ChainedStomper"));
        map.put(Sonic1ObjectIds.BURNING_GRASS, List.of("BurningGrass"));
        map.put(Sonic1ObjectIds.LAVA_TAG, List.of("LavaTag"));
        map.put(Sonic1ObjectIds.BATBRAIN, List.of("Batbrain"));
        map.put(Sonic1ObjectIds.SEESAW, List.of("Seesaw"));
        map.put(Sonic1ObjectIds.BOMB, List.of("Bomb"));
        map.put(Sonic1ObjectIds.ORBINAUT, List.of("Orbinaut"));
        map.put(Sonic1ObjectIds.CATERKILLER, List.of("Caterkiller"));
        map.put(Sonic1ObjectIds.LAMPPOST, List.of("Lamppost"));
        map.put(Sonic1ObjectIds.HIDDEN_BONUS, List.of("HiddenBonus"));
        return Map.copyOf(map);
    }

    /** Map S1 LevelData index to zone/act for Sonic1ObjectPlacement. */
    private static final Map<LevelData, int[]> LEVEL_ZONE_ACT = Map.ofEntries(
            Map.entry(LevelData.S1_GREEN_HILL_1, new int[]{Sonic1Constants.ZONE_GHZ, 0}),
            Map.entry(LevelData.S1_GREEN_HILL_2, new int[]{Sonic1Constants.ZONE_GHZ, 1}),
            Map.entry(LevelData.S1_GREEN_HILL_3, new int[]{Sonic1Constants.ZONE_GHZ, 2}),
            Map.entry(LevelData.S1_LABYRINTH_1, new int[]{Sonic1Constants.ZONE_LZ, 0}),
            Map.entry(LevelData.S1_LABYRINTH_2, new int[]{Sonic1Constants.ZONE_LZ, 1}),
            Map.entry(LevelData.S1_LABYRINTH_3, new int[]{Sonic1Constants.ZONE_LZ, 2}),
            Map.entry(LevelData.S1_MARBLE_1, new int[]{Sonic1Constants.ZONE_MZ, 0}),
            Map.entry(LevelData.S1_MARBLE_2, new int[]{Sonic1Constants.ZONE_MZ, 1}),
            Map.entry(LevelData.S1_MARBLE_3, new int[]{Sonic1Constants.ZONE_MZ, 2}),
            Map.entry(LevelData.S1_STAR_LIGHT_1, new int[]{Sonic1Constants.ZONE_SLZ, 0}),
            Map.entry(LevelData.S1_STAR_LIGHT_2, new int[]{Sonic1Constants.ZONE_SLZ, 1}),
            Map.entry(LevelData.S1_STAR_LIGHT_3, new int[]{Sonic1Constants.ZONE_SLZ, 2}),
            Map.entry(LevelData.S1_SPRING_YARD_1, new int[]{Sonic1Constants.ZONE_SYZ, 0}),
            Map.entry(LevelData.S1_SPRING_YARD_2, new int[]{Sonic1Constants.ZONE_SYZ, 1}),
            Map.entry(LevelData.S1_SPRING_YARD_3, new int[]{Sonic1Constants.ZONE_SYZ, 2}),
            Map.entry(LevelData.S1_SCRAP_BRAIN_1, new int[]{Sonic1Constants.ZONE_SBZ, 0}),
            Map.entry(LevelData.S1_SCRAP_BRAIN_2, new int[]{Sonic1Constants.ZONE_SBZ, 1}),
            Map.entry(LevelData.S1_SCRAP_BRAIN_3, new int[]{Sonic1Constants.ZONE_SBZ, 2}),
            Map.entry(LevelData.S1_FINAL_ZONE, new int[]{Sonic1Constants.ZONE_ENDZ, 0})
    );

    @Override public String gameName() { return "Sonic 1"; }
    @Override public String gameId() { return "s1"; }
    @Override public String defaultRomPath() { return "Sonic The Hedgehog (W) (REV01) [!].gen"; }
    @Override public String outputFilename() { return "S1_OBJECT_CHECKLIST.md"; }
    @Override public List<LevelConfig> getLevels() { return LEVELS; }
    @Override public Set<Integer> getImplementedIds() { return IMPLEMENTED_IDS; }
    @Override public Set<Integer> getBadnikIds() { return BADNIK_IDS; }
    @Override public Set<Integer> getBossIds() { return BOSS_IDS; }
    @Override public Map<String, List<DynamicBoss>> getDynamicBosses() { return DYNAMIC_BOSSES; }
    @Override public Map<Integer, List<String>> getObjectNames() { return OBJECT_NAMES; }

    @Override
    public List<ObjectSpawn> loadObjects(RomByteReader rom, LevelConfig level) {
        int[] za = LEVEL_ZONE_ACT.get(level.levelData());
        return new Sonic1ObjectPlacement(rom).load(za[0], za[1]);
    }

    @Override
    public boolean isFinalAct(LevelConfig level) {
        return switch (level.shortName()) {
            case "FZ" -> level.act() == 1;
            default -> level.act() == 3;
        };
    }
}
