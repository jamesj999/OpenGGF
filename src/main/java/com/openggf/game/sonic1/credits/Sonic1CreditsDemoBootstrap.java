package com.openggf.game.sonic1.credits;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.level.WaterSystem;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Deterministic per-credits-demo bootstrap. Establishes the engine-side
 * starting pose for each of the 8 Sonic 1 credits demos using ROM-derived
 * constants from {@link Sonic1CreditsDemoData} ONLY.
 *
 * <p>This helper exists because each credits demo begins at an arbitrary
 * mid-level state (LZ Act 3 at a lamppost with a door + water, SBZ Act 1 at
 * a junction with object-controlled spin) and the engine's fresh-init pose
 * does not match the ROM's post-Obj01_Init/post-first-Sonic_Animate pose.
 *
 * <p><b>Trace-replay invariant.</b> Per CLAUDE.md "Trace Replay Tests" the
 * comparison-only invariant requires that engine state be derived from ROM
 * defaults, never hydrated from {@code TraceEvent.StateSnapshot} events. All
 * values written here are constants verified against the s1disasm credit
 * demo bootstrap routines (see s1.asm:2987-2990 for the per-credit
 * timer/level/lamppost data referenced in
 * {@link Sonic1CreditsDemoData}).
 *
 * <p><b>Per-demo starting animations.</b> Frame-zero recordings show:
 * <ul>
 *   <li>Demo 0 (GHZ1): {@code anim_id=0} ({@link Sonic1AnimationIds#WALK})
 *       — Sonic spawns at the level start with no prior pose.</li>
 *   <li>Demos 1-7: {@code anim_id=5} ({@link Sonic1AnimationIds#WAIT}) — the
 *       ROM has already settled the animation to the idle pose (zero ground
 *       speed) by the time recording begins on credit demo entry.</li>
 * </ul>
 */
public final class Sonic1CreditsDemoBootstrap {

    /**
     * Per-demo starting animation ID. Indices match
     * {@link Sonic1CreditsDemoData#DEMO_ZONE} / {@code DEMO_ACT}.
     * Demo 0 starts mid-walk-cycle (anim WALK = 0); all other demos boot
     * with zero ground speed and the engine should already be in WAIT, so
     * we set anim WAIT = 5 explicitly to match the ROM's settled idle pose.
     */
    public static final int[] STARTING_ANIMATION_ID = {
            Sonic1AnimationIds.WALK.id(), // Credit 0: GHZ1
            Sonic1AnimationIds.WAIT.id(), // Credit 1: MZ2
            Sonic1AnimationIds.WAIT.id(), // Credit 2: SYZ3
            Sonic1AnimationIds.WAIT.id(), // Credit 3: LZ3
            Sonic1AnimationIds.WAIT.id(), // Credit 4: SLZ3
            Sonic1AnimationIds.WAIT.id(), // Credit 5: SBZ1
            Sonic1AnimationIds.WAIT.id(), // Credit 6: SBZ2
            Sonic1AnimationIds.WAIT.id(), // Credit 7: GHZ1 (second demo)
    };

    private Sonic1CreditsDemoBootstrap() {}

    /**
     * Establishes the deterministic frame-zero player pose for the given
     * credits demo index (0-7). Called once after level load and before the
     * first replay frame.
     *
     * <p>All other player flags (control_locked, on_object, pushing,
     * roll_jumping, air, rolling, direction) match the engine's default
     * post-spawn state for every credits demo per the recorded snapshots,
     * so they are not touched here. Hitbox radii are already set to
     * standing (x=9, y=19) by {@link AbstractPlayableSprite#prepareForLevel}
     * so they are likewise not adjusted.
     *
     * @param demoIndex 0..{@link Sonic1CreditsDemoData#DEMO_CREDITS}-1
     * @param player    the player sprite to configure
     */
    public static void applyStartingPose(int demoIndex, AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        if (demoIndex < 0 || demoIndex >= STARTING_ANIMATION_ID.length) {
            return;
        }
        // Direction defaults to RIGHT; every credits demo starts facing right
        // per the recorded status_byte=0x00/0x40 snapshots (bit 0 clear).
        player.setDirection(Direction.RIGHT);
        player.setAnimationId(STARTING_ANIMATION_ID[demoIndex]);
    }

    /**
     * Applies the LZ-specific lamppost state for credit demo 3 (LZ Act 3).
     * Mirrors {@code EndDemo_LampVar} (s1.asm:4176-4187) which the ROM
     * restores before the LZ demo runs: ring count, camera position, bottom
     * boundary, and water height/routine.
     *
     * <p>Camera position and ring count are taken straight from
     * {@link Sonic1CreditsDemoData#LZ_LAMP_CAMERA_X} /
     * {@code LZ_LAMP_CAMERA_Y} / {@code LZ_LAMP_RINGS} — never from the
     * recorded trace.
     */
    public static void applyLzLampostState(AbstractPlayableSprite player,
                                           Camera camera) {
        if (player == null || camera == null) {
            return;
        }
        // Lamp_LoadInfo (s1disasm/_incObj/79 Lamppost.asm) writes
        // v_lamp_rings into v_rings then immediately clears v_rings to 0
        // via clr.w (v_rings).w on the very next line. The
        // EndDemo_LampVar dc.w 13 entry is therefore loaded then thrown
        // away, and ROM frame 0 always shows rings=0 for the LZ credits
        // demo. Match that observed ROM behaviour rather than the
        // misleading "13" constant. (LZ_LAMP_RINGS is retained in
        // Sonic1CreditsDemoData for documentation parity with the ROM
        // table layout.)
        player.setRingCount(0);
        camera.setX((short) Sonic1CreditsDemoData.LZ_LAMP_CAMERA_X);
        camera.setY((short) Sonic1CreditsDemoData.LZ_LAMP_CAMERA_Y);
        camera.setMaxY((short) Sonic1CreditsDemoData.LZ_LAMP_BOTTOM_BND);

        WaterSystem waterSystem = GameServices.water();
        int featureZone = GameServices.level().getFeatureZoneId();
        int featureAct = GameServices.level().getFeatureActId();
        waterSystem.setWaterLevelDirect(featureZone, featureAct,
                Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);
        waterSystem.setWaterLevelTarget(featureZone, featureAct,
                Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);

        ZoneFeatureProvider featureProvider = GameServices.level().getZoneFeatureProvider();
        if (featureProvider != null) {
            featureProvider.setWaterRoutine(Sonic1CreditsDemoData.LZ_LAMP_WATER_ROUTINE);
        }

        // Sync player's underwater flag with the water level we just set.
        // Without this, the first frame runs with inWater=false and uses
        // normal (non-underwater) acceleration, causing physics divergence.
        player.updateWaterState(Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);
    }
}
