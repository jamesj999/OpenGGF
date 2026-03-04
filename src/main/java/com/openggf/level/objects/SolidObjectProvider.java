package com.openggf.level.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;

public interface SolidObjectProvider {
    SolidObjectParams getSolidParams();

    default boolean isSolidFor(AbstractPlayableSprite player) {
        return true;
    }

    default boolean isTopSolidOnly() {
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
     * Half-width of the standable top surface used by landing checks.
     * <p>
     * Defaults to the full collision half-width. Override for objects whose
     * side/body collision is intentionally wider than their top landing area.
     */
    default int getTopLandingHalfWidth(AbstractPlayableSprite player, int collisionHalfWidth) {
        return collisionHalfWidth;
    }

    /**
     * Called when the player is pushing against this object.
     * ROM: bset #p1_pushing_bit,status(a0) (s2.asm:35220-35226).
     * Objects that need to react to being pushed (e.g., spring walls) can override.
     */
    default void setPlayerPushing(AbstractPlayableSprite player, boolean pushing) {
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
}
