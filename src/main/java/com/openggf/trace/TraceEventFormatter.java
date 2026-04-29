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
            case TraceEvent.TailsCpuNormalStep step ->
                    String.format("tailsCpu status=%02X obj=%02X gv=%04X xv=%04X stat=%02X input=%04X branch=%s ctrl2=%04X/%02X post=%04X,%04X,%02X",
                            step.status() & 0xFF,
                            step.objectControl() & 0xFF,
                            step.groundVel() & 0xFFFF,
                            step.xVel() & 0xFFFF,
                            step.delayedStat() & 0xFF,
                            step.delayedInput() & 0xFFFF,
                            step.loc13dd0Branch(),
                            step.ctrl2Logical() & 0xFFFF,
                            step.ctrl2HeldLogical() & 0xFF,
                            step.pathPostGroundVel() & 0xFFFF,
                            step.pathPostXVel() & 0xFFFF,
                            step.pathPostStatus() & 0xFF);
            case TraceEvent.SidekickInteractObjectState state ->
                    String.format("%sInteract slot=%d ptr=%04X obj=%08X rtn=%02X st=%02X @%04X,%04X sub=%02X %s rf=%02X obj=%02X onObj=%s objP2=%s active=%s destroyed=%s",
                            state.character() == null || state.character().isBlank()
                                    ? "sidekick"
                                    : state.character(),
                            state.interactSlot(),
                            state.interact() & 0xFFFF,
                            state.objectCode(),
                            state.objectRoutine() & 0xFF,
                            state.objectStatus() & 0xFF,
                            state.objectX() & 0xFFFF,
                            state.objectY() & 0xFFFF,
                            state.objectSubtype() & 0xFF,
                            state.character() == null || state.character().isBlank()
                                    ? "sidekick"
                                    : state.character(),
                            state.tailsRenderFlags() & 0xFF,
                            state.tailsObjectControl() & 0xFF,
                            state.tailsOnObject(),
                            state.objectP2Standing(),
                            state.objectActive(),
                            state.objectDestroyed());
            case TraceEvent.AizBoundaryState state ->
                    String.format("%sAizBoundary cam=%04X/%04X y=%04X/%04X tree=%04X,%04X,%04X,%04X->%04X,%04X,%04X,%04X boundary=%s %04X,%04X,%04X,%04X->%04X,%04X,%04X,%04X post=%04X,%04X,%04X,%04X",
                            state.character() == null || state.character().isBlank()
                                    ? "sidekick"
                                    : state.character(),
                            state.cameraMinX() & 0xFFFF,
                            state.cameraMaxX() & 0xFFFF,
                            state.cameraMinY() & 0xFFFF,
                            state.cameraMaxY() & 0xFFFF,
                            state.treePreX() & 0xFFFF,
                            state.treePreY() & 0xFFFF,
                            state.treePreXVel() & 0xFFFF,
                            state.treePreYVel() & 0xFFFF,
                            state.treePostX() & 0xFFFF,
                            state.treePostY() & 0xFFFF,
                            state.treePostXVel() & 0xFFFF,
                            state.treePostYVel() & 0xFFFF,
                            state.boundaryAction(),
                            state.boundaryPreX() & 0xFFFF,
                            state.boundaryPreY() & 0xFFFF,
                            state.boundaryPreXVel() & 0xFFFF,
                            state.boundaryPreYVel() & 0xFFFF,
                            state.boundaryPostX() & 0xFFFF,
                            state.boundaryPostY() & 0xFFFF,
                            state.boundaryPostXVel() & 0xFFFF,
                            state.boundaryPostYVel() & 0xFFFF,
                            state.postMoveX() & 0xFFFF,
                            state.postMoveY() & 0xFFFF,
                            state.postMoveXVel() & 0xFFFF,
                            state.postMoveYVel() & 0xFFFF);
            case TraceEvent.AizTransitionFloorSolidState state ->
                    String.format("aizFloor s%d @%04X,%04X st=%02X stand=%s/%s p1=%s y=%04X yr=%02X st=%02X obj=%02X d=%04X/%04X/%04X p2=%s y=%04X yr=%02X st=%02X obj=%02X d=%04X/%04X/%04X",
                            state.slot(),
                            state.objectX() & 0xFFFF,
                            state.objectY() & 0xFFFF,
                            state.objectStatus() & 0xFF,
                            state.p1Standing(),
                            state.p2Standing(),
                            state.p1Path(),
                            state.p1Y() & 0xFFFF,
                            state.p1YRadius() & 0xFF,
                            state.p1Status() & 0xFF,
                            state.p1ObjectControl() & 0xFF,
                            state.p1D1() & 0xFFFF,
                            state.p1D2() & 0xFFFF,
                            state.p1D3() & 0xFFFF,
                            state.p2Path(),
                            state.p2Y() & 0xFFFF,
                            state.p2YRadius() & 0xFF,
                            state.p2Status() & 0xFF,
                            state.p2ObjectControl() & 0xFF,
                            state.p2D1() & 0xFFFF,
                            state.p2D2() & 0xFFFF,
                            state.p2D3() & 0xFFFF);
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
