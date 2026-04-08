package com.openggf.game.sonic3k.objects;

import com.openggf.game.LevelState;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotRewardObjects {

    @Test
    void ringRewardAddsOneBonusStageRingToPlayableOnExpiry() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        TrackingPlayableSprite player = new TrackingPlayableSprite(0);
        S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate();

        stepUntilExpired(reward, 0x1A - 1, player);

        assertFalse(reward.isDestroyed());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, player.liveRingDelta);
        assertEquals(0, controller.rewardCount());

        reward.update(0x1A, player);

        assertTrue(reward.isDestroyed());
        assertEquals(1, services.totalBonusStageRingDelta);
        assertEquals(1, player.liveRingDelta);
        assertEquals(1, controller.rewardCount());
    }

    @Test
    void spikeRewardConsumesOneAvailableBonusStageRingAndUpdatesPlayableOnExpiry() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.addRewardRing();

        TrackingPlayableSprite player = new TrackingPlayableSprite(5);
        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate();

        stepUntilExpired(reward, 0x1E - 1, player);

        assertFalse(reward.isDestroyed());
        assertEquals(1, controller.rewardCount());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, player.liveRingDelta);

        reward.update(0x1E, player);

        assertTrue(reward.isDestroyed());
        assertEquals(0, controller.rewardCount());
        assertEquals(-1, services.totalBonusStageRingDelta);
        assertEquals(-1, player.liveRingDelta);
    }

    @Test
    void spikeRewardConsumesOneCarriedRingWhenNoRewardCounterExists() {
        TrackingBonusStageServices services = new TrackingBonusStageServices(5);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate();

        stepUntilExpired(reward, 0x1E, null);

        assertTrue(reward.isDestroyed());
        assertEquals(-1, controller.rewardCount());
        assertEquals(-1, services.totalBonusStageRingDelta);
        assertEquals(4, services.levelState.getRings());
    }

    @Test
    void spikeRewardDoesNotUnderflowWhenNoBonusStageRingIsAvailable() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate();

        stepUntilExpired(reward, 0x1E, null);

        assertTrue(reward.isDestroyed());
        assertEquals(0, controller.rewardCount());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, services.levelState.getRings());
    }

    private static void stepUntilExpired(S3kSlotRingRewardObjectInstance reward, int frames, Sonic player) {
        for (int frame = 0; frame < frames; frame++) {
            reward.update(frame, player);
        }
    }

    private static void stepUntilExpired(S3kSlotSpikeRewardObjectInstance reward, int frames, Sonic player) {
        for (int frame = 0; frame < frames; frame++) {
            reward.update(frame, player);
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

    private static final class TrackingPlayableSprite extends Sonic {
        private int ringCount;
        private int liveRingDelta;

        private TrackingPlayableSprite(int ringCount) {
            super("sonic", (short) 0x460, (short) 0x430);
            this.ringCount = ringCount;
        }

        @Override
        public void addRings(int delta) {
            liveRingDelta += delta;
            ringCount += delta;
        }

        @Override
        public int getRingCount() {
            return ringCount;
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
