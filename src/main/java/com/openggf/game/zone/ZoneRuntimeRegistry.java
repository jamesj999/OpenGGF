package com.openggf.game.zone;

import java.util.Objects;
import java.util.Optional;

public final class ZoneRuntimeRegistry {
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
}
