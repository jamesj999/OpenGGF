package com.openggf.game.zone;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.ZoneRuntimeSnapshot;

import java.util.Objects;
import java.util.Optional;

public final class ZoneRuntimeRegistry implements RewindSnapshottable<ZoneRuntimeSnapshot> {
    private ZoneRuntimeState current = NoOpZoneRuntimeState.INSTANCE;

    public ZoneRuntimeState current() {
        return current;
    }

    public void install(ZoneRuntimeState state) {
        this.current = Objects.requireNonNull(state, "state");
    }

    public void clear() {
        this.current = NoOpZoneRuntimeState.INSTANCE;
    }

    public <T extends ZoneRuntimeState> Optional<T> currentAs(Class<T> type) {
        if (type.isInstance(current)) {
            return Optional.of(type.cast(current));
        }
        return Optional.empty();
    }

    // ── RewindSnapshottable ───────────────────────────────────────────────

    @Override
    public String key() {
        return "zone-runtime";
    }

    @Override
    public ZoneRuntimeSnapshot capture() {
        return new ZoneRuntimeSnapshot(current.captureBytes());
    }

    @Override
    public void restore(ZoneRuntimeSnapshot s) {
        current.restoreBytes(s.stateBytes());
    }
}
