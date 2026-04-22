package com.openggf.tests.trace;

import java.util.ArrayList;
import java.util.List;

final class TraceEventFormatter {

    private TraceEventFormatter() {
    }

    static String summariseFrameEvents(List<TraceEvent> events) {
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
                String base = String.format("near s%d %s @%04X,%04X",
                        near.slot(),
                        near.objectType(),
                        near.x() & 0xFFFF,
                        near.y() & 0xFFFF);
                yield near.routine().isEmpty() ? base : base + " rtn=" + stripHexPrefix(near.routine());
            }
            case TraceEvent.ModeChange mode ->
                    String.format("mode %s %d->%d", mode.field(), mode.from(), mode.to());
            case TraceEvent.RoutineChange routine ->
                    String.format("routine %s->%s @%04X,%04X",
                            routine.from(),
                            routine.to(),
                            routine.sonicX() & 0xFFFF,
                            routine.sonicY() & 0xFFFF);
            default -> "";
        };
    }

    private static String stripHexPrefix(String value) {
        return value.replace("0x", "");
    }
}
