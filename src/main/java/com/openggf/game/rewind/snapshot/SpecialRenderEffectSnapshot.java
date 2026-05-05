package com.openggf.game.rewind.snapshot;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectStage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of {@link com.openggf.game.render.SpecialRenderEffectRegistry}
 * active-effect list per stage.
 *
 * <p>Effect object references are captured by identity — effects are
 * stateless or hold their own mutable state (which is snapshotted
 * elsewhere). Restoring the snapshot re-instates the same set of
 * registered effect objects without re-creating them.
 */
public record SpecialRenderEffectSnapshot(
        Map<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectsByStage
) {
    public SpecialRenderEffectSnapshot {
        // Defensive copy: outer map is unmodifiable, inner lists are unmodifiable copies
        EnumMap<SpecialRenderEffectStage, List<SpecialRenderEffect>> copy =
                new EnumMap<>(SpecialRenderEffectStage.class);
        for (Map.Entry<SpecialRenderEffectStage, List<SpecialRenderEffect>> e
                : effectsByStage.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        effectsByStage = java.util.Collections.unmodifiableMap(copy);
    }
}
