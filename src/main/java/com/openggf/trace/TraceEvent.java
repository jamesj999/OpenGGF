package com.openggf.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.level.objects.RomObjectSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An event from the auxiliary trace file (aux_state.jsonl).
 * Events are frame-keyed and only written for notable moments.
 */
public sealed interface TraceEvent {

    int frame();

    record ObjectAppeared(int frame, int slot, String objectType, short x, short y)
        implements TraceEvent {}

    record ObjectRemoved(int frame, int slot, String objectType)
        implements TraceEvent {}

    record ObjectNear(int frame, String character, int slot, String objectType, short x, short y,
                      String routine, String status)
        implements TraceEvent {}

    record StateSnapshot(int frame, Map<String, Object> fields)
        implements TraceEvent {}

    record CollisionEvent(int frame, String type, String objectType, short x, short y)
        implements TraceEvent {}

    record ModeChange(int frame, String character, String field, int from, int to)
        implements TraceEvent {}

    record RoutineChange(int frame, String character, String from, String to, short x, short y)
        implements TraceEvent {}

    record Checkpoint(int frame, String name, Integer actualZoneId, Integer actualAct,
                      Integer apparentAct, Integer gameMode, String notes)
        implements TraceEvent {}

    record ZoneActState(int frame, Integer actualZoneId, Integer actualAct,
                        Integer apparentAct, Integer gameMode)
        implements TraceEvent {}

    record PlayerHistorySnapshot(int frame, int historyPos, short[] xHistory,
                                 short[] yHistory, short[] inputHistory, byte[] statusHistory)
        implements TraceEvent {}

    /**
     * Pre-trace snapshot of CPU-side sidekick globals emitted by the Lua recorder
     * at the instant gameplay begins (before trace frame 0 is written).
     */
    record CpuStateSnapshot(int frame, String character, int controlCounter,
                            int respawnCounter, int cpuRoutine, short targetX,
                            short targetY, int interactId, boolean jumping)
        implements TraceEvent {}

    /**
     * Per-frame snapshot of the Tails CPU global block (sonic3k.constants.asm:618-626)
     * plus {@code Ctrl_2_logical} (held + pressed). Emitted on every recorded trace
     * frame by the v6+ recorder as diagnostic comparison context for the
     * engine's native {@link com.openggf.sprites.playable.SidekickCpuController}
     * state machine. Older traces (schema &lt; 6) never emit this event.
     *
     * <p>Field layout:
     * <ul>
     * <li>{@code interact} - {@code Tails_CPU_interact} (RAM address of last object Tails stood on)</li>
     * <li>{@code idleTimer} - {@code Tails_CPU_idle_timer} (counts down while Ctrl_2 idle)</li>
     * <li>{@code flightTimer} - {@code Tails_CPU_flight_timer} (counts up during respawn)</li>
     * <li>{@code cpuRoutine} - {@code Tails_CPU_routine} (current AI routine index)</li>
     * <li>{@code targetX}/{@code targetY} - flight steering targets</li>
     * <li>{@code autoFlyTimer} - {@code Tails_CPU_auto_fly_timer} (MGZ2 boss carry)</li>
     * <li>{@code autoJumpFlag} - {@code Tails_CPU_auto_jump_flag} (set when AI fires jump)</li>
     * <li>{@code ctrl2Held}/{@code ctrl2Pressed} - {@code Ctrl_2_held_logical}/{@code Ctrl_2_pressed_logical}</li>
     * </ul>
     */
    record CpuState(int frame, String character, int interact,
                    int idleTimer, int flightTimer, int cpuRoutine,
                    short targetX, short targetY, int autoFlyTimer,
                    int autoJumpFlag, int ctrl2Held, int ctrl2Pressed)
        implements TraceEvent {}

