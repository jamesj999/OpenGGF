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
     * ROM Clamer spring child collision_flags = $D7 ($C0 | $17), set once at
     * spawn by word_89136 (sonic3k.asm:185986+) and never modified. Size index
     * $17 = 8x8 collision rect. The high $C0 bits would normally decode to
     * BOSS in engine, but $17 is one of the Touch_Special property indices
     * (sonic3k.asm:21165-21194), so usesS3kTouchSpecialPropertyResponse() = true
     * routes the rect through SPECIAL with a cprop-style latch. This object
     * exposes the rect with the ROM-correct $D7 flags every frame the spring
     * is "in the response list" -- never widening to $12 (engine-only hack).
     */
    private static final int PARENT_COLLISION_FLAGS = 0x0A;
    private static final int SPRING_COLLISION_FLAGS = 0x40 | 0x17;
    private static final int SPRING_OFFSET_Y = -8;
    private static final int LAUNCH_SPEED = 0x800;
    private static final int CLOSE_FRAMES = 10;

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

    /**
     * Spring-child routine state, mirroring the ROM (a0) pointer cycle at
     * sonic3k.asm:185953-185973. LIVE = loc_890AA (in list, can fire).
     * COOLDOWN_DRAIN = loc_890C8 cooldown frame (NOT added to list).
     * COOLDOWN_DONE = engine-only intermediate; (a0)=loc_890AA but slot was
     * NOT in last frame list, so Sonic touch walk skips this slot and any
     * latched collision_property survives across the frame boundary.
     */
    private enum SpringRoutine { LIVE, COOLDOWN_DRAIN, COOLDOWN_DONE }

    private final int currentX;
    private final int currentY;
    private final boolean facingRight;

    private int routine = ROUTINE_IDLE;
    private int mappingFrame;
    private SpringRoutine springRoutine = SpringRoutine.LIVE;
    private boolean springCprop;
    private boolean springInListLastFrame = true;
    /**
     * True when {@link #onTouchResponse} fired the spring during this engine
     * frame touch phase. Recorded so the subsequent
     * {@link #advanceSpringRoutine} call still simulates ROM
     * {@code loc_890C4: jmp Child_DrawTouch_Sprite} (sonic3k.asm:185961-185962)
     * which adds the slot to the response list AFTER the fire branch sets
     * {@code (a0) = loc_890C8}.
     */
    private boolean firedDuringThisFramesTouch;
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

        // ROM Clamer_Index dispatch (sonic3k.asm:185860).
        switch (routine) {
            case ROUTINE_IDLE -> updateIdle(playerEntity);
            case ROUTINE_SNAP_SHUT -> updateSnapShut();
            case ROUTINE_AUTO_CLOSE -> updateAutoClose();
            default -> {
                // No-op for routine 0 (init runs once; we treat construction as init).
            }
        }

        // Spring-child update mirrors ROM (a0)=loc_890AA / loc_890C8 / loc_890D0.
        // ROM slot order runs Sonic (slot 0) BEFORE the spring child (slot 5),
        // so this update sees collision_property already latched by the
        // current frame Touch_Special walk. The engine mirrors that order by
        // running player touch responses inside the per-player physics tick
        // (LevelFrameStep step 2: physics) BEFORE this object update
        // (LevelFrameStep step 3: objects).
        advanceSpringRoutine(playerEntity);
    }

    /**
     * Advances the ROM (a0) cycle and the Add_SpriteToCollisionResponseList
     * timing (sonic3k.asm:185953-185973). Only loc_890AA reaches loc_890C4
     * which jumps to Child_DrawTouch_Sprite (sonic3k.asm:178048-178053) --
     * the only path that calls Add_SpriteToCollisionResponseList. The
     * cooldown frame and its tail-call into loc_890D0 both return via plain
     * rts, leaving the slot absent from the list for one frame. This is the
     * exact mechanism that delays Sonic next overlap test by one frame,
     * allowing the collision_property latch set during the cooldown frame to
     * fire on the frame after the cooldown.
     */
    private void advanceSpringRoutine(PlayableEntity playerEntity) {
        boolean addToList;
        switch (springRoutine) {
            case LIVE -> {
                if (firedDuringThisFramesTouch) {
                    // onTouchResponse already fired the spring this frame
                    // (engine-side touch-phase handling). ROM loc_890AA still
                    // jumps through loc_890C4 to Child_DrawTouch_Sprite after
                    // the fire branch, so the slot remains in this frame list.
                    // Transition to COOLDOWN_DRAIN to mirror the ROM
                    // (a0)=loc_890C8 store.
                    springRoutine = SpringRoutine.COOLDOWN_DRAIN;
                } else if (springCprop && playerEntity instanceof AbstractPlayableSprite player) {
                    // ROM loc_890AA (sonic3k.asm:185953-185962): bsr.w
                    // Check_PlayerCollision; beq.s loc_890C4 (no fire branch).
                    // Non-zero collision_property -> Check_PlayerCollision
                    // (sonic3k.asm:179904-179916) clears the byte and fires.
                    // Reached when a latch survived through cooldown into the
                    // post-cooldown LIVE state without a touch-phase fire.
                    springCprop = false;
                    fireSpring(player);
                    springRoutine = SpringRoutine.COOLDOWN_DRAIN;
                }
                // ROM loc_890C4: jmp Child_DrawTouch_Sprite is reached on
                // both fire and no-fire branches.
                addToList = true;
            }
            case COOLDOWN_DRAIN -> {
                // ROM loc_890C8 (sonic3k.asm:185965-185968): subq.w #1, $2E(a0);
                // bmi.s loc_890D0; rts. Both paths skip Child_DrawTouch_Sprite,
                // so the slot is NOT added to the list this frame.
                springRoutine = SpringRoutine.COOLDOWN_DONE;
                addToList = false;
            }
            case COOLDOWN_DONE -> {
                // Engine-only intermediate state. Sonic touch walk this frame
                // did NOT see the spring slot (springInListLastFrame was
                // false going into touch), so collision_property survives.
                // If the latch is set from the prior cooldown frame touch
                // walk, fire now -- this is the F=621 fire in the trace.
                if (springCprop && playerEntity instanceof AbstractPlayableSprite player) {
                    springCprop = false;
                    fireSpring(player);
                    springRoutine = SpringRoutine.COOLDOWN_DRAIN;
                } else {
                    springRoutine = SpringRoutine.LIVE;
                }
                addToList = true;
            }
            default -> addToList = false;
        }
        springInListLastFrame = addToList && !destroyed;
        firedDuringThisFramesTouch = false;
    }

    private void fireSpring(AbstractPlayableSprite player) {
        // ROM loc_890AA fire branch (sonic3k.asm:185955-185959):
        //     move.l #loc_890C8, (a0)
        //     bsr.w  sub_890D8           (apply launch velocities)
        //     movea.w parent3(a0), a1
        //     bset   #0, $38(a1)         (signal parent snap-shut)
        applySpringLaunch(player);
        closeTimer = CLOSE_FRAMES;
        routine = ROUTINE_SNAP_SHUT;
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
    public boolean usesS3kTouchSpecialPropertyResponse() {
        // ROM Touch_ChkValue (sonic3k.asm:20773-20778) routes objects with
        // collision_flags high bits = $C0 to Touch_Special. The spring child
        // uses cflags = $D7 (size $17), which is one of the Touch_Special
        // property indices (sonic3k.asm:21165-21194). Without this hook the
        // engine decoder maps $C0 to BOSS, blocking SPECIAL dispatch.
        return true;
    }

    @Override
    public TouchRegion[] getMultiTouchRegions() {
        if (destroyed) {
            return new TouchRegion[0];
        }

        TouchRegion parent = new TouchRegion(currentX, currentY, getCollisionFlags());
        // The spring rect is exposed only when the spring slot was in the
        // ROM Collision_response_list at the end of the previous frame.
        // ROM Process_Sprites (sonic3k.asm:35965+) runs slot 3
        // (Reserved_object_3 / Obj_ResetCollisionResponseList,
        // sonic3k.asm:8467) which clears the list before slots 4+ repopulate
        // it, but slot 0 (Sonic) reads the list before the clear. So Sonic
        // touch walk at frame F sees slot 5 last-frame list state -- only
        // present when loc_890AA ran during F-1.
        if (!springInListLastFrame) {
            return new TouchRegion[] { parent };
        }

        TouchRegion spring = new TouchRegion(currentX, currentY + SPRING_OFFSET_Y, SPRING_COLLISION_FLAGS);
        return new TouchRegion[] { parent, spring };
    }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        lastObservedFrameCounter = frameCounter;
        if (result.category() != TouchCategory.SPECIAL
                || !(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        // ROM Touch_Special (sonic3k.asm:21162-21194) writes
        // collision_property(a1) for size index $17 every overlap.
        // Check_PlayerCollision inside loc_890AA consumes it on the next
        // post-cooldown spring update.
        if (springRoutine == SpringRoutine.LIVE && !firedDuringThisFramesTouch) {
            // Engine ordering: this touch response runs inside the player
            // physics tick (LevelFrameStep step 2), one phase BEFORE the
            // spring update (step 3). To match the same-frame fire seen in
            // ROM at loc_890AA, fire here directly. The state transition to
            // COOLDOWN_DRAIN happens during this frame advanceSpringRoutine
            // call, which also sets springInListLastFrame=true to mirror the
            // ROM loc_890C4 -> Child_DrawTouch_Sprite tail call after the
            // fire branch.
            springCprop = false;
            fireSpring(player);
            firedDuringThisFramesTouch = true;
            return;
        }
        // Cooldown frames (COOLDOWN_DRAIN / COOLDOWN_DONE) latch the cprop
        // byte without firing. The fire happens during the next non-cooldown
        // update phase, mirroring the ROM Touch_Special -> next-frame
        // loc_890AA -> Check_PlayerCollision sequence.
        springCprop = true;
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
