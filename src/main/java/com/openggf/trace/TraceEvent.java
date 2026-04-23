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
     * Pre-trace snapshot of a single ROM SST slot emitted by the Lua recorder
     * at the instant gameplay begins (before trace frame 0 is written).
     * The {@link #frame()} is -1 to keep it out of the frame-0 event bucket,
     * and {@link #slot()} is the ROM SST slot index (not the engine slot).
     */
    record ObjectStateSnapshot(int frame, int slot, int objectType,
                               RomObjectSnapshot fields)
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
                case "object_state_snapshot" -> new ObjectStateSnapshot(
                    frame,
                    node.has("slot") ? node.get("slot").asInt() : -1,
                    node.has("object_type")
                        ? Integer.parseInt(stripHexPrefix(node.get("object_type").asText()), 16)
                        : 0,
                    RomObjectSnapshot.fromJsonNode(node.get("fields"))
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
}


