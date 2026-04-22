package com.openggf.game.sonic3k.sidekick;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCarryTrigger;

/**
 * CNZ1 Tails-carry-Sonic trigger. Zone 3 (CNZ) + Act 0 + Player_mode 0
 * (SONIC_AND_TAILS) fires; everything else returns false.
 *
 * <p>ROM trigger: {@code sonic3k.asm loc_13A32} reads
 * {@code (Current_zone_and_act).w}; on {@code 0x0300} it teleports Tails to
 * {@code (0x0018, 0x0600)} and sets {@code Tails_CPU_routine = 0x0C}.
 *
 * <p>Player_mode gating here matches how the trace-recorded BK2 was captured.
 * The ROM's zone check itself is Player_mode-agnostic (see design spec §5.7).
 */
public final class Sonic3kCnzCarryTrigger implements SidekickCarryTrigger {

    /** S3K canonical zone id for Carnival Night. */
    private static final int ZONE_CNZ = 3;

    @Override
    public boolean shouldEnterCarry(int zoneId, int actId, PlayerCharacter playerMode) {
        return zoneId == ZONE_CNZ
                && actId == 0
                && playerMode == PlayerCharacter.SONIC_AND_TAILS;
    }

    @Override
    public void applyInitialPlacement(AbstractPlayableSprite carrier,
                                      AbstractPlayableSprite cargo) {
        // Teleport Tails (carrier) to the ROM's fixed pickup position.
        // ROM sub_1459E then parents Sonic at carrier.y + 0x1C.
        carrier.setCentreXPreserveSubpixel((short) Sonic3kConstants.CARRY_INIT_TAILS_X);
        carrier.setCentreYPreserveSubpixel((short) Sonic3kConstants.CARRY_INIT_TAILS_Y);
    }

    @Override
    public int carryDescendOffsetY() { return Sonic3kConstants.CARRY_DESCEND_OFFSET_Y; }

    @Override
    public short carryInitXVel() { return Sonic3kConstants.CARRY_INIT_TAILS_X_VEL; }

    @Override
    public int carryInputInjectMask() { return Sonic3kConstants.CARRY_INPUT_INJECT_MASK; }

    @Override
    public int carryJumpReleaseCooldownFrames() {
        return Sonic3kConstants.CARRY_COOLDOWN_JUMP_RELEASE;
    }

    @Override
    public int carryLatchReleaseCooldownFrames() {
        return Sonic3kConstants.CARRY_COOLDOWN_LATCH_RELEASE;
    }

    @Override
    public short carryReleaseJumpYVel() { return Sonic3kConstants.CARRY_RELEASE_JUMP_Y_VEL; }

    @Override
    public short carryReleaseJumpXVel() { return Sonic3kConstants.CARRY_RELEASE_JUMP_X_VEL; }
}
