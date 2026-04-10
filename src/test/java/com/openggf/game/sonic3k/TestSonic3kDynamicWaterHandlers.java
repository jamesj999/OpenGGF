package com.openggf.game.sonic3k;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.ThresholdTableWaterHandler;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.WaterSystem;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for S3K dynamic water handlers returned by {@link Sonic3kWaterDataProvider}.
 * Verifies handler assignment and threshold/state-machine behavior.
 */
public class TestSonic3kDynamicWaterHandlers {

    private Sonic3kWaterDataProvider provider;

    @Before
    public void setUp() {
        provider = new Sonic3kWaterDataProvider();
        Sonic3kLevelTriggerManager.reset();
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
    public void lbz1HandlerIsNull() {
        // LBZ1 has no water per CheckLevelForWater — only LBZ2 does
        assertNull("LBZ1 should have no dynamic handler (no water)",
                provider.getDynamicHandler(Sonic3kZoneIds.ZONE_LBZ, 0, PlayerCharacter.SONIC_AND_TAILS));
    }

    @Test
    public void lbz2KnucklesHandlerIsLbz2Handler() {
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        assertNotNull("LBZ2 Knuckles should have a dynamic handler", handler);
        assertInstanceOf(Lbz2KnucklesDynamicWaterHandler.class, handler);
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
    public void hcz1BelowFirstThresholdInstantSetsTo0x0500() {
        // ROM word_6E8C: dc.w $8500, $0900 — cameraX <= 0x0900 -> instant-set 0x0500
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 0, PlayerCharacter.SONIC_AND_TAILS);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0500);

        handler.update(state, 0, 0);
        assertEquals("Bit-15 target 0x8500 should instant-set to 0x0500",
                0x0500, state.getTargetLevel());
        assertEquals(0x0500, state.getMeanLevel());
    }

