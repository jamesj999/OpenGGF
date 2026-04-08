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

        stepFrames(reward, 0x19, player);

        assertFalse(reward.isDestroyed());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, player.liveRingDelta);
        assertEquals(0, controller.rewardCount());

        reward.update(0x19, player);

        // Ring is granted on expiry frame, but sparkle phase keeps it alive
        assertFalse(reward.isDestroyed());
        assertTrue(reward.isInSparkle());
        assertEquals(1, services.totalBonusStageRingDelta);
        assertEquals(1, player.liveRingDelta);
        assertEquals(1, controller.rewardCount());

        // Sparkle phase (8 frames) then destroyed
        stepFrames(reward, 8, player);
        assertTrue(reward.isDestroyed());
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

        stepFrames(reward, 0x1D, player);

        assertFalse(reward.isDestroyed());
        assertEquals(1, controller.rewardCount());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, player.liveRingDelta);

        reward.update(0x1D, player);

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

        stepFrames(reward, 0x1E, null);

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

        stepFrames(reward, 0x1E, null);

        assertTrue(reward.isDestroyed());
        assertEquals(0, controller.rewardCount());
        assertEquals(0, services.totalBonusStageRingDelta);
        assertEquals(0, services.levelState.getRings());
    }

    // -------------------------------------------------------------------------
    // Interpolated movement tests (ROM Obj_SlotRing / Obj_SlotSpike)
    // -------------------------------------------------------------------------

    @Test
    void ringRewardInterpolatesPositionTowardCenterEachFrame() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        int spawnX = 0x500, spawnY = 0x500;
        int centerX = 0x460, centerY = 0x430;

        S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(
                new ObjectSpawn(centerX, centerY, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate(spawnX, spawnY, centerX, centerY);

        assertEquals(spawnX, reward.getInterpolatedX());
        assertEquals(spawnY, reward.getInterpolatedY());

        reward.update(0, null);

        // After one frame: moved 1/16th of distance toward center
        // dx = (centerX - spawnX) = -0xA0; current += dx >> 4 = -0xA
        int expectedX = spawnX + ((centerX - spawnX) >> 4);
        int expectedY = spawnY + ((centerY - spawnY) >> 4);
        assertEquals(expectedX, reward.getInterpolatedX(),
                "X should move 1/16th toward center after one frame");
        assertEquals(expectedY, reward.getInterpolatedY(),
                "Y should move 1/16th toward center after one frame");

        // Position should be between spawn and center (closer to spawn after 1 frame)
        assertTrue(reward.getInterpolatedX() < spawnX,
                "X should have moved toward center (decreased)");
        assertTrue(reward.getInterpolatedY() < spawnY,
                "Y should have moved toward center (decreased)");
    }

    @Test
    void ringRewardConvergesOnCenterOverMultipleFrames() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        int spawnX = 0x500, spawnY = 0x500;
        int centerX = 0x460, centerY = 0x430;

        S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(
                new ObjectSpawn(centerX, centerY, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate(spawnX, spawnY, centerX, centerY);

        int prevX = reward.getInterpolatedX();
        int prevY = reward.getInterpolatedY();

        // Step several frames (fewer than EXPIRY_FRAMES = 0x1A)
        for (int frame = 0; frame < 5; frame++) {
            reward.update(frame, null);
            assertTrue(reward.getInterpolatedX() <= prevX,
                    "X should not move away from center at frame " + frame);
            assertTrue(reward.getInterpolatedY() <= prevY,
                    "Y should not move away from center at frame " + frame);
            prevX = reward.getInterpolatedX();
            prevY = reward.getInterpolatedY();
        }
        // After several frames, still converging (not yet at center due to exponential decay)
        assertTrue(reward.getInterpolatedX() < spawnX,
                "X should be closer to center than spawn");
        assertTrue(reward.getInterpolatedY() < spawnY,
                "Y should be closer to center than spawn");
    }

    @Test
    void ringRewardNoArgActivateDoesNotChangePosition() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        int spawnX = 0x460, spawnY = 0x430;

        S3kSlotRingRewardObjectInstance reward = new S3kSlotRingRewardObjectInstance(
                new ObjectSpawn(spawnX, spawnY, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate();

        // No-arg activate sets target = spawn, so no movement
        assertEquals(spawnX, reward.getInterpolatedX());
        assertEquals(spawnY, reward.getInterpolatedY());

        reward.update(0, null);

        // After one frame: dx = 0, so position stays the same
        assertEquals(spawnX, reward.getInterpolatedX(),
                "X should not move when target equals spawn");
        assertEquals(spawnY, reward.getInterpolatedY(),
                "Y should not move when target equals spawn");
    }

    @Test
    void spikeRewardInterpolatesPositionTowardCenterEachFrame() {
        TrackingBonusStageServices services = new TrackingBonusStageServices();
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.addRewardRing();

        int spawnX = 0x3C0, spawnY = 0x3C0;
        int centerX = 0x460, centerY = 0x430;

        S3kSlotSpikeRewardObjectInstance reward = new S3kSlotSpikeRewardObjectInstance(
                new ObjectSpawn(centerX, centerY, 0x00, 0x00, 0x00, false, 0), controller);
        reward.setServices(services);
        reward.activate(spawnX, spawnY, centerX, centerY);

        assertEquals(spawnX, reward.getInterpolatedX());
        assertEquals(spawnY, reward.getInterpolatedY());

        reward.update(0, null);

        int expectedX = spawnX + ((centerX - spawnX) >> 4);
        int expectedY = spawnY + ((centerY - spawnY) >> 4);
        assertEquals(expectedX, reward.getInterpolatedX(),
                "X should move 1/16th toward center after one frame");
        assertEquals(expectedY, reward.getInterpolatedY(),
                "Y should move 1/16th toward center after one frame");

        // Position moved toward center (increased from spawn which was < center)
        assertTrue(reward.getInterpolatedX() > spawnX,
                "X should have moved toward center (increased)");
        assertTrue(reward.getInterpolatedY() > spawnY,
                "Y should have moved toward center (increased)");
    }

    private static void stepFrames(S3kSlotRingRewardObjectInstance reward, int frames, Sonic player) {
        for (int frame = 0; frame < frames; frame++) {
            reward.update(frame, player);
        }
    }

    private static void stepFrames(S3kSlotSpikeRewardObjectInstance reward, int frames, Sonic player) {
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
