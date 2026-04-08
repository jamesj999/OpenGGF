package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Slot-machine bonus cage.
 *
 * <p>ROM reference: {@code Obj_Sonic_RotatingSlotBonus} cage state. Keeps the player
 * centered and locked inside the slot bonus cage.
 */
public final class S3kSlotBonusCageObjectInstance extends AbstractObjectInstance {

    private static final int CAPTURE_RADIUS = 0x18;

    private final S3kSlotStageController controller;
    private AbstractPlayableSprite capturedPlayer;

    public S3kSlotBonusCageObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotBonusCage");
        this.controller = controller;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player) || player.isDebugMode()) {
            return;
        }

        if (capturedPlayer != null && capturedPlayer != player) {
            return;
        }

        if (!isWithinCaptureRange(player) && capturedPlayer != player) {
            return;
        }

        capturedPlayer = player;
        centerPlayer(player);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(true);
        player.setObjectControlled(true);
        player.setAir(true);
        player.setOnObject(false);
    }

    private boolean isWithinCaptureRange(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - spawn.x());
        int dy = Math.abs(player.getCentreY() - spawn.y());
        return dx <= CAPTURE_RADIUS && dy <= CAPTURE_RADIUS;
    }

    private void centerPlayer(AbstractPlayableSprite player) {
        player.setCentreX((short) spawn.x());
        player.setCentreY((short) spawn.y());
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
        // Logic-only object for the slot bonus runtime.
    }
}
