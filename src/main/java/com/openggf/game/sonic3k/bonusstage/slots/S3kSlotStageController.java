package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

public class S3kSlotStageController {
    private int statTable;
    private int scalarIndex;  // SStage_scalar_index_1 — rotation velocity per frame
    private int rewardCounter;
    private int pendingRingRewards;
    private int pendingSpikeRewards;

    public void bootstrap() {
        statTable = 0;
        scalarIndex = 0x40;  // ROM line 98732
        rewardCounter = 0;
        pendingRingRewards = 0;
        pendingSpikeRewards = 0;
    }

    /** Per-frame rotation: Stat_table += SStage_scalar_index_1 (lines 98776-98778) */
    public void tick() {
        statTable = (statTable + scalarIndex) & 0xFF;
    }

    public int scalarIndex() {
        return scalarIndex;
    }

    public void setScalarIndex(int value) {
        scalarIndex = value;
    }

    /** ROM: neg.w (SStage_scalar_index_1).w — used by spike tile and cage release */
    public void negateScalar() {
        scalarIndex = -scalarIndex;
    }

    public void tickPlayer(S3kSlotBonusPlayer player, boolean left, boolean right,
                           boolean jump, int frameCounter) {
        // Input is handled by S3kSlotBonusPlayer.applyGroundMotion()
        // Controller only manages angle and jump
        player.setAngle((byte) statTable);

        if (jump && player instanceof AbstractPlayableSprite sprite
                && sprite.isJumpJustPressed() && !sprite.getAir()) {
            int angle = (-((statTable & 0xFC)) - 0x40) & 0xFF;
            sprite.setXSpeed((short) ((TrigLookupTable.cosHex(angle) * 0x680) >> 8));
            sprite.setYSpeed((short) ((TrigLookupTable.sinHex(angle) * 0x680) >> 8));
            sprite.setAir(true);
        }
    }

    public int angle() {
        return statTable;
    }

    public void addRewardRing() {
        rewardCounter++;
    }

    public void queueRingReward() {
        pendingRingRewards++;
    }

    public void queueSpikeReward() {
        pendingSpikeRewards++;
    }

    public boolean consumePendingRingReward() {
        if (pendingRingRewards <= 0) {
            return false;
        }
        pendingRingRewards--;
        return true;
    }

    public boolean consumePendingSpikeReward() {
        if (pendingSpikeRewards <= 0) {
            return false;
        }
        pendingSpikeRewards--;
        return true;
    }

    public boolean consumeRewardRing() {
        if (rewardCounter <= 0) {
            return false;
        }
        rewardCounter--;
        return true;
    }

    public boolean consumeRewardRing(int carriedRingCount) {
        if (carriedRingCount + rewardCounter <= 0) {
            return false;
        }
        rewardCounter--;
        return true;
    }

    public int rewardCount() {
        return rewardCounter;
    }
}
