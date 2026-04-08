package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotBonusStageRuntime {
    private boolean initialized;

    public void bootstrap() {
        initialized = true;
    }

    public void update(int frameCounter) {
    }

    public void shutdown() {
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
