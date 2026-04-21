package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestDivergenceReport {

    @Test
    void testEmptyReportHasNoErrors() {
        DivergenceReport report = new DivergenceReport(List.of());
        assertFalse(report.hasErrors());
        assertFalse(report.hasWarnings());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void testSingleErrorFrame() {
        FrameComparison frame = makeComparison(5, "air", Severity.ERROR, "0", "1");
        DivergenceReport report = new DivergenceReport(List.of(frame));

        assertTrue(report.hasErrors());
        assertEquals(1, report.errors().size());
        DivergenceGroup group = report.errors().get(0);
        assertEquals("air", group.field());
        assertEquals(5, group.startFrame());
        assertEquals(5, group.endFrame());
    }

    @Test
    void testConsecutiveFramesGrouped() {
        FrameComparison f1 = makeComparison(10, "x", Severity.ERROR, "0x0050", "0x0150");
        FrameComparison f2 = makeComparison(11, "x", Severity.ERROR, "0x0052", "0x0152");
        FrameComparison f3 = makeComparison(12, "x", Severity.ERROR, "0x0054", "0x0154");

        DivergenceReport report = new DivergenceReport(List.of(f1, f2, f3));
        assertEquals(1, report.errors().size());
        DivergenceGroup group = report.errors().get(0);
        assertEquals(10, group.startFrame());
        assertEquals(12, group.endFrame());
        assertEquals(3, group.frameSpan());
    }

    @Test
    void testWarningsAndErrorsSeparated() {
        FrameComparison f1 = makeComparison(0, "x", Severity.WARNING, "0x0050", "0x0051");
        FrameComparison f2 = makeComparison(1, "air", Severity.ERROR, "0", "1");

        DivergenceReport report = new DivergenceReport(List.of(f1, f2));
        assertTrue(report.hasErrors());
        assertTrue(report.hasWarnings());
        assertEquals(1, report.errors().size());
        assertEquals(1, report.warnings().size());
    }

    @Test
    void testSummaryOutput() {
        FrameComparison f1 = makeComparison(5, "air", Severity.ERROR, "0", "1");
        DivergenceReport report = new DivergenceReport(List.of(f1));
        String summary = report.toSummary();

        assertTrue(summary.contains("1 error"));
        assertTrue(summary.contains("frame 5"));
    }

    @Test
    void testContextWindow() {
        FrameComparison f0 = makeMatchComparison(0, (short) 0x50, (short) 0x3B0);
        FrameComparison f1 = makeMatchComparison(1, (short) 0x51, (short) 0x3B0);
        FrameComparison f2 = makeComparison(2, "air", Severity.ERROR, "0", "1");
        FrameComparison f3 = makeComparison(3, "air", Severity.ERROR, "0", "1");
        FrameComparison f4 = makeMatchComparison(4, (short) 0x54, (short) 0x3B0);

        DivergenceReport report = new DivergenceReport(List.of(f0, f1, f2, f3, f4));
        String context = report.getContextWindow(2, 2);

        assertNotNull(context);
        assertTrue(context.contains("Frame"));
    }

    @Test
    void testSummaryIncludesLatestCheckpointAndZoneActState() throws IOException {
        TraceData trace = createTraceDataWithAuxState();
        FrameComparison frame = makeComparison(2, "air", Severity.ERROR, "0", "1");

        DivergenceReport report = new DivergenceReport(List.of(frame), trace);
        String summary = report.toSummary();

        assertTrue(summary.contains("1 error"));
        assertTrue(summary.contains("Latest checkpoint: cp gameplay_start z=0 a=0 ap=0 gm=12"));
        assertTrue(summary.contains("Latest zone/act state: zoneact z=0 a=0 ap=0 gm=12"));
    }

    @Test
    void testJsonIncludesLatestCheckpointAndZoneActState() throws IOException {
        TraceData trace = createTraceDataWithAuxState();
        FrameComparison frame = makeComparison(2, "air", Severity.ERROR, "0", "1");

        DivergenceReport report = new DivergenceReport(List.of(frame), trace);
        String json = report.toJson();

        assertTrue(json.contains("\"latest_checkpoint\""));
        assertTrue(json.contains("\"name\" : \"gameplay_start\""));
        assertTrue(json.contains("\"latest_zone_act_state\""));
        assertTrue(json.contains("\"actual_zone_id\" : 0"));
        assertTrue(json.contains("\"actual_act\" : 0"));
        assertTrue(json.contains("\"apparent_act\" : 0"));
        assertTrue(json.contains("\"game_mode\" : 12"));
    }

    @Test
    void testContextWindowIncludesLatestCheckpointAndZoneActState() throws IOException {
        TraceData trace = createTraceDataWithAuxState();
        FrameComparison f0 = makeMatchComparison(0, (short) 0x50, (short) 0x3B0);
        FrameComparison f1 = makeMatchComparison(1, (short) 0x51, (short) 0x3B0);
        FrameComparison f2 = makeComparison(2, "air", Severity.ERROR, "0", "1");

        DivergenceReport report = new DivergenceReport(List.of(f0, f1, f2), trace);
        String context = report.getContextWindow(2, 1);

        assertTrue(context.contains("Latest checkpoint: cp gameplay_start z=0 a=0 ap=0 gm=12"));
        assertTrue(context.contains("Latest zone_act_state: zoneact z=0 a=0 ap=0 gm=12"));
    }

    @Test
    void testTraceBinderBuildReportUsesTraceMetadataContext() throws IOException {
        TraceData trace = createTraceDataWithAuxState();
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        binder.compareFrame(trace.getFrame(0),
            trace.getFrame(0).x(), trace.getFrame(0).y(),
            trace.getFrame(0).xSpeed(), trace.getFrame(0).ySpeed(), trace.getFrame(0).gSpeed(),
            trace.getFrame(0).angle(), trace.getFrame(0).air(), trace.getFrame(0).rolling(),
            trace.getFrame(0).groundMode());
        binder.compareFrame(trace.getFrame(1),
            trace.getFrame(1).x(), trace.getFrame(1).y(),
            trace.getFrame(1).xSpeed(), trace.getFrame(1).ySpeed(), trace.getFrame(1).gSpeed(),
            trace.getFrame(1).angle(), trace.getFrame(1).air(), trace.getFrame(1).rolling(),
            trace.getFrame(1).groundMode());
        binder.compareFrame(trace.getFrame(2),
            trace.getFrame(2).x(), trace.getFrame(2).y(),
            trace.getFrame(2).xSpeed(), trace.getFrame(2).ySpeed(), trace.getFrame(2).gSpeed(),
            trace.getFrame(2).angle(), true, trace.getFrame(2).rolling(),
            trace.getFrame(2).groundMode());

        DivergenceReport report = binder.buildReport(trace);

        assertTrue(report.toSummary().contains("Latest checkpoint: cp gameplay_start z=0 a=0 ap=0 gm=12"));
        assertTrue(report.toJson().contains("\"latest_checkpoint\""));
    }

    @Test
    void testLegacyConstructorContractRemainsWithoutTraceContext() {
        FrameComparison frame = makeComparison(2, "air", Severity.ERROR, "0", "1");

        DivergenceReport report = new DivergenceReport(List.of(frame));
        String summary = report.toSummary();
        String json = report.toJson();
        String context = report.getContextWindow(2, 0);

        assertFalse(summary.contains("checkpoint"));
        assertFalse(json.contains("\"latest_checkpoint\""));
        assertFalse(context.contains("Latest checkpoint:"));
    }

    private FrameComparison makeComparison(int frame, String field,
            Severity severity, String expected, String actual) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        fields.put(field, new FieldComparison(field, expected, actual, severity,
            severity == Severity.MATCH ? 0 : 1));
        return new FrameComparison(frame, fields);
    }

    private FrameComparison makeMatchComparison(int frame, short x, short y) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        String xHex = String.format("0x%04X", x & 0xFFFF);
        String yHex = String.format("0x%04X", y & 0xFFFF);
        fields.put("x", new FieldComparison("x", xHex, xHex, Severity.MATCH, 0));
        fields.put("y", new FieldComparison("y", yHex, yHex, Severity.MATCH, 0));
        fields.put("air", new FieldComparison("air", "0", "0", Severity.MATCH, 0));
        return new FrameComparison(frame, fields);
    }

    private TraceData createTraceDataWithAuxState() throws IOException {
        Path dir = Files.createTempDirectory("trace-report");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 3,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-21",
              "lua_script_version": "3.1-s3k",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            0001,0000,0081,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0002,00,0002,0000
            0002,0000,0082,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0003,00,0003,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"intro_begin","actual_zone_id":null,"actual_act":null,"apparent_act":null,"game_mode":12}
            {"frame":1,"event":"zone_act_state","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            {"frame":2,"event":"checkpoint","name":"gameplay_start","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);
        return TraceData.load(dir);
    }
}


