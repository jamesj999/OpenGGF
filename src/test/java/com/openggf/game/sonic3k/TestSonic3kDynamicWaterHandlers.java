package com.openggf.game.sonic3k;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.ThresholdTableWaterHandler;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.WaterSystem;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for S3K dynamic water handlers returned by {@link Sonic3kWaterDataProvider}.
 * Verifies handler assignment and threshold/state-machine behavior.
 */
public class TestSonic3kDynamicWaterHandlers {

    private Sonic3kWaterDataProvider provider;

    @Before
    public void setUp() {
        provider = new Sonic3kWaterDataProvider();
    }

    // =====================================================================
    // Handler assignment tests
    // =====================================================================

    @Test
    public void aiz1HandlerIsNull() {
        assertNull("AIZ1 should have no dynamic handler (static water)",
                provider.getDynamicHandler(Sonic3kZoneIds.ZONE_AIZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void aiz2HandlerExists() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_AIZ, 1, PlayerCharacter.SONIC_AND_TAILS);
        assertNotNull("AIZ2 should have a dynamic handler", handler);
        assertInstanceOf(Aiz2DynamicWaterHandler.class, handler);
    }

    @Test
    public void hcz1HandlerIsThresholdTable() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 0, PlayerCharacter.SONIC_AND_TAILS);
        assertNotNull("HCZ1 should have a dynamic handler", handler);
        assertInstanceOf(ThresholdTableWaterHandler.class, handler);
    }

    @Test
    public void hcz2SonicHandlerIsThresholdTable() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.SONIC_AND_TAILS);
        assertNotNull("HCZ2 Sonic should have a dynamic handler", handler);
        assertInstanceOf(ThresholdTableWaterHandler.class, handler);
    }

    @Test
    public void hcz2KnucklesHandlerIsThresholdTable() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.KNUCKLES);
        assertNotNull("HCZ2 Knuckles should have a dynamic handler", handler);
        assertInstanceOf(ThresholdTableWaterHandler.class, handler);
    }

    @Test
    public void lbz1HandlerIsThresholdTable() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_LBZ, 0, PlayerCharacter.SONIC_AND_TAILS);
        assertNotNull("LBZ1 should have a dynamic handler", handler);
        assertInstanceOf(ThresholdTableWaterHandler.class, handler);
    }

    @Test
    public void lbz2KnucklesHandlerIsThresholdTable() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        assertNotNull("LBZ2 Knuckles should have a dynamic handler", handler);
        assertInstanceOf(ThresholdTableWaterHandler.class, handler);
    }

    @Test
    public void lbz2SonicHandlerIsNull() {
        assertNull("LBZ2 Sonic should have no dynamic handler",
                provider.getDynamicHandler(Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void lbz2TailsHandlerIsNull() {
        assertNull("LBZ2 Tails should have no dynamic handler",
                provider.getDynamicHandler(Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.TAILS_ALONE));
    }

    @Test
    public void nonWaterZoneHandlerIsNull() {
        assertNull("MGZ should have no dynamic handler",
                provider.getDynamicHandler(Sonic3kZoneIds.ZONE_MGZ, 0, PlayerCharacter.SONIC_AND_TAILS));
        assertNull("DEZ should have no dynamic handler",
                provider.getDynamicHandler(Sonic3kZoneIds.ZONE_DEZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    // =====================================================================
    // Threshold handler behavior tests
    // =====================================================================

    @Test
    public void hcz1BelowFirstThresholdSetsTarget0x0900() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 0, PlayerCharacter.SONIC_AND_TAILS);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0500);

        // cameraX=0 is below first threshold (0x8500)
        handler.update(state, 0, 0);
        assertEquals(0x0900, state.getTargetLevel());
    }

    @Test
    public void hcz2SonicBelowFirstThresholdSetsTarget0x3E00() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.SONIC_AND_TAILS);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0700);

        handler.update(state, 0, 0);
        assertEquals(0x3E00, state.getTargetLevel());
    }

    @Test
    public void hcz2KnucklesBelowFirstThresholdSetsTarget0x4100() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0700);

        handler.update(state, 0, 0);
        assertEquals(0x4100, state.getTargetLevel());
    }

    @Test
    public void lbz1BelowFirstThresholdSetsTarget0x0E00() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_LBZ, 0, PlayerCharacter.SONIC_AND_TAILS);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0AD8);

        handler.update(state, 0, 0);
        assertEquals(0x0E00, state.getTargetLevel());
    }

    @Test
    public void lbz2KnucklesBelowThresholdSetsTarget0x0D80() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0A80);

        handler.update(state, 0, 0);
        assertEquals(0x0D80, state.getTargetLevel());
    }

    // =====================================================================
    // AIZ2 state machine tests
    // =====================================================================

    @Test
    public void aiz2DropsWaterBeforeFirstThreshold() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        // Camera X before FIRST_THRESHOLD_X, target at INITIAL_LEVEL -> should drop
        handler.update(state, 0x1000, 0);
        assertEquals("Target should be set to DROP_LEVEL",
                Aiz2DynamicWaterHandler.DROP_LEVEL, state.getTargetLevel());
    }

    @Test
    public void aiz2DoesNotDropWhenAlreadyAtDropLevel() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.DROP_LEVEL);

        // Already at drop level, should not change
        handler.update(state, 0x1000, 0);
        assertEquals("Target should remain at DROP_LEVEL",
                Aiz2DynamicWaterHandler.DROP_LEVEL, state.getTargetLevel());
    }

    @Test
    public void aiz2RaisesWaterAfterTrigger() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        // Phase 1: Drop the water
        handler.update(state, 0x1000, 0);
        assertEquals(Aiz2DynamicWaterHandler.DROP_LEVEL, state.getTargetLevel());

        // Phase 2: Move past first threshold but before trigger - no raise yet
        handler.update(state, 0x2500, 0);
        assertEquals("Before trigger, target should remain DROP_LEVEL",
                Aiz2DynamicWaterHandler.DROP_LEVEL, state.getTargetLevel());

        // Phase 3: Move past trigger - water should rise back
        handler.update(state, Aiz2DynamicWaterHandler.TRIGGER_X, 0);
        assertEquals("After trigger, target should be INITIAL_LEVEL",
                Aiz2DynamicWaterHandler.INITIAL_LEVEL, state.getTargetLevel());
    }

    @Test
    public void aiz2ResetClearsTriggeredState() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        // Trigger the handler
        handler.update(state, 0x1000, 0); // Drop
        handler.update(state, Aiz2DynamicWaterHandler.TRIGGER_X, 0); // Trigger rise

        // Reset
        handler.reset();

        // After reset, a new state should behave as if fresh
        WaterSystem.DynamicWaterState freshState = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);
        handler.update(freshState, 0x1000, 0);
        assertEquals("After reset, drop should happen again",
                Aiz2DynamicWaterHandler.DROP_LEVEL, freshState.getTargetLevel());

        // And moving past first threshold but before trigger should NOT raise
        handler.update(freshState, 0x2500, 0);
        assertEquals("After reset, before trigger should not raise",
                Aiz2DynamicWaterHandler.DROP_LEVEL, freshState.getTargetLevel());
    }

    @Test
    public void aiz2DropSetsSpeed2() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        handler.update(state, 0x1000, 0);

        // Verify speed was set to 2 by stepping the state and checking movement rate
        // The state starts at INITIAL_LEVEL (0x0618) with target DROP_LEVEL (0x0528)
        // With speed=2, one update() call should move mean by 2 pixels
        int before = state.getMeanLevel();
        state.update();
        int after = state.getMeanLevel();
        assertEquals("Speed should be 2 (move 2 pixels per frame toward target)",
                2, before - after);
    }

    private static void assertInstanceOf(Class<?> expectedType, Object actual) {
        assertTrue("Expected " + expectedType.getSimpleName() + " but got " +
                        (actual == null ? "null" : actual.getClass().getSimpleName()),
                expectedType.isInstance(actual));
    }
}
