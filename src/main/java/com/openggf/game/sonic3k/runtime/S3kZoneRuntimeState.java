package com.openggf.game.sonic3k.runtime;

import com.openggf.game.zone.ZoneRuntimeState;

public interface S3kZoneRuntimeState extends ZoneRuntimeState {
    String GAME_ID = "s3k";

    @Override default String gameId() { return GAME_ID; }

    int getDynamicResizeRoutine();
    boolean isActTransitionFlagActive();
}
