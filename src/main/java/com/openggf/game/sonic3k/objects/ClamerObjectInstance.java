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
import com.openggf.level.objects.ObjectServices;
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

    /** ROM loc_88FEC: cmpi.w #$60, d2; bhs loc_8900C. */
    private static final int AUTO_CLOSE_DX_THRESHOLD = 0x60;

    /** Routine values from Clamer_Index (sonic3k.asm:185866-185874). */
    private static final int ROUTINE_IDLE = 0x02;
    private static final int ROUTINE_SNAP_SHUT = 0x04;
    private static final int ROUTINE_AUTO_CLOSE = 0x06;
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

    private int routine = ROUTINE_IDLE;
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
        lastObservedFrameCounter = frameCounter;

        // ROM Clamer_Index dispatch (sonic3k.asm:185860):
        //     move.b routine(a0), d0
        //     move.w Clamer_Index(pc, d0.w), d1
        //     jsr    Clamer_Index(pc, d1.w)
        switch (routine) {
            case ROUTINE_IDLE -> updateIdle(playerEntity);
            case ROUTINE_SNAP_SHUT -> updateSnapShut();
            case ROUTINE_AUTO_CLOSE -> updateAutoClose();
            default -> {
                // No-op for routine 0 (init runs once; we treat construction as init).
            }
        }
    }

    /**
     * ROM loc_88FEC (sonic3k.asm:185880-185902): idle / auto-close gate.
     * <pre>
     * loc_88FEC:
     *     btst    #0, $38(a0)             ; spring-fired flag
     *     bne.s   loc_89014               ; -> routine 4 (snap shut)
     *     jsr     Find_SonicTails(pc)     ; a1=closer player, d0=0/2 side, d2=abs(dx)
     *     cmpi.w  #$60, d2
     *     bhs.s   loc_8900C               ; abs(dx) >= $60: just animate idle
     *     btst    #0, render_flags(a0)
     *     beq.s   loc_89008
     *     subq.w  #2, d0                  ; flip side check when facing right
     * loc_89008:
     *     tst.w   d0
     *     beq.s   loc_89036               ; -> routine 6 (auto-close)
     * </pre>
     */
    private void updateIdle(PlayableEntity primary) {
        if (closeTimer > 0) {
            closeTimer--;
            mappingFrame = closeTimer == 0 ? 0 : Math.min(4, CLOSE_FRAMES - closeTimer);
            return;
        }

        // Find_SonicTails (sonic3k.asm:178243-178277): returns closer of Sonic/Tails
        // by abs(dx). d0 = 0 if closer is left of object, 2 if right.
        ClosestPlayer closest = findClosestPlayer(primary);
        if (closest == null) {
            return;
        }
        int dxAbs = Math.abs(closest.dx);
        if (dxAbs >= AUTO_CLOSE_DX_THRESHOLD) {
            return; // loc_8900C: just animate idle.
        }
        // ROM Find_SonicTails computes d2 = x_pos(a0) - x_pos(a1). bpl skips
        // the negate; on player-right (a0.x - a1.x < 0) the negate runs and d0
        // is incremented by 2. Engine's closest.dx is player - clamer, so:
        //     player on LEFT  (dx <= 0): d0 = 0
        //     player on RIGHT (dx >  0): d0 = 2
        int d0 = closest.dx > 0 ? 2 : 0;
        if (facingRight) {
            d0 -= 2; // subq.w #2, d0
        }
        if (d0 == 0) {
            // loc_89036: transition to routine 0x06 (auto-close).
            routine = ROUTINE_AUTO_CLOSE;
            mappingFrame = 0;
            closeTimer = CLOSE_FRAMES;
            // ROM loc_890AA runs independently of the parent's routine, so we
            // do not touch the spring child cooldown here.
        }
    }

    /**
     * ROM loc_8904E (sonic3k.asm:185919-185921): snap-shut animation after
     * spring-child fires. We collapse this onto our local close timer and
     * then return to routine 0x02 via loc_89056.
     */
    private void updateSnapShut() {
        if (closeTimer > 0) {
            closeTimer--;
            mappingFrame = closeTimer == 0 ? 0 : Math.min(4, CLOSE_FRAMES - closeTimer);
            return;
        }
        routine = ROUTINE_IDLE;
        mappingFrame = 0;
    }

    /**
     * ROM loc_89064 (sonic3k.asm:185930-185940): close animation triggered by
     * the auto-close gate. The original spring child is inactive while in this
     * routine; once the close animation completes the parent resets to
     * routine 0x02 via loc_89056.
     */
    private void updateAutoClose() {
        if (closeTimer > 0) {
            closeTimer--;
            mappingFrame = closeTimer == 0 ? 0 : Math.min(4, CLOSE_FRAMES - closeTimer);
            return;
        }
        routine = ROUTINE_IDLE;
        mappingFrame = 0;
    }

    @Override
    public int getCollisionFlags() {
        if (destroyed) {
            return 0;
        }
        // ROM loc_89014 (sonic3k.asm:185899-185906) clears collision_flags
        // during the snap-shut animation (routine 0x04). loc_89036 (auto-close)
        // does NOT clear collision_flags, so the parent collision box stays
        // alive while the auto-close anim plays.
        if (routine == ROUTINE_SNAP_SHUT) {
            return 0;
        }
        return closeTimer > 0 ? 0 : PARENT_COLLISION_FLAGS;
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
        // ROM loc_890AA (the spring child) runs independently of the parent's
        // routine, so the child's collision box stays active across routines
        // 0x02 / 0x04 / 0x06. Only the local post-fire cooldown disables it.
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

        // ROM loc_890AA (sonic3k.asm:185953-185962): spring child fires on
        // player collision, then sets bit 0 of $38 on parent. Parent's
        // loc_88FEC then transitions to routine 0x04 on next tick.
        springReenableFrame = frameCounter + CHILD_REENABLE_DELAY_FRAMES;
        closeTimer = CLOSE_FRAMES;
        routine = ROUTINE_SNAP_SHUT; // loc_89014
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
        ObjectServices svc = tryServices();
        if (svc != null && svc.levelManager() != null) {
            return svc.levelManager().getFrameCounter();
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

    /**
     * Engine equivalent of ROM Find_SonicTails (sonic3k.asm:178243-178277):
     * picks the closer of Sonic/Tails by abs(dx). Returns closer player and
     * its signed dx (positive when player is right of object).
     */
    private ClosestPlayer findClosestPlayer(PlayableEntity primary) {
        ClosestPlayer best = null;
        if (primary instanceof AbstractPlayableSprite leader && !leader.getDead()) {
            int dx = leader.getCentreX() - currentX;
            best = new ClosestPlayer(leader, dx);
        }
        ObjectServices svc = tryServices();
        if (svc != null) {
            for (PlayableEntity entity : svc.sidekicks()) {
                if (!(entity instanceof AbstractPlayableSprite s) || s.getDead()) {
                    continue;
                }
                int dx = s.getCentreX() - currentX;
                if (best == null || Math.abs(dx) < Math.abs(best.dx)) {
                    best = new ClosestPlayer(s, dx);
                }
            }
        }
        return best;
    }

    private record ClosestPlayer(AbstractPlayableSprite player, int dx) {
    }

    // Test hooks (package-private) ------------------------------------------

    /** Test-only: current ROM-style routine value. */
    int testRoutine() {
        return routine;
    }

    /** Test-only: directly run the auto-close evaluator with a primary player. */
    void testStepIdle(PlayableEntity primary) {
        updateIdle(primary);
    }
}