    /**
     * Per-frame snapshot of the ROM's {@code Oscillating_table}
     * (sonic3k.constants.asm:853, $42 bytes at $FFFFFE6E) plus the running
     * {@code Level_frame_counter}. Emitted on every recorded trace frame by
     * the v6.1+ S3K recorder so divergence diagnostics can ROM-verify global
     * oscillator phase, used by HoverFan, platforms, and other oscillating
     * objects. <strong>Diagnostic only:</strong> tests must NOT hydrate the
     * engine's {@code OscillationManager} from these values; the engine must
     * produce the correct oscillator phase natively from the same inputs as
     * the ROM. Older traces (recorder &lt; 6.1) never emit this event.
     */
    record OscillationState(int frame, int levelFrameCounter, byte[] oscTable)
        implements TraceEvent {}

    /**
     * Pre-trace snapshot of a single ROM SST slot emitted by the Lua recorder
     * at the instant gameplay begins (before trace frame 0 is written).
     * The {@link #frame()} is -1 to keep it out of the frame-0 event bucket,
     * and {@link #slot()} is the ROM SST slot index (not the engine slot).
     */
    record ObjectStateSnapshot(int frame, int slot, int objectType,
                               RomObjectSnapshot fields)
        implements TraceEvent {}

    /**
     * Per-frame snapshot of a CNZ wire cage object's per-player state bytes
     * plus its main status byte. Emitted by the v6.3+ S3K recorder for every
     * OST slot containing {@code Obj_CNZWireCage} ($0001365C) on every frame.
     *
     * <p>Field layout matches the cage's OST offsets:
     * <ul>
     * <li>{@code x}/{@code y} - cage object's world position</li>
     * <li>{@code subtype} - cage subtype byte at offset $2C (vertical extent)</li>
     * <li>{@code status} - cage status byte at offset $2A (bit 3 = p1_standing,
     *     bit 4 = p2_standing)</li>
     * <li>{@code p1Phase} - per-P1 phase byte at offset $30 (sin/cos angle)</li>
     * <li>{@code p1State} - per-P1 state byte at offset $31 (0 = capture-rotate,
     *     1 = released-cooldown one-frame, $10 = unmounted-cooldown 16f)</li>
     * <li>{@code p2Phase} - per-P2 phase byte at offset $34</li>
     * <li>{@code p2State} - per-P2 state byte at offset $35</li>
     * </ul>
     *
     * <p><strong>Diagnostic only:</strong> the comparator uses these to render
     * cage state in the divergence report; engine state must NEVER be hydrated
     * from these values.
     */
    record CageState(int frame, int slot, short x, short y, int subtype,
                     int status, int p1Phase, int p1State, int p2Phase, int p2State)
        implements TraceEvent {}

    /**
     * Per-frame summary of CNZ wire cage execution-hook hits emitted by the
     * v6.3+ S3K recorder. Each hit records which cage-routine branch the M68K
     * CPU entered ({@code sub_338C4} entry, {@code loc_339A0} mounted,
     * {@code loc_33ADE} cooldown, {@code loc_33B1E} continue, {@code loc_33B62}
     * release) along with M68K register state and the per-player state byte.
     *
     * <p>Used to root-cause CNZ1 trace F2222 release-cooldown divergence:
     * ROM-side execution path proves which of {@code loc_33ADE} (button-press
     * release) or {@code loc_33B1E} (continue ride) the cage actually took on
     * each frame for each player, given that the engine's
     * {@link com.openggf.game.sonic3k.objects.CnzWireCageObjectInstance} model
     * fires Tails's release one frame ahead of ROM at this trace frame.
     *
     * <p><strong>Diagnostic only:</strong> never hydrated into engine state.
     */
    record CageExecution(int frame, java.util.List<Hit> hits)
        implements TraceEvent {

        /**
         * Single execution-hook hit at one of the cage's branch entry points.
         */
        public record Hit(String branch, int pc, int cageAddr, int playerAddr,
                          int stateAddr, int d5, int d6, int stateByte,
                          int playerStatus, int playerObjCtrl, int cageStatus) {}
    }

