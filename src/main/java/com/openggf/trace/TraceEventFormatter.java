package com.openggf.trace;

import java.util.ArrayList;
import java.util.List;

public final class TraceEventFormatter {

    private TraceEventFormatter() {
    }

    public static String summariseFrameEvents(List<TraceEvent> events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (TraceEvent event : events) {
            String summary = summarise(event);
            if (!summary.isEmpty()) {
                parts.add(summary);
            }
        }
        return String.join(" | ", parts);
    }

    private static String summarise(TraceEvent event) {
        return switch (event) {
            case TraceEvent.ObjectAppeared appeared ->
                    String.format("obj+ s%d %s @%04X,%04X",
                            appeared.slot(),
                            appeared.objectType(),
                            appeared.x() & 0xFFFF,
                            appeared.y() & 0xFFFF);
            case TraceEvent.ObjectRemoved removed ->
                    String.format("obj- s%d %s", removed.slot(), removed.objectType());
            case TraceEvent.ObjectNear near -> {
                String base = String.format("near %ss%d %s @%04X,%04X",
                        characterPrefix(near.character()),
                        near.slot(),
                        near.objectType(),
                        near.x() & 0xFFFF,
                        near.y() & 0xFFFF);
                yield near.routine().isEmpty() ? base : base + " rtn=" + stripHexPrefix(near.routine());
            }
            case TraceEvent.ModeChange mode ->
                    String.format("%smode %s %d->%d",
                            characterPrefix(mode.character()),
                            mode.field(),
                            mode.from(),
                            mode.to());
            case TraceEvent.RoutineChange routine ->
                    String.format("%sroutine %s->%s @%04X,%04X",
                            characterPrefix(routine.character()),
                            routine.from(),
                            routine.to(),
                            routine.x() & 0xFFFF,
                            routine.y() & 0xFFFF);
            case TraceEvent.Checkpoint checkpoint ->
                    String.format("cp %s z=%s a=%s ap=%s gm=%s",
                            checkpoint.name(),
                            nullableInt(checkpoint.actualZoneId()),
                            nullableInt(checkpoint.actualAct()),
                            nullableInt(checkpoint.apparentAct()),
                            nullableInt(checkpoint.gameMode()));
            case TraceEvent.ZoneActState state ->
                    String.format("zoneact z=%s a=%s ap=%s gm=%s",
                            nullableInt(state.actualZoneId()),
                            nullableInt(state.actualAct()),
                            nullableInt(state.apparentAct()),
                            nullableInt(state.gameMode()));
            case TraceEvent.CageState cage ->
                    String.format("cage s%d @%04X,%04X sub=%02X st=%02X p1=%02X/%02X p2=%02X/%02X",
                            cage.slot(),
                            cage.x() & 0xFFFF,
                            cage.y() & 0xFFFF,
                            cage.subtype() & 0xFF,
                            cage.status() & 0xFF,
                            cage.p1Phase() & 0xFF,
                            cage.p1State() & 0xFF,
                            cage.p2Phase() & 0xFF,
                            cage.p2State() & 0xFF);
            case TraceEvent.CageExecution execution ->
                    summariseCageExecution(execution);
            default -> "";
        };
    }

    private static String summariseCageExecution(TraceEvent.CageExecution execution) {
        if (execution.hits().isEmpty()) {
            return "cageExec empty";
        }
        List<String> parts = new ArrayList<>();
        int limit = Math.min(3, execution.hits().size());
        for (int i = 0; i < limit; i++) {
            TraceEvent.CageExecution.Hit hit = execution.hits().get(i);
            parts.add(String.format("%s@%05X cage=%04X player=%04X d5=%04X d6=%02X state=%02X obj=%02X cst=%02X",
                    hit.branch(),
                    hit.pc(),
                    hit.cageAddr(),
                    hit.playerAddr(),
                    hit.d5() & 0xFFFF,
                    hit.d6() & 0xFF,
                    hit.stateByte() & 0xFF,
                    hit.playerObjCtrl() & 0xFF,
                    hit.cageStatus() & 0xFF));
        }
        String suffix = execution.hits().size() > limit
                ? String.format(" +%d", execution.hits().size() - limit)
                : "";
        return "cageExec " + String.join("; ", parts) + suffix;
    }

    private static String nullableInt(Integer value) {
        return value == null ? "null" : Integer.toString(value);
    }

    private static String stripHexPrefix(String value) {
        return value.replace("0x", "");
    }

    private static String characterPrefix(String character) {
        if (character == null || character.isBlank() || "sonic".equalsIgnoreCase(character)) {
            return "";
        }
        return character + " ";
    }
}
