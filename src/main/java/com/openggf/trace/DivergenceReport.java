package com.openggf.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DivergenceReport {

    private final List<FrameComparison> allComparisons;
    private final List<DivergenceGroup> errors;
    private final List<DivergenceGroup> warnings;
    private final TraceData traceData;

    public DivergenceReport(List<FrameComparison> comparisons) {
        this(comparisons, null);
    }

    public DivergenceReport(List<FrameComparison> comparisons, TraceData traceData) {
        this.allComparisons = List.copyOf(comparisons);
        this.traceData = traceData;
        List<DivergenceGroup> allGroups = buildGroups(comparisons);
        this.errors = allGroups.stream()
            .filter(g -> g.severity() == Severity.ERROR)
            .toList();
        this.warnings = allGroups.stream()
            .filter(g -> g.severity() == Severity.WARNING)
            .toList();
    }

    public List<DivergenceGroup> errors() { return errors; }
    public List<DivergenceGroup> warnings() { return warnings; }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }

    public String toSummary() {
        int errorCount = errors.size();
        int warningCount = warnings.size();

        if (errorCount == 0 && warningCount == 0) {
            return "All frames match trace. No divergences.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d error%s, %d warning%s.",
            errorCount, errorCount == 1 ? "" : "s",
            warningCount, warningCount == 1 ? "" : "s"));

        if (errorCount > 0) {
            DivergenceGroup first = errors.get(0);
            sb.append(String.format(" First error: frame %d -- %s mismatch (expected=%s, actual=%s)",
                first.startFrame(), first.field(), first.expectedAtStart(), first.actualAtStart()));
        }

        appendTraceContextSummary(sb, summaryReferenceFrame());
        return sb.toString();
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            ObjectNode root = mapper.createObjectNode();

            root.put("error_count", errors.size());
            root.put("warning_count", warnings.size());
            root.put("total_frames", allComparisons.size());
            root.put("summary", toSummary());

            int referenceFrame = summaryReferenceFrame();
            TraceEvent.Checkpoint checkpoint = latestCheckpointAtOrBefore(referenceFrame);
            if (checkpoint != null) {
                root.set("latest_checkpoint", checkpointToJson(mapper, checkpoint));
            }

            TraceEvent.ZoneActState zoneActState = latestZoneActStateAtOrBefore(referenceFrame);
            if (zoneActState != null) {
                root.set("latest_zone_act_state", zoneActStateToJson(mapper, zoneActState));
            }

            List<String> missingAuxSchemas = missingAdvertisedAuxSchemas();
            if (!missingAuxSchemas.isEmpty()) {
                ArrayNode missingNode = root.putArray("missing_advertised_aux_schemas");
                for (String schema : missingAuxSchemas) {
                    missingNode.add(schema);
                }
            }

            ArrayNode errorsNode = root.putArray("errors");
            for (DivergenceGroup g : errors) {
                errorsNode.add(groupToJson(mapper, g));
            }

            ArrayNode warningsNode = root.putArray("warnings");
            for (DivergenceGroup g : warnings) {
                warningsNode.add(groupToJson(mapper, g));
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialise report: " + e.getMessage() + "\"}";
        }
    }

    public String getContextWindow(int centreFrame, int radius) {
        int centreIndex = comparisonIndexForFrame(centreFrame);
        int start = Math.max(0, centreIndex - radius);
        int end = Math.min(allComparisons.size() - 1, centreIndex + radius);

        StringBuilder sb = new StringBuilder();
        appendTraceContextWindow(sb, centreFrame);
        sb.append(String.format("%-6s", "Frame"));

        Set<String> fieldNames = new LinkedHashSet<>();
        boolean hasDiagnostics = false;
        for (int i = start; i <= end; i++) {
            if (i < allComparisons.size()) {
                FrameComparison fc = allComparisons.get(i);
                fieldNames.addAll(fc.fields().keySet());
                if (!fc.romDiagnostics().isEmpty() || !fc.engineDiagnostics().isEmpty()) {
                    hasDiagnostics = true;
                }
            }
        }

        for (String field : fieldNames) {
            sb.append(String.format(" | %-8s | %-8s", "Exp " + field, "Act " + field));
        }
        sb.append("\n");

        for (int i = start; i <= end; i++) {
            if (i >= allComparisons.size()) {
                break;
            }
            FrameComparison fc = allComparisons.get(i);
            sb.append(String.format("%-6d", fc.frame()));
            for (String field : fieldNames) {
                FieldComparison comp = fc.fields().get(field);
                if (comp != null) {
                    String marker = comp.severity() == Severity.ERROR ? "*" : " ";
                    sb.append(String.format(" | %-8s |%s%-7s",
                        comp.expected(), marker, comp.actual()));
                } else {
                    sb.append(String.format(" | %-8s | %-8s", "?", "?"));
                }
            }
            if (hasDiagnostics && fc.hasDivergence()) {
                String romDiag = fc.romDiagnostics();
                String engDiag = fc.engineDiagnostics();
                if (!romDiag.isEmpty() || !engDiag.isEmpty()) {
                    sb.append("\n       ROM: ").append(romDiag.isEmpty() ? "-" : romDiag);
                    sb.append("\n       ENG: ").append(engDiag.isEmpty() ? "-" : engDiag);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private int comparisonIndexForFrame(int frame) {
        if (allComparisons.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < allComparisons.size(); i++) {
            if (allComparisons.get(i).frame() == frame) {
                return i;
            }
        }
        int insertion = 0;
        while (insertion < allComparisons.size() && allComparisons.get(insertion).frame() < frame) {
            insertion++;
        }
        if (insertion == 0) {
            return 0;
        }
        if (insertion >= allComparisons.size()) {
            return allComparisons.size() - 1;
        }
        int beforeFrame = allComparisons.get(insertion - 1).frame();
        int afterFrame = allComparisons.get(insertion).frame();
        return Math.abs(frame - beforeFrame) <= Math.abs(afterFrame - frame)
                ? insertion - 1
                : insertion;
    }

    private void appendTraceContextSummary(StringBuilder sb, int frame) {
        TraceEvent.Checkpoint checkpoint = latestCheckpointAtOrBefore(frame);
        if (checkpoint != null) {
            sb.append(" Latest checkpoint: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(checkpoint)));
        }

        TraceEvent.ZoneActState zoneActState = latestZoneActStateAtOrBefore(frame);
        if (zoneActState != null) {
            sb.append(" Latest zone/act state: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(zoneActState)));
        }
    }

    private void appendTraceContextWindow(StringBuilder sb, int frame) {
        TraceEvent.Checkpoint checkpoint = latestCheckpointAtOrBefore(frame);
        if (checkpoint != null) {
            sb.append("Latest checkpoint: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(checkpoint)))
                .append("\n");
        }

        TraceEvent.ZoneActState zoneActState = latestZoneActStateAtOrBefore(frame);
        if (zoneActState != null) {
            sb.append("Latest zone_act_state: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(zoneActState)))
                .append("\n");
        }

        List<String> missingAuxSchemas = missingAdvertisedAuxSchemas();
        if (!missingAuxSchemas.isEmpty()) {
            sb.append("Missing advertised aux schemas: ")
                .append(String.join(", ", missingAuxSchemas))
                .append("\n");
        }

        appendFocusedTraceDiagnostics(sb, frame);
    }

    private int summaryReferenceFrame() {
        if (!errors.isEmpty()) {
            return errors.get(0).startFrame();
        }
        if (!warnings.isEmpty()) {
            return warnings.get(0).startFrame();
        }
        if (!allComparisons.isEmpty()) {
            return allComparisons.get(allComparisons.size() - 1).frame();
        }
        return -1;
    }

    private TraceEvent.Checkpoint latestCheckpointAtOrBefore(int frame) {
        if (traceData == null || frame < 0) {
            return null;
        }
        return traceData.latestCheckpointAtOrBefore(frame);
    }

    private TraceEvent.ZoneActState latestZoneActStateAtOrBefore(int frame) {
        if (traceData == null || frame < 0) {
            return null;
        }
        return traceData.latestZoneActStateAtOrBefore(frame);
    }

    private List<String> missingAdvertisedAuxSchemas() {
        return traceData == null
                ? List.of()
                : traceData.missingAdvertisedAuxSchemas();
    }

    private void appendFocusedTraceDiagnostics(StringBuilder sb, int frame) {
        if (traceData == null || frame < 0) {
            return;
        }
        List<TraceEvent> diagnostics = new ArrayList<>();
        diagnostics.addAll(traceData.cageStatesForFrame(frame));
        TraceEvent.CageExecution cageExecution = traceData.cageExecutionForFrame(frame);
        if (cageExecution != null) {
            diagnostics.add(cageExecution);
        }
        TraceEvent.VelocityWrite velocityWrite = traceData.velocityWriteForFrame(frame, "tails");
        if (velocityWrite != null) {
            diagnostics.add(velocityWrite);
        }
        TraceEvent.TailsCpuNormalStep cpuNormalStep =
                traceData.tailsCpuNormalStepForFrame(frame, "tails");
        if (cpuNormalStep != null) {
            diagnostics.add(cpuNormalStep);
        }
        TraceEvent.SidekickInteractObjectState interactObject =
                traceData.sidekickInteractObjectStateForFrame(frame, "tails");
        if (interactObject != null) {
            diagnostics.add(interactObject);
        }
        TraceEvent.AizBoundaryState aizBoundary =
                traceData.aizBoundaryStateForFrame(frame, "tails");
        if (aizBoundary != null) {
            diagnostics.add(aizBoundary);
        }
        TraceEvent.AizTransitionFloorSolidState aizFloor =
                traceData.aizTransitionFloorSolidStateForFrame(frame);
        if (aizFloor != null) {
            diagnostics.add(aizFloor);
        }
        if (!diagnostics.isEmpty()) {
            sb.append("Trace diagnostics @")
                .append(frame)
                .append(": ")
                .append(TraceEventFormatter.summariseFrameEvents(diagnostics))
                .append("\n");
        }
    }

    private static List<DivergenceGroup> buildGroups(List<FrameComparison> comparisons) {
        List<DivergenceGroup> groups = new ArrayList<>();
        Map<String, DivergenceGroupBuilder> openGroups = new LinkedHashMap<>();

        for (FrameComparison fc : comparisons) {
            Set<String> activeFields = new HashSet<>();

            for (Map.Entry<String, FieldComparison> entry : fc.fields().entrySet()) {
                String field = entry.getKey();
                FieldComparison comp = entry.getValue();

                if (comp.isDivergent()) {
                    activeFields.add(field);
                    DivergenceGroupBuilder builder = openGroups.get(field);
                    if (builder != null && builder.severity == comp.severity()
                            && builder.endFrame == fc.frame() - 1) {
                        builder.endFrame = fc.frame();
                    } else {
                        if (builder != null) {
                            groups.add(builder.build());
                        }
                        openGroups.put(field, new DivergenceGroupBuilder(
                            field, comp.severity(), fc.frame(),
                            comp.expected(), comp.actual()));
                    }
                }
            }

            Iterator<Map.Entry<String, DivergenceGroupBuilder>> iter =
                openGroups.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, DivergenceGroupBuilder> entry = iter.next();
                if (!activeFields.contains(entry.getKey())) {
                    groups.add(entry.getValue().build());
                    iter.remove();
                }
            }
        }

        for (DivergenceGroupBuilder builder : openGroups.values()) {
            groups.add(builder.build());
        }

        groups.sort(Comparator.comparingInt(DivergenceGroup::startFrame));
        markCascading(groups);
        return groups;
    }

    private static void markCascading(List<DivergenceGroup> groups) {
        int earliestErrorFrame = Integer.MAX_VALUE;
        String earliestErrorField = null;
        for (DivergenceGroup g : groups) {
            if (g.severity() == Severity.ERROR && g.startFrame() < earliestErrorFrame) {
                earliestErrorFrame = g.startFrame();
                earliestErrorField = g.field();
            }
        }

        if (earliestErrorField == null) {
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            DivergenceGroup g = groups.get(i);
            boolean cascading = g.severity() == Severity.ERROR
                && g.startFrame() > earliestErrorFrame
                && !g.field().equals(earliestErrorField);
            if (cascading != g.cascading()) {
                groups.set(i, new DivergenceGroup(g.field(), g.severity(),
                    g.startFrame(), g.endFrame(),
                    g.expectedAtStart(), g.actualAtStart(), cascading));
            }
        }
    }

    private ObjectNode groupToJson(ObjectMapper mapper, DivergenceGroup g) {
        ObjectNode node = mapper.createObjectNode();
        node.put("field", g.field());
        node.put("severity", g.severity().name());
        node.put("start_frame", g.startFrame());
        node.put("end_frame", g.endFrame());
        node.put("frame_span", g.frameSpan());
        node.put("expected_at_start", g.expectedAtStart());
        node.put("actual_at_start", g.actualAtStart());
        node.put("cascading", g.cascading());
        return node;
    }

    private ObjectNode checkpointToJson(ObjectMapper mapper, TraceEvent.Checkpoint checkpoint) {
        ObjectNode node = mapper.createObjectNode();
        node.put("frame", checkpoint.frame());
        node.put("name", checkpoint.name());
        putNullableInt(node, "actual_zone_id", checkpoint.actualZoneId());
        putNullableInt(node, "actual_act", checkpoint.actualAct());
        putNullableInt(node, "apparent_act", checkpoint.apparentAct());
        putNullableInt(node, "game_mode", checkpoint.gameMode());
        if (checkpoint.notes() == null) {
            node.putNull("notes");
        } else {
            node.put("notes", checkpoint.notes());
        }
        return node;
    }

    private ObjectNode zoneActStateToJson(ObjectMapper mapper, TraceEvent.ZoneActState zoneActState) {
        ObjectNode node = mapper.createObjectNode();
        node.put("frame", zoneActState.frame());
        putNullableInt(node, "actual_zone_id", zoneActState.actualZoneId());
        putNullableInt(node, "actual_act", zoneActState.actualAct());
        putNullableInt(node, "apparent_act", zoneActState.apparentAct());
        putNullableInt(node, "game_mode", zoneActState.gameMode());
        return node;
    }

    private void putNullableInt(ObjectNode node, String field, Integer value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static class DivergenceGroupBuilder {
        final String field;
        final Severity severity;
        final int startFrame;
        final String expectedAtStart;
        final String actualAtStart;
        int endFrame;

        DivergenceGroupBuilder(String field, Severity severity, int frame,
                String expected, String actual) {
            this.field = field;
            this.severity = severity;
            this.startFrame = frame;
            this.endFrame = frame;
            this.expectedAtStart = expected;
            this.actualAtStart = actual;
        }

        DivergenceGroup build() {
            return new DivergenceGroup(field, severity, startFrame, endFrame,
                expectedAtStart, actualAtStart, false);
        }
    }
}
