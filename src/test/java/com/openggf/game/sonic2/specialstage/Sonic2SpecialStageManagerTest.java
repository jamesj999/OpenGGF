package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageConstants.*;
import com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug;

/**
 * Unit tests for Sonic2SpecialStageManager.
 * These tests don't require the ROM file.
 */
public class Sonic2SpecialStageManagerTest {

    @Test
    public void testManagerConstruction() {
        Sonic2SpecialStageManager instance1 = assertDoesNotThrow(() -> new Sonic2SpecialStageManager(),
                "Default construction should not require configured EngineServices");
        Sonic2SpecialStageManager instance2 = assertDoesNotThrow(() -> new Sonic2SpecialStageManager(),
                "Repeated default construction should stay bootstrap-safe");

        assertNotNull(instance1, "Manager instance should not be null");
        assertNotSame(instance1, instance2, "Separate constructions should yield separate instances");
    }

    @Test
    public void testInjectedDebugConstructionDoesNotRequireConfiguredEngineServices() {
        Sonic2SpecialStageSpriteDebug debug = new Sonic2SpecialStageSpriteDebug();

        Sonic2SpecialStageManager manager = assertDoesNotThrow(() -> new Sonic2SpecialStageManager(debug),
                "Injected debug construction should not require configured EngineServices");

        assertNotNull(manager, "Manager instance should not be null");
    }

    @Test
    public void testNotInitializedByDefault() {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        manager.reset();
        assertFalse(manager.isInitialized(), "Manager should not be initialized by default");
    }

    @Test
    public void testH32Dimensions() {
        assertEquals(256, Sonic2SpecialStageManager.H32_WIDTH, "H32 width should be 256 pixels");
        assertEquals(224, Sonic2SpecialStageManager.H32_HEIGHT, "H32 height should be 224 pixels");
    }

    @Test
    public void testSegmentAnimationLengths() {
        assertEquals(24, ANIM_TURN_THEN_RISE.length, "SEGMENT_TURN_THEN_RISE animation should have 24 frames");
        assertEquals(24, ANIM_TURN_THEN_DROP.length, "SEGMENT_TURN_THEN_DROP animation should have 24 frames");
        assertEquals(12, ANIM_TURN_THEN_STRAIGHT.length, "SEGMENT_TURN_THEN_STRAIGHT animation should have 12 frames");
        assertEquals(16, ANIM_STRAIGHT.length, "SEGMENT_STRAIGHT animation should have 16 frames");
        assertEquals(11, ANIM_STRAIGHT_THEN_TURN.length, "SEGMENT_STRAIGHT_THEN_TURN animation should have 11 frames");
    }

    @Test
    public void testAnimBaseDurations() {
        assertEquals(60, ANIM_BASE_DURATIONS[0], "First duration should be 60");
        assertEquals(30, ANIM_BASE_DURATIONS[1], "Second duration should be 30");
        assertEquals(15, ANIM_BASE_DURATIONS[2], "Third duration should be 15");
        assertEquals(10, ANIM_BASE_DURATIONS[3], "Fourth duration should be 10");
        assertEquals(8, ANIM_BASE_DURATIONS[4], "Fifth duration should be 8");
        assertEquals(6, ANIM_BASE_DURATIONS[5], "Sixth duration should be 6");
        assertEquals(5, ANIM_BASE_DURATIONS[6], "Seventh duration should be 5");
        assertEquals(0, ANIM_BASE_DURATIONS[7], "Eighth duration should be 0");
    }

    @Test
    public void testSegmentFrameCounts() {
        assertEquals(5, SEGMENT_FRAME_COUNTS.length, "Should have 5 segment types");
        assertEquals(24, SEGMENT_FRAME_COUNTS[SEGMENT_TURN_THEN_RISE], "TurnThenRise should have 24 frames");
        assertEquals(24, SEGMENT_FRAME_COUNTS[SEGMENT_TURN_THEN_DROP], "TurnThenDrop should have 24 frames");
        assertEquals(12, SEGMENT_FRAME_COUNTS[SEGMENT_TURN_THEN_STRAIGHT], "TurnThenStraight should have 12 frames");
        assertEquals(16, SEGMENT_FRAME_COUNTS[SEGMENT_STRAIGHT], "Straight should have 16 frames");
        assertEquals(11, SEGMENT_FRAME_COUNTS[SEGMENT_STRAIGHT_THEN_TURN], "StraightThenTurn should have 11 frames");
    }

    @Test
    public void testTrackFrameOffsets() {
        assertEquals(TRACK_FRAME_COUNT, TRACK_FRAME_OFFSETS.length, "Should have 56 track frame offsets");
        assertEquals(TRACK_FRAME_COUNT, TRACK_FRAME_SIZES.length, "Should have 56 track frame sizes");

        assertEquals(0x0CA904, TRACK_FRAME_OFFSETS[0], "First frame offset should be 0x0CA904");
        assertEquals(1188, TRACK_FRAME_SIZES[0], "First frame size should be 1188");

        long expectedEnd = TRACK_FRAME_OFFSETS[TRACK_FRAME_COUNT - 1] + TRACK_FRAME_SIZES[TRACK_FRAME_COUNT - 1];
        assertEquals(TRACK_FRAMES_END, expectedEnd, "Last frame should end at TRACK_FRAMES_END");
    }
}


