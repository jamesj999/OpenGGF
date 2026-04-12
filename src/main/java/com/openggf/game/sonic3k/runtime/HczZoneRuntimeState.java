package com.openggf.game.sonic3k.runtime;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;

import java.util.Objects;

public final class HczZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final Sonic3kHCZEvents events;

    public HczZoneRuntimeState(int actIndex, Sonic3kHCZEvents events) {
        this.actIndex = actIndex;
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_HCZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5(); }
}
