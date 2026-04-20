package com.openggf.tests.trace;

@FunctionalInterface
public interface TraceExecutionModel {

    TraceExecutionPhase phaseFor(TraceFrame previous, TraceFrame current);

    static TraceExecutionModel forGame(String game) {
        if (game == null) {
            throw new IllegalArgumentException("Unsupported trace game: null");
        }
        return switch (game) {
            case "s1", "s2", "s3", "s3k" -> TraceExecutionModel::deriveFromGameplayCounter;
            default -> throw new IllegalArgumentException("Unsupported trace game: " + game);
        };
    }

    private static TraceExecutionPhase deriveFromGameplayCounter(
            TraceFrame previous, TraceFrame current) {
        if (previous == null) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        // Pre-v3 checked-in traces do not carry authoritative VBlank counters,
        // so keep their classification semantics inside this single model.
        if (current.vblankCounter() < 0) {
            return deriveLegacyHeuristic(previous, current);
        }
        if (current.gameplayFrameCounter() != previous.gameplayFrameCounter()) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        return TraceExecutionPhase.VBLANK_ONLY;
    }

    private static TraceExecutionPhase deriveLegacyHeuristic(
            TraceFrame previous, TraceFrame current) {
        if (!current.stateEquals(previous)) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        return current.xSpeed() != 0 || current.ySpeed() != 0
                || current.gSpeed() != 0 || current.air()
                ? TraceExecutionPhase.VBLANK_ONLY
                : TraceExecutionPhase.FULL_LEVEL_FRAME;
    }
}