    /**
     * Per-frame snapshot of a player's interact / object_control / status state.
     * The v6.3+ recorder fixes a long-standing bug where the field labelled
     * {@code object_control} was actually reading the {@code status} byte
     * (offset $2A); v6.3 splits these into separate JSON fields and parses
     * the real {@code object_control} byte (offset $2E).
     *
     * <p>Field layout:
     * <ul>
     * <li>{@code character} - "sonic" or "tails"</li>
     * <li>{@code interact} - {@code interact} field at offset $42 (RAM address
     *     of last linked object)</li>
     * <li>{@code interactSlot} - resolved OST slot index</li>
     * <li>{@code status} - status byte at offset $2A (Status_OnObj, In_Air, ...)</li>
     * <li>{@code statusSecondary} - status_secondary byte at offset $2B (shields,
     *     speed shoes, invincibility)</li>
     * <li>{@code objectControl} - object_control byte at offset $2E (cage ride
     *     bits 1+6, CPU-blocking bit 7, jumpable bit 0)</li>
     * </ul>
     *
     * <p><strong>Diagnostic only:</strong> never hydrated into engine state.
     */
    record InteractState(int frame, String character, int interact,
                         int interactSlot, int status, int statusSecondary,
                         int objectControl)
        implements TraceEvent {}

    /**
     * Per-frame summary of all M68K writes to a player's {@code x_vel} and
     * {@code y_vel} RAM addresses, captured by the v6.4+ S3K recorder via
     * {@code event.onmemorywrite} hooks. Each write records the M68K PC of
     * the writing instruction plus the resulting word value.
     *
     * <p>Currently emitted only for Tails ({@code character = "tails"}).
     * Used to root-cause the CNZ1 trace F3649 divergence where ROM Tails
     * {@code x_speed} jumps from -$48 to -$0A00 in a single frame; the
     * engine arrives at -$0A00 only at F3650 (a 1-frame phase shift).
     * The PC of the ROM instruction that writes -$0A00 pinpoints which
     * code path produced the value.
     *
     * <p><strong>Diagnostic only:</strong> never hydrated into engine state.
     */
    record VelocityWrite(int frame, String character,
                         java.util.List<Hit> xVelWrites,
                         java.util.List<Hit> yVelWrites)
        implements TraceEvent {

        /**
         * Single velocity-write hit. {@code pc} is the M68K program counter
         * at the writing instruction (post-fetch); {@code value} is the full
         * 16-bit word value of the velocity field after the write.
         */
        public record Hit(int pc, int value) {}
    }

    /**
     * Per-frame focused diagnostic for Tails CPU's normal follow step in S3K.
     * Captures the ROM state around {@code loc_13DD0}, the generated delayed
     * input word, and the state before/after {@code Tails_InputAcceleration_Path}
     * (docs/skdisasm/sonic3k.asm:26702-26705, 26717-26741, 27798-27805,
     * 28103-28122, 27957-28017). Diagnostic only: never hydrated into engine
     * state.
     */
    record TailsCpuNormalStep(int frame, String character, int status,
                              int objectControl, int groundVel, int xVel,
                              int delayedStat, int delayedInput,
                              String loc13dd0Branch, int ctrl2Logical,
                              int ctrl2HeldLogical, int pathPreGroundVel,
                              int pathPreXVel, int pathPreStatus,
                              int pathPostGroundVel, int pathPostXVel,
                              int pathPostStatus)
        implements TraceEvent {}

    /**
     * Per-frame focused diagnostic for the sidekick's interact object in S3K.
     * Captures the sidekick's raw interact pointer and the target object's key
     * bytes needed to diagnose AIZ object ride/grab handoffs
     * (docs/skdisasm/sonic3k.asm:28407-28451, 43758-43810, 46481-46549,
     * 46602-46631, 46709-46743, 46749-46789, 46929-46950). Diagnostic only:
     * never hydrated into engine state.
     */
    record SidekickInteractObjectState(int frame, String character, int interact,
                                       int interactSlot, int tailsRenderFlags,
                                       int tailsObjectControl, int tailsStatus,
                                       boolean tailsOnObject, int objectCode,
                                       int objectRoutine, int objectStatus,
                                       short objectX, short objectY,
                                       int objectSubtype, int objectRenderFlags,
                                       int objectObjectControl,
                                       boolean objectActive,
                                       boolean objectDestroyed,
                                       boolean objectP1Standing,
                                       boolean objectP2Standing)
        implements TraceEvent {}

