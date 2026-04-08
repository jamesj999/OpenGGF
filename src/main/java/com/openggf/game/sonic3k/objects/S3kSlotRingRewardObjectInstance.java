package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Slot-machine ring reward child.
 *
 * <p>ROM reference: {@code Obj_SlotRing}. The object interpolates its position toward the cage
 * center each frame (1/16th of remaining distance), and when its timer expires it awards one
 * bonus-stage ring and deletes itself.
 */
public final class S3kSlotRingRewardObjectInstance extends AbstractObjectInstance {

    private static final int EXPIRY_FRAMES = 0x1A;
    private static final int SPARKLE_FRAMES = 8;  // approximate sparkle duration (ROM routine 1)

    private final S3kSlotStageController controller;
    private int framesRemaining = EXPIRY_FRAMES;
    private boolean active;
    private boolean inSparkle;
    private int sparkleTimer;

    // ROM $34/$38: 32-bit fixed-point position (pixel:16 | sub:16)
    private long currentX32;
    private long currentY32;
    // ROM $3C/$3E: target pixel position (cage center)
    private int targetX;
    private int targetY;
    private int lastFrameCounter;

    public S3kSlotRingRewardObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotRingReward");
        this.controller = controller;
    }

    /** Activates without position tracking (backward-compatible; position stays at spawn). */
    public void activate() {
        activate(spawn.x(), spawn.y(), spawn.x(), spawn.y());
    }

    /**
     * Activates with interpolated movement toward the cage center.
     *
     * <p>ROM reference: {@code Obj_SlotRing} — spawned at a radial offset, converges on
     * {@code (centerX, centerY)} at 1/16th of the remaining distance each frame.
     */
    public void activate(int spawnX, int spawnY, int centerX, int centerY) {
        active = true;
        framesRemaining = EXPIRY_FRAMES;
        inSparkle = false;
        sparkleTimer = 0;
        setDestroyed(false);
        this.currentX32 = (long) spawnX << 16;
        this.currentY32 = (long) spawnY << 16;
        this.targetX = centerX;
        this.targetY = centerY;
        this.lastFrameCounter = 0;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Returns the current interpolated X position (pixel units).
     *
     * <p>Only meaningful while active.
     */
    public int getInterpolatedX() {
        return (int) (currentX32 >> 16);
    }

    /**
     * Returns the current interpolated Y position (pixel units).
     *
     * <p>Only meaningful while active.
     */
    public int getInterpolatedY() {
        return (int) (currentY32 >> 16);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed() || !active) {
            return;
        }
        lastFrameCounter = frameCounter;

        // ROM Obj_SlotRing routine 1: sparkle animation after ring grant
        if (inSparkle) {
            if (--sparkleTimer <= 0) {
                setDestroyed(true);
                active = false;
            }
            return;
        }

        // ROM Obj_SlotRing routine 0: interpolation toward cage center
        //   d0 = current - target ; asr.l #4,d0 ; sub.l d0,$34
        //   equivalent: current += (target - current) >> 4
        long dx = ((long) targetX << 16) - currentX32;
        currentX32 += dx >> 4;
        long dy = ((long) targetY << 16) - currentY32;
        currentY32 += dy >> 4;

        if (--framesRemaining > 0) {
            return;
        }

        // Grant ring and enter sparkle phase (ROM routine 1)
        controller.addRewardRing();
        addLiveRings(playerEntity, 1);
        services().addBonusStageRings(1);
        services().playSfx(Sonic3kSfx.RING_RIGHT.id);
        inSparkle = true;
        sparkleTimer = SPARKLE_FRAMES;
    }

    /** Returns true while in sparkle animation phase (ROM routine 1). */
    public boolean isInSparkle() {
        return inSparkle;
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
        if (!active || isDestroyed()) {
            return;
        }
        if (services().ringManager() == null) {
            return;
        }
        services().ringManager().drawRingAt(getInterpolatedX(), getInterpolatedY(), lastFrameCounter);
    }
}
