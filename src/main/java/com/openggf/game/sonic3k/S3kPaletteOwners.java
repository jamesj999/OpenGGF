package com.openggf.game.sonic3k;

/**
 * Owner ID constants and priority levels for S3K palette ownership registry.
 *
 * <p>Owner IDs are namespaced strings that identify which subsystem wrote a
 * palette color. Priority determines which write wins when multiple owners
 * target the same color index within a single frame.
 */
public final class S3kPaletteOwners {
    public static final String HCZ_WATER_CYCLE = "s3k.hcz.waterCycle";
    public static final String HCZ_CAVE_LIGHTING = "s3k.hcz.caveLighting";
    public static final String HPZ_ZONE_CYCLE = "s3k.hpz.zoneCycle";
    public static final String HPZ_MASTER_EMERALD = "s3k.hpz.masterEmerald";
    public static final String HPZ_PALETTE_CONTROL = "s3k.hpz.paletteControl";

    public static final int PRIORITY_ZONE_CYCLE = 100;
    public static final int PRIORITY_ZONE_EVENT = 150;
    public static final int PRIORITY_OBJECT_OVERRIDE = 200;
    public static final int PRIORITY_CUTSCENE_OVERRIDE = 300;

    private S3kPaletteOwners() {
    }
}
