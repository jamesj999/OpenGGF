package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
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
            int dplcAddr
    ) {
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
     */
    public record LevelArtEntry(
            String key,
            int mappingAddr,
            int artTileBase,
            int palette,
            String builderName
    ) {
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
     * Returns the art plan for the given zone and act.
     *
     * <p>The plan includes shared entries (spikes, springs) for all zones,
     * plus any zone-specific entries registered in {@link #addZoneEntries}.
     *
     * @param zoneIndex zone index (0=AIZ, 1=HCZ, ...)
     * @param actIndex  act index (0 or 1)
     * @return immutable art plan for the zone+act
     */
    public static ZoneArtPlan getPlan(int zoneIndex, int actIndex) {
        List<StandaloneArtEntry> standalone = new ArrayList<>();
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
        }
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
        } else {
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
        }
    }
}
