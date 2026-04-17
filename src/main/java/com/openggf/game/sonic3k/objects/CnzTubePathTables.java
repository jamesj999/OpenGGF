package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;

/**
 * Verified CNZ tube-source metadata shared by {@code Obj_CNZVacuumTube} and
 * {@code Obj_CNZSpiralTube}.
 *
 * <p>Step 0 findings from the S&K-side lock-on disassembly:
 * <ul>
 *   <li>{@code Obj_CNZVacuumTube} has <strong>no standalone path-data table</strong>
 *   on either half of the lock-on ROM. Its behavior is inline in the S&K-side
 *   {@code sonic3k.asm} flow at {@code Obj_CNZVacuumTube}, {@code sub_31F62},
 *   {@code loc_31FF4}, and {@code sub_32010}.</li>
 *   <li>{@code Obj_CNZSpiralTube} does use a dedicated S&K-side table family:
 *   {@code off_33320} at {@code 0x033320}, with route payloads
 *   {@code word_33328}, {@code word_33336}, {@code word_33344}, and
 *   {@code word_33352}.</li>
 *   <li>The two objects do <strong>not</strong> share one table family:
 *   Vacuum Tube is inline controller logic, while Spiral Tube is table-driven.</li>
 *   <li>Both objects are controller-only. Neither verified source path includes
 *   mappings or {@code make_art_tile} ownership, so neither object should claim
 *   a dedicated object-art sheet.</li>
 * </ul>
 */
public final class CnzTubePathTables {
    private static final String VACUUM_SOURCE = """
            Obj_CNZVacuumTube uses inline S&K-side controller logic in sonic3k.asm
            (Obj_CNZVacuumTube, sub_31F62, loc_31FF4, sub_32010), not an external path family.
            Controller-only: no mappings or make_art_tile ownership were verified.""";

    private static final String SPIRAL_SOURCE = """
            Obj_CNZSpiralTube uses the S&K-side off_33320 family in sonic3k.asm
            (0x033320 -> word_33328/word_33336/word_33344/word_33352).
            Controller-only: no mappings or make_art_tile ownership were verified.""";

    private static final SpiralPath[] SPIRAL_PATHS = {
            new SpiralPath("word_33328",
                    new RoutePoint(0x1390, 0x02D0),
                    new RoutePoint(0x1230, 0x02D0),
                    new RoutePoint(0x1230, 0x0300)),
            new SpiralPath("word_33336",
                    new RoutePoint(0x13F0, 0x02D0),
                    new RoutePoint(0x1560, 0x02D0),
                    new RoutePoint(0x1560, 0x0280)),
            new SpiralPath("word_33344",
                    new RoutePoint(0x2090, 0x0650),
                    new RoutePoint(0x2030, 0x0650),
                    new RoutePoint(0x2030, 0x0680)),
            new SpiralPath("word_33352",
                    new RoutePoint(0x20F0, 0x0650),
                    new RoutePoint(0x21E0, 0x0650),
                    new RoutePoint(0x21E0, 0x0600)),
    };

    private CnzTubePathTables() {
    }

    public static int vacuumObjectId() {
        return Sonic3kObjectIds.CNZ_VACUUM_TUBE;
    }

    public static int spiralObjectId() {
        return Sonic3kObjectIds.CNZ_SPIRAL_TUBE;
    }

    public static String describeVacuumSource() {
        return VACUUM_SOURCE;
    }

    public static String describeSpiralSource() {
        return SPIRAL_SOURCE;
    }

    public static boolean vacuumHasStandalonePathFamily() {
        return false;
    }

    public static boolean tubesAreControllerOnly() {
        return true;
    }

    public static int configuredVacuumLiftFrames(int subtype) {
        return (subtype & 0xFF) * 2;
    }

    public static int spiralPathCount() {
        return SPIRAL_PATHS.length;
    }

    public static SpiralPath spiralPath(int subtype) {
        return SPIRAL_PATHS[(subtype & 0x0F) & 0x03];
    }

    public record RoutePoint(int centerX, int centerY) {}

    public record SpiralPath(String label, RoutePoint first, RoutePoint second, RoutePoint third) {}
}
