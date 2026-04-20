package com.openggf.game.sonic3k;

/**
 * Owner ID constants and priority levels for S3K palette ownership registry.
 *
 * <p>Owner IDs are namespaced strings that identify which subsystem wrote a
 * palette color. Priority determines which write wins when multiple owners
 * target the same color index within a single frame.
 */
public final class S3kPaletteOwners {
    public static final String AIZ_RESIZE_MUTATION = "s3k.aiz.resizeMutation";
    public static final String AIZ_FIRE_TRANSITION = "s3k.aiz.fireTransition";
    public static final String AIZ_BOSS_SMALL = "s3k.aiz.bossSmall";
    public static final String AIZ_MINIBOSS = "s3k.aiz.miniboss";
    public static final String AIZ_MINIBOSS_CUTSCENE = "s3k.aiz.minibossCutscene";
    public static final String AIZ_END_BOSS = "s3k.aiz.endBoss";
    public static final String ZONE_EVENT_PALETTE_LOAD = "s3k.zoneEvents.paletteLoad";
    public static final String HCZ_WATER_CYCLE = "s3k.hcz.waterCycle";
    public static final String HCZ_CAVE_LIGHTING = "s3k.hcz.caveLighting";
    public static final String HCZ_MINIBOSS = "s3k.hcz.miniboss";
    public static final String HCZ_END_BOSS = "s3k.hcz.endBoss";
    public static final String MGZ_END_BOSS = "s3k.mgz.endBoss";
    /**
     * CNZ AnPal palette ownership for the bumper, background, and tertiary
     * animation tables.
     *
     * <p>CNZ's ROM keeps separate normal and water palette tables, but this
     * engine slice routes the normal-table writes through the registry and
     * mirrors them into the underwater surface so both planes stay aligned.
     */
    public static final String CNZ_ANPAL = "s3k.cnz.anpal";
    /**
     * Reserved owner ID for the Knuckles-route teleporter palette override.
     *
     * <p>Task 5 introduces the owner now so Task 8's teleporter object can
     * claim palette line 2 through the shared registry instead of introducing
     * a CNZ-local side channel.
     */
    public static final String CNZ_TELEPORTER = "s3k.cnz.teleporter";

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
