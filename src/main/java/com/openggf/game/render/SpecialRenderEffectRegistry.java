package com.openggf.game.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-owned registry for staged special render effects.
 */
public final class SpecialRenderEffectRegistry {

    private final EnumMap<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectsByStage =
            new EnumMap<>(SpecialRenderEffectStage.class);

    public SpecialRenderEffectRegistry() {
        for (SpecialRenderEffectStage stage : SpecialRenderEffectStage.values()) {
            effectsByStage.put(stage, new ArrayList<>());
        }
    }

    public void register(SpecialRenderEffect effect) {
        Objects.requireNonNull(effect, "effect");
        effectsByStage.get(effect.stage()).add(effect);
    }

    public void clear() {
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            effects.clear();
        }
    }

    public boolean isEmpty() {
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            if (!effects.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size(SpecialRenderEffectStage stage) {
        Objects.requireNonNull(stage, "stage");
        List<SpecialRenderEffect> effects = effectsByStage.get(stage);
        return effects != null ? effects.size() : 0;
    }

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
}
