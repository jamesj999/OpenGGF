package com.openggf.game.sonic3k.events;

import com.openggf.game.LevelEventProvider;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kTransitionWriteSupport {

    @Test
    void helpersRouteWritesThroughSharedBridges() {
        RecordingBridge bridge = new RecordingBridge();
        RecordingServices services = new RecordingServices(bridge);

        S3kHczEventWriteSupport.setBossFlag(services, true);
        S3kTransitionWriteSupport.signalActTransition(services);
        S3kTransitionWriteSupport.requestHczPostTransitionCutscene(bridge);

        assertTrue(bridge.hczBossFlag);
        assertTrue(bridge.actTransitionSignaled);
        assertTrue(bridge.hczPostTransitionCutsceneRequested);
    }

    @Test
    void helpersFallBackCleanlyWhenProviderDoesNotExposeBridges() {
        RecordingServices services = new RecordingServices(new LevelEventProvider() {
            @Override
            public void initLevel(int zone, int act) {
            }

            @Override
            public void update() {
            }
        });

        S3kHczEventWriteSupport.setBossFlag(services, true);
        S3kTransitionWriteSupport.signalActTransition(services);
        S3kTransitionWriteSupport.requestHczPostTransitionCutscene(services.levelEventProvider());
    }

    private static final class RecordingServices extends TestObjectServices {
        private final LevelEventProvider provider;

        private RecordingServices(LevelEventProvider provider) {
            this.provider = provider;
        }

        @Override
        public LevelEventProvider levelEventProvider() {
            return provider;
        }
    }

    private static final class RecordingBridge
            implements HczObjectEventBridge, S3kTransitionEventBridge, LevelEventProvider {
        private boolean hczBossFlag;
        private boolean actTransitionSignaled;
        private boolean hczPostTransitionCutsceneRequested;

        @Override
        public void initLevel(int zone, int act) {
        }

        @Override
        public void update() {
        }

        @Override
        public void setHczBossFlag(boolean value) {
            hczBossFlag = value;
        }

        @Override
        public void signalActTransition() {
            actTransitionSignaled = true;
        }

        @Override
        public void requestHczPostTransitionCutscene() {
            hczPostTransitionCutsceneRequested = true;
        }
    }
}
