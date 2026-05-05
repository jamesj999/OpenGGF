package com.openggf.game.rewind.snapshot;

/**
 * Snapshot of {@link com.openggf.game.palette.PaletteOwnershipRegistry} state.
 *
 * <p>The registry's {@code beginFrame()} wipes both the {@code writes} queue and
 * the {@code owners} lookup at the start of every frame. Since rewind
 * capture/restore happens at frame boundaries, the {@code writes} list is always
 * empty at capture time and is excluded. The {@code owners} array is captured as
 * a flat {@code String[]} to preserve any ownership that was committed after
 * {@code resolveInto} ran during the preceding frame (relevant when future code
 * queries ownership before the next {@code beginFrame}).
 */
public record PaletteOwnershipSnapshot(String[] owners) {
    /** owners is a defensive copy: surface x line x color flattened to 1-D. */
    public PaletteOwnershipSnapshot {
        owners = owners.clone();
    }
}
