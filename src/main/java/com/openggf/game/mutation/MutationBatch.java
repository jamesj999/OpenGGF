package com.openggf.game.mutation;

import java.util.List;
import java.util.Objects;

public record MutationBatch(List<LayoutMutationIntent> intents) {

    public MutationBatch {
        intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
    }

    public static MutationBatch empty() {
        return new MutationBatch(List.of());
    }

    public boolean isEmpty() {
        return intents.isEmpty();
    }

    public List<LayoutMutationIntent> remainingFrom(int startIndex) {
        if (startIndex <= 0) {
            return intents;
        }
        if (startIndex >= intents.size()) {
            return List.of();
        }
        return intents.subList(startIndex, intents.size());
    }
}