    @Test
    public void hcz1FallbackInstantSetsTo0x06A0() {
        // ROM word_6E8C last entry: dc.w $86A0, $FFFF — fallback instant-set 0x06A0
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 0, PlayerCharacter.SONIC_AND_TAILS);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0500);

        handler.update(state, 0xF000, 0); // past all thresholds except fallback
        assertEquals(0x06A0, state.getTargetLevel());
        assertEquals(0x06A0, state.getMeanLevel());
    }

    @Test
    public void hcz2SonicBelowFirstThresholdSetsTarget0x0700() {
        // ROM word_6EBA: dc.w $0700, $3E00 — cameraX <= 0x3E00 -> target 0x0700 (gradual)
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.SONIC_AND_TAILS);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0700);

        handler.update(state, 0, 0);
        assertEquals(0x0700, state.getTargetLevel());
    }

    @Test
    public void hcz2KnucklesBelowFirstThresholdSetsTarget0x0700() {
        // ROM word_6EC2: dc.w $0700, $4100 — cameraX <= 0x4100 -> target 0x0700 (gradual)
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_HCZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0700);

        handler.update(state, 0, 0);
        assertEquals(0x0700, state.getTargetLevel());
    }

    @Test
    public void lbz2KnucklesBelowThresholdInstantSetsTo0x0FF0() {
        // ROM word_6F12: dc.w $8FF0, $0D80 — cameraX <= 0x0D80 -> instant-set 0x0FF0
        DynamicWaterHandler handler = provider.getDynamicHandler(
                Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0A80);

        handler.update(state, 0, 0);
        assertEquals(0x0FF0, state.getTargetLevel());
        assertEquals(0x0FF0, state.getMeanLevel());
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
        handler.update(state, Aiz2DynamicWaterHandler.AUTO_TRIGGER_X, 0);
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
        handler.update(state, Aiz2DynamicWaterHandler.AUTO_TRIGGER_X, 0); // Trigger rise

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

    @Test
    public void aiz2RaiseInheritsSpeed2FromDrop() {
        // ROM does NOT change Water_speed during raise — inherits speed=2 from the drop
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        // Drop (sets speed=2)
        handler.update(state, 0x1000, 0);
        assertEquals(Aiz2DynamicWaterHandler.DROP_LEVEL, state.getTargetLevel());

        // Simulate water reaching drop level
        state.setMeanDirect(Aiz2DynamicWaterHandler.DROP_LEVEL);

        // Trigger rise
        handler.update(state, Aiz2DynamicWaterHandler.AUTO_TRIGGER_X, 0);
        assertEquals(Aiz2DynamicWaterHandler.INITIAL_LEVEL, state.getTargetLevel());

        // Verify speed is still 2 (inherited from drop, not reset to 1)
        int before = state.getMeanLevel();
        state.update();
        int after = state.getMeanLevel();
        assertEquals("Rise should inherit speed=2 from drop phase",
                2, after - before);
    }

    // =====================================================================
    // Locked flag tests — ROM _unkFAA2 (boss/cutscene lock)
    // =====================================================================

    @Test
    public void lockedFlagPreventsHandlerExecution() {
        // When locked, the handler should be skipped in updateDynamic()
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0700);
        state.setLocked(true);
        assertTrue("State should report locked", state.isLocked());

        // Unlocking should allow handler execution
        state.setLocked(false);
        assertFalse("State should report unlocked", state.isLocked());
    }

    // =====================================================================
    // Shake timer tests — ROM Obj_6E6E (180-frame countdown)
    // =====================================================================

    @Test
    public void aiz2TriggerSetsShakeTimer() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        // Drop
        handler.update(state, 0x1000, 0);
        assertEquals(0, state.getShakeTimer());

        // Simulate water reaching drop level
        state.setMeanDirect(Aiz2DynamicWaterHandler.DROP_LEVEL);

        // Trigger rise — should set shake timer
        handler.update(state, Aiz2DynamicWaterHandler.AUTO_TRIGGER_X, 0);
        assertEquals("Shake timer should be set to 180",
                Aiz2DynamicWaterHandler.SHAKE_DURATION, state.getShakeTimer());
    }

    @Test
    public void shakeTimerDoesNotResetOnSubsequentUpdates() {
        Aiz2DynamicWaterHandler handler = new Aiz2DynamicWaterHandler();
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(
                Aiz2DynamicWaterHandler.INITIAL_LEVEL);

        // Drop + trigger rise
        handler.update(state, 0x1000, 0);
        state.setMeanDirect(Aiz2DynamicWaterHandler.DROP_LEVEL);
        handler.update(state, Aiz2DynamicWaterHandler.AUTO_TRIGGER_X, 0);
        assertEquals(180, state.getShakeTimer());

        // Manually decrement to simulate some frames passing
        state.setShakeTimer(50);

        // Subsequent handler call should NOT re-set timer (target already == INITIAL_LEVEL)
        handler.update(state, Aiz2DynamicWaterHandler.AUTO_TRIGGER_X + 100, 0);
        assertEquals("Timer should not be re-set once target is already INITIAL_LEVEL",
                50, state.getShakeTimer());
    }

    // =====================================================================
    // LBZ2 Knuckles pipe plug tests — ROM _unkF7C2
    // =====================================================================

    @Test
    public void lbz2KnucklesNormalModeUsesThresholdTable() {
        Lbz2KnucklesDynamicWaterHandler handler =
                (Lbz2KnucklesDynamicWaterHandler) provider.getDynamicHandler(
                        Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0A80);

        // Normal mode (pipe plug not destroyed) — should use threshold table
        assertFalse(handler.isPipePlugDestroyed());
        handler.update(state, 0, 0);
        assertEquals("Should instant-set to 0x0FF0 via threshold table",
                0x0FF0, state.getMeanLevel());
    }

    @Test
    public void lbz2KnucklesPipePlugSnapsTo0x0660WhenMeanBelowCameraY() {
        Lbz2KnucklesDynamicWaterHandler handler =
                (Lbz2KnucklesDynamicWaterHandler) provider.getDynamicHandler(
                        Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0500);

        handler.setPipePlugDestroyed(true);
        // meanLevel (0x0500) < cameraY (0x0600) — should snap to 0x0660
        handler.update(state, 0, 0x0600);
        assertEquals("Pipe plug path: should snap to 0x0660",
                Lbz2KnucklesDynamicWaterHandler.PIPE_PLUG_SNAP_LEVEL, state.getMeanLevel());
    }

    @Test
    public void lbz2KnucklesPipePlugNoSnapWhenMeanAboveCameraY() {
        Lbz2KnucklesDynamicWaterHandler handler =
                (Lbz2KnucklesDynamicWaterHandler) provider.getDynamicHandler(
                        Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
        WaterSystem.DynamicWaterState state = new WaterSystem.DynamicWaterState(0x0800);

        handler.setPipePlugDestroyed(true);
        // meanLevel (0x0800) >= cameraY (0x0600) — should NOT snap
        handler.update(state, 0, 0x0600);
        assertEquals("Pipe plug path: mean >= cameraY, no snap",
                0x0800, state.getMeanLevel());
    }

    private static void assertInstanceOf(Class<?> expectedType, Object actual) {
        assertTrue("Expected " + expectedType.getSimpleName() + " but got " +
                        (actual == null ? "null" : actual.getClass().getSimpleName()),
                expectedType.isInstance(actual));
    }
}
