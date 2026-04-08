package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Slot-machine spike reward child.
 *
 * <p>ROM reference: {@code Obj_SlotSpike}. When its timer expires it drains one bonus-stage
 * ring if any remain, then deletes itself.
 */
public final class S3kSlotSpikeRewardObjectInstance extends AbstractObjectInstance {

    private static final int EXPIRY_FRAMES = 0x1E;

    private final S3kSlotStageController controller;
    private int framesRemaining = EXPIRY_FRAMES;
    private boolean active;

    public S3kSlotSpikeRewardObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotSpikeReward");
        this.controller = controller;
    }

    public void activate() {
        active = true;
        framesRemaining = EXPIRY_FRAMES;
        setDestroyed(false);
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed() || !active) {
            return;
        }
        if (--framesRemaining > 0) {
            return;
        }

        int carriedRingCount = resolveCarriedRingCount(playerEntity);
        if (controller.consumeRewardRing(carriedRingCount)) {
            if (carriedRingCount > 0) {
                addLiveRings(playerEntity, -1);
            }
            services().addBonusStageRings(-1);
        }
        setDestroyed(true);
        active = false;
    }

    private int resolveCarriedRingCount(PlayableEntity playerEntity) {
        if (playerEntity instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
            return sprite.getRingCount();
        }
        return services().levelGamestate() != null ? services().levelGamestate().getRings() : 0;
    }

    private void addLiveRings(PlayableEntity playerEntity, int amount) {
        if (playerEntity instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
            sprite.addRings(amount);
            return;
        }
        if (services().levelGamestate() != null) {
            services().levelGamestate().addRings(amount);
        }
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
