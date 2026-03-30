package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

public class DivergenceReport {

    private final List<FrameComparison> allComparisons;
    private final List<DivergenceGroup> errors;
    private final List<DivergenceGroup> warnings;

    public DivergenceReport(List<FrameComparison> comparisons) {
        this.allComparisons = List.copyOf(comparisons);
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
        int start = Math.max(0, centreFrame - radius);
        int end = Math.min(allComparisons.size() - 1, centreFrame + radius);

        StringBuilder sb = new StringBuilder();
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
            if (i >= allComparisons.size()) break;
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
            // Append diagnostic context on divergent frames
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

        if (earliestErrorField == null) return;

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
