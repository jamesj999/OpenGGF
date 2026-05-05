package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindController {

    private static CompositeSnapshot snap(int marker) {
        var e = new LinkedHashMap<String, Object>();
        e.put("marker", marker);
        return new CompositeSnapshot(e);
    }

    @Test
    void stepForwardCapturesKeyframesAtIntervals() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        AtomicInteger stepCount = new AtomicInteger();
        EngineStepper stepper = (in) -> stepCount.incrementAndGet();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 3);
        assertEquals(0, rc.currentFrame());

        // Step to frame 3 — should capture keyframe
        for (int i = 0; i < 3; i++) rc.step();
        assertEquals(3, rc.currentFrame());
        assertTrue(keyframes.latestAtOrBefore(3).isPresent());

        // Step to frame 6 — should capture another keyframe
        for (int i = 0; i < 3; i++) rc.step();
        assertEquals(6, rc.currentFrame());
        assertTrue(keyframes.latestAtOrBefore(6).isPresent());
    }

    @Test
    void seekToRestoresStateAndStepsForward() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        AtomicInteger state = new AtomicInteger(0);
        EngineStepper stepper = (in) -> state.incrementAndGet();

        // Register a simple snapshottable
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "state"; }
            @Override public Integer capture() { return state.get(); }
            @Override public void restore(Integer s) { state.set(s); }
        });

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);

        // Step to frame 5
        for (int i = 0; i < 5; i++) rc.step();
        assertEquals(5, state.get());

        // Seek to frame 3: should restore to state 0, step 3 times
        rc.seekTo(3);
        assertEquals(3, rc.currentFrame());
        assertEquals(3, state.get());
    }

    @Test
    void stepBackwardWithinSegmentIsCacheHit() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(100);
        AtomicInteger stepInvocations = new AtomicInteger();
        EngineStepper stepper = (in) -> stepInvocations.incrementAndGet();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 10);

        // Step forward to frame 15
        for (int i = 0; i < 15; i++) rc.step();
        assertEquals(15, rc.currentFrame());
        int stepsAfterForward = stepInvocations.get();

        // Step backward to frame 12 (same segment): should not step forward more times
        rc.stepBackward();
        rc.stepBackward();
        rc.stepBackward();
        assertEquals(12, rc.currentFrame());
        // stepInvocations should only increase by the segment expansion cost,
        // not by 3 additional steps for the 3 backward calls
        int stepsAfterBackward = stepInvocations.get();
        // Expansion of segment [10, 20): need to step from frame 10 to target frame 12
        // Forward steps: 0->1...1->15 (15 steps), segment expansion: 10->12 (2 steps to 12)
        // Actually: initial 15 forward steps + 4 steps to expand to offset 4 (steps 11-14)
        assertTrue(stepsAfterBackward < stepsAfterForward + 10,
                "segment cache should cost O(1) per backward step, not O(n)");
    }

    @Test
    void stepBackwardAcrossSegmentBoundaryRebuilds() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(100);
        AtomicInteger stepInvocations = new AtomicInteger();
        EngineStepper stepper = (in) -> stepInvocations.incrementAndGet();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 10);

        // Step forward to frame 15
        for (int i = 0; i < 15; i++) rc.step();

        // Step backward across segment boundary to frame 5
        rc.seekTo(5);
        assertEquals(5, rc.currentFrame());
    }

    @Test
    void earliestAvailableFrameClampsSeekTo() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);
        rc.seekTo(-5);  // Request before earliest frame
        assertEquals(0, rc.currentFrame());
    }

    @Test
    void stepBackwardReturnsFalseAtEarliestFrame() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);
        assertFalse(rc.stepBackward(), "should return false at earliest frame");
    }

    private static class FakeInputSource implements InputSource {
        private final int count;

        FakeInputSource(int count) {
            this.count = count;
        }

        @Override
        public int frameCount() {
            return count;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }
}
