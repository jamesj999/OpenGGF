package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $A3 - Clamer.
 *
 * <p>ROM reference: {@code Obj_Clamer} and child {@code loc_8908C} in
 * {@code docs/skdisasm/sonic3k.asm}. The visible parent owns a hidden spring
 * child at {@code y_pos - 8}; touching that child launches the player and sets
 * the parent close flag.
 */
public final class ClamerObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, TouchResponseAttackable {

    /*
     * The ROM child uses collision_flags $D7. In the engine, $C0 category bits
     * are still decoded as BOSS globally, so this object supplies SPECIAL bits
     * directly. The initial touch uses the ROM size index $17. Re-hits after
     * the child returns from loc_890C8 need to cover the previous-frame touch
     * latch window used by Check_PlayerCollision; S3K Touch_Sizes[$12] is 8x16,
     * which matches the observed Clamer spring re-hit in the CNZ trace.
     */
    private static final int PARENT_COLLISION_FLAGS = 0x0A;
    private static final int SPRING_COLLISION_FLAGS = 0x40 | 0x17;
    private static final int SPRING_RELATCH_COLLISION_FLAGS = 0x40 | 0x12;
    private static final int SPRING_OFFSET_Y = -8;
    private static final int LAUNCH_SPEED = 0x800;
    private static final int CLOSE_FRAMES = 10;
    private static final int CHILD_REENABLE_DELAY_FRAMES = 1;
    private static final int NO_REENABLE_FRAME = Integer.MIN_VALUE;
    private static final DestructionConfig S3K_DESTRUCTION_CONFIG = new DestructionConfig(
            Sonic3kSfx.BREAK.id,
            AnimalObjectInstance::new,
            false,
            (spawn, svc, pts) -> new Sonic3kPointsObjectInstance(spawn, svc, pts),
            null
    );

    private final int currentX;
    private final int currentY;
    private final boolean facingRight;

    private int mappingFrame;
    private int springReenableFrame = NO_REENABLE_FRAME;
    private int lastObservedFrameCounter;
    private int closeTimer;
    private boolean destroyed;

    public ClamerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Clamer");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.facingRight = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (destroyed) {
            return;
        }
        if (closeTimer > 0) {
            closeTimer--;
            mappingFrame = closeTimer == 0 ? 0 : Math.min(4, CLOSE_FRAMES - closeTimer);
        }
        lastObservedFrameCounter = frameCounter;
    }

    @Override
    public int getCollisionFlags() {
        return destroyed || closeTimer > 0 ? 0 : PARENT_COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return true;
    }

    @Override
    public TouchRegion[] getMultiTouchRegions() {
        if (destroyed) {
            return new TouchRegion[0];
        }

        TouchRegion parent = new TouchRegion(currentX, currentY, getCollisionFlags());
        if (!isSpringCollisionActive(currentFrameCounter())) {
            return new TouchRegion[] { parent };
        }

        TouchRegion spring = new TouchRegion(currentX, currentY + SPRING_OFFSET_Y, currentSpringCollisionFlags());
        return new TouchRegion[] { parent, spring };
    }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        lastObservedFrameCounter = frameCounter;
        if (!isSpringCollisionActive(frameCounter)
                || result.category() != TouchCategory.SPECIAL
                || !(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        springReenableFrame = frameCounter + CHILD_REENABLE_DELAY_FRAMES;
        closeTimer = CLOSE_FRAMES;
        applySpringLaunch(player);
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        if (destroyed) {
            return;
        }
        destroyed = true;
        int mySlot = getSlotIndex();
        setSlotIndex(-1);
        setDestroyed(true);
        DestructionEffects.destroyBadnik(
                currentX, currentY, spawn, mySlot, playerEntity, services(), S3K_DESTRUCTION_CONFIG);
    }

    private boolean isSpringCollisionActive(int frameCounter) {
        return springReenableFrame == NO_REENABLE_FRAME || frameCounter >= springReenableFrame;
    }

    private int currentSpringCollisionFlags() {
        return springReenableFrame == NO_REENABLE_FRAME
                ? SPRING_COLLISION_FLAGS
                : SPRING_RELATCH_COLLISION_FLAGS;
    }

    private int currentFrameCounter() {
        try {
            if (services() != null && services().levelManager() != null) {
                return services().levelManager().getFrameCounter();
            }
        } catch (Exception ignored) {
            // Some object unit tests use minimal service doubles.
        }
        return lastObservedFrameCounter;
    }

    private void applySpringLaunch(AbstractPlayableSprite player) {
        int xSpeed = facingRight ? -LAUNCH_SPEED : LAUNCH_SPEED;
        Direction direction = facingRight ? Direction.LEFT : Direction.RIGHT;

        player.setXSpeed((short) xSpeed);
        player.setGSpeed((short) xSpeed);
        player.setYSpeed((short) -LAUNCH_SPEED);
        player.setDirection(direction);
        player.setAir(true);
        player.setCentreYPreserveSubpixel((short) (player.getCentreY() + 6));
        player.setAnimationId(Sonic3kAnimationIds.SPRING);
        player.setJumping(false);

        try {
            services().playSfx(GameSound.SPRING);
        } catch (Exception ignored) {
            // Audio is not required for deterministic headless trace replay.
        }
    }

    @Override
    public ObjectSpawn getSpawn() {
        return buildSpawnAt(getX(), getY());
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return 5;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.CNZ_CLAMER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, facingRight, false);
    }
}
