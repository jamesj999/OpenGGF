package uk.co.jamesj999.sonic.tools;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectPlacement;
import uk.co.jamesj999.sonic.game.sonic2.ZoneAct;
import uk.co.jamesj999.sonic.game.sonic2.objects.Sonic2ObjectRegistryData;
import uk.co.jamesj999.sonic.level.LevelData;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.DynamicBoss;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.LevelConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants.LEVEL_SELECT_ADDR;

/**
 * Sonic 2 object profile for the ObjectDiscoveryTool.
 */
public class Sonic2ObjectProfile implements GameObjectProfile {

    private static final List<LevelConfig> LEVELS = List.of(
            new LevelConfig(LevelData.EMERALD_HILL_1, "EHZ", "Emerald Hill Zone", 1),
            new LevelConfig(LevelData.EMERALD_HILL_2, "EHZ", "Emerald Hill Zone", 2),
            new LevelConfig(LevelData.CHEMICAL_PLANT_1, "CPZ", "Chemical Plant Zone", 1),
            new LevelConfig(LevelData.CHEMICAL_PLANT_2, "CPZ", "Chemical Plant Zone", 2),
            new LevelConfig(LevelData.AQUATIC_RUIN_1, "ARZ", "Aquatic Ruin Zone", 1),
            new LevelConfig(LevelData.AQUATIC_RUIN_2, "ARZ", "Aquatic Ruin Zone", 2),
            new LevelConfig(LevelData.CASINO_NIGHT_1, "CNZ", "Casino Night Zone", 1),
            new LevelConfig(LevelData.CASINO_NIGHT_2, "CNZ", "Casino Night Zone", 2),
            new LevelConfig(LevelData.HILL_TOP_1, "HTZ", "Hill Top Zone", 1),
            new LevelConfig(LevelData.HILL_TOP_2, "HTZ", "Hill Top Zone", 2),
            new LevelConfig(LevelData.MYSTIC_CAVE_1, "MCZ", "Mystic Cave Zone", 1),
            new LevelConfig(LevelData.MYSTIC_CAVE_2, "MCZ", "Mystic Cave Zone", 2),
            new LevelConfig(LevelData.OIL_OCEAN_1, "OOZ", "Oil Ocean Zone", 1),
            new LevelConfig(LevelData.OIL_OCEAN_2, "OOZ", "Oil Ocean Zone", 2),
            new LevelConfig(LevelData.METROPOLIS_1, "MTZ", "Metropolis Zone", 1),
            new LevelConfig(LevelData.METROPOLIS_2, "MTZ", "Metropolis Zone", 2),
            new LevelConfig(LevelData.METROPOLIS_3, "MTZ", "Metropolis Zone", 3),
            new LevelConfig(LevelData.SKY_CHASE, "SCZ", "Sky Chase Zone", 1),
            new LevelConfig(LevelData.WING_FORTRESS, "WFZ", "Wing Fortress Zone", 1),
            new LevelConfig(LevelData.DEATH_EGG, "DEZ", "Death Egg Zone", 1)
    );

    private static final Set<Integer> IMPLEMENTED_IDS = Set.of(
            0x03, 0x06, 0x0B, 0x0D, 0x11, 0x14, 0x15, 0x16, 0x18, 0x19,
            0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x22, 0x23, 0x24, 0x26, 0x2A,
            0x2B, 0x2C, 0x2D, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x36, 0x3D,
            0x3E, 0x3F, 0x40, 0x41, 0x42, 0x44, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x50,
            0x51, 0x52, 0x56, 0x57, 0x5C, 0x5D, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76,
            0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7F, 0x80, 0x81, 0x82, 0x83,
            0x84, 0x85, 0x86, 0x89, 0x8B, 0x8C, 0x8D, 0x8E, 0x91, 0x92, 0x94,
            0x95, 0x96, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA1, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xAC, 0xAD, 0xAE, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB8, 0xB9, 0xBA, 0xBC, 0xBD, 0xBE, 0xC0, 0xC1, 0xC2, 0xC8, 0xD2,
            0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9
    );

    private static final Set<Integer> BADNIK_IDS = Set.of(
            0x4A, 0x4B, 0x50, 0x5C, 0x8C, 0x8D, 0x8E, 0x91, 0x92, 0x94, 0x95, 0x96, 0x97,
            0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4,
            0xA5, 0xA6, 0xA7, 0xAC, 0xAD, 0xAE, 0xAF, 0xBF, 0xC8
    );

    private static final Set<Integer> BOSS_IDS = Set.of(
            0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x5D, 0x89, 0xC5, 0xC6, 0xC7
    );

    private static final Map<String, List<DynamicBoss>> DYNAMIC_BOSSES = Map.of(
            "EHZ", List.of(new DynamicBoss(0x56, "EHZBoss", "Drill car boss")),
            "CPZ", List.of(new DynamicBoss(0x5D, "CPZBoss", "Water dropper boss")),
            "ARZ", List.of(new DynamicBoss(0x89, "ARZBoss", "Hammer/arrow boss")),
            "CNZ", List.of(new DynamicBoss(0x51, "CNZBoss", "Catcher boss")),
            "HTZ", List.of(new DynamicBoss(0x52, "HTZBoss", "Lava-mobile boss")),
            "MCZ", List.of(new DynamicBoss(0x57, "MCZBoss", "Drill boss")),
            "OOZ", List.of(new DynamicBoss(0x55, "OOZBoss", "Laser/spike boss")),
            "MTZ", List.of(
                    new DynamicBoss(0x53, "MTZBossOrb", "Bouncing orb projectiles"),
                    new DynamicBoss(0x54, "MTZBoss", "Eggman's balloon machine")
            ),
            "SCZ", List.of()
    );

    @Override public String gameName() { return "Sonic 2"; }
    @Override public String gameId() { return "s2"; }
    @Override public String defaultRomPath() { return "Sonic The Hedgehog 2 (W) (REV01) [!].gen"; }
    @Override public String outputFilename() { return "OBJECT_CHECKLIST.md"; }
    @Override public List<LevelConfig> getLevels() { return LEVELS; }
    @Override public Set<Integer> getImplementedIds() { return IMPLEMENTED_IDS; }
    @Override public Set<Integer> getBadnikIds() { return BADNIK_IDS; }
    @Override public Set<Integer> getBossIds() { return BOSS_IDS; }
    @Override public Map<String, List<DynamicBoss>> getDynamicBosses() { return DYNAMIC_BOSSES; }
    @Override public Map<Integer, List<String>> getObjectNames() { return Sonic2ObjectRegistryData.NAMES_BY_ID; }

    @Override
    public List<ObjectSpawn> loadObjects(RomByteReader rom, LevelConfig level) {
        int levelIdx = level.levelData().getLevelIndex();
        int zoneIdx = rom.readU8(LEVEL_SELECT_ADDR + levelIdx * 2);
        int actIdx = rom.readU8(LEVEL_SELECT_ADDR + levelIdx * 2 + 1);
        ZoneAct zoneAct = new ZoneAct(zoneIdx, actIdx);
        return new Sonic2ObjectPlacement(rom).load(zoneAct);
    }

    @Override
    public boolean isFinalAct(LevelConfig level) {
        return switch (level.shortName()) {
            case "MTZ" -> level.act() == 3;
            case "SCZ", "WFZ", "DEZ" -> level.act() == 1;
            default -> level.act() == 2;
        };
    }
}