    /**
     * Per-frame AIZ boundary/tree diagnostic for the sidekick around the
     * frame-order-sensitive path where {@code Process_Sprites} runs before
     * {@code DeformBgLayer}/{@code ScreenEvents}
     * (docs/skdisasm/sonic3k.asm:7884-7898), AIZ resize writes
     * {@code Camera_min_X_pos=$2D80} (docs/skdisasm/sonic3k.asm:38961-38974),
     * {@code AIZTree_SetPlayerPos} can reposition and zero velocity
     * (docs/skdisasm/sonic3k.asm:43776-43810), and
     * {@code Tails_Check_Screen_Boundaries} can clamp/despawn
     * (docs/skdisasm/sonic3k.asm:28407-28451). Diagnostic only: never
     * hydrated into engine state.
     */
    record AizBoundaryState(int frame, String character,
                            int cameraMinX, int cameraMaxX,
                            int cameraMinY, int cameraMaxY,
                            int treePreX, int treePreY,
                            int treePreXVel, int treePreYVel,
                            int treePostX, int treePostY,
                            int treePostXVel, int treePostYVel,
                            int boundaryPreX, int boundaryPreY,
                            int boundaryPreXVel, int boundaryPreYVel,
                            int boundaryPostX, int boundaryPostY,
                            int boundaryPostXVel, int boundaryPostYVel,
                            String boundaryAction,
                            int postMoveX, int postMoveY,
                            int postMoveXVel, int postMoveYVel)
        implements TraceEvent {}

