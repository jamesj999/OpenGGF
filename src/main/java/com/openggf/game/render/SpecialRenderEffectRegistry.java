package com.openggf.game.render;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.SpecialRenderEffectSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-owned registry for staged special render effects.
 *
 * <p>Effects are grouped by {@link SpecialRenderEffectStage} and dispatched from
 * the standard scene pipeline at fixed points between background, foreground,
 * and sprite rendering.
 */
public final class SpecialRenderEffectRegistry
        implements RewindSnapshottable<SpecialRenderEffectSnapshot> {

    private final EnumMap<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectsByStage =
            new EnumMap<>(SpecialRenderEffectStage.class);

    public SpecialRenderEffectRegistry() {
        for (SpecialRenderEffectStage stage : SpecialRenderEffectStage.values()) {
            effectsByStage.put(stage, new ArrayList<>());
        }
    }

    /** Registers one effect at its declared stage. */
    public void register(SpecialRenderEffect effect) {
        Objects.requireNonNull(effect, "effect");
        effectsByStage.get(effect.stage()).add(effect);
    }

    /** Removes all staged effects. */
    public void clear() {
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            effects.clear();
        }
    }

    /** Returns {@code true} when no stage contains any registered effects. */
    public boolean isEmpty() {
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            if (!effects.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Returns the number of registered effects for one stage. */
    public int size(SpecialRenderEffectStage stage) {
        Objects.requireNonNull(stage, "stage");
        List<SpecialRenderEffect> effects = effectsByStage.get(stage);
        return effects != null ? effects.size() : 0;
    }

    /** Returns the total number of registered effects across all stages. */
    public int activeEffectCount() {
        int count = 0;
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            count += effects.size();
        }
        return count;
    }

    /** Executes all effects registered for the requested stage. */
    public void dispatch(SpecialRenderEffectStage stage, SpecialRenderEffectContext context) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(context, "context");
        List<SpecialRenderEffect> effects = effectsByStage.get(stage);
        if (effects == null || effects.isEmpty()) {
            return;
        }
        for (SpecialRenderEffect effect : List.copyOf(effects)) {
            effect.render(context);
        }
    }

    // ── RewindSnapshottable ───────────────────────────────────────────────

    @Override
    public String key() {
        return "special-render";
    }

    @Override
    public SpecialRenderEffectSnapshot capture() {
        return new SpecialRenderEffectSnapshot(effectsByStage);
    }

    @Override
    public void restore(SpecialRenderEffectSnapshot s) {
        for (Map.Entry<SpecialRenderEffectStage, List<SpecialRenderEffect>> e
                : effectsByStage.entrySet()) {
            e.getValue().clear();
            List<SpecialRenderEffect> saved = s.effectsByStage().get(e.getKey());
            if (saved != null) {
                e.getValue().addAll(saved);
            }
        }
    }
}
