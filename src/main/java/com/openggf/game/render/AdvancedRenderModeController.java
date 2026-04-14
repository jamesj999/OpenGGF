package com.openggf.game.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-owned registry and resolver for advanced render modes.
 */
public final class AdvancedRenderModeController {

    private final List<AdvancedRenderMode> modes = new ArrayList<>();

    public void register(AdvancedRenderMode mode) {
        modes.add(Objects.requireNonNull(mode, "mode"));
    }

    public void clear() {
        modes.clear();
    }

    public boolean isEmpty() {
        return modes.isEmpty();
    }

    public int size() {
        return modes.size();
    }

    public AdvancedRenderFrameState resolve(AdvancedRenderModeContext context) {
        if (context == null || modes.isEmpty()) {
            return AdvancedRenderFrameState.disabled();
        }
        AdvancedRenderFrameState.Builder builder = AdvancedRenderFrameState.builder();
        for (AdvancedRenderMode mode : List.copyOf(modes)) {
            mode.contribute(context, builder);
        }
        return builder.build();
    }
}
