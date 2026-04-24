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
}
