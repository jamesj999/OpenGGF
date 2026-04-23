package com.openggf.trace;

@FunctionalInterface
public interface TraceExecutionModel {

    TraceExecutionPhase phaseFor(TraceFrame previous, TraceFrame current);

    static TraceExecutionModel forGame(String game) {
        if (game == null) {
            throw new IllegalArgumentException("Unsupported trace game: null");
        }
        return switch (game) {
            case "s1" -> TraceExecutionModel::deriveSonic1Phase;
            case "s2" -> TraceExecutionModel::deriveSonic2Phase;
            case "s3", "s3k" -> TraceExecutionModel::deriveSonic3kPhase;
            default -> throw new IllegalArgumentException("Unsupported trace game: " + game);
        };
    }

    private static TraceExecutionPhase deriveSonic1Phase(
            TraceFrame previous, TraceFrame current) {
        // Sonic 1 increments v_framecount only after WaitForVBla returns, while
        // the lag-only VBla_00 handler still advances the VBlank counter.
        return deriveCounterDrivenPhase(previous, current);
    }

    private static TraceExecutionPhase deriveSonic2Phase(
            TraceFrame previous, TraceFrame current) {
        // Sonic 2 follows the same phase split with different ROM symbols:
        // WaitForVint -> Level_frame_counter on full frames, Vint_Lag for
        // VBlank-only frames that still bump Vint_runcount.
        return deriveCounterDrivenPhase(previous, current);
    }

    private static TraceExecutionPhase deriveSonic3kPhase(
            TraceFrame previous, TraceFrame current) {
        if (previous == null) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        if (!hasAuthoritativeVblankCounter(current)) {
            return deriveLegacyHeuristic(previous, current);
        }
        if (gameplayCounterAdvanced(previous, current)) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        // S3K exposes Lag_frame_count in VInt_0_Main, but the disassembly
        // clears it again during the normal frame path. Treat it as diagnostic
        // only and continue to classify replay phases from gameplay/VBlank
        // counter deltas.
        if (vblankCounterAdvanced(previous, current)) {
            return TraceExecutionPhase.VBLANK_ONLY;
        }
        return TraceExecutionPhase.FULL_LEVEL_FRAME;
    }

    private static TraceExecutionPhase deriveCounterDrivenPhase(
            TraceFrame previous, TraceFrame current) {
        if (previous == null) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        // Pre-v3 checked-in traces do not carry authoritative VBlank counters,
        // so keep their classification semantics inside this single model.
        if (!hasAuthoritativeVblankCounter(current)) {
            return deriveLegacyHeuristic(previous, current);
        }
        if (gameplayCounterAdvanced(previous, current)) {
            return TraceExecutionPhase.FULL_LEVEL_FRAME;
        }
        if (vblankCounterAdvanced(previous, current)) {
            return TraceExecutionPhase.VBLANK_ONLY;
        }
        return TraceExecutionPhase.FULL_LEVEL_FRAME;
    }

    private static boolean hasAuthoritativeVblankCounter(TraceFrame current) {
        return current.vblankCounter() >= 0;
    }

    private static boolean gameplayCounterAdvanced(TraceFrame previous, TraceFrame current) {
        return current.gameplayFrameCounter() != previous.gameplayFrameCounter();
    }

    private static boolean vblankCounterAdvanced(TraceFrame previous, TraceFrame current) {
        return current.vblankCounter() != previous.vblankCounter();
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
