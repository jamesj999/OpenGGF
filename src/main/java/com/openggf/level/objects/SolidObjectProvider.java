package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

public interface SolidObjectProvider {
    SolidObjectParams getSolidParams();

    default SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.AUTO_AFTER_UPDATE;
    }

    default boolean isSolidFor(PlayableEntity player) {
        return true;
    }

    default boolean isTopSolidOnly() {
        return false;
    }

    /**
     * Whether this top-solid object rejects the exact surface boundary before landing.
     * <p>
     * Most shared top-solid callers keep the established engine/profile behavior.
     * Some helper variants reject {@code d0 == 0}; S3K's shared
     * {@code SolidObjectTop_1P} accepts it and only rejects positive separation
     * or overlap below {@code -$10}.
     */
    default boolean rejectsZeroDistanceTopSolidLanding() {
        return false;
    }

    default boolean rejectsZeroDistanceTopSolidLanding(PlayableEntity player) {
        return rejectsZeroDistanceTopSolidLanding();
    }

    /**
     * Called when a top-solid first-landing check reaches the exact surface
     * boundary and this provider rejected that boundary.
     */
    default void onRejectedZeroDistanceTopSolidLanding(PlayableEntity player) {
        // Default no-op
    }

    /**
     * Whether this solid can keep a grounded player attached during the
     * pre-movement terrain attachment check used by S2/S3K inline solid
     * resolution.
     * <p>
     * Normal solids rely on the previous frame's standing snapshot. ROM helper
     * objects spawned immediately before their first {@code SolidObjectTop}
     * call do not have a previous snapshot yet, but can still support the
     * player in the same frame.
     */
    default boolean providesPreMovementGroundAttachmentSupport() {
        return false;
    }

    /**
     * Whether this solid should still be evaluated while the player is in an
     * object-controlled state. Most scripted object-control states suppress
     * generic solid contacts; a few ROM routines still call SolidObject and only
     * reject specific signed object_control values.
     */
    default boolean allowsObjectControlledSolidContacts() {
        return false;
    }

    /**
     * Whether this object uses monitor-style solidity (SPG: "Item Monitor").
     * Monitor solidity differs from normal solid objects:
     * - No +4 added during vertical overlap check
     * - Landing only if player Y relative to top < 16 AND within object width + 4px margin
     * - Never pushes player downward, only to sides
     */
    default boolean hasMonitorSolidity() {
        return false;
    }

    /**
     * Whether this object should use the generic sticky contact buffer while being ridden.
     * <p>
     * The buffer reduces edge jitter for moving platforms, but some hazards should not
     * preserve contact through this tolerance.
     */
    default boolean usesStickyContactBuffer() {
        return true;
    }

    /**
     * Whether side-contact at exact edge overlap (distX == 0) should preserve
     * player subpixel motion instead of immediately zeroing horizontal speed.
     * <p>
     * Most static solids should return false to keep the player stable against
     * walls and avoid 1px edge jitter. Push-driven objects that depend on ROM
     * edge cadence (for example Sonic 1 push blocks) can return true.
     */
    default boolean preservesEdgeSubpixelMotion() {
        return false;
    }

    /**
     * Whether the right edge of the full solid X window is inclusive.
     * <p>
     * Most engine objects keep the established exclusive bound. S3K horizontal
     * springs use {@code SolidObjectFull2_1P}, whose initial X gate rejects with
     * {@code bhi}; that makes {@code relX == width * 2} a valid contact.
     */
    default boolean usesInclusiveRightEdge() {
        return false;
    }

    /**
     * Half-width of the standable top surface used by landing checks.
     * <p>
     * Defaults to the full collision half-width. Override for objects whose
     * side/body collision is intentionally wider than their top landing area.
     */
    default int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) {
        return collisionHalfWidth;
    }

    /**
     * Whether the collision half-width already matches the ROM's standable top
     * width for new landings.
     * <p>
     * Most solid helpers pass {@code d1 = obActWid + $B} into the generic solid
     * routines, so the landing check must narrow back down to {@code obActWid}.
     * Platform-style helpers such as Sonic 1's {@code PlatformObject} instead pass
     * {@code d1 = obActWid} directly, so the collision half-width is already the
     * correct landing width and should not be narrowed again.
     */
    default boolean usesCollisionHalfWidthForTopLanding() {
        return false;
    }

    /**
     * Called when the player is pushing against this object.
     * ROM: bset #p1_pushing_bit,status(a0) (s2.asm:35220-35226).
     * Objects that need to react to being pushed (e.g., spring walls) can override.
     */
    default void setPlayerPushing(PlayableEntity player, boolean pushing) {
        // Default no-op
    }

    /**
     * Whether this object should run a DropOnFloor terrain check after repositioning
     * the player each frame. When enabled, if terrain is detected at or above the
     * player's feet, the player detaches from this object and enters the air state.
     * <p>
     * ROM: DropOnFloor (s2.asm:35810) — called by objects that can push the player
     * into solid terrain (e.g., vertically-moving platforms like HTZ rising lava).
     */
    default boolean dropOnFloor() {
        return false;
    }

    /**
     * Whether losing ride contact through the inline carrying path should force the
     * player airborne.
     * <p>
     * Generic platform helpers in the original games typically clear
     * {@code status.player.on_object} and set {@code status.player.in_air} when the
     * player walks off the ride bounds. Some bespoke solids, such as the EHZ/HPZ log
     * bridge helper ({@code PlatformObject11_cont}), clear only the on-object flag and
     * allow immediate terrain handoff without an airborne frame.
     */
    default boolean forceAirOnRideExit() {
        return true;
    }

    /**
     * Whether this object's continued-riding routine still applies its platform
     * carry after {@code ExitPlatform} has cleared the player's on-object flag
     * because the player jumped.
     * <p>
     * Most platform helpers stop as soon as the rider is airborne. Sonic 1 Obj52
     * is a narrow exception: {@code MBlock_StandOn} calls {@code ExitPlatform},
     * then moves the block, then unconditionally calls {@code MvSonicOnPtfm2}.
     * See {@code docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83} and
     * {@code docs/s1disasm/_incObj/15 Swinging Platforms.asm:177-194}.
     */
    default boolean carriesAirborneRiderAfterExitPlatform() {
        return false;
    }

    /**
     * Whether the inline continued-riding slope sample should be suppressed for
     * exactly this frame while the player remains attached to the object.
     * <p>
     * Default: {@code false} (slope sample writes y_pos every frame, matching
     * ROM {@code SolidObjSloped2} sonic3k.asm:41727-41752 / {@code MvSonicOnSlope}
     * s2disasm:35429 invoked by {@code sub_205B6} sonic3k.asm:44830).
     * <p>
     * ROM divergence covered by this hook: S3K {@code Obj_CollapsingPlatform}
     * state-1 routine {@code loc_20594} (sonic3k.asm:44814-44824) decrements its
     * collapse timer {@code $38} and, when the timer is already zero at frame
     * start, branches to {@code ObjPlatformCollapse_CreateFragments}
     * (sonic3k.asm:45394-45442). That branch rewrites {@code (a0)} to
     * {@code loc_205DE} and {@code jmp}s to {@code Play_SFX} <em>without</em>
     * falling through to {@code sub_205B6} (sonic3k.asm:44830) -- so the slope
     * sample / y_pos write is skipped on the state-1 to state-2 transition
     * frame. Sonic remains attached because {@code Status_OnObj} and
     * {@code p1_standing_bit} are not cleared, but his y_pos is held at the
     * value written by the previous frame's {@code SolidObjSloped2}.
     * <p>
     * Engine architecture has the platform's {@code update()} (state machine)
     * and the {@code SolidContacts} continued-riding pass as separate steps,
     * so the post-update solid pass would still run a slope sample on the
     * transition frame. Returning {@code true} here for that exact frame keeps
     * the player riding (no air transition, no x carry change) while skipping
     * the y_pos write, mirroring ROM.
     */
    default boolean suppressSlopeSampleThisFrame(PlayableEntity player) {
        return false;
    }

    /**
     * Whether the {@code SolidObject_cont} on-screen gate (engine flag
     * {@link com.openggf.game.PhysicsFeatureSet#solidObjectOffscreenGate()})
     * should be bypassed for this object's new-contact resolution path.
     * <p>
     * ROM divergence: the on-screen gate at {@code loc_1DF88}
     * (sonic3k.asm:41390) lives <em>only</em> in the {@code SolidObjectFull_1P}
     * helper (sonic3k.asm:41016-41018). Objects that route through the
     * sibling helper {@code SolidObjectFull2_1P} (sonic3k.asm:41065-41067)
     * fall through directly to {@code SolidObject_cont} and never test
     * {@code render_flags} bit 7. Notably <strong>all spring variants</strong>
     * call {@code SolidObjectFull2_1P} (sonic3k.asm:47664/47673/47692/47701/
     * 47779/47798/47829/47848/48036/48045/48064/48074), so an off-screen
     * spring still resolves push and side contact in the ROM. The S2 spring
     * helpers use the equivalent {@code SolidObject_Always_SingleCharacter}
     * (s2.asm:33709/33718/33784/33802) which also bypasses the on-screen gate.
     * <p>
     * Default: {@code false} (gate applies, matching the existing
     * {@link com.openggf.game.PhysicsFeatureSet#solidObjectOffscreenGate()}
     * default behaviour). Spring instances and other objects that route through
     * the {@code Full2} helpers must override to {@code true}.
     */
    default boolean bypassesOffscreenSolidGate() {
        return false;
    }
}
