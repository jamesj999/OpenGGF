package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;

import java.util.Objects;

public final class HczZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final Sonic3kHCZEvents events;

    public HczZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter, Sonic3kHCZEvents events) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_HCZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5(); }

    /**
     * Whether the HCZ2 wall-chase BG high-priority overlay is currently active.
     * Drives the staged {@code HczWallChaseBgOverlayEffect} render pass.
     */
    public boolean wallChaseBgOverlayActive() {
        return events.isWallChaseBgOverlayActive();
    }
}
