package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTraceEventFormatting {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesObjectNearAndSummarisesWithSlotAndPosition() {
        TraceEvent event = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"object_near","slot":44,"type":"0x23","x":"0x024A","y":"0x0386","routine":"0x04","status":"0x00"}
                """.trim(),
                mapper);

        assertTrue(event instanceof TraceEvent.ObjectNear);
        assertEquals("near s44 0x23 @024A,0386 rtn=04",
                TraceEventFormatter.summariseFrameEvents(List.of(event)));
    }

    @Test
    void parsesRoutineAndModeChangesIntoCompactSummary() {
        TraceEvent mode = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"mode_change","field":"rolling","from":1,"to":0}
                """.trim(),
                mapper);
        TraceEvent routine = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"routine_change","from":"0x02","to":"0x04","sonic_x":"0x0241","sonic_y":"0x038F"}
                """.trim(),
                mapper);

        assertEquals("mode rolling 1->0 | routine 02->04 @0241,038F",
                TraceEventFormatter.summariseFrameEvents(List.of(mode, routine)));
    }

    @Test
    void parsesObjectLifecycleEvents() {
        TraceEvent appeared = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"object_appeared","slot":45,"object_type":"0x37","x":"0x0240","y":"0x0390"}
                """.trim(),
                mapper);
        TraceEvent removed = TraceEvent.parseJsonLine(
                """
                {"frame":390,"vfc":391,"event":"object_removed","slot":44,"object_type":"0x23"}
                """.trim(),
                mapper);

        assertEquals("obj+ s45 0x37 @0240,0390 | obj- s44 0x23",
                TraceEventFormatter.summariseFrameEvents(List.of(appeared, removed)));
    }

    @Test
    void preservesArrayPayloadsForStateSnapshots() {
        TraceEvent event = TraceEvent.parseJsonLine(
                """
                {"frame":3101,"vfc":3093,"event":"slot_dump","slots":[[32,"0x46"],[75,"0x55"]]}
                """.trim(),
                mapper);

        assertTrue(event instanceof TraceEvent.StateSnapshot);
        TraceEvent.StateSnapshot snapshot = (TraceEvent.StateSnapshot) event;
        assertEquals("slot_dump", snapshot.fields().get("event"));
        assertEquals("[[32,\"0x46\"],[75,\"0x55\"]]", snapshot.fields().get("slots"));
    }
}
