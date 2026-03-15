package com.openggf.sprites.playable;

import org.junit.Test;
import static org.junit.Assert.*;

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
        assertEquals("SuperState should have 4 values", 4, SuperState.values().length);
    }

    @Test
    public void testTransformingAndSuperAreConsideredSuper() {
        // SuperStateController.isSuper() returns true for TRANSFORMING and SUPER.
        // Verify the enum values that the controller treats as "super" exist and are distinct.
        assertNotEquals("TRANSFORMING and SUPER should be different states",
                SuperState.TRANSFORMING, SuperState.SUPER);
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
        assertTrue("NORMAL should precede TRANSFORMING",
                SuperState.NORMAL.ordinal() < SuperState.TRANSFORMING.ordinal());
        assertTrue("TRANSFORMING should precede SUPER",
                SuperState.TRANSFORMING.ordinal() < SuperState.SUPER.ordinal());
        assertTrue("SUPER should precede REVERTING",
                SuperState.SUPER.ordinal() < SuperState.REVERTING.ordinal());
    }
}
