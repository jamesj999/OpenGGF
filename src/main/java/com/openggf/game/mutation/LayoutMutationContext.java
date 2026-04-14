package com.openggf.game.mutation;

import java.util.Objects;
import java.util.function.Consumer;

public final class LayoutMutationContext {

    private final LevelMutationSurface surface;
    private final Consumer<MutationEffects> effectSink;

    public LayoutMutationContext(Consumer<MutationEffects> effectSink) {
        this(null, effectSink);
    }

    public LayoutMutationContext(LevelMutationSurface surface, Consumer<MutationEffects> effectSink) {
        this.surface = surface;
        this.effectSink = Objects.requireNonNull(effectSink, "effectSink");
    }

    public LevelMutationSurface surface() {
        if (surface == null) {
            throw new IllegalStateException("This mutation context does not expose a LevelMutationSurface.");
        }
        return surface;
    }

    void publish(MutationEffects effects) {
        effectSink.accept(effects != null ? effects : MutationEffects.NONE);
    }
}
