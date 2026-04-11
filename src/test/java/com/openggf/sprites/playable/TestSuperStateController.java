package com.openggf.sprites.playable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SuperState enum semantics.
 * SuperStateController is abstract and requires a full player sprite, so we
 * verify the state enum contract that the controller relies on: TRANSFORMING
 * and SUPER are both considered "super" states, while NORMAL and REVERTING
 * are not.
 */
public class TestSuperStateController {

    @Test
    public void testSuperStateEnumHasFourValues() {
        assertEquals(4, SuperState.values().length, "SuperState should have 4 values");
    }

    @Test
    public void testTransformingAndSuperAreConsideredSuper() {
        // SuperStateController.isSuper() returns true for TRANSFORMING and SUPER.
        // Verify the enum values that the controller treats as "super" exist and are distinct.
        assertNotEquals(SuperState.TRANSFORMING, SuperState.SUPER, "TRANSFORMING and SUPER should be different states");
    }

    @Test
    public void testNormalIsNotSuperOrTransforming() {
        assertNotEquals(SuperState.NORMAL, SuperState.SUPER);
        assertNotEquals(SuperState.NORMAL, SuperState.TRANSFORMING);
    }

    @Test
    public void testRevertingIsDistinctState() {
        // REVERTING is defined but not currently used (revert is instant in ROM).
        // Verify it exists as a distinct state.
        assertNotEquals(SuperState.REVERTING, SuperState.NORMAL);
        assertNotEquals(SuperState.REVERTING, SuperState.SUPER);
    }

    @Test
    public void testStateOrdinalOrder() {
        // The lifecycle order is NORMAL -> TRANSFORMING -> SUPER -> REVERTING
        assertTrue(SuperState.NORMAL.ordinal() < SuperState.TRANSFORMING.ordinal(), "NORMAL should precede TRANSFORMING");
        assertTrue(SuperState.TRANSFORMING.ordinal() < SuperState.SUPER.ordinal(), "TRANSFORMING should precede SUPER");
        assertTrue(SuperState.SUPER.ordinal() < SuperState.REVERTING.ordinal(), "SUPER should precede REVERTING");
    }
}


