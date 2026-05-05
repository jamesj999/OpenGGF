package com.openggf.game.render;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.AdvancedRenderModeSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-owned registry and resolver for advanced render modes.
 *
 * <p>The controller collects active contributors for the current zone/runtime
 * and resolves them into one {@link AdvancedRenderFrameState} per frame.
 */
public final class AdvancedRenderModeController
        implements RewindSnapshottable<AdvancedRenderModeSnapshot> {

    private final List<AdvancedRenderMode> modes = new ArrayList<>();

    /** Registers one render-mode contributor for subsequent frame resolution. */
    public void register(AdvancedRenderMode mode) {
        modes.add(Objects.requireNonNull(mode, "mode"));
    }

    /** Removes all registered contributors. */
    public void clear() {
        modes.clear();
    }

    /** Returns {@code true} when no contributors are registered. */
    public boolean isEmpty() {
        return modes.isEmpty();
    }

    /** Returns the number of registered contributors. */
    public int size() {
        return modes.size();
    }

    /** Resolves the current frame's aggregate render-mode state. */
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

    // ── RewindSnapshottable ───────────────────────────────────────────────

    @Override
    public String key() {
        return "advanced-render-mode";
    }

    @Override
    public AdvancedRenderModeSnapshot capture() {
        return new AdvancedRenderModeSnapshot(modes);
    }

    @Override
    public void restore(AdvancedRenderModeSnapshot s) {
        modes.clear();
        modes.addAll(s.modes());
    }
}
