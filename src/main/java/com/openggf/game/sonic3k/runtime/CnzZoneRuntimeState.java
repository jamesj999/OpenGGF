package com.openggf.game.sonic3k.runtime;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;

import java.util.Objects;

public final class CnzZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final Sonic3kCNZEvents events;

    public CnzZoneRuntimeState(int actIndex, Sonic3kCNZEvents events) {
        this.actIndex = actIndex;
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_CNZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5(); }

    public Sonic3kCNZEvents.BossBackgroundMode bossBackgroundMode() {
        return events.getBossBackgroundMode();
    }
}
