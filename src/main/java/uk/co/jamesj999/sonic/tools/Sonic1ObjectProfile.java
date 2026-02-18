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
            Sonic1ObjectIds.SPINNING_LIGHT,
            Sonic1ObjectIds.BREAKABLE_POLE,
            Sonic1ObjectIds.FLAPPING_DOOR,
            Sonic1ObjectIds.SIGNPOST,
            Sonic1ObjectIds.MONITOR,
            Sonic1ObjectIds.RING,
            Sonic1ObjectIds.HARPOON,
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
            Sonic1ObjectIds.JAWS,
            Sonic1ObjectIds.BURROBOT,
            Sonic1ObjectIds.CRABMEAT,
            Sonic1ObjectIds.MOTOBUG,
            Sonic1ObjectIds.NEWTRON,
            Sonic1ObjectIds.CATERKILLER,
            Sonic1ObjectIds.BATBRAIN,
            Sonic1ObjectIds.YADRIN,
            Sonic1ObjectIds.ROLLER,
            Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM,
            Sonic1ObjectIds.MZ_GLASS_BLOCK,
            Sonic1ObjectIds.CHAINED_STOMPER,
            Sonic1ObjectIds.PUSH_BLOCK,
            Sonic1ObjectIds.BUTTON,
            Sonic1ObjectIds.BURNING_GRASS,
            Sonic1ObjectIds.SMASH_BLOCK,
            Sonic1ObjectIds.MOVING_BLOCK,
            Sonic1ObjectIds.COLLAPSING_FLOOR,
            Sonic1ObjectIds.SPIKED_BALL_CHAIN,
            Sonic1ObjectIds.BIG_SPIKED_BALL,
            Sonic1ObjectIds.SLZ_ELEVATOR,
            Sonic1ObjectIds.SLZ_CIRCLING_PLATFORM,
            Sonic1ObjectIds.SLZ_STAIRCASE,
            Sonic1ObjectIds.INVISIBLE_BARRIER,
            Sonic1ObjectIds.LAVA_BALL_MAKER,
            Sonic1ObjectIds.LAVA_GEYSER_MAKER,
            Sonic1ObjectIds.LAVA_GEYSER,
            Sonic1ObjectIds.LAVA_TAG,
            Sonic1ObjectIds.LAVA_WALL,
            Sonic1ObjectIds.LABYRINTH_BLOCK,
            Sonic1ObjectIds.GARGOYLE,
            Sonic1ObjectIds.LZ_CONVEYOR,
            Sonic1ObjectIds.BUBBLES,
            Sonic1ObjectIds.WATERFALL,
            Sonic1ObjectIds.BUMPER,
            Sonic1ObjectIds.FLOATING_BLOCK,
            Sonic1ObjectIds.FAN,
            Sonic1ObjectIds.SEESAW,
            Sonic1ObjectIds.PYLON,
            Sonic1ObjectIds.WATERFALL_SOUND,
            Sonic1ObjectIds.ORBINAUT,
            Sonic1ObjectIds.BOMB,
            Sonic1ObjectIds.GIANT_RING,
            Sonic1ObjectIds.GHZ_BOSS,
            Sonic1ObjectIds.MZ_BOSS,
            Sonic1ObjectIds.SYZ_BOSS,
            Sonic1ObjectIds.SYZ_BOSS_BLOCK,
            Sonic1ObjectIds.LZ_BOSS,
            Sonic1ObjectIds.SLZ_BOSS,
            Sonic1ObjectIds.FZ_BOSS,
            Sonic1ObjectIds.BOSS_FIRE,
            Sonic1ObjectIds.EGG_PRISON,
            Sonic1ObjectIds.HIDDEN_BONUS,
            Sonic1ObjectIds.ELECTROCUTER,
            Sonic1ObjectIds.SBZ_SMALL_DOOR,
            Sonic1ObjectIds.SBZ_CONVEYOR_BELT,
            Sonic1ObjectIds.SBZ_SPINNING_PLATFORM,
            Sonic1ObjectIds.SBZ_SAW,
            Sonic1ObjectIds.SBZ_STOMPER_DOOR,
            Sonic1ObjectIds.SBZ_VANISHING_PLATFORM,
            Sonic1ObjectIds.FLAMETHROWER,
            Sonic1ObjectIds.GIRDER,
            Sonic1ObjectIds.BALL_HOG,
            Sonic1ObjectIds.TELEPORTER,
            Sonic1ObjectIds.RUNNING_DISC,
            Sonic1ObjectIds.SBZ_SPIN_CONVEYOR,
            Sonic1ObjectIds.JUNCTION
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
            Sonic1ObjectIds.ROLLER,
            Sonic1ObjectIds.BATBRAIN,
            Sonic1ObjectIds.BOMB,
            Sonic1ObjectIds.ORBINAUT,
            Sonic1ObjectIds.CATERKILLER,
            Sonic1ObjectIds.BALL_HOG
    );

    private static final Set<Integer> BOSS_IDS = Set.of(
            Sonic1ObjectIds.GHZ_BOSS,
            Sonic1ObjectIds.MZ_BOSS,
            Sonic1ObjectIds.SYZ_BOSS,
            Sonic1ObjectIds.LZ_BOSS,
            Sonic1ObjectIds.SLZ_BOSS,
            Sonic1ObjectIds.FZ_BOSS
    );

    private static final Map<String, List<DynamicBoss>> DYNAMIC_BOSSES = Map.of();

    /** Object names from Sonic1ObjectRegistry.getPrimaryName() switch cases. */
    private static final Map<Integer, List<String>> OBJECT_NAMES = buildNames();

    private static Map<Integer, List<String>> buildNames() {
        Map<Integer, List<String>> map = new HashMap<>();
        map.put(Sonic1ObjectIds.SONIC, List.of("Sonic"));
        map.put(Sonic1ObjectIds.BREAKABLE_POLE, List.of("PoleThatBreaks"));
        map.put(Sonic1ObjectIds.FLAPPING_DOOR, List.of("FlappingDoor"));
        map.put(Sonic1ObjectIds.SIGNPOST, List.of("Signpost"));
        map.put(Sonic1ObjectIds.BRIDGE, List.of("Bridge"));
        map.put(Sonic1ObjectIds.HARPOON, List.of("Harpoon"));
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
        map.put(Sonic1ObjectIds.MZ_BOSS, List.of("MZBoss"));
        map.put(Sonic1ObjectIds.SYZ_BOSS, List.of("SYZBoss"));
        map.put(Sonic1ObjectIds.SYZ_BOSS_BLOCK, List.of("BossBlock"));
        map.put(Sonic1ObjectIds.SLZ_BOSS, List.of("SLZBoss", "BossStarLight"));
        map.put(Sonic1ObjectIds.FZ_BOSS, List.of("FZBoss", "BossFinal"));
        map.put(Sonic1ObjectIds.EGGMAN_CYLINDER, List.of("EggmanCylinder"));
        map.put(Sonic1ObjectIds.BOSS_PLASMA, List.of("BossPlasma"));
        map.put(Sonic1ObjectIds.SLZ_BOSS_SPIKEBALL, List.of("BossSpikeball"));
        map.put(Sonic1ObjectIds.BOSS_FIRE, List.of("BossFire"));
        map.put(Sonic1ObjectIds.EGG_PRISON, List.of("EggPrison"));
        map.put(Sonic1ObjectIds.MOTOBUG, List.of("Motobug"));
        map.put(Sonic1ObjectIds.SPRING, List.of("Spring"));
        map.put(Sonic1ObjectIds.EDGE_WALLS, List.of("EdgeWalls"));
        map.put(Sonic1ObjectIds.MZ_BRICK, List.of("MzBrick"));
        map.put(Sonic1ObjectIds.NEWTRON, List.of("Newtron"));
        map.put(Sonic1ObjectIds.ROLLER, List.of("Roller"));
        map.put(Sonic1ObjectIds.BUMPER, List.of("Bumper"));
        map.put(Sonic1ObjectIds.BOSS_BALL, List.of("BossBall"));
        map.put(Sonic1ObjectIds.WATERFALL_SOUND, List.of("WaterfallSound"));
        map.put(Sonic1ObjectIds.GIANT_RING, List.of("GiantRing"));
        map.put(Sonic1ObjectIds.YADRIN, List.of("Yadrin"));
        map.put(Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM, List.of("MzLargeGrassyPlatform"));
        map.put(Sonic1ObjectIds.MZ_GLASS_BLOCK, List.of("MzGlassBlock"));
        map.put(Sonic1ObjectIds.SMASH_BLOCK, List.of("SmashBlock"));
        map.put(Sonic1ObjectIds.PUSH_BLOCK, List.of("PushBlock"));
        map.put(Sonic1ObjectIds.CHAINED_STOMPER, List.of("ChainedStomper"));
        map.put(Sonic1ObjectIds.BURNING_GRASS, List.of("BurningGrass"));
        map.put(Sonic1ObjectIds.LAVA_BALL_MAKER, List.of("LavaBallMaker"));
        map.put(Sonic1ObjectIds.LAVA_BALL, List.of("LavaBall"));
        map.put(Sonic1ObjectIds.LAVA_GEYSER_MAKER, List.of("LavaGeyserMaker"));
        map.put(Sonic1ObjectIds.LAVA_GEYSER, List.of("LavaGeyser"));
        map.put(Sonic1ObjectIds.LAVA_TAG, List.of("LavaTag"));
        map.put(Sonic1ObjectIds.LAVA_WALL, List.of("LavaWall"));
        map.put(Sonic1ObjectIds.LABYRINTH_BLOCK, List.of("LabyrinthBlock"));
        map.put(Sonic1ObjectIds.LZ_CONVEYOR, List.of("LZConveyor"));
        map.put(Sonic1ObjectIds.BUBBLES, List.of("Bubbles"));
        map.put(Sonic1ObjectIds.WATERFALL, List.of("Waterfall"));
        map.put(Sonic1ObjectIds.BATBRAIN, List.of("Batbrain"));
        map.put(Sonic1ObjectIds.SLZ_ELEVATOR, List.of("Elevator"));
        map.put(Sonic1ObjectIds.SLZ_CIRCLING_PLATFORM, List.of("CirclingPlatform"));
        map.put(Sonic1ObjectIds.SLZ_STAIRCASE, List.of("Staircase"));
        map.put(Sonic1ObjectIds.PYLON, List.of("Pylon"));
        map.put(Sonic1ObjectIds.FAN, List.of("Fan"));
        map.put(Sonic1ObjectIds.SEESAW, List.of("Seesaw"));
        map.put(Sonic1ObjectIds.BOMB, List.of("Bomb"));
        map.put(Sonic1ObjectIds.ORBINAUT, List.of("Orbinaut"));
        map.put(Sonic1ObjectIds.INVISIBLE_BARRIER, List.of("InvisibleBarrier"));
        map.put(Sonic1ObjectIds.TELEPORTER, List.of("Teleporter"));
        map.put(Sonic1ObjectIds.ELECTROCUTER, List.of("Electrocuter"));
        map.put(Sonic1ObjectIds.SBZ_SMALL_DOOR, List.of("SmallDoor", "AutoDoor"));
        map.put(Sonic1ObjectIds.CATERKILLER, List.of("Caterkiller"));
        map.put(Sonic1ObjectIds.LAMPPOST, List.of("Lamppost"));
        map.put(Sonic1ObjectIds.HIDDEN_BONUS, List.of("HiddenBonus"));
        map.put(Sonic1ObjectIds.SBZ_SPIN_CONVEYOR, List.of("SpinConveyor", "SpinConvey"));
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
