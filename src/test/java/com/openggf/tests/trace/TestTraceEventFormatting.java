package com.openggf.tests.trace;

import com.openggf.trace.*;

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
    void parsesCharacterScopedEventsIntoCompactSummary() {
        TraceEvent near = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"object_near","character":"tails","slot":44,"type":"0x23","x":"0x024A","y":"0x0386","routine":"0x04","status":"0x00"}
                """.trim(),
                mapper);
        TraceEvent mode = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"mode_change","character":"tails","field":"rolling","from":1,"to":0}
                """.trim(),
                mapper);
        TraceEvent routine = TraceEvent.parseJsonLine(
                """
                {"frame":387,"vfc":388,"event":"routine_change","character":"tails","from":"0x02","to":"0x04","x":"0x0241","y":"0x038F"}
                """.trim(),
                mapper);

        assertEquals("near tails s44 0x23 @024A,0386 rtn=04 | tails mode rolling 1->0 | tails routine 02->04 @0241,038F",
                TraceEventFormatter.summariseFrameEvents(List.of(near, mode, routine)));
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

    @Test
    void parsesCheckpointAndZoneActStateIntoCompactSummary() {
        TraceEvent checkpoint = TraceEvent.parseJsonLine(
                """
                {"frame":1200,"event":"checkpoint","name":"aiz2_main_gameplay","actual_zone_id":0,"actual_act":1,"apparent_act":0,"game_mode":12,"notes":"resume strict replay"}
                """.trim(),
                mapper);
        TraceEvent zoneActState = TraceEvent.parseJsonLine(
                """
                {"frame":1200,"event":"zone_act_state","actual_zone_id":0,"actual_act":1,"apparent_act":0,"game_mode":12}
                """.trim(),
                mapper);

        assertEquals("cp aiz2_main_gameplay z=0 a=1 ap=0 gm=12 | zoneact z=0 a=1 ap=0 gm=12",
                TraceEventFormatter.summariseFrameEvents(List.of(checkpoint, zoneActState)));
    }

    @Test
    void summarisesCnzCageDiagnostics() {
        TraceEvent cageState = TraceEvent.parseJsonLine(
                """
                {"frame":2137,"vfc":2138,"event":"cage_state","slot":4,"x":"0x1300","y":"0x07C0","subtype":"0x28","status":"0x09","p1_phase":"0x80","p1_state":"0x00","p2_phase":"0xC0","p2_state":"0x01"}
                """.trim(),
                mapper);
        TraceEvent cageExecution = TraceEvent.parseJsonLine(
                """
                {"frame":2137,"vfc":2138,"event":"cage_execution","hits":[{"branch":"sub_338C4_entry","pc":"0x338C4","cage_addr":"0xB128","player_addr":"0xB04A","state_addr":"0xB15C","d5":"0x0800","d6":"0x04","state_byte":"0x01","player_status":"0x08","player_obj_ctrl":"0x42","cage_status":"0x09"}]}
                """.trim(),
                mapper);

        assertEquals("cage s4 @1300,07C0 sub=28 st=09 p1=80/00 p2=C0/01 | cageExec sub_338C4_entry@338C4 cage=B128 player=B04A d5=0800 d6=04 state=01 obj=42 cst=09",
                TraceEventFormatter.summariseFrameEvents(List.of(cageState, cageExecution)));
    }
}
