package com.openggf.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that CanonicalAnimation contains the full cross-game animation vocabulary
 * covering Sonic 1, Sonic 2, and Sonic 3&K player animations.
 */
public class TestCanonicalAnimationMapping {

    @Test
    void canonicalEnumContainsAllExpectedEntries() {
        // Core movement
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WALK"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("RUN"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("ROLL"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("ROLL2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("PUSH"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WAIT"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("DUCK"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("LOOK_UP"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SPRING"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BALANCE"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BALANCE2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BALANCE3"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BALANCE4"));

        // Combat/state
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("HURT"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("DEATH"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("DROWN"));

        // S1-specific
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("STOP"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WARP1"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WARP2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WARP3"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WARP4"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("FLOAT3"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("FLOAT4"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("LEAP1"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("LEAP2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SURF"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("GET_AIR"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BURNT"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SHRINK"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("WATER_SLIDE"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("NULL_ANIM"));

        // Shared
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("FLOAT"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("FLOAT2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("HANG"));

        // S2+
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SPINDASH"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SKID"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SLIDE"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("HANG2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BUBBLE"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("HURT2"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("FLY"));

        // S3K
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BLINK"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("GET_UP"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("VICTORY"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("BLANK"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("HURT_FALL"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("GLIDE_DROP"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("GLIDE_LAND"));
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("GLIDE_SLIDE"));

        // Super
        assertDoesNotThrow(() -> CanonicalAnimation.valueOf("SUPER_TRANSFORM"));
    }

    @Test
    void canonicalEnumHasExpectedCount() {
        assertEquals(50, CanonicalAnimation.values().length);
    }
}