    /**
     * Parse a single JSONL line into the appropriate TraceEvent subtype.
     * Unknown event types are returned as StateSnapshot with all fields preserved.
     */
    static TraceEvent parseJsonLine(String jsonLine, ObjectMapper mapper) {
        try {
            JsonNode node = mapper.readTree(jsonLine);
            int frame = node.get("frame").asInt();
            String event = node.has("event") ? node.get("event").asText() : "unknown";

            return switch (event) {
                case "object_appeared" -> new ObjectAppeared(
                    frame,
                    node.get("slot").asInt(),
                    node.get("object_type").asText(),
                    parseHexShort(node, "x"),
                    parseHexShort(node, "y")
                );
                case "object_removed" -> new ObjectRemoved(
                    frame,
                    node.get("slot").asInt(),
                    node.has("object_type") ? node.get("object_type").asText() : ""
                );
                case "object_near" -> new ObjectNear(
                    frame,
                    parseCharacter(node),
                    node.get("slot").asInt(),
                    node.has("type") ? node.get("type").asText() : "",
                    parseHexShort(node, "x"),
                    parseHexShort(node, "y"),
                    node.has("routine") ? node.get("routine").asText() : "",
                    node.has("status") ? node.get("status").asText() : ""
                );
                case "collision_event" -> new CollisionEvent(
                    frame,
                    node.get("type").asText(),
                    node.has("object_type") ? node.get("object_type").asText() : "",
                    parseHexShort(node, "x"),
                    parseHexShort(node, "y")
                );
                case "mode_change" -> new ModeChange(
                    frame,
                    parseCharacter(node),
                    node.get("field").asText(),
                    node.get("from").asInt(),
                    node.get("to").asInt()
                );
                case "routine_change" -> new RoutineChange(
                    frame,
                    parseCharacter(node),
                    node.has("from") ? stripHexPrefix(node.get("from").asText()) : "",
                    node.has("to") ? stripHexPrefix(node.get("to").asText()) : "",
                    parseHexShort(node, "x", "sonic_x", "tails_x"),
                    parseHexShort(node, "y", "sonic_y", "tails_y")
                );
                case "checkpoint" -> new Checkpoint(
                    frame,
                    node.has("name") ? node.get("name").asText() : "",
                    parseNullableInt(node, "actual_zone_id"),
                    parseNullableInt(node, "actual_act"),
                    parseNullableInt(node, "apparent_act"),
                    parseNullableInt(node, "game_mode"),
                    node.has("notes") && !node.get("notes").isNull()
                        ? node.get("notes").asText()
                        : null
                );
                case "zone_act_state" -> new ZoneActState(
                    frame,
                    parseNullableInt(node, "actual_zone_id"),
                    parseNullableInt(node, "actual_act"),
                    parseNullableInt(node, "apparent_act"),
                    parseNullableInt(node, "game_mode")
                );
                case "player_history_snapshot" -> new PlayerHistorySnapshot(
                    frame,
                    node.has("history_pos") ? node.get("history_pos").asInt() : 0,
                    parseShortArray(node.get("x_history")),
                    parseShortArray(node.get("y_history")),
                    parseShortArray(node.get("input_history")),
                    parseByteArray(node.get("status_history"))
                );
                case "cpu_state_snapshot" -> new CpuStateSnapshot(
                    frame,
                    node.has("character") ? node.get("character").asText() : "",
                    node.has("control_counter") ? node.get("control_counter").asInt() : 0,
                    node.has("respawn_counter") ? node.get("respawn_counter").asInt() : 0,
                    node.has("cpu_routine") ? node.get("cpu_routine").asInt() : 0,
                    parseHexShort(node, "target_x"),
                    parseHexShort(node, "target_y"),
                    parseHexInt(node, "interact_id"),
                    node.has("jumping") && node.get("jumping").asInt() != 0
                );
                case "cpu_state" -> new CpuState(
                    frame,
                    node.has("character") ? node.get("character").asText() : "",
                    parseHexInt(node, "interact"),
                    node.has("idle_timer") ? node.get("idle_timer").asInt() : 0,
                    node.has("flight_timer") ? node.get("flight_timer").asInt() : 0,
                    node.has("cpu_routine") ? node.get("cpu_routine").asInt() : 0,
                    parseHexShort(node, "target_x"),
                    parseHexShort(node, "target_y"),
                    node.has("auto_fly_timer") ? node.get("auto_fly_timer").asInt() : 0,
                    node.has("auto_jump_flag") ? node.get("auto_jump_flag").asInt() : 0,
                    parseHexInt(node, "ctrl2_held"),
                    parseHexInt(node, "ctrl2_pressed")
                );
                case "oscillation_state" -> new OscillationState(
                    frame,
                    node.has("level_frame_counter") ? node.get("level_frame_counter").asInt() : 0,
                    parseHexByteString(node.has("osc_table") ? node.get("osc_table").asText() : "")
                );
                case "object_state_snapshot" -> new ObjectStateSnapshot(
                    frame,
                    node.has("slot") ? node.get("slot").asInt() : -1,
                    node.has("object_type")
                        ? Integer.parseInt(stripHexPrefix(node.get("object_type").asText()), 16)
                        : 0,
                    RomObjectSnapshot.fromJsonNode(node.get("fields"))
                );
                case "cage_state" -> new CageState(
                    frame,
                    node.has("slot") ? node.get("slot").asInt() : -1,
                    parseHexShort(node, "x"),
                    parseHexShort(node, "y"),
                    parseHexInt(node, "subtype"),
                    parseHexInt(node, "status"),
                    parseHexInt(node, "p1_phase"),
                    parseHexInt(node, "p1_state"),
                    parseHexInt(node, "p2_phase"),
                    parseHexInt(node, "p2_state")
                );
                case "cage_execution" -> {
                    java.util.List<CageExecution.Hit> hits = new java.util.ArrayList<>();
                    JsonNode hitsNode = node.get("hits");
                    if (hitsNode != null && hitsNode.isArray()) {
                        for (JsonNode h : hitsNode) {
                            hits.add(new CageExecution.Hit(
                                h.has("branch") ? h.get("branch").asText() : "",
                                parseHexInt(h, "pc"),
                                parseHexInt(h, "cage_addr"),
                                parseHexInt(h, "player_addr"),
                                parseHexInt(h, "state_addr"),
                                parseHexInt(h, "d5"),
                                parseHexInt(h, "d6"),
                                parseHexInt(h, "state_byte"),
                                parseHexInt(h, "player_status"),
                                parseHexInt(h, "player_obj_ctrl"),
                                parseHexInt(h, "cage_status")
                            ));
                        }
                    }
                    yield new CageExecution(frame, hits);
                }
                case "interact_state" -> new InteractState(
                    frame,
                    parseCharacter(node),
                    parseHexInt(node, "interact"),
                    node.has("interact_slot") ? node.get("interact_slot").asInt() : 0,
                    parseHexInt(node, "status"),
                    parseHexInt(node, "status_secondary"),
                    parseHexInt(node, "object_control")
                );
                case "velocity_write" -> {
                    java.util.List<VelocityWrite.Hit> xWrites = new java.util.ArrayList<>();
                    JsonNode xWritesNode = node.get("x_vel_writes");
                    if (xWritesNode != null && xWritesNode.isArray()) {
                        for (JsonNode h : xWritesNode) {
                            xWrites.add(new VelocityWrite.Hit(
                                parseHexInt(h, "pc"),
                                parseHexInt(h, "val")
                            ));
                        }
                    }
                    java.util.List<VelocityWrite.Hit> yWrites = new java.util.ArrayList<>();
                    JsonNode yWritesNode = node.get("y_vel_writes");
                    if (yWritesNode != null && yWritesNode.isArray()) {
                        for (JsonNode h : yWritesNode) {
                            yWrites.add(new VelocityWrite.Hit(
                                parseHexInt(h, "pc"),
                                parseHexInt(h, "val")
                            ));
                        }
                    }
                    yield new VelocityWrite(frame, parseCharacter(node), xWrites, yWrites);
                }
                case "tails_cpu_normal_step" -> new TailsCpuNormalStep(
                    frame,
                    parseCharacter(node),
                    parseHexInt(node, "status"),
                    parseHexInt(node, "object_control"),
                    parseHexInt(node, "ground_vel"),
                    parseHexInt(node, "x_vel"),
                    parseHexInt(node, "delayed_stat"),
                    parseHexInt(node, "delayed_input"),
                    node.has("loc_13dd0_branch") ? node.get("loc_13dd0_branch").asText() : "",
                    parseHexInt(node, "ctrl2_logical"),
                    parseHexInt(node, "ctrl2_held_logical"),
                    parseHexInt(node, "path_pre_ground_vel"),
                    parseHexInt(node, "path_pre_x_vel"),
                    parseHexInt(node, "path_pre_status"),
                    parseHexInt(node, "path_post_ground_vel"),
                    parseHexInt(node, "path_post_x_vel"),
                    parseHexInt(node, "path_post_status")
                );
                case "sidekick_interact_object" -> new SidekickInteractObjectState(
                    frame,
                    parseCharacter(node),
                    parseHexInt(node, "interact"),
                    node.has("interact_slot") ? node.get("interact_slot").asInt() : 0,
                    parseHexInt(node, "tails_render_flags"),
                    parseHexInt(node, "tails_object_control"),
                    parseHexInt(node, "tails_status"),
                    node.has("tails_on_object") && node.get("tails_on_object").asBoolean(),
                    parseHexInt(node, "object_code"),
                    parseHexInt(node, "object_routine"),
                    parseHexInt(node, "object_status"),
                    parseHexShort(node, "object_x"),
                    parseHexShort(node, "object_y"),
                    parseHexInt(node, "object_subtype"),
                    parseHexInt(node, "object_render_flags"),
                    parseHexInt(node, "object_object_control"),
                    node.has("object_active") && node.get("object_active").asBoolean(),
                    node.has("object_destroyed") && node.get("object_destroyed").asBoolean(),
                    node.has("object_p1_standing") && node.get("object_p1_standing").asBoolean(),
                    node.has("object_p2_standing") && node.get("object_p2_standing").asBoolean()
                );
                case "aiz_boundary_state" -> new AizBoundaryState(
                    frame,
                    parseCharacter(node),
                    parseHexInt(node, "camera_min_x"),
                    parseHexInt(node, "camera_max_x"),
                    parseHexInt(node, "camera_min_y"),
                    parseHexInt(node, "camera_max_y"),
                    parseHexInt(node, "tree_pre_x"),
                    parseHexInt(node, "tree_pre_y"),
                    parseHexInt(node, "tree_pre_x_vel"),
                    parseHexInt(node, "tree_pre_y_vel"),
                    parseHexInt(node, "tree_post_x"),
                    parseHexInt(node, "tree_post_y"),
                    parseHexInt(node, "tree_post_x_vel"),
                    parseHexInt(node, "tree_post_y_vel"),
                    parseHexInt(node, "boundary_pre_x"),
                    parseHexInt(node, "boundary_pre_y"),
                    parseHexInt(node, "boundary_pre_x_vel"),
                    parseHexInt(node, "boundary_pre_y_vel"),
                    parseHexInt(node, "boundary_post_x"),
                    parseHexInt(node, "boundary_post_y"),
                    parseHexInt(node, "boundary_post_x_vel"),
                    parseHexInt(node, "boundary_post_y_vel"),
                    node.has("boundary_action") ? node.get("boundary_action").asText() : "",
                    parseHexInt(node, "post_move_x"),
                    parseHexInt(node, "post_move_y"),
                    parseHexInt(node, "post_move_x_vel"),
                    parseHexInt(node, "post_move_y_vel")
                );
                default -> {
                    // state_snapshot or unknown: preserve all fields as map
                    Map<String, Object> fields = new LinkedHashMap<>();
                    node.fields().forEachRemaining(
                        entry -> fields.put(entry.getKey(), nodeToValue(entry.getValue()))
                    );
                    yield new StateSnapshot(frame, fields);
                }
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSONL line: " + jsonLine, e);
        }
    }

    private static short parseHexShort(JsonNode node, String... fields) {
        for (String field : fields) {
            if (!node.has(field)) {
                continue;
            }
            String hex = stripHexPrefix(node.get(field).asText());
            return (short) Integer.parseInt(hex, 16);
        }
        return 0;
    }

    private static String parseCharacter(JsonNode node) {
        if (!node.has("character") || node.get("character").isNull()) {
            return null;
        }
        String value = node.get("character").asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer parseNullableInt(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static int parseHexInt(JsonNode node, String field) {
        if (!node.has(field)) {
            return 0;
        }
        String hex = stripHexPrefix(node.get(field).asText());
        return Integer.parseInt(hex, 16);
    }

    private static String stripHexPrefix(String value) {
        return value.replace("0x", "");
    }

    private static Object nodeToValue(JsonNode node) {
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isDouble()) return node.asDouble();
        if (node.isArray() || node.isObject()) return node.toString();
        return node.asText();
    }

    private static short[] parseShortArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new short[0];
        }
        short[] values = new short[node.size()];
        for (int i = 0; i < node.size(); i++) {
            values[i] = (short) node.get(i).asInt();
        }
        return values;
    }

    private static byte[] parseByteArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new byte[0];
        }
        byte[] values = new byte[node.size()];
        for (int i = 0; i < node.size(); i++) {
            values[i] = (byte) node.get(i).asInt();
        }
        return values;
    }

    /**
     * Parses a contiguous hex string (e.g. "00FF1234...") into a byte array.
     * Used by {@link OscillationState} to decode the recorder's compact
     * representation of {@code Oscillating_table} bytes.
     */
    private static byte[] parseHexByteString(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() & 1) != 0) {
            return new byte[0];
        }
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}


