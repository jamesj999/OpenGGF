package com.openggf.game.sonic3k.events;

import com.openggf.game.LevelEventProvider;
import com.openggf.level.objects.ObjectServices;

public final class S3kTransitionWriteSupport {
    private S3kTransitionWriteSupport() {
    }

    public static void signalActTransition(ObjectServices services) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.signalActTransition();
        }
    }

    public static void requestHczPostTransitionCutscene(LevelEventProvider provider) {
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.requestHczPostTransitionCutscene();
        }
    }
}
