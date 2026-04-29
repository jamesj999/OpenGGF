package com.openggf.tests.trace;
import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestTraceDataParsing {

    private static final Path SYNTHETIC_3FRAMES =
        Path.of("src/test/resources/traces/synthetic/basic_3frames");
    private static final Path SYNTHETIC_EXECUTION_V3 =
        Path.of("src/test/resources/traces/synthetic/execution_v3_2frames");

    @Test
    public void testMetadataLoading() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        TraceMetadata meta = data.metadata();

        assertEquals("s1", meta.game());
        assertEquals("ghz", meta.zone());
        assertEquals(1, meta.act());
        assertEquals(100, meta.bk2FrameOffset());
        assertEquals(3, meta.traceFrameCount());
        assertEquals((short) 0x0050, meta.startX());
        assertEquals((short) 0x03B0, meta.startY());
    }

    @Test
    void metadataParsesRecordedTeamWhenPresent() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-team");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "4.0-s2",
              "trace_schema": 4,
              "csv_version": 4,
              "rom_checksum": "",
              "notes": "",
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0060,0290,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0230,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);
        TraceMetadata meta = data.metadata();

        assertTrue(meta.hasRecordedTeam());
        assertEquals("sonic", meta.mainCharacter());
        assertEquals(List.of("tails"), meta.recordedSidekicks());
    }

    @Test
    void metadataParsesInitialRngSeedWhenPresent() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-rng");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 3171,
              "trace_frame_count": 1,
              "start_x": "0x0018",
              "start_y": "0x0600",
              "rng_seed": "0x89ABCDEF",
              "recording_date": "2026-04-23",
              "lua_script_version": "3.2-s3k",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "",
              "notes": ""
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0018,0600,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0500,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceMetadata meta = TraceData.load(dir).metadata();

        assertEquals(0x89ABCDEFL, meta.initialRngSeed());
    }

    @Test
    void parsesPlayerHistorySnapshot() {
        TraceEvent event = TraceEvent.parseJsonLine(
            """
            {"frame":-1,"event":"player_history_snapshot","history_pos":62,"x_history":[96,96,97],"y_history":[656,656,656],"input_history":[0,8,8],"status_history":[0,0,0]}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.PlayerHistorySnapshot.class, event);
        TraceEvent.PlayerHistorySnapshot snapshot = (TraceEvent.PlayerHistorySnapshot) event;
        assertEquals(62, snapshot.historyPos());
        assertArrayEquals(new short[]{96, 96, 97}, snapshot.xHistory());
        assertArrayEquals(new short[]{656, 656, 656}, snapshot.yHistory());
        assertArrayEquals(new short[]{0, 8, 8}, snapshot.inputHistory());
        assertArrayEquals(new byte[]{0, 0, 0}, snapshot.statusHistory());
    }

    @Test
    void parsesCpuStateSnapshot() {
        TraceEvent event = TraceEvent.parseJsonLine(
            """
            {"frame":-1,"event":"cpu_state_snapshot","character":"tails","control_counter":0,"respawn_counter":299,"cpu_routine":6,"target_x":"0x0613","target_y":"0x0264","interact_id":"0x10","jumping":1}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.CpuStateSnapshot.class, event);
        TraceEvent.CpuStateSnapshot snapshot = (TraceEvent.CpuStateSnapshot) event;
        assertEquals("tails", snapshot.character());
        assertEquals(0, snapshot.controlCounter());
        assertEquals(299, snapshot.respawnCounter());
        assertEquals(6, snapshot.cpuRoutine());
        assertEquals((short) 0x0613, snapshot.targetX());
        assertEquals((short) 0x0264, snapshot.targetY());
        assertEquals(0x10, snapshot.interactId());
        assertTrue(snapshot.jumping());
    }

    @Test
    void v5TraceParsesRecordedSidekickState() throws IOException {
        Path dir = Files.createTempDirectory("trace-v5-sidekick");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "5.0-s2",
              "trace_schema": 5,
              "csv_version": 5,
              "rom_checksum": "",
              "notes": "",
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0008,0060,0290,000C,0000,000C,00,0,0,0,0C00,0000,02,0000,0230,0000,00,0001,00,02AC,0000,1,0050,0288,0010,FFF0,0010,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);
        TraceFrame frame = data.getFrame(0);

        assertNotNull(frame.sidekick());
        assertTrue(frame.sidekick().present());
        assertEquals((short) 0x0050, frame.sidekick().x());
        assertEquals((short) 0x0288, frame.sidekick().y());
        assertEquals((short) 0x0010, frame.sidekick().xSpeed());
        assertEquals((short) -0x0010, frame.sidekick().ySpeed());
        assertEquals((byte) 0x08, frame.sidekick().angle());
        assertTrue(frame.sidekick().air());
        assertEquals(0x8000, frame.sidekick().xSub());
        assertEquals(0x4000, frame.sidekick().ySub());
        assertEquals(0x02, frame.sidekick().routine());
        assertEquals(0x0A, frame.sidekick().statusByte());
        assertEquals(0x03, frame.sidekick().standOnObj());
    }

    @Test
    void v5TraceExposesTrackedCharactersByName() throws IOException {
        Path dir = Files.createTempDirectory("trace-v5-characters");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "5.0-s2",
              "trace_schema": 5,
              "csv_version": 5,
              "rom_checksum": "",
              "notes": "",
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0008,0060,0290,000C,0000,000C,00,0,0,0,0C00,0000,02,0000,0230,0000,00,0001,04,02AC,0000,1,0050,0288,0010,FFF0,0010,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("sonic", "tails"), data.recordedCharacters());

        TraceCharacterState sonic = data.characterState(0, "sonic");
        TraceCharacterState tails = data.characterState(0, "tails");

        assertNotNull(sonic);
        assertNotNull(tails);
        assertEquals((short) 0x0060, sonic.x());
        assertEquals((short) 0x0290, sonic.y());
        assertEquals((short) 0x000C, sonic.xSpeed());
        assertEquals(0x0C00, sonic.xSub());
        assertEquals(0x04, sonic.standOnObj());
        assertEquals((short) 0x0050, tails.x());
        assertEquals((short) 0x0288, tails.y());
        assertEquals((short) 0x0010, tails.xSpeed());
        assertEquals(0x03, tails.standOnObj());
        assertNull(data.characterState(0, "knuckles"));
    }

    @Test
    void v6TraceParsesExplicitSonicAndTailsBlocks() throws IOException {
        Path dir = Files.createTempDirectory("trace-v6-characters");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "6.0-s2",
              "trace_schema": 6,
              "csv_version": 6,
              "rom_checksum": "",
              "notes": "",
              "characters": ["sonic", "tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,sonic_present,sonic_x,sonic_y,sonic_x_speed,sonic_y_speed,sonic_g_speed,sonic_angle,sonic_air,sonic_rolling,sonic_ground_mode,sonic_x_sub,sonic_y_sub,sonic_routine,sonic_status_byte,sonic_stand_on_obj,tails_present,tails_x,tails_y,tails_x_speed,tails_y_speed,tails_g_speed,tails_angle,tails_air,tails_rolling,tails_ground_mode,tails_x_sub,tails_y_sub,tails_routine,tails_status_byte,tails_stand_on_obj
            0000,0008,0000,0230,0000,0001,02AC,0000,1,0060,0290,000C,0000,000C,00,0,0,0,0C00,0000,02,00,04,1,0050,0288,0010,FFF0,0010,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);
        TraceMetadata meta = data.metadata();
        TraceFrame frame = data.getFrame(0);

        assertTrue(meta.hasRecordedTeam());
        assertEquals(List.of("sonic", "tails"), meta.recordedCharacters());
        assertEquals(List.of("tails"), meta.recordedSidekicks());

        TraceCharacterState sonic = data.characterState(0, "sonic");
        TraceCharacterState tails = data.characterState(0, "tails");

        assertNotNull(sonic);
        assertNotNull(tails);
        assertEquals((short) 0x0060, frame.x());
        assertEquals((short) 0x0290, frame.y());
        assertEquals((short) 0x000C, frame.xSpeed());
        assertEquals(0x0C00, frame.xSub());
        assertEquals(0x04, frame.standOnObj());
        assertEquals((short) 0x0060, sonic.x());
        assertEquals((short) 0x0290, sonic.y());
        assertEquals((short) 0x0050, tails.x());
        assertEquals((short) 0x0288, tails.y());
        assertEquals((short) 0x0010, tails.xSpeed());
        assertEquals(0x03, tails.standOnObj());
    }

    @Test
    public void testFrameCount() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        assertEquals(3, data.frameCount());
    }

    @Test
    public void testFrameParsing() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);

        TraceFrame frame0 = data.getFrame(0);
        assertEquals(0, frame0.frame());
        assertEquals(0, frame0.input());
        assertEquals((short) 0x0050, frame0.x());
        assertEquals((short) 0x03B0, frame0.y());
        assertEquals((short) 0, frame0.xSpeed());
        assertFalse(frame0.air());
        assertFalse(frame0.rolling());
        assertEquals(0, frame0.groundMode());

        TraceFrame frame1 = data.getFrame(1);
        assertEquals(0x0008, frame1.input());
        assertEquals((short) 0x0051, frame1.x());
        assertEquals((short) 0x000C, frame1.xSpeed());
        assertEquals((short) 0x000C, frame1.gSpeed());

        TraceFrame frame2 = data.getFrame(2);
        assertEquals((byte) 0x10, frame2.angle());
    }

    @Test
    public void testAuxEventsParsing() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);

        List<TraceEvent> frame0Events = data.getEventsForFrame(0);
        assertEquals(1, frame0Events.size());
        assertInstanceOf(TraceEvent.StateSnapshot.class, frame0Events.get(0));

        List<TraceEvent> frame2Events = data.getEventsForFrame(2);
        assertEquals(1, frame2Events.size());
        TraceEvent.ModeChange mc = (TraceEvent.ModeChange) frame2Events.get(0);
        assertEquals("angle", mc.field());
        assertEquals(0, mc.from());
        assertEquals(16, mc.to());
    }

    @Test
    void loadsAuxEventsFromGzipWhenPlainJsonlIsAbsent() throws IOException {
        Path dir = Files.createTempDirectory("trace-gz-aux");
        writeMinimalTraceFiles(dir);
        writeGzipString(dir.resolve("aux_state.jsonl.gz"), """
            {"frame":0,"event":"checkpoint","name":"gz_aux_loaded","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        List<TraceEvent> events = data.getEventsForFrame(0);
        assertEquals(1, events.size());
        TraceEvent.Checkpoint checkpoint = assertInstanceOf(TraceEvent.Checkpoint.class, events.getFirst());
        assertEquals("gz_aux_loaded", checkpoint.name());
    }

    @Test
    void plainAuxJsonlTakesPrecedenceOverGzipSidecar() throws IOException {
        Path dir = Files.createTempDirectory("trace-plain-aux-first");
        writeMinimalTraceFiles(dir);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"plain_aux_loaded","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);
        writeGzipString(dir.resolve("aux_state.jsonl.gz"), """
            {"frame":0,"event":"checkpoint","name":"gzip_aux_loaded","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        List<TraceEvent> events = data.getEventsForFrame(0);
        assertEquals(1, events.size());
        TraceEvent.Checkpoint checkpoint = assertInstanceOf(TraceEvent.Checkpoint.class, events.getFirst());
        assertEquals("plain_aux_loaded", checkpoint.name());
    }

    @Test
    void loadsPhysicsFramesFromGzipWhenPlainCsvIsAbsent() throws IOException {
        Path dir = Files.createTempDirectory("trace-gz-physics");
        writeMinimalMetadata(dir);
        writeGzipString(dir.resolve("physics.csv.gz"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            0001,0008,0081,03A0,000C,0000,000C,00,0,0,0,0C00,0000,02,0000,0000,0000,00,0002,00,0002,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(2, data.frameCount());
        assertEquals(0x0008, data.getFrame(1).input());
        assertEquals((short) 0x0081, data.getFrame(1).x());
    }

    @Test
    public void testNoEventsForFrame() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        List<TraceEvent> events = data.getEventsForFrame(1);
        assertTrue(events.isEmpty());
    }

    @Test
    public void testEventRange() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        List<TraceEvent> events = data.getEventsInRange(0, 2);
        assertEquals(2, events.size());
    }

    @Test
    public void testV22TraceStillParsesWithMissingExecutionCounters() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        TraceFrame frame0 = data.getFrame(0);

        assertEquals(-1, frame0.vblankCounter());
        assertEquals(-1, frame0.lagCounter());
        assertEquals(100, data.initialVblankCounter());
    }

    @Test
    public void testV3TraceParsesExecutionCounters() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_EXECUTION_V3);
        TraceMetadata meta = data.metadata();
        TraceFrame frame0 = data.getFrame(0);
        TraceFrame frame1 = data.getFrame(1);

        assertEquals(3, meta.traceSchema());
        assertEquals(0x3456, frame0.gameplayFrameCounter());
        assertEquals(0x0120, frame0.vblankCounter());
        assertEquals(0, frame0.lagCounter());
        assertEquals(0x3456, frame1.gameplayFrameCounter());
        assertEquals(0x0121, frame1.vblankCounter());
        assertEquals(0, frame1.lagCounter());
        assertEquals(0x0120, data.initialVblankCounter());
    }

    @Test
    void parsesCheckpointEvent() {
        TraceEvent event = TraceEvent.parseJsonLine(
            """
            {"frame":1200,"event":"checkpoint","name":"aiz2_main_gameplay","actual_zone_id":0,"actual_act":1,"apparent_act":0,"game_mode":12,"notes":"resume strict replay"}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.Checkpoint.class, event);
        TraceEvent.Checkpoint checkpoint = (TraceEvent.Checkpoint) event;
        assertEquals("aiz2_main_gameplay", checkpoint.name());
        assertEquals(0, checkpoint.actualZoneId());
        assertEquals(1, checkpoint.actualAct());
        assertEquals(0, checkpoint.apparentAct());
        assertEquals(12, checkpoint.gameMode());
        assertEquals("resume strict replay", checkpoint.notes());
    }

    @Test
    void parsesCharacterScopedAuxEvents() {
        TraceEvent modeEvent = TraceEvent.parseJsonLine(
            """
            {"frame":4325,"event":"mode_change","character":"tails","field":"on_object","from":0,"to":1}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());
        TraceEvent routineEvent = TraceEvent.parseJsonLine(
            """
            {"frame":4325,"event":"routine_change","character":"tails","from":"0x02","to":"0x04","x":"0x2360","y":"0x0318"}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());
        TraceEvent nearEvent = TraceEvent.parseJsonLine(
            """
            {"frame":4325,"event":"object_near","character":"tails","slot":24,"type":"0x06","x":"0x236E","y":"0x0320","routine":"0x04","status":"0x00"}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.ModeChange.class, modeEvent);
        assertInstanceOf(TraceEvent.RoutineChange.class, routineEvent);
        assertInstanceOf(TraceEvent.ObjectNear.class, nearEvent);

        TraceEvent.ModeChange mode = (TraceEvent.ModeChange) modeEvent;
        TraceEvent.RoutineChange routine = (TraceEvent.RoutineChange) routineEvent;
        TraceEvent.ObjectNear near = (TraceEvent.ObjectNear) nearEvent;

        assertEquals("tails", mode.character());
        assertEquals("on_object", mode.field());
        assertEquals("tails", routine.character());
        assertEquals((short) 0x2360, routine.x());
        assertEquals((short) 0x0318, routine.y());
        assertEquals("tails", near.character());
        assertEquals(24, near.slot());
    }

    @Test
    void latestCheckpointLookupReturnsNearestEarlierCheckpoint() throws IOException {
        Path dir = Files.createTempDirectory("s3k-trace");
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
            0001,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0002,00,0002,0000
            0002,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0003,00,0003,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"intro_begin","actual_zone_id":null,"actual_act":null,"apparent_act":null,"game_mode":12}
            {"frame":1,"event":"zone_act_state","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            {"frame":2,"event":"checkpoint","name":"gameplay_start","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);
        TraceEvent.Checkpoint checkpoint = data.latestCheckpointAtOrBefore(2);
        TraceEvent.ZoneActState state = data.latestZoneActStateAtOrBefore(2);

        assertEquals("gameplay_start", checkpoint.name());
        assertEquals(0, state.actualZoneId());
        assertEquals(0, state.actualAct());
        assertEquals(0, state.apparentAct());
    }

    @Test
    void reportsAdvertisedCageAuxSchemasMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-trace");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-29",
              "lua_script_version": "test",
              "trace_schema": 3,
              "csv_version": 4,
              "aux_schema_extras": ["cage_state_per_frame", "cage_execution_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"gameplay_start","actual_zone_id":3,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("cage_state_per_frame", "cage_execution_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    private static void writeMinimalTraceFiles(Path dir) throws IOException {
        writeMinimalMetadata(dir);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            """);
    }

    private static void writeMinimalMetadata(Path dir) throws IOException {
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-27",
              "lua_script_version": "test",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "test"
            }
            """);
    }

    private static void writeGzipString(Path path, String contents) throws IOException {
        try (OutputStream out = Files.newOutputStream(path);
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}

