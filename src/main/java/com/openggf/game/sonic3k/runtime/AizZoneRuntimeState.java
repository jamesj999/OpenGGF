package com.openggf.game.sonic3k.runtime;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;

import java.util.Objects;

public final class AizZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final Sonic3kAIZEvents events;

    public AizZoneRuntimeState(int actIndex, Sonic3kAIZEvents events) {
        this.actIndex = actIndex;
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_AIZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5(); }

    public boolean isBossFlagActive() { return events.isBossFlag(); }
    public boolean isPostFireHazeActive() { return events.isPostFireHazeActive(); }
    public boolean isBattleshipAutoScrollActive() { return events.isBattleshipAutoScrollActive(); }
    public int getBattleshipSmoothScrollX() { return events.getBattleshipSmoothScrollX(); }
    public int getScreenShakeOffsetY() { return events.getScreenShakeOffsetY(); }
}
