package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomData;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

import static com.openggf.physics.TrigLookupTable.cosHex;
import static com.openggf.physics.TrigLookupTable.sinHex;

/**
 * Slot-machine bonus cage.
 *
 * <p>ROM reference: {@code sub_4C014} / {@code loc_4BF62}, lines 99308-99557.
 */
public final class S3kSlotBonusCageObjectInstance extends AbstractObjectInstance {

    private static final int CAPTURE_RADIUS = 0x18;
    private static final int MAX_ACTIVE_REWARDS = 0x10;
    private static final int RING_ANGLE_INCREMENT = 0x89;
    private static final int SPIKE_ANGLE_INCREMENT = 0x90;

    private static final short SNAP_X = S3kSlotRomData.SLOT_BONUS_START_X;
    private static final short SNAP_Y = S3kSlotRomData.SLOT_BONUS_START_Y;

    private final S3kSlotStageController controller;

    private int cageState;
    private int waitTimer;
    private int rewardAngle;
    private int rewardsToSpawn;
    private boolean spawnRings;
    private int sfxCounter;

    private short currentX = SNAP_X;
    private short currentY = SNAP_Y;
    private int mappingFrame;
    private int animFrameTimer = 1;
    private int armDelayFrames;

    public S3kSlotBonusCageObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotBonusCage");
        this.controller = controller;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player) || player.isDebugMode()) {
            return;
        }

        updateAnimatedPosition(player);
        tickAnimation();

        switch (cageState) {
            case 0 -> updateCapture(player);
            case 1 -> updateSpawnRewards(player, frameCounter);
            case 2 -> updateRelease(player);
            case 3 -> updateCooldown();
            default -> {
            }
        }
    }

    private void updateCapture(AbstractPlayableSprite player) {
        if (armDelayFrames > 0) {
            armDelayFrames--;
            return;
        }
        if (player.isObjectControlled() || !isWithinCaptureRange(player)) {
            return;
        }

        player.setCentreX(SNAP_X);
        player.setCentreY(SNAP_Y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(true);
        player.setObjectControlled(true);
        player.setAir(true);
        player.setOnObject(false);

        int payout = controller.beginCapturePayout();
        spawnRings = payout >= 0;
        rewardsToSpawn = Math.abs(payout);
        rewardAngle = 0;
        sfxCounter = 0;
        controller.setPaletteCycleEnabled(true);

        waitTimer = 0x78;
        cageState = 1;
    }

    private boolean isWithinCaptureRange(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - currentX);
        int dy = Math.abs(player.getCentreY() - currentY);
        return dx < CAPTURE_RADIUS && dy < CAPTURE_RADIUS;
    }

    private void updateSpawnRewards(AbstractPlayableSprite player, int frameCounter) {
        player.setCentreX(SNAP_X);
        player.setCentreY(SNAP_Y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        if ((frameCounter & 1) != 0 && controller.activeRewardObjects() < MAX_ACTIVE_REWARDS && rewardsToSpawn > 0) {
            int centerX = currentX;
            int centerY = currentY;
            if (spawnRings) {
                int spawnX = centerX + (cosHex(rewardAngle) >> 1);
                int spawnY = centerY + (sinHex(rewardAngle) >> 1);
                controller.queueRingRewardAt(spawnX, spawnY, centerX, centerY);
                rewardAngle = (rewardAngle + RING_ANGLE_INCREMENT) & 0xFF;
            } else {
                int spawnX = centerX + (cosHex(rewardAngle) >> 1);
                int spawnY = centerY + (sinHex(rewardAngle) >> 1);
                controller.queueSpikeRewardAt(spawnX, spawnY, centerX, centerY);
                rewardAngle = (rewardAngle + SPIKE_ANGLE_INCREMENT) & 0xFF;
            }
            rewardsToSpawn--;
        }

        sfxCounter++;
        if ((sfxCounter & 0x0F) == 0) {
            services().playSfx(Sonic3kSfx.SLOT_MACHINE.id);
        }

        // ROM loc_4C23E: transition when spawn quota exhausted (not waiting for expiry)
        if (rewardsToSpawn <= 0) {
            waitTimer = 8;
            cageState = 2;
            controller.setPaletteCycleEnabled(false);
        }
    }

    private void updateRelease(AbstractPlayableSprite player) {
        if ((controller.angle() & 0x3C) != 0) {
            return;
        }

        int angle = controller.angle() & 0xFC;
        short vx = (short) (TrigLookupTable.cosHex(angle) * 4);
        short vy = (short) (TrigLookupTable.sinHex(angle) * 4);
        player.setXSpeed(vx);
        player.setYSpeed(vy);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setAir(true);
        controller.negateScalar();
        controller.endCapturePayout();

        waitTimer = 8;
        cageState = 3;
    }

    private void updateCooldown() {
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        cageState = 0;
        rewardsToSpawn = 0;
        rewardAngle = 0;
        sfxCounter = 0;
        controller.setPaletteCycleEnabled(false);
    }

    private void updateAnimatedPosition(AbstractPlayableSprite player) {
        int angle = controller.angle() & 0xFC;
        int sin = sinHex(angle);
        int cos = cosHex(angle);
        int dx = SNAP_X - player.getX();
        int dy = SNAP_Y - player.getY();
        int x = (((dx * cos) - (dy * sin)) >> 8) + player.getX();
        int y = (((dx * sin) + (dy * cos)) >> 8) + player.getY();
        currentX = (short) x;
        currentY = (short) y;
    }

    private void tickAnimation() {
        if (--animFrameTimer >= 0) {
            return;
        }
        animFrameTimer = 1;
        mappingFrame++;
        if (mappingFrame >= 6) {
            mappingFrame = 0;
        }
    }

    public int cageStateForTest() {
        return cageState;
    }

    public void suppressInitialCaptureOnce() {
        armDelayFrames = 1;
    }

    public boolean spawnsRingsForTest() {
        return spawnRings;
    }

    public int pendingRewardsForTest() {
        return rewardsToSpawn;
    }

    public short getCurrentX() {
        return currentX;
    }

    public short getCurrentY() {
        return currentY;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(0);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SLOT_BONUS_CAGE);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }
}
