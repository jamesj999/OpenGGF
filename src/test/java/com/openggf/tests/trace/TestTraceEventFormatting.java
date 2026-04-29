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

    @Test
    void summarisesS3kTailsCpuNormalStepDiagnostics() {
        TraceEvent event = TraceEvent.parseJsonLine(
                """
                {"frame":3905,"vfc":3906,"event":"tails_cpu_normal_step","character":"tails","status":"0x00","object_control":"0x00","ground_vel":"0x000C","x_vel":"0x0000","delayed_stat":"0x08","delayed_input":"0x0800","loc_13dd0_branch":"leader_on_object","ctrl2_logical":"0x0808","ctrl2_held_logical":"0x08","path_pre_ground_vel":"0x000C","path_pre_x_vel":"0x0000","path_pre_status":"0x00","path_post_ground_vel":"0x000C","path_post_x_vel":"0x000C","path_post_status":"0x00"}
                """.trim(),
                mapper);

        assertTrue(event instanceof TraceEvent.TailsCpuNormalStep);
        assertEquals("tailsCpu status=00 obj=00 gv=000C xv=0000 stat=08 input=0800 branch=leader_on_object ctrl2=0808/08 post=000C,000C,00",
                TraceEventFormatter.summariseFrameEvents(List.of(event)));
    }

    @Test
    void summarisesS3kSidekickInteractObjectDiagnostics() {
        TraceEvent event = TraceEvent.parseJsonLine(
                """
                {"frame":4679,"vfc":4680,"event":"sidekick_interact_object","character":"tails","interact":"0xB128","interact_slot":4,"tails_render_flags":"0x80","tails_object_control":"0x03","tails_status":"0x08","tails_on_object":true,"object_code":"0x000220C2","object_routine":"0x02","object_status":"0x10","object_x":"0x2D95","object_y":"0x0420","object_subtype":"0x40","object_render_flags":"0x80","object_object_control":"0x00","object_active":true,"object_destroyed":false,"object_p1_standing":false,"object_p2_standing":true}
                """.trim(),
                mapper);

        assertTrue(event instanceof TraceEvent.SidekickInteractObjectState);
        assertEquals("tailsInteract slot=4 ptr=B128 obj=000220C2 rtn=02 st=10 @2D95,0420 sub=40 tails rf=80 obj=03 onObj=true objP2=true active=true destroyed=false",
                TraceEventFormatter.summariseFrameEvents(List.of(event)));
    }

    @Test
    void summarisesS3kAizBoundaryDiagnostics() {
        TraceEvent event = TraceEvent.parseJsonLine(
                """
                {"frame":4679,"vfc":4680,"event":"aiz_boundary_state","character":"tails","camera_min_x":"0x2D80","camera_max_x":"0x4000","camera_min_y":"0x0000","camera_max_y":"0x0300","tree_pre_x":"0x2D40","tree_pre_y":"0x0402","tree_pre_x_vel":"0x00F7","tree_pre_y_vel":"0x0198","tree_post_x":"0x2D95","tree_post_y":"0x040F","tree_post_x_vel":"0x0000","tree_post_y_vel":"0x0000","boundary_pre_x":"0x2D95","boundary_pre_y":"0x040F","boundary_pre_x_vel":"0x0000","boundary_pre_y_vel":"0x0000","boundary_post_x":"0x2D95","boundary_post_y":"0x040F","boundary_post_x_vel":"0x0000","boundary_post_y_vel":"0x0000","boundary_action":"none","post_move_x":"0x2D95","post_move_y":"0x040F","post_move_x_vel":"0x0000","post_move_y_vel":"0x0000"}
                """.trim(),
                mapper);

        assertTrue(event instanceof TraceEvent.AizBoundaryState);
        assertEquals("tailsAizBoundary cam=2D80/4000 y=0000/0300 tree=2D40,0402,00F7,0198->2D95,040F,0000,0000 boundary=none 2D95,040F,0000,0000->2D95,040F,0000,0000 post=2D95,040F,0000,0000",
                TraceEventFormatter.summariseFrameEvents(List.of(event)));
    }

    @Test
    void summarisesS3kAizTransitionFloorDiagnostics() {
        TraceEvent event = TraceEvent.parseJsonLine(
                """
                {"frame":5415,"vfc":1700,"event":"aiz_transition_floor_solid","slot":4,"object_status":"0x90","object_x":"0x2FB0","object_y":"0x03A0","p1_standing":false,"p2_standing":true,"p1_path":"first_reject","p2_path":"standing","p1_d1":"0x00A0","p1_d2":"0x0010","p1_d3":"0x0010","p1_status":"0x00","p1_object_control":"0x00","p1_y_radius":"0x13","p1_x":"0x2FCD","p1_y":"0x0379","p1_y_vel":"0x0000","p1_interact_slot":4,"p2_d1":"0x00A0","p2_d2":"0x0140","p2_d3":"0x0010","p2_status":"0x08","p2_object_control":"0x00","p2_y_radius":"0x10","p2_x":"0x2FB1","p2_y":"0x0380","p2_y_vel":"0x0000","p2_interact_slot":4}
                """.trim(),
                mapper);

        assertTrue(event instanceof TraceEvent.AizTransitionFloorSolidState);
        assertEquals("aizFloor s4 @2FB0,03A0 st=90 stand=false/true p1=first_reject y=0379 yr=13 st=00 obj=00 d=00A0/0010/0010 p2=standing y=0380 yr=10 st=08 obj=00 d=00A0/0140/0010",
                TraceEventFormatter.summariseFrameEvents(List.of(event)));
    }
}
