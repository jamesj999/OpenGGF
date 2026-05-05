package com.openggf.game.rewind.snapshot;

import com.openggf.game.render.AdvancedRenderMode;

import java.util.List;

/**
 * Snapshot of {@link com.openggf.game.render.AdvancedRenderModeController}
 * registered-contributor list.
 *
 * <p>Contributor object references are captured by identity. Restoring the
 * snapshot re-instates the same set of registered mode objects.
 */
public record AdvancedRenderModeSnapshot(List<AdvancedRenderMode> modes) {
    public AdvancedRenderModeSnapshot {
        modes = List.copyOf(modes);
    }
}
