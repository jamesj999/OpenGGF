package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.resources.CompressionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data-driven registry of per-zone art entries for S3K objects.
 *
 * <p>Each zone+act combination yields a {@link ZoneArtPlan} containing:
 * <ul>
 *   <li>{@link StandaloneArtEntry} - badniks/bosses decompressed independently from ROM</li>
 *   <li>{@link LevelArtEntry} - objects referencing patterns already in the level buffer</li>
 * </ul>
 *
 * <p>Shared entries (spikes, springs) appear in every zone. Zone-specific entries
 * are added by {@link #addZoneEntries} (populated in later tasks).
 */
public final class Sonic3kPlcArtRegistry {

    private Sonic3kPlcArtRegistry() {
    }

    /**
     * Art decompressed independently from ROM (badniks, bosses, etc.).
     *
     * @param key          object art key from {@link Sonic3kObjectArtKeys}
     * @param artAddr      ROM address of compressed art data
     * @param compression  compression type used for the art
     * @param artSize      uncompressed art size in bytes (0 if unknown/variable)
     * @param mappingAddr  ROM address of sprite mapping table
     * @param palette      palette line (0-3)
     * @param dplcAddr     ROM address of DPLC table, or -1 if no DPLCs
     */
    public record StandaloneArtEntry(
            String key,
            int artAddr,
            CompressionType compression,
            int artSize,
            int mappingAddr,
            int palette,
            int dplcAddr,
            S3kSpriteDataLoader.MappingFormat mappingFormat
    ) {
        public StandaloneArtEntry(String key, int artAddr, CompressionType compression, int artSize,
                int mappingAddr, int palette, int dplcAddr) {
            this(key, artAddr, compression, artSize, mappingAddr, palette, dplcAddr,
                    S3kSpriteDataLoader.MappingFormat.STANDARD);
        }
    }

    /**
     * Art referencing patterns already loaded into the level VRAM buffer.
     *
     * @param key          object art key from {@link Sonic3kObjectArtKeys}
     * @param mappingAddr  ROM address of mapping table, or -1 for hardcoded builder
     * @param artTileBase  VRAM tile index base for the art
     * @param palette      palette line (0-3)
     * @param builderName  name of hardcoded builder method on {@link Sonic3kObjectArt},
     *                     or null if mappings are ROM-parsed
     * @param frameFilter  if non-null, only include these frame indices from the mapping table
     */
    public record LevelArtEntry(
            String key,
            int mappingAddr,
            int artTileBase,
            int palette,
            String builderName,
            int[] frameFilter,
            S3kSpriteDataLoader.MappingFormat mappingFormat
    ) {
        public LevelArtEntry(String key, int mappingAddr, int artTileBase, int palette,
                String builderName) {
            this(key, mappingAddr, artTileBase, palette, builderName, null,
                    S3kSpriteDataLoader.MappingFormat.STANDARD);
        }

        public LevelArtEntry(String key, int mappingAddr, int artTileBase, int palette,
                String builderName, int[] frameFilter) {
            this(key, mappingAddr, artTileBase, palette, builderName, frameFilter,
                    S3kSpriteDataLoader.MappingFormat.STANDARD);
        }
    }

    /**
     * Complete art plan for one zone+act.
     *
     * @param standaloneArt art entries decompressed from ROM
     * @param levelArt      art entries referencing level buffer patterns
     */
    public record ZoneArtPlan(
            List<StandaloneArtEntry> standaloneArt,
            List<LevelArtEntry> levelArt
    ) {
    }

    /**
     * Shared level-art entries present in every zone: spikes and all 6 spring variants.
     */
    private static final List<LevelArtEntry> SHARED_LEVEL_ART = List.of(
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPIKES,
                    -1,
                    Sonic3kConstants.ARTTILE_SPIKES_SPRINGS,
                    0,
                    "buildSpikesSheet"
            ),
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPRING_VERTICAL,
                    -1,
                    Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x10,
                    0,
                    "buildSpringVerticalSheet"
            ),
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPRING_VERTICAL_YELLOW,
                    -1,
                    Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x10,
                    0,
                    "buildSpringVerticalYellowSheet"
            ),
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPRING_HORIZONTAL,
                    -1,
                    Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x20,
                    0,
                    "buildSpringHorizontalSheet"
            ),
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPRING_HORIZONTAL_YELLOW,
                    -1,
                    Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x20,
                    0,
                    "buildSpringHorizontalYellowSheet"
            ),
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPRING_DIAGONAL,
                    -1,
                    Sonic3kConstants.ARTTILE_DIAGONAL_SPRING,
                    0,
                    "buildSpringDiagonalSheet"
            ),
            new LevelArtEntry(
                    Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW,
                    -1,
                    Sonic3kConstants.ARTTILE_DIAGONAL_SPRING,
                    0,
                    "buildSpringDiagonalYellowSheet"
            )
    );

    /**
     * Shared standalone art entries present in every zone: SS Entry Ring (big ring).
     */
    private static final List<StandaloneArtEntry> SHARED_STANDALONE_ART = List.of(
            new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.SS_ENTRY_RING,
                    Sonic3kConstants.ART_UNC_SS_ENTRY_RING_ADDR,
                    CompressionType.UNCOMPRESSED,
                    Sonic3kConstants.ART_UNC_SS_ENTRY_RING_SIZE,
                    Sonic3kConstants.MAP_SS_ENTRY_RING_ADDR,
                    1,  // palette line 1 (ROM: make_art_tile(ArtTile_Explosion,1,0))
                    Sonic3kConstants.DPLC_SS_ENTRY_RING_ADDR
            ),
            new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.SS_ENTRY_FLASH,
                    Sonic3kConstants.ART_UNC_SS_ENTRY_FLASH_ADDR,
                    CompressionType.UNCOMPRESSED,
                    Sonic3kConstants.ART_UNC_SS_ENTRY_FLASH_SIZE,
                    Sonic3kConstants.MAP_SS_ENTRY_FLASH_ADDR,
                    1,  // palette line 1 (ROM: make_art_tile(ArtTile_Player_1,1,0))
                    Sonic3kConstants.DPLC_SS_ENTRY_FLASH_ADDR
            )
    );

    /**
     * Returns the art plan for the given zone and act.
     *
     * <p>The plan includes shared entries (spikes, springs, big ring) for all zones,
     * plus any zone-specific entries registered in {@link #addZoneEntries}.
     *
     * @param zoneIndex zone index (0=AIZ, 1=HCZ, ...)
     * @param actIndex  act index (0 or 1)
     * @return immutable art plan for the zone+act
     */
    public static ZoneArtPlan getPlan(int zoneIndex, int actIndex) {
        List<StandaloneArtEntry> standalone = new ArrayList<>(SHARED_STANDALONE_ART);
        List<LevelArtEntry> levelArt = new ArrayList<>(SHARED_LEVEL_ART);

        addZoneEntries(zoneIndex, actIndex, standalone, levelArt);

        return new ZoneArtPlan(
                Collections.unmodifiableList(standalone),
                Collections.unmodifiableList(levelArt)
        );
    }

    /**
     * Adds zone-specific art entries to the given lists.
     * Populated in subsequent tasks with per-zone badnik and object art.
     *
     * @param zoneIndex    zone index (0=AIZ, 1=HCZ, ...)
     * @param actIndex     act index (0 or 1)
     * @param standalone   mutable list to append standalone art entries to
     * @param levelArt     mutable list to append level art entries to
     */
    private static void addZoneEntries(int zoneIndex, int actIndex,
                                       List<StandaloneArtEntry> standalone,
                                       List<LevelArtEntry> levelArt) {
        switch (zoneIndex) {
            case 0x00 -> addAizEntries(actIndex, standalone, levelArt);
            case 0x01 -> addHczEntries(actIndex, standalone, levelArt);
            case 0x02 -> addMgzEntries(actIndex, standalone, levelArt);
            case 0x03 -> addCnzEntries(actIndex, standalone, levelArt);
            case 0x04 -> addFbzEntries(actIndex, standalone, levelArt);
            case 0x05 -> addIczEntries(actIndex, standalone, levelArt);
            case 0x06 -> addLbzEntries(actIndex, standalone, levelArt);
            case 0x07 -> addMhzEntries(actIndex, standalone, levelArt);
            case 0x08 -> addSozEntries(actIndex, standalone, levelArt);
            case 0x09 -> addLrzEntries(actIndex, standalone, levelArt);
            case 0x0A -> addSszEntries(actIndex, standalone, levelArt);
            case 0x0B -> addDezEntries(actIndex, standalone, levelArt);
            case 0x0C -> addDdzEntries(actIndex, standalone, levelArt);
            case 0x13 -> addGumballEntries(actIndex, standalone, levelArt);
            case 0x14 -> addPachinkoEntries(actIndex, standalone, levelArt);
            case 0x15 -> addSlotsEntries(actIndex, standalone, levelArt);
        }
    }

    /**
     * Populates Gumball Bonus Stage art entries.
     * ROM: PLC_47 loads ArtNem_BonusStage to VRAM at ArtTile_BonusStage (0x15B).
     * Objects use make_art_tile(ArtTile_BonusStage, 1, 1) — palette 1.
     * The art is a standalone Nemesis blob, NOT in the level tile data.
     */
    private static void addGumballEntries(int actIndex,
            List<StandaloneArtEntry> standalone, List<LevelArtEntry> levelArt) {
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.GUMBALL_BONUS,
                Sonic3kConstants.GUMBALL_ART_NEM_ADDR,
                CompressionType.NEMESIS,
                0,
                Sonic3kConstants.GUMBALL_MAP_ADDR,
                1,
                -1
        ));
        // Gumball bonus dispenser springs use the standalone ArtNem_VerticalSpring sheet.
        // ROM: ObjDat3_613C8 uses make_art_tile(..., palette 0).
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.GUMBALL_SPRING,
                Sonic3kConstants.ART_NEM_VERTICAL_SPRING_ADDR,
                CompressionType.NEMESIS,
                0,
                Sonic3kConstants.MAP_SPRING_ADDR,
                0,
                -1
        ));
    }

    /**
     * Populates Pachinko / Glowing Spheres bonus stage art entries.
     *
     * <p>The main Pachinko object art is loaded into level pattern memory by the stage PLC
     * (PLC_50 -> ArtTile_PachinkoMain), so these entries use ROM-parsed mappings with
     * level-art tile bases rather than standalone Nemesis sheets.
     */
    private static void addPachinkoEntries(int actIndex,
            List<StandaloneArtEntry> standalone, List<LevelArtEntry> levelArt) {
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_BUMPER,
                Sonic3kConstants.MAP_PACHINKO_BUMPER_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN,
                3,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_TRIANGLE_BUMPER,
                Sonic3kConstants.MAP_PACHINKO_TRIANGLE_BUMPER_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x1E,
                3,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_FLIPPER,
                Sonic3kConstants.MAP_PACHINKO_FLIPPER_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x62,
                0,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_ENERGY_TRAP,
                Sonic3kConstants.MAP_PACHINKO_ENERGY_TRAP_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x85,
                1,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_INVISIBLE_UNKNOWN,
                Sonic3kConstants.MAP_PACHINKO_INVISIBLE_UNKNOWN_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x97,
                3,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_PLATFORM,
                Sonic3kConstants.MAP_PACHINKO_PLATFORM_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x8B,
                1,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_ITEM_ORB,
                Sonic3kConstants.MAP_PACHINKO_ITEM_ORB_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x97,
                3,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_F_ITEM,
                Sonic3kConstants.MAP_PACHINKO_F_ITEM_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0xAB,
                3,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.PACHINKO_GUMBALLS,
                Sonic3kConstants.GUMBALL_MAP_ADDR,
                Sonic3kConstants.ARTTILE_PACHINKO_GUMBALLS,
                0,
                null
        ));
    }

    private static void addSlotsEntries(int actIndex,
            List<StandaloneArtEntry> standalone, List<LevelArtEntry> levelArt) {
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_COLORED_WALL,
                Sonic3kConstants.MAP_SLOT_COLORED_WALL_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS,
                0,
                null,
                null,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_GOAL,
                Sonic3kConstants.MAP_SLOT_GOAL_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x14C,
                0,
                null,
                null,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_BUMPER,
                Sonic3kConstants.MAP_SLOT_BUMPER_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0xF9,
                0,
                null,
                null,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_R_LABEL,
                Sonic3kConstants.MAP_SLOT_R_AND_PEPPERMINT_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x13D,
                0,
                null,
                null,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_PEPPERMINT,
                Sonic3kConstants.MAP_SLOT_R_AND_PEPPERMINT_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x122,
                0,
                null,
                null,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_MACHINE_FACE,
                Sonic3kConstants.MAP_SLOT_MACHINE_FACE_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x146,
                0,
                null,
                null,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_BONUS_CAGE,
                Sonic3kConstants.MAP_SLOT_BONUS_CAGE_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x146,
                0,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.SLOT_SPIKE_REWARD,
                Sonic3kConstants.MAP_SLOT_SPIKE_REWARD_ADDR,
                Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x155,
                1,
                null
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SLOT_RING_STAGE,
                Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR,
                CompressionType.NEMESIS,
                0,
                Sonic3kConstants.MAP_SLOT_RING_STAGE_ADDR,
                1,
                -1,
                S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X
        ));
    }

    /**
     * Populates HCZ (Hydrocity Zone) art entries.
     * Act 1: Blastoid, TurboSpiker, MegaChopper, Pointdexter.
     * Act 2: Jawz, TurboSpiker, MegaChopper, Pointdexter.
     */
    private static void addHczEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // Act-specific lead badnik
        if (actIndex == 0) {
            standalone.add(new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.HCZ_BLASTOID,
                    Sonic3kConstants.ART_KOSM_HCZ_BLASTOID_ADDR,
                    CompressionType.KOSINSKI_MODULED,
                    0,
                    Sonic3kConstants.MAP_BLASTOID_ADDR,
                    1,
                    -1
            ));
        } else {
            standalone.add(new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.HCZ_JAWZ,
                    Sonic3kConstants.ART_KOSM_HCZ_JAWZ_ADDR,
                    CompressionType.KOSINSKI_MODULED,
                    0,
                    Sonic3kConstants.MAP_JAWZ_ADDR,
                    1,
                    -1
            ));
        }
        // StillSprite groups: subtypes 6-10 (waterfalls), 15-19 (tubes/post)
        // base 0x001: subtypes 6,7,8,9,10 (HCZ waterfalls)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_HCZ_001,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 1, 2,
                null, new int[]{6, 7, 8, 9, 10}));
        // base 0x36E: subtype 15 (HCZ2 tube bend 1)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_HCZ_TUBE1,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x36E, 2,
                null, new int[]{15}));
        // base 0x37F: subtype 16 (HCZ2 tube bend 2)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_HCZ_TUBE2,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x37F, 2,
                null, new int[]{16}));
        // base 0x39F: subtype 17 (HCZ2 tube bend 3)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_HCZ_TUBE3,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x39F, 2,
                null, new int[]{17}));
        // base 0x3AA: subtype 18 (HCZ2 tube crossover)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_HCZ_TUBE4,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x3AA, 2,
                null, new int[]{18}));
        // base 0x048: subtype 19 (HCZ2 bridge post)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_HCZ_POST,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x048, 2,
                null, new int[]{19}));

        // Shared across both acts
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER,
                Sonic3kConstants.ART_KOSM_HCZ_TURBO_SPIKER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_TURBO_SPIKER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.HCZ_MEGA_CHOPPER,
                Sonic3kConstants.ART_KOSM_HCZ_MEGA_CHOPPER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_MEGA_CHOPPER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.HCZ_POINTDEXTER,
                Sonic3kConstants.ART_KOSM_HCZ_POINTDEXTER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_POINTDEXTER_ADDR,
                1,
                -1
        ));

        // Breakable Wall: frames 0-1 use art_tile $001/pal 3, frames 2-3 use $0350/pal 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_HCZ,
                Sonic3kConstants.MAP_HCZ_BREAKABLE_WALL_ADDR,
                1,
                3,
                null,
                new int[]{0, 1}
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_HCZ_KNUX,
                Sonic3kConstants.MAP_HCZ_BREAKABLE_WALL_ADDR,
                Sonic3kConstants.ARTTILE_HCZ2_KNUX_WALL,
                2,
                null,
                new int[]{2, 3}
        ));

        // Floating Platform: ArtTile_HCZMisc + $53
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.FLOATING_PLATFORM_HCZ,
                Sonic3kConstants.MAP_HCZ_FLOATING_PLATFORM_ADDR,
                Sonic3kConstants.ARTTILE_HCZ_MISC + 0x53,
                2,
                null
        ));

        // Button: ArtTile_HCZButton, palette 1
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.HCZ_BUTTON,
                Sonic3kConstants.MAP_HCZ_BUTTON_ADDR,
                Sonic3kConstants.ARTTILE_HCZ_BUTTON,
                1,
                null
        ));
    }

    /**
     * Populates MGZ (Marble Garden Zone) art entries.
     * Both acts: Spiker, BubblesBadnik. Act 1: Miniboss + debris. Act 2: Mantis.
     * Overrides diagonal spring art tile to MGZ/MHZ value.
     */
    private static void addMgzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite: subtypes 11-14 (MGZ signposts), base 0x451
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_MGZ,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x451, 2,
                null, new int[]{11, 12, 13, 14}));

        // Both acts
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.MGZ_SPIKER,
                Sonic3kConstants.ART_KOSM_SPIKER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_SPIKER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.MGZ_BUBBLES_BADNIK,
                Sonic3kConstants.ART_UNC_BUBBLES_BADNIK_ADDR,
                CompressionType.UNCOMPRESSED,
                Sonic3kConstants.ART_UNC_BUBBLES_BADNIK_SIZE,
                Sonic3kConstants.MAP_BUBBLES_BADNIK_ADDR,
                1,
                Sonic3kConstants.DPLC_BUBBLES_BADNIK_ADDR
        ));

        // Act-specific
        if (actIndex == 0) {
            standalone.add(new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.MGZ_MINIBOSS,
                    Sonic3kConstants.ART_KOSM_MGZ_MINIBOSS_ADDR,
                    CompressionType.KOSINSKI_MODULED,
                    0,
                    Sonic3kConstants.MAP_MGZ_MINIBOSS_ADDR,
                    1,
                    -1
            ));
            standalone.add(new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS,
                    Sonic3kConstants.ART_KOSM_MGZ_ENDBOSS_DEBRIS_ADDR,
                    CompressionType.KOSINSKI_MODULED,
                    0,
                    Sonic3kConstants.MAP_MGZ_MINIBOSS_ADDR,
                    1,
                    -1
            ));
        } else {
            standalone.add(new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.MGZ_MANTIS,
                    Sonic3kConstants.ART_KOSM_MANTIS_ADDR,
                    CompressionType.KOSINSKI_MODULED,
                    0,
                    Sonic3kConstants.MAP_MANTIS_ADDR,
                    1,
                    -1
            ));
        }

        // Diagonal spring override
        levelArt.removeIf(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL));
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.SPRING_DIAGONAL, -1, Sonic3kConstants.ARTTILE_MGZ_MHZ_DIAGONAL_SPRING, 0, "buildSpringDiagonalSheet"));
        levelArt.removeIf(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW));
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW, -1, Sonic3kConstants.ARTTILE_MGZ_MHZ_DIAGONAL_SPRING, 0, "buildSpringDiagonalYellowSheet"));

        // Breakable Wall: art_tile = $001, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                Sonic3kConstants.MAP_MGZ_BREAKABLE_WALL_ADDR,
                1,
                2,
                null
        ));

        // Floating Platform: art_tile = $001, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.FLOATING_PLATFORM_MGZ,
                Sonic3kConstants.MAP_MGZ_FLOATING_PLATFORM_ADDR,
                1,
                2,
                null
        ));

        // Button: ArtTile_GrayButton, palette 0
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BUTTON,
                Sonic3kConstants.MAP_BUTTON_ADDR,
                Sonic3kConstants.ARTTILE_GRAY_BUTTON,
                0,
                null
        ));
    }

    /**
     * Populates CNZ (Carnival Night Zone) art entries.
     * Both acts: Sparkle, Batbot, Clamer (with DPLC), ClamerShot, CNZBalloon.
     */
    private static void addCnzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CNZ_SPARKLE,
                Sonic3kConstants.ART_KOSM_SPARKLE_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_SPARKLE_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CNZ_BATBOT,
                Sonic3kConstants.ART_KOSM_BATBOT_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_BATBOT_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CNZ_CLAMER,
                Sonic3kConstants.ART_UNC_CLAMER_ADDR,
                CompressionType.UNCOMPRESSED,
                Sonic3kConstants.ART_UNC_CLAMER_SIZE,
                Sonic3kConstants.MAP_CLAMER_ADDR,
                1,
                Sonic3kConstants.DPLC_CLAMER_ADDR
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CNZ_CLAMER_SHOT,
                Sonic3kConstants.ART_KOSM_CLAMER_SHOT_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_CLAMER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CNZ_BALLOON,
                Sonic3kConstants.ART_KOSM_CNZ_BALLOON_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_CNZ_BALLOON_ADDR,
                0,
                -1
        ));

        // Cork Floor: ArtTile_CNZPlatform
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.CORK_FLOOR_CNZ,
                Sonic3kConstants.MAP_CNZ_CORK_FLOOR_ADDR,
                Sonic3kConstants.ARTTILE_CNZ_PLATFORM,
                2,
                null
        ));

        // Breakable Wall: art_tile = ArtTile_CNZMisc + $CF = $0420, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_CNZ,
                Sonic3kConstants.MAP_CNZ_SOZ_BREAKABLE_WALL_ADDR,
                Sonic3kConstants.ARTTILE_CNZ_MISC + 0xCF,
                2,
                null
        ));

        // Button: ArtTile_CNZMisc + $C9, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.CNZ_BUTTON,
                Sonic3kConstants.MAP_CNZ_BUTTON_ADDR,
                Sonic3kConstants.ARTTILE_CNZ_BUTTON,
                2,
                null
        ));
    }

    /**
     * Populates FBZ (Flying Battery Zone) art entries.
     * Overrides shared spikes to FBZ-specific VRAM tile.
     * Both acts: Blaster, Technosqueek, FBZButton.
     */
    private static void addFbzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite groups: subtypes 39-45
        // base 0x379: subtypes 39, 40, 41, 42 (hangers, palette 2)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_FBZ_HANGER,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x379, 2,
                null, new int[]{39, 40, 41, 42}));
        // base 0x443 (FBZMisc+$CA): subtype 43, palette 1
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_FBZ_EXTRA,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x443, 1,
                null, new int[]{43}));
        // base 0x339: subtypes 44, 45 (rails)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_FBZ_RAIL,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x339, 1,
                null, new int[]{44, 45}));

        // Override shared spikes to FBZ tile address
        levelArt.removeIf(e -> e.key().equals(Sonic3kObjectArtKeys.SPIKES));
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.SPIKES, -1, Sonic3kConstants.ARTTILE_FBZ_SPIKES, 0, "buildSpikesSheet"));

        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.FBZ_BLASTER,
                Sonic3kConstants.ART_KOSM_FBZ_BLASTER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_BLASTER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.FBZ_TECHNOSQUEEK,
                Sonic3kConstants.ART_KOSM_FBZ_TECHNOSQUEEK_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_TECHNOSQUEEK_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.FBZ_BUTTON,
                Sonic3kConstants.ART_KOSM_FBZ_BUTTON_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_BUTTON_ADDR,
                0,
                -1
        ));

        // Cork Floor: ArtTile_FBZMisc + $C1
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.CORK_FLOOR_FBZ,
                Sonic3kConstants.MAP_FBZ_CORK_FLOOR_ADDR,
                Sonic3kConstants.ARTTILE_FBZ_MISC + 0xC1,
                1,
                null
        ));
    }

    /**
     * Populates ICZ (IceCap Zone) art entries.
     * Level-art: collapsing bridge (both acts).
     * Standalone: Snowdust, StarPointer, Penguinator (with DPLC).
     */
    private static void addIczEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_ICZ,
                Sonic3kConstants.MAP_ICZ_COLLAPSING_BRIDGE_ADDR,
                1,
                2,
                null
        ));

        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.ICZ_SNOWDUST,
                Sonic3kConstants.ART_KOSM_ICZ_SNOWDUST_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_ICZ_SNOWDUST_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.ICZ_STAR_POINTER,
                Sonic3kConstants.ART_KOSM_ICZ_STAR_POINTER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_STAR_POINTER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.ICZ_PENGUINATOR,
                Sonic3kConstants.ART_UNC_ICZ_PENGUINATOR_ADDR,
                CompressionType.UNCOMPRESSED,
                Sonic3kConstants.ART_UNC_ICZ_PENGUINATOR_SIZE,
                Sonic3kConstants.MAP_PENGUINATOR_ADDR,
                1,
                Sonic3kConstants.DPLC_PENGUINATOR_ADDR
        ));

        // Cork Floor: art_tile = $001, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.CORK_FLOOR_ICZ,
                Sonic3kConstants.MAP_ICZ_CORK_FLOOR_ADDR,
                1,
                2,
                null
        ));

        // Button: ArtTile_GrayButton, palette 0
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BUTTON,
                Sonic3kConstants.MAP_BUTTON_ADDR,
                Sonic3kConstants.ARTTILE_GRAY_BUTTON,
                0,
                null
        ));
    }

    /**
     * Populates LBZ (Launch Base Zone) art entries.
     * Both acts: SnaleBlaster, Orbinaut, Ribot, Corkey.
     */
    private static void addLbzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite groups: subtype 20 (pole), subtypes 21-23 (girders)
        // base 0x40D: subtype 20
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_LBZ_POLE,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x40D, 2,
                null, new int[]{20}));
        // base 0x433: subtypes 21, 22, 23
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_LBZ_GIRDER,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x433, 1,
                null, new int[]{21, 22, 23}));

        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SNALE_BLASTER,
                Sonic3kConstants.ART_KOSM_SNALE_BLASTER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_SNALE_BLASTER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.ORBINAUT,
                Sonic3kConstants.ART_KOSM_ORBINAUT_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_ORBINAUT_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.RIBOT,
                Sonic3kConstants.ART_KOSM_RIBOT_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_RIBOT_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CORKEY,
                Sonic3kConstants.ART_KOSM_CORKEY_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_CORKEY_ADDR,
                1,
                -1
        ));

        // Cork Floor: art_tile = $001, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.CORK_FLOOR_LBZ,
                Sonic3kConstants.MAP_LBZ_CORK_FLOOR_ADDR,
                1,
                2,
                null
        ));

        // Breakable Wall: art_tile = ArtTile_LBZ2Misc = $02EA, palette 1
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_LBZ,
                Sonic3kConstants.MAP_LBZ_BREAKABLE_WALL_ADDR,
                Sonic3kConstants.ARTTILE_LBZ2_MISC,
                1,
                null
        ));

        // Button: ArtTile_GrayButton, palette 0
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BUTTON,
                Sonic3kConstants.MAP_BUTTON_ADDR,
                Sonic3kConstants.ARTTILE_GRAY_BUTTON,
                0,
                null
        ));
    }

    /**
     * Populates MHZ (Mushroom Hill Zone) art entries.
     * Both acts: Madmole, Mushmeanie, Dragonfly, Cluckoid (with DPLC).
     * Act 2 only: CluckoidArrow.
     * Overrides diagonal spring art tile to MGZ/MHZ value.
     */
    private static void addMhzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite groups: subtypes 24-30
        // base 0x357: subtypes 24, 25, 26 (cliff edges)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_MHZ_CLIFF,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x357, 2,
                null, new int[]{24, 25, 26}));
        // base 0x40E: subtypes 27, 28 (columns, palette 3)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_MHZ_COLUMN,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x40E, 3,
                null, new int[]{27, 28}));
        // base 0x41E: subtype 29 (vine)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_MHZ_VINE,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x41E, 2,
                null, new int[]{29}));
        // base 0x347: subtype 30 (pedestal, palette 0)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_MHZ_PEDESTAL,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x347, 0,
                null, new int[]{30}));

        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.MADMOLE,
                Sonic3kConstants.ART_KOSM_MADMOLE_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_MADMOLE_ADDR,
                0,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.MUSHMEANIE,
                Sonic3kConstants.ART_KOSM_MUSHMEANIE_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_MUSHMEANIE_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.DRAGONFLY,
                Sonic3kConstants.ART_KOSM_DRAGONFLY_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_DRAGONFLY_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CLUCKOID,
                Sonic3kConstants.ART_UNC_CLUCKOID_ADDR,
                CompressionType.UNCOMPRESSED,
                Sonic3kConstants.ART_UNC_CLUCKOID_SIZE,
                Sonic3kConstants.MAP_CLUCKOID_ADDR,
                1,
                Sonic3kConstants.DPLC_CLUCKOID_ADDR
        ));

        if (actIndex == 1) {
            standalone.add(new StandaloneArtEntry(
                    Sonic3kObjectArtKeys.CLUCKOID_ARROW,
                    Sonic3kConstants.ART_KOSM_CLUCKOID_ARROW_ADDR,
                    CompressionType.KOSINSKI_MODULED,
                    0,
                    Sonic3kConstants.MAP_CLUCKOID_ARROW_ADDR,
                    1,
                    -1
            ));
        }

        // Diagonal spring override
        levelArt.removeIf(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL));
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.SPRING_DIAGONAL, -1, Sonic3kConstants.ARTTILE_MGZ_MHZ_DIAGONAL_SPRING, 0, "buildSpringDiagonalSheet"));
        levelArt.removeIf(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW));
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW, -1, Sonic3kConstants.ARTTILE_MGZ_MHZ_DIAGONAL_SPRING, 0, "buildSpringDiagonalYellowSheet"));

        // Breakable Wall: art_tile = ArtTile_MHZMisc + $4 = $034B, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_MHZ,
                Sonic3kConstants.MAP_MHZ_BREAKABLE_WALL_ADDR,
                Sonic3kConstants.ARTTILE_MHZ_MISC + 0x4,
                2,
                null
        ));
    }

    /**
     * Populates SOZ (Sandopolis Zone) art entries.
     * Both acts: Skorp, Sandworm, Rockn.
     */
    private static void addSozEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite groups: subtypes 46 (SOZ objects), 47 (cork)
        // base 0x001: subtype 46
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_SOZ_001,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 1, 2,
                null, new int[]{46}));
        // base 0x3AF: subtype 47 (palette 0)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_SOZ_CORK,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x3AF, 0,
                null, new int[]{47}));

        // AnimatedStillSprite: SOZ (base 0x40F)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.ANIM_STILL_SOZ,
                -1, Sonic3kConstants.ARTTILE_SOZ_MISC + 0x46, 2,
                "buildAnimStillSozSheet"));

        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SKORP,
                Sonic3kConstants.ART_KOSM_SKORP_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_SKORP_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SANDWORM,
                Sonic3kConstants.ART_KOSM_SANDWORM_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_SANDWORM_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.ROCKN,
                Sonic3kConstants.ART_KOSM_ROCKN_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_ROCKN_ADDR,
                1,
                -1
        ));

        // Breakable Wall: art_tile = ArtTile_SOZMisc + $C3 = $048C, palette 2
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_SOZ,
                Sonic3kConstants.MAP_CNZ_SOZ_BREAKABLE_WALL_ADDR,
                Sonic3kConstants.ARTTILE_SOZ_MISC + 0xC3,
                2,
                null
        ));
    }

    /**
     * Populates LRZ (Lava Reef Zone) art entries.
     * Level-art: breakable rocks (act-specific).
     * Standalone: FirewormSegments, Iwamodoki, Toxomister.
     */
    private static void addLrzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite groups: subtypes 31-38
        // base 0x3A1, pal 2: subtypes 31, 32, 33 (horizontal rails)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_LRZ_RAIL,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x3A1, 2,
                null, new int[]{31, 32, 33}));
        // base 0x3A1, pal 1: subtypes 35, 36, 37, 38 (vertical gear rails)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_LRZ_GEAR,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x3A1, 1,
                null, new int[]{35, 36, 37, 38}));
        // base 0x0D3: subtype 34 (rock decoration)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_LRZ_ROCK,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x0D3, 2,
                null, new int[]{34}));

        // AnimatedStillSprite: LRZ act 1 (base 0xD3) and act 2 (base 0x40D)
        if (actIndex == 0) {
            levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.ANIM_STILL_LRZ_D3,
                    -1, 0x00D3, 2,
                    "buildAnimStillLrzD3Sheet"));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.LRZ1_ROCK,
                    Sonic3kConstants.MAP_LRZ_BREAKABLE_ROCK_ADDR,
                    0x00D3,
                    2,
                    null
            ));
        } else {
            levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.ANIM_STILL_LRZ2,
                    -1, Sonic3kConstants.ARTTILE_LRZ2_MISC, 1,
                    "buildAnimStillLrz2Sheet"));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.LRZ2_ROCK,
                    Sonic3kConstants.MAP_LRZ_BREAKABLE_ROCK2_ADDR,
                    Sonic3kConstants.ARTTILE_LRZ2_MISC,
                    3,
                    null
            ));
        }

        // Standalone badniks (both acts)
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.FIREWORM_SEGMENTS,
                Sonic3kConstants.ART_KOSM_FIREWORM_SEGMENTS_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_FIREWORM_SEGMENTS_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.IWAMODOKI,
                Sonic3kConstants.ART_KOSM_IWAMODOKI_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_IWAMODOKI_ADDR,
                0,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.TOXOMISTER,
                Sonic3kConstants.ART_KOSM_TOXOMISTER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_TOXOMISTER_ADDR,
                1,
                -1
        ));

        // Breakable Wall: art_tile = ArtTile_LRZ2Misc = $040D, palette 3
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_LRZ,
                Sonic3kConstants.MAP_LRZ_BREAKABLE_WALL_ADDR,
                Sonic3kConstants.ARTTILE_LRZ2_MISC,
                3,
                null
        ));

        // Button: act-specific art tile and palette
        if (actIndex == 0) {
            // LRZ Act 1: ArtTile_LRZMisc, palette 3
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.LRZ_BUTTON,
                    Sonic3kConstants.MAP_LRZ_BUTTON_ADDR,
                    Sonic3kConstants.ARTTILE_LRZ_MISC,
                    3,
                    null
            ));
        } else {
            // LRZ Act 2: ArtTile_LRZ2Misc + $1C, palette 1
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.LRZ2_BUTTON,
                    Sonic3kConstants.MAP_LRZ_BUTTON_ADDR,
                    Sonic3kConstants.ARTTILE_LRZ2_BUTTON,
                    1,
                    null
            ));
        }
    }

    /**
     * Populates SSZ (Sky Sanctuary Zone) art entries.
     * EggRobo (palette 0).
     */
    private static void addSszEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SSZ_EGG_ROBO,
                Sonic3kConstants.ART_KOSM_EGG_ROBO_BADNIK_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_EGG_ROBO_ADDR,
                0,
                -1
        ));
    }

    /**
     * Populates DEZ (Death Egg Zone) art entries.
     * Both acts: Spikebonker, Chainspike.
     */
    private static void addDezEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // StillSprite groups: subtypes 48-50
        // base 0x3FF: subtypes 48, 49 (beams)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_DEZ_BEAM,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x3FF, 1,
                null, new int[]{48, 49}));
        // base 0x385: subtype 50 (post)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_DEZ_POST,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 0x385, 1,
                null, new int[]{50}));

        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SPIKEBONKER,
                Sonic3kConstants.ART_KOSM_SPIKEBONKER_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_SPIKEBONKER_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CHAINSPIKE,
                Sonic3kConstants.ART_KOSM_CHAINSPIKE_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_CHAINSPIKE_ADDR,
                1,
                -1
        ));
    }

    /**
     * Populates DDZ (Doomsday Zone) art entries.
     * EggRobo (palette 0).
     */
    private static void addDdzEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.DDZ_EGG_ROBO,
                Sonic3kConstants.ART_KOSM_EGG_ROBO_BADNIK_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_EGG_ROBO_ADDR,
                0,
                -1
        ));
    }

    /**
     * Populates AIZ (Angel Island Zone) art entries.
     *
     * <p>Standalone: Bloominator, Rhinobot (with DPLC), MonkeyDude.
     * <p>Level-art: ride vine, animated still sprites, foreground plant (both acts),
     * plus act-specific rocks, trees, zipline pegs, collapsing platforms.
     */
    private static void addAizEntries(int actIndex,
                                      List<StandaloneArtEntry> standalone,
                                      List<LevelArtEntry> levelArt) {
        // Badniks (both acts)
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.BLOOMINATOR,
                Sonic3kConstants.ART_KOSM_AIZ_BLOOMINATOR_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_BLOOMINATOR_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.RHINOBOT,
                Sonic3kConstants.ART_UNC_AIZ_RHINOBOT_ADDR,
                CompressionType.UNCOMPRESSED,
                Sonic3kConstants.ART_UNC_AIZ_RHINOBOT_SIZE,
                Sonic3kConstants.MAP_RHINOBOT_ADDR,
                1,
                Sonic3kConstants.DPLC_RHINOBOT_ADDR
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.MONKEY_DUDE,
                Sonic3kConstants.ART_KOSM_AIZ_MONKEY_DUDE_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_MONKEY_DUDE_ADDR,
                1,
                -1
        ));
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.CATERKILLER_JR,
                Sonic3kConstants.ART_KOSM_AIZ_CATERKILLER_JR_ADDR,
                CompressionType.KOSINSKI_MODULED,
                0,
                Sonic3kConstants.MAP_CATERKILLER_JR_ADDR,
                1,
                -1
        ));

        // PLC_EndSignStuff - end sign face (uncompressed art + DPLC remap)
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.END_SIGN,
                Sonic3kConstants.ART_UNC_END_SIGNS_ADDR,
                CompressionType.UNCOMPRESSED,
                Sonic3kConstants.ART_UNC_END_SIGNS_SIZE,
                Sonic3kConstants.MAP_END_SIGNS_ADDR,
                0,
                Sonic3kConstants.DPLC_END_SIGNS_ADDR));

        // PLC_EndSignStuff - signpost pole/stub (Nemesis compressed)
        standalone.add(new StandaloneArtEntry(
                Sonic3kObjectArtKeys.SIGNPOST_STUB,
                Sonic3kConstants.ART_NEM_SIGNPOST_STUB_ADDR,
                CompressionType.NEMESIS, -1, Sonic3kConstants.MAP_SIGNPOST_STUB_ADDR,
                0, -1));

        // Level-art shared across both acts
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.AIZ_RIDE_VINE,
                Sonic3kConstants.MAP_AIZ_MHZ_RIDE_VINE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_SWING_VINE,
                0,
                null
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.ANIMATED_STILL_SPRITES,
                -1,
                Sonic3kConstants.ARTTILE_AIZ_MISC2,
                3,
                "buildAnimatedStillSpritesSheet"
        ));
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.AIZ_FOREGROUND_PLANT,
                -1,
                Sonic3kConstants.ARTTILE_AIZ_MISC1,
                2,
                "buildAizForegroundPlantSheet"
        ));

        // StillSprite groups: subtypes 0-5 (AIZ2 decorations)
        // base 0x2E9 (AIZMisc2): subtypes 0,1,2,5
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_AIZ_MISC2,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, Sonic3kConstants.ARTTILE_AIZ_MISC2, 2,
                null, new int[]{0, 1, 2, 5}));
        // base 0x001, pal 2: subtype 3
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_AIZ_001,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 1, 2,
                null, new int[]{3}));
        // base 0x001, pal 3: subtype 4 (waterfall)
        levelArt.add(new LevelArtEntry(Sonic3kObjectArtKeys.STILL_AIZ_WATERFALL,
                Sonic3kConstants.MAP_STILL_SPRITES_ADDR, 1, 3,
                null, new int[]{4}));

        // Breakable Wall: art_tile = make_art_tile($001, 2, 0), all 6 frames
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                Sonic3kConstants.MAP_AIZ_BREAKABLE_WALL_ADDR,
                1,
                2,
                null
        ));

        // Disappearing Floor: parent overlay uses tile base 1, palette 2.
        // Interleaved mapping tables require explicit frame count (custom builder).
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.AIZ_DISAPPEARING_FLOOR,
                -1,
                1,
                2,
                "buildDisappearingFloorSheet"
        ));
        // Disappearing Floor water border: ArtTile_AIZMisc2, palette 3.
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.AIZ_DISAPPEARING_FLOOR_BORDER,
                -1,
                Sonic3kConstants.ARTTILE_AIZ_MISC2,
                3,
                "buildDisappearingFloorBorderSheet"
        ));

        // Button: ArtTile_GrayButton, palette 0
        levelArt.add(new LevelArtEntry(
                Sonic3kObjectArtKeys.BUTTON,
                Sonic3kConstants.MAP_BUTTON_ADDR,
                Sonic3kConstants.ARTTILE_GRAY_BUTTON,
                0,
                null
        ));

        // Falling Log: act-specific log body + splash mappings
        // Act 1: ArtTile_AIZFallingLog (0x03CF), palette 2 for both log and splash
        // Act 2: ArtTile_AIZMisc2 (0x02E9), palette 2 for log, palette 3 for splash
        if (actIndex == 0) {
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ1_FALLING_LOG,
                    Sonic3kConstants.MAP_AIZ_FALLING_LOG_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_FALLING_LOG,
                    2,
                    null
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ1_FALLING_LOG_SPLASH,
                    Sonic3kConstants.MAP_AIZ_FALLING_LOG_SPLASH_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_FALLING_LOG,
                    2,
                    null
            ));
        } else {
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ2_FALLING_LOG,
                    Sonic3kConstants.MAP_AIZ_FALLING_LOG_2_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    null
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ2_FALLING_LOG_SPLASH,
                    Sonic3kConstants.MAP_AIZ_FALLING_LOG_SPLASH_2_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    3,
                    null
            ));
        }

        // Act-specific level-art
        if (actIndex == 0) {
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ1_TREE,
                    -1,
                    1,
                    2,
                    "buildAiz1TreeSheet"
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ1_ZIPLINE_PEG,
                    -1,
                    Sonic3kConstants.ARTTILE_AIZ_SLIDE_ROPE,
                    2,
                    "buildAiz1ZiplinePegSheet"
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ1_ROCK,
                    Sonic3kConstants.MAP_AIZ_ROCK_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC1,
                    1,
                    null
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_AIZ1,
                    Sonic3kConstants.MAP_AIZ_COLLAPSING_PLATFORM_ADDR,
                    1,
                    2,
                    null
            ));
            // Cork Floor: art_tile = make_art_tile($001, 2, 0)
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1,
                    Sonic3kConstants.MAP_AIZ_CORK_FLOOR_ADDR,
                    1,
                    2,
                    null
            ));
            // Floating Platform: ArtTile_AIZFloatingPlatform
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.FLOATING_PLATFORM_AIZ1,
                    Sonic3kConstants.MAP_AIZ_FLOATING_PLATFORM_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_FLOATING_PLATFORM,
                    2,
                    null
            ));
        } else {
            // AIZ Spiked Log: art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0)
            // ArtNem_AIZMisc2 is loaded by PLC_0C_0D (Act 2 objects)
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ_SPIKED_LOG,
                    Sonic3kConstants.MAP_AIZ_SPIKED_LOG_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    null
            ));
            // AIZ Flipping Bridge: art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0)
            // ArtNem_AIZMisc2 loaded by PLC_0C_0D. 32 frames, explicit count needed.
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ_FLIPPING_BRIDGE,
                    -1,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    "buildFlippingBridgeSheet"
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE,
                    -1,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    "buildDrawBridgeSheet"
            ));
            // AIZ Collapsing Log Bridge: art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0)
            // ArtNem_AIZMisc2 is loaded by PLC_0C_0D (Act 2 objects)
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE,
                    Sonic3kConstants.MAP_AIZ_COLLAPSING_LOG_BRIDGE_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    null
            ));
            // AIZ Draw Bridge Fire: art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 1)
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE,
                    Sonic3kConstants.MAP_AIZ_DRAW_BRIDGE_FIRE_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    null
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.AIZ2_ROCK,
                    Sonic3kConstants.MAP_AIZ_ROCK2_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_MISC2,
                    2,
                    null
            ));
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_AIZ2,
                    Sonic3kConstants.MAP_AIZ_COLLAPSING_PLATFORM2_ADDR,
                    1,
                    2,
                    null
            ));
            // Cork Floor: AIZ2 variant
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.CORK_FLOOR_AIZ2,
                    Sonic3kConstants.MAP_AIZ_CORK_FLOOR_2_ADDR,
                    1,
                    2,
                    null
            ));
            // Floating Platform: ArtTile_AIZ2FloatingPlatform
            levelArt.add(new LevelArtEntry(
                    Sonic3kObjectArtKeys.FLOATING_PLATFORM_AIZ2,
                    Sonic3kConstants.MAP_AIZ_FLOATING_PLATFORM_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ2_FLOATING_PLATFORM,
                    2,
                    null
            ));
        }
    }
}
