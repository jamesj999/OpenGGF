package com.openggf.game;

import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void s1AnimationsRoundTripThroughCanonical() {
        for (Sonic1AnimationIds s1Anim : Sonic1AnimationIds.values()) {
            CanonicalAnimation canonical = s1Anim.toCanonical();
            assertNotNull(canonical, "S1 " + s1Anim + " should map to a canonical animation");
            int resolved = Sonic1AnimationIds.fromCanonical(canonical);
            assertEquals(s1Anim.id(), resolved,
                    "S1 " + s1Anim + " -> " + canonical + " should round-trip back to " + s1Anim.id());
        }
    }

    @Test
    void s1FloatNameBridge() {
        assertEquals(CanonicalAnimation.FLOAT, Sonic1AnimationIds.FLOAT1.toCanonical());
        assertEquals(Sonic1AnimationIds.FLOAT1.id(), Sonic1AnimationIds.fromCanonical(CanonicalAnimation.FLOAT));
    }

    @Test
    void s2AnimationsRoundTripThroughCanonical() {
        for (Sonic2AnimationIds s2Anim : Sonic2AnimationIds.values()) {
            if (s2Anim.name().startsWith("SUPER_")) continue;
            CanonicalAnimation canonical = s2Anim.toCanonical();
            assertNotNull(canonical, "S2 " + s2Anim + " should map to a canonical animation");
            int resolved = Sonic2AnimationIds.fromCanonical(canonical);
            assertEquals(s2Anim.id(), resolved,
                    "S2 " + s2Anim + " -> " + canonical + " should round-trip back to " + s2Anim.id());
        }
    }

    @Test
    void s3kAnimationsRoundTripThroughCanonical() {
        for (Sonic3kAnimationIds s3kAnim : Sonic3kAnimationIds.values()) {
            if (s3kAnim.name().startsWith("SUPER_")) continue;
            CanonicalAnimation canonical = s3kAnim.toCanonical();
            assertNotNull(canonical, "S3K " + s3kAnim + " should map to a canonical animation");
            int resolved = Sonic3kAnimationIds.fromCanonical(canonical);
            assertEquals(s3kAnim.id(), resolved,
                    "S3K " + s3kAnim + " -> " + canonical + " should round-trip back to " + s3kAnim.id());
        }
    }

    @Test
    void unsupportedAnimationsReturnMinusOne() {
        assertEquals(-1, Sonic1AnimationIds.fromCanonical(CanonicalAnimation.SPINDASH));
        assertEquals(-1, Sonic2AnimationIds.fromCanonical(CanonicalAnimation.WARP1));
        assertEquals(-1, Sonic1AnimationIds.fromCanonical(CanonicalAnimation.BLINK));
        assertEquals(-1, Sonic3kAnimationIds.fromCanonical(CanonicalAnimation.WARP1));
    }

    @Test
    void s2SuperVariantsReturnNullFromToCanonical() {
        // SUPER_WALK, SUPER_RUN etc. index a separate table — toCanonical() returns null
        // Only SUPER_TRANSFORM maps to a canonical entry
        assertNull(Sonic2AnimationIds.SUPER_WALK.toCanonical());
        assertNull(Sonic2AnimationIds.SUPER_RUN.toCanonical());
        assertNotNull(Sonic2AnimationIds.SUPER_TRANSFORM.toCanonical());
        assertEquals(CanonicalAnimation.SUPER_TRANSFORM, Sonic2AnimationIds.SUPER_TRANSFORM.toCanonical());
    }
}


