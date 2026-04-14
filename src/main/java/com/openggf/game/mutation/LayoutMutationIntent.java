package com.openggf.game.mutation;

@FunctionalInterface
public interface LayoutMutationIntent {
    MutationEffects apply(LayoutMutationContext context);
}
