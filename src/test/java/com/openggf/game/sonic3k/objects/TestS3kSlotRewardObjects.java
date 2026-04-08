package com.openggf.game.sonic3k.objects;

import com.openggf.game.LevelState;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotRewardObjects {

    @Test
    void ringRewardAddsOneBonusStageRingOnExpiry() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);

        stepUntilExpired(reward, 0x1A - 1);

        assertFalse(reward.isDestroyed());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, controller.rewardCount());

        reward.update(0x1A, null);

        assertTrue(reward.isDestroyed());
        assertEquals(1, services.totalBonusStageRingDelta);
        assertEquals(1, controller.rewardCount());
    }

    @Test
    void spikeRewardConsumesOneAvailableBonusStageRingOnExpiry() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.addRewardRing();

        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);

        stepUntilExpired(reward, 0x1E - 1);

        assertFalse(reward.isDestroyed());
        assertEquals(1, controller.rewardCount());
        assertEquals(0, services.totalBonusStageRingDelta);

        reward.update(0x1E, null);

        assertTrue(reward.isDestroyed());
        assertEquals(0, controller.rewardCount());
        assertEquals(-1, services.totalBonusStageRingDelta);
    }

    @Test
    void spikeRewardConsumesOneCarriedRingWhenNoRewardCounterExists() {
        TrackingBonusStageServices services = new TrackingBonusStageServices(5);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);

        stepUntilExpired(reward, 0x1E);

        assertTrue(reward.isDestroyed());
        assertEquals(-1, controller.rewardCount());
        assertEquals(-1, services.totalBonusStageRingDelta);
    }

    @Test
    void spikeRewardDoesNotUnderflowWhenNoBonusStageRingIsAvailable() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);

        stepUntilExpired(reward, 0x1E);

        assertTrue(reward.isDestroyed());
        assertEquals(0, controller.rewardCount());
        assertEquals(0, services.totalBonusStageRingDelta);
    }

    private static void stepUntilExpired(S3kSlotRingRewardObjectInstance reward, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            reward.update(frame, null);
        }
    }

    private static void stepUntilExpired(S3kSlotSpikeRewardObjectInstance reward, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            reward.update(frame, null);
        }
    }

    private static final class TrackingBonusStageServices extends TestObjectServices {
        private int totalBonusStageRingDelta;
        private final LevelState levelState;

        private TrackingBonusStageServices() {
            this(0);
        }

        private TrackingBonusStageServices(int rings) {
            this.levelState = new FixedRingLevelState(rings);
        }

        @Override
        public LevelState levelGamestate() {
            return levelState;
        }

        @Override
        public void addBonusStageRings(int count) {
            totalBonusStageRingDelta += count;
        }
    }

    private static final class FixedRingLevelState implements LevelState {
        private int rings;

        private FixedRingLevelState(int rings) {
            this.rings = rings;
        }

        @Override public void update() { }
        @Override public int getRings() { return rings; }
        @Override public void addRings(int amount) { rings += amount; }
        @Override public void setRings(int rings) { this.rings = rings; }
        @Override public boolean isTimeOver() { return false; }
        @Override public String getDisplayTime() { return "0:00"; }
        @Override public boolean shouldFlashTimer() { return false; }
        @Override public boolean getFlashCycle() { return false; }
        @Override public void pauseTimer() { }
        @Override public void resumeTimer() { }
        @Override public boolean isTimerPaused() { return false; }
        @Override public int getElapsedSeconds() { return 0; }
        @Override public long getTimerFrames() { return 0; }
        @Override public void setTimerFrames(long frames) { }
    }
}
