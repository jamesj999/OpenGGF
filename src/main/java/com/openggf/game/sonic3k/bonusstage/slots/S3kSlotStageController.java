package com.openggf.game.sonic3k.bonusstage.slots;

public class S3kSlotStageController {
    private int statTable;
    private int rewardCounter;

    public void bootstrap() {
        statTable = 0;
        rewardCounter = 0;
    }

    public void tickPlayer(S3kSlotBonusPlayer player, boolean left, boolean right, boolean jump, int frameCounter) {
        if (left == right) {
            return;
        }
        statTable = (statTable + (right ? 4 : -4)) & 0xFC;
        player.setAngle((byte) statTable);
    }

    public int angle() {
        return statTable;
    }
}
