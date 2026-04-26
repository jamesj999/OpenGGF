package com.openggf.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the per-game {@code sidekickSpawningRequiresGroundedLeader}
 * feature-flag values match the disassembly references documented on
 * {@link PhysicsFeatureSet}.
 *
 * <p>S2 ({@code TailsCPU_Spawning}, s2.asm:38751-38762): checks
 * {@code Status_OnGround}, {@code Status_Underwater}, {@code Status_RollJump}
 * and skips respawn while any are blocking. Engine-side gate: {@code true}.
 *
 * <p>S3K ({@code Tails_Catch_Up_Flying}, sonic3k.asm:26474-26486): does NOT
 * check those; only the 64-frame {@code (Level_frame_counter & $3F) == 0}
 * gate, the leader's {@code object_control} bit 7, and the leader's
 * {@code Status_Super}. Engine-side gate: {@code false}.
 *
 * <p>S1: no Tails CPU sidekick — value is unreachable; kept {@code true}
 * for symmetry with S2 (the default S2-style behaviour).
 */
class TestSidekickSpawningRequiresGroundedLeaderFeatureFlag {

    @Test
    void sonic1RequiresGroundedLeader() {
        assertTrue(PhysicsFeatureSet.SONIC_1.sidekickSpawningRequiresGroundedLeader(),
                "S1 has no Tails CPU sidekick; flag is unreachable but kept true for symmetry with S2");
    }

    @Test
    void sonic2RequiresGroundedLeader() {
        assertTrue(PhysicsFeatureSet.SONIC_2.sidekickSpawningRequiresGroundedLeader(),
                "S2 TailsCPU_Spawning checks Status_OnGround / Underwater / RollJump (s2.asm:38751-38762)");
    }

    @Test
    void sonic3kDoesNotRequireGroundedLeader() {
        assertFalse(PhysicsFeatureSet.SONIC_3K.sidekickSpawningRequiresGroundedLeader(),
                "S3K Tails_Catch_Up_Flying (sonic3k.asm:26474-26486) only honours the "
                        + "64-frame gate + leader.object_control + leader.Status_Super; it does NOT "
                        + "check leader-grounded / underwater / roll-jumping");
    }
}
