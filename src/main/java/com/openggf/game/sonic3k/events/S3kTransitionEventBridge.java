package com.openggf.game.sonic3k.events;

public interface S3kTransitionEventBridge {
    void signalActTransition();

    void requestHczPostTransitionCutscene();
}
