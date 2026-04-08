package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Slot-machine ring reward child.
 *
 * <p>ROM reference: {@code Obj_SlotRing}. When its timer expires it awards one bonus-stage
 * ring and deletes itself.
 */
public final class S3kSlotRingRewardObjectInstance extends AbstractObjectInstance {

    private static final int EXPIRY_FRAMES = 0x1A;

    private final S3kSlotStageController controller;
    private int framesRemaining = EXPIRY_FRAMES;

    public S3kSlotRingRewardObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotRingReward");
        this.controller = controller;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        if (--framesRemaining > 0) {
            return;
        }

        controller.addRewardRing();
        services().addBonusStageRings(1);
        setDestroyed(true);
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
