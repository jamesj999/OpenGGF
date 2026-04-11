package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExplosionObjectInstance {

    @Test
    public void deferredExplosionSoundPlaysWhenServicesAreInjectedAfterConstruction() {
        RecordingObjectServices services = new RecordingObjectServices();

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, 100, 200, null, 77);
        assertEquals(0, services.playedSfxCount);

        explosion.setServices(services);

        assertEquals(1, services.playedSfxCount);
        assertEquals(77, services.lastSfxId);
    }

    @Test
    public void constructionContextPlaybackDoesNotReplayWhenServicesAreInjected() {
        RecordingObjectServices services = new RecordingObjectServices();

        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(services);
        try {
            ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, 100, 200, null, 88);
            assertEquals(1, services.playedSfxCount);

            explosion.setServices(services);

            assertEquals(1, services.playedSfxCount);
            assertEquals(88, services.lastSfxId);
        } finally {
            AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
        }
    }

    private static final class RecordingObjectServices extends TestObjectServices {
        private int playedSfxCount;
        private int lastSfxId = -1;

        @Override
        public void playSfx(int soundId) {
            playedSfxCount++;
            lastSfxId = soundId;
        }
    }
}


