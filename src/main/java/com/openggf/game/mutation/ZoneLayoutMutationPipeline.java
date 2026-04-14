package com.openggf.game.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ZoneLayoutMutationPipeline {

    private final List<LayoutMutationIntent> queued = new ArrayList<>();

    public void queue(LayoutMutationIntent intent) {
        queued.add(Objects.requireNonNull(intent, "intent"));
    }

    public void flush(LayoutMutationContext context) {
        Objects.requireNonNull(context, "context");
        if (queued.isEmpty()) {
            return;
        }

        MutationBatch batch = new MutationBatch(queued);
        queued.clear();
        apply(batch, context);
    }

    public void applyImmediately(LayoutMutationIntent intent, LayoutMutationContext context) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(context, "context");
        context.publish(intent.apply(context));
    }

    public void clear() {
        queued.clear();
    }

    public boolean isEmpty() {
        return queued.isEmpty();
    }

    private void apply(MutationBatch batch, LayoutMutationContext context) {
        for (int i = 0; i < batch.intents().size(); i++) {
            LayoutMutationIntent intent = batch.intents().get(i);
            MutationEffects effects;
            try {
                effects = intent.apply(context);
            } catch (RuntimeException | Error ex) {
                queued.addAll(0, batch.remainingFrom(i));
                throw ex;
            }
            context.publish(effects);
        }
    }
}
