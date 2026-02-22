package com.openggf.game.sonic1.objects.badniks;

import com.openggf.audio.AudioManager;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic2.objects.ExplosionObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance;
import com.openggf.game.sonic2.objects.badniks.AbstractBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.AnimalObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Caterkiller (0x78) - Segmented worm Badnik from Marble Zone and Scrap Brain Zone.
 * <p>
 * The Caterkiller is a multi-part enemy: a head with 3 trailing body segments.
 * The head walks along terrain, pausing periodically, while body segments follow
 * using a ring buffer of Y-deltas recorded by the head. Body segments alternate
 * between having legs (BodySeg2, animated) and not (BodySeg1, static).
 * <p>
 * The head uses a non-standard animation system: instead of the normal S1 animation
 * driver, it uses a direct lookup table (Ani_Cat) indexed by obAngle, advancing by 4
 * each frame. The table values (0-7) index different Y-offset head frames for a
 * bobbing effect.
 * <p>
 * Critically, body segments have obColType = $CB: the $C0 bit means they HURT the
 * player on any contact (even rolling/spinning). Only the head ($0B) can be destroyed
 * by rolling. This is a deliberate ROM behavior.
 * <p>
 * When a body segment is touched, Caterkiller enters "fragment" mode - each segment
 * gets a unique X velocity and bounces around independently.
 * If the head is destroyed normally, body segments delete with it.
 * <p>
 * Based on docs/s1disasm/_incObj/78 Caterkiller.asm.
 * <p>
 * Routine index:
 * <ul>
 *   <li>0 (Cat_Main): Fall until hitting floor, then init art/segments</li>
 *   <li>2 (Cat_Head): Main head behavior with secondary routines:
 *     <ul>
 *       <li>ob2ndRout=0 (.wait): Decrement timer, then start moving</li>
 *       <li>ob2ndRout=2 (loc_16B02): Walk with terrain following, record Y-deltas</li>
 *     </ul>
 *   </li>
 *   <li>$A (Cat_Delete): Cleanup</li>
 *   <li>$C (loc_16CC0): Fragment mode - bounce around after destruction</li>
 * </ul>
 */
public class Sonic1CaterkillerBadnikInstance extends AbstractBadnikInstance
        implements CaterkillerParentState {

    // From disassembly: obColType = $B (enemy, collision size index $B)
    private static final int COLLISION_SIZE_INDEX = 0x0B;

    // From disassembly: obHeight = 7, obWidth = 8
    private static final int Y_RADIUS = 7;

    // Walking velocity: move.w #-$C0,obVelX(a0)
    private static final int WALK_VELOCITY = 0xC0;

    // Inertia for body segment trailing: move.w #$40,obInertia(a0)
    private static final int BODY_INERTIA = 0x40;

    // Wait timer initial value: move.b #7,objoff_2A(a0)
    private static final int INITIAL_WAIT_TIMER = 7;

    // Move timer: move.b #$10,objoff_2A(a0) (16 frames)
    private static final int MOVE_TIMER = 0x10;

    // Wait timer between moves: move.b #7,objoff_2A(a0)
    private static final int INTER_MOVE_WAIT = 7;

    // Body segment spacing: moveq #$C,d5
    private static final int SEGMENT_SPACING = 0x0C;

    // Number of body segments: moveq #2,d1 → dbf → 3 iterations
    private static final int BODY_SEGMENT_COUNT = 3;

    // Floor detection thresholds from loc_16B02:
    // cmpi.w #-8,d1 / blt.s .loc_16B70 / cmpi.w #$C,d1 / bge.s .loc_16B70
    private static final int FLOOR_MIN_DIST = -8;
    private static final int FLOOR_MAX_DIST = 0x0C;

    // ObjectFall gravity: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;

    // Fragment velocities from Cat_FragSpeed: indexed by (obRoutine-2)
    // For head (routine 2): Cat_FragSpeed-2+2 → first entry → -$200
    // But head uses loc_16C96 which reads Cat_FragSpeed-2(pc,d0.w) where d0=obRoutine
    // Head routine=2: Cat_FragSpeed-2+2 = Cat_FragSpeed[0] = -$200
    private static final int HEAD_FRAG_X_VELOCITY = -0x200;

    // Fragment Y velocity: move.w #-$400,obVelY(a0)
    private static final int FRAG_Y_VELOCITY = -0x400;

    // Animation angle increment: addq.b #4,obAngle(a0)
    private static final int ANIM_ANGLE_STEP = 4;

    /**
     * Ani_Cat: Non-standard animation table from docs/s1disasm/_anim/Caterkiller.asm.
     * 128 bytes. Values 0-7 index mapping frames (head Y-offset variants).
     * $FF marks end of half-cycle (resets bit 7 of objoff_2B).
     * Accessed as: Ani_Cat[obAngle & 0x7F] → frame index.
     */
    static final int[] ANI_CAT = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,  // 0x00-0x0F
        1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3,  // 0x10-0x1F
        4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6,  // 0x20-0x2F
        6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 0xFF, 7, 7, 0xFF,  // 0x30-0x3F
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6,  // 0x40-0x4F
        6, 6, 6, 6, 6, 6, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4,  // 0x50-0x5F
        4, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1,  // 0x60-0x6F
        1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0, 0, 0xFF  // 0x70-0x7F
    };

    // Secondary routine states (ob2ndRout)
    private static final int STATE_WAIT = 0;
    private static final int STATE_MOVE = 1;

    // Instance state
    private boolean initialized;
    private int secondaryState;
    private int waitTimer;        // objoff_2A
    private int animControl;      // objoff_2B: bit 7 = animating, bit 4 = alternating flag
    private int animAngle;        // obAngle: animation table index
    private int currentDisplayFrame; // Computed mapping frame from last animation update
    private int inertia;          // obInertia: body trailing offset
    private int xSubpixel;
    private int ySubpixel;
    private int fallVelocity;
    private boolean fragmenting;
    private boolean deleting;

    // Ring buffer for Y-deltas (objoff_2C through objoff_2C+15)
    private final byte[] ringBuffer = new byte[16];

    // Ring buffer write pointer (cat_parent for head = low byte only)
    private int ringBufferWriteIndex;

    // Child body segment objects
    private final List<Sonic1CaterkillerBodyInstance> bodySegments = new ArrayList<>();

    public Sonic1CaterkillerBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Caterkiller");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        // S1: obStatus bit 0 set = xFlip (facing right in screen terms)
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlip;
        this.initialized = false;
        this.secondaryState = STATE_WAIT;
        this.waitTimer = INITIAL_WAIT_TIMER;
        this.animControl = 0;
        this.animAngle = 0;
        this.currentDisplayFrame = 0;
        this.inertia = 0;
        this.xSubpixel = 0;
        this.ySubpixel = 0;
        this.fallVelocity = 0;
        this.fragmenting = false;
        this.deleting = false;
        this.ringBufferWriteIndex = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        if (fragmenting) {
            updateFragment();
            return;
        }

        if (!initialized) {
            initialize();
            return;
        }

        // Cat_Head: check if status bit 7 set (indicating body segment triggered fragmentation)
        // tst.b obStatus(a0) / bmi.w loc_16C96
        // In our implementation, this is handled via the destroyed flag

        switch (secondaryState) {
            case STATE_WAIT -> updateWait();
            case STATE_MOVE -> updateMove();
        }
    }

    /**
     * Routine 0: Cat_Main - Fall under gravity until hitting floor.
     * Then initialize art, collision, and spawn 3 body segments.
     */
    private void initialize() {
        // ObjectFall: apply velocity, then gravity
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += fallVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;
        fallVelocity += GRAVITY;

        // ObjFloorDist: check floor from feet
        TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);

        // tst.w d1 / bpl.s locret_16950
        if (!floorResult.foundSurface() || floorResult.distance() >= 0) {
            return; // Still falling
        }

        // Floor found: snap to it
        currentY += floorResult.distance();  // add.w d1,obY(a0)
        fallVelocity = 0;                     // clr.w obVelY(a0)
        initialized = true;                   // addq.b #2,obRoutine(a0)

        // Spawn body segments
        spawnBodySegments();

        // move.b #7,objoff_2A(a0) - initial wait timer
        waitTimer = INITIAL_WAIT_TIMER;
        // clr.b cat_parent(a0) - clear ring buffer write index
        ringBufferWriteIndex = 0;
    }

    /**
     * Spawns 3 body segments behind the head, spaced 12px apart.
     * From Cat_Loop in disassembly.
     * <p>
     * Segments alternate between routine 4 (BodySeg1) and routine 6 (BodySeg2):
     * <pre>
     * move.b d6,obRoutine(a1) ; d6 starts at 4
     * addq.b #2,d6           ; alternate: 4, 6, 8 (but 8 maps to BodySeg1 again)
     * </pre>
     */
    private void spawnBodySegments() {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        int segX = currentX;
        int spacing = SEGMENT_SPACING;
        // ROM: btst #0,obStatus(a0) / beq.s .noflip / neg.w d5
        // Negate spacing when bit 0 is SET (xFlip/facing right)
        if (!facingLeft) {
            spacing = -spacing;
        }

        // d6 starts at 4, increments by 2: 4, 6, 8
        // Routine 4 → BodySeg1, 6 → BodySeg2, 8 → BodySeg1
        boolean[] isAnimated = { false, true, false };

        // Ring buffer indices: d4 starts at 4, increments by 4: 4, 8, 12
        int ringBufIdx = 4;
        CaterkillerParentState parentState = this;

        for (int i = 0; i < BODY_SEGMENT_COUNT; i++) {
            segX += spacing;

            Sonic1CaterkillerBodyInstance body = new Sonic1CaterkillerBodyInstance(
                    this, parentState, segX, currentY, facingLeft,
                    isAnimated[i], i, ringBufIdx, levelManager);
            bodySegments.add(body);
            objectManager.addDynamicObject(body);
            parentState = body;

            ringBufIdx += 4;
        }
    }

    /**
     * ob2ndRout=0 (.wait): Decrement timer. When expired, start moving.
     * <pre>
     * subq.b #1,objoff_2A(a0)
     * bmi.s  .move
     * rts
     * </pre>
     */
    private void updateWait() {
        waitTimer--;
        if (waitTimer >= 0) {
            return; // bmi.s .move - wait until negative
        }

        // Timer expired: transition to move
        secondaryState = STATE_MOVE;
        waitTimer = MOVE_TIMER;   // move.b #$10,objoff_2A(a0)

        // move.w #-$C0,obVelX(a0) / move.w #$40,obInertia(a0)
        xVelocity = -WALK_VELOCITY;
        inertia = BODY_INERTIA;

        // bchg #4,objoff_2B(a0) / bne.s loc_16AFC
        // Toggle bit 4 of animControl. If it WAS set (now clear), bne is taken.
        boolean wasBit4Set = (animControl & 0x10) != 0;
        animControl ^= 0x10;

        if (!wasBit4Set) {
            // bne.s not taken: bit 4 was clear, now set
            // clr.w obVelX(a0) / neg.w obInertia(a0)
            xVelocity = 0;
            inertia = -inertia;
        }

        // bset #7,objoff_2B(a0) - start animation
        animControl |= 0x80;
    }

    /**
     * ob2ndRout=2 (loc_16B02): Walk with terrain following.
     * Apply velocity, check floor, record Y-delta in ring buffer.
     * After move timer expires, return to wait state.
     * <p>
     * REV01 version used (has subpixel handling and direction change fixes).
     */
    private void updateMove() {
        waitTimer--;
        if (waitTimer < 0) {
            // .loc_16B5E: return to wait state
            secondaryState = STATE_WAIT;
            waitTimer = INTER_MOVE_WAIT;
            xVelocity = 0;
            inertia = 0;
            return;
        }

        // If not moving (xVelocity == 0), skip position update
        if (xVelocity == 0) {
            return;
        }

        // SpeedToPos: apply velocity with subpixel precision
        int effectiveVel = xVelocity;
        // btst #0,obStatus(a0) / beq.s .noflip / neg.w d0
        // Negate when bit 0 = 1 (xFlip / facing right)
        if (!facingLeft) {
            effectiveVel = -effectiveVel;
        }

        int oldXWhole = currentX;
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += effectiveVel;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        // swap d3 / cmp.w obX(a0),d3 / beq.s .notmoving
        if (currentX == oldXWhole) {
            return; // No whole-pixel movement
        }

        // ObjFloorDist: check floor
        TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        int floorDist = floorResult.foundSurface() ? floorResult.distance() : 100;

        // cmpi.w #-8,d1 / blt.s .loc_16B70 / cmpi.w #$C,d1 / bge.s .loc_16B70
        if (floorDist < FLOOR_MIN_DIST || floorDist >= FLOOR_MAX_DIST) {
            // Edge detected: reverse direction
            handleEdgeDetected(floorDist);
            return;
        }

        // Snap to floor: add.w d1,obY(a0)
        currentY += floorDist;

        // Record Y-delta in ring buffer
        ringBuffer[ringBufferWriteIndex] = (byte) floorDist;
        ringBufferWriteIndex = (ringBufferWriteIndex + 1) & 0x0F;
    }

    /**
     * Handles edge/wall detection during movement.
     * REV01 version: neg.w obX+2(a0) for subpixel reversal.
     * <pre>
     * .loc_16B70:
     *   moveq  #0,d0
     *   move.b cat_parent(a0),d0
     *   move.b #$80,objoff_2C(a0,d0.w)    ; write direction change marker
     *   neg.w  obX+2(a0)                   ; negate subpixel
     *   beq.s  .loc_1730A
     *   btst   #0,obStatus(a0)
     *   beq.s  .loc_1730A
     *   subq.w #1,obX(a0)
     *   addq.b #1,cat_parent(a0)
     *   ...clear next entry...
     * .loc_1730A:
     *   bchg   #0,obStatus(a0)
     *   ...
     *   addq.b #1,cat_parent(a0)
     *   andi.b #$F,cat_parent(a0)
     * </pre>
     */
    private void handleEdgeDetected(int floorDist) {
        // Write direction change marker to ring buffer
        ringBuffer[ringBufferWriteIndex] = (byte) 0x80;

        // REV01: negate subpixel
        // neg.w obX+2(a0) / beq.s .loc_1730A / btst #0,obStatus(a0) / beq.s .loc_1730A
        xSubpixel = (-xSubpixel) & 0xFFFF;
        if (xSubpixel != 0 && !facingLeft) {
            // subq.w #1,obX(a0) - adjust when bit 0 = 1 (facing right)
            currentX--;
            ringBufferWriteIndex = (ringBufferWriteIndex + 1) & 0x0F;
            ringBuffer[ringBufferWriteIndex] = 0;
        }

        // Reverse direction
        facingLeft = !facingLeft;

        // Advance ring buffer
        ringBufferWriteIndex = (ringBufferWriteIndex + 1) & 0x0F;
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (fragmenting || !initialized) {
            return;
        }

        // Head animation from Cat_Head:
        // move.b objoff_2B(a0),d1
        // bpl.s  .display                ; if bit 7 clear, skip animation
        if ((animControl & 0x80) == 0) {
            return;
        }

        // lea (Ani_Cat).l,a1
        // move.b obAngle(a0),d0
        // andi.w #$7F,d0
        int tableIndex = animAngle & 0x7F;

        // addq.b #4,obAngle(a0)
        animAngle += ANIM_ANGLE_STEP;

        // move.b (a1,d0.w),d0 / bpl.s .animate
        if (tableIndex < ANI_CAT.length) {
            int frameVal = ANI_CAT[tableIndex];
            if (frameVal == 0xFF) {
                // bclr #7,objoff_2B(a0) - end animation cycle
                animControl &= ~0x80;
                // bra.s .display - keep previous frame
            } else {
                // .animate: andi.b #$10,d1 / add.b d1,d0 / move.b d0,obFrame(a0)
                int offset = animControl & 0x10;
                currentDisplayFrame = frameVal + offset;
            }
        }
    }

    /**
     * Returns the current head mapping frame.
     * Computed during updateAnimation from the Ani_Cat lookup table.
     * During fragmentation, resets to base frame 0.
     */
    private int getHeadMappingFrame() {
        if (fragmenting) {
            // andi.b #$F8,obFrame(a0) - strip low 3 bits → base head frame 0
            return 0;
        }
        return currentDisplayFrame;
    }

    /**
     * Fragment physics for the head: ObjectFall + floor bounce.
     * From loc_16C96 and loc_16CC0.
     */
    private void updateFragment() {
        // ObjectFall: apply velocity + gravity
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += yVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;

        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += xVelocity;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        yVelocity += GRAVITY;

        // Floor bounce when falling
        if (yVelocity >= 0) {
            TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
            if (floorResult.foundSurface() && floorResult.distance() < 0) {
                currentY += floorResult.distance();
                yVelocity = FRAG_Y_VELOCITY;
            }
        }
    }

    @Override
    protected void destroyBadnik(AbstractPlayableSprite player) {
        destroyed = true;
        deleting = true;

        var objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            if (spawn.respawnTracked()) {
                objectManager.markRemembered(spawn);
            } else {
                objectManager.removeFromActiveSpawns(spawn);
            }
        }

        // Spawn explosion at head position
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, currentX, currentY,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);

        // Spawn animal
        AnimalObjectInstance animal = new AnimalObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x28, 0, 0, false, 0), levelManager);
        levelManager.getObjectManager().addDynamicObject(animal);

        // Award points
        int pointsValue = 100;
        if (player != null) {
            pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        Sonic1PointsObjectInstance points = new Sonic1PointsObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x29, 0, 0, false, 0), levelManager, pointsValue);
        levelManager.getObjectManager().addDynamicObject(points);

        AudioManager.getInstance().playSfx(Sonic1Sfx.BREAK_ITEM.id);

        // Normal head destruction does not use fragment mode in S1; body segments delete.
        markBodySegmentsForDeletion();
        setDestroyed(true);
    }

    /**
     * Starts fragment mode for the head.
     * From loc_16C96:
     * <pre>
     * moveq  #0,d0
     * move.b obRoutine(a0),d0
     * move.w Cat_FragSpeed-2(pc,d0.w),d0
     * btst   #0,obStatus(a0)
     * beq.s  loc_16CAA
     * neg.w  d0
     * move.w d0,obVelX(a0)
     * move.w #-$400,obVelY(a0)
     * move.b #$C,obRoutine(a0)
     * andi.b #$F8,obFrame(a0)
     * </pre>
     */
    private void startHeadFragment() {
        fragmenting = true;
        int fragXVel = HEAD_FRAG_X_VELOCITY;
        // btst #0,obStatus(a0) / beq.s / neg.w d0
        // Negate when bit 0 = 1 (xFlip / facing right)
        if (!facingLeft) {
            fragXVel = -fragXVel;
        }
        xVelocity = fragXVel;
        yVelocity = FRAG_Y_VELOCITY;
    }

    /**
     * Cat_Delete parity for dynamic children: when head is removed (destroyed or off-screen),
     * mark all spawned body segments for deletion so they cannot linger in dynamic lists.
     */
    private void markBodySegmentsForDeletion() {
        for (Sonic1CaterkillerBodyInstance body : bodySegments) {
            body.markDestroyed();
        }
        bodySegments.clear();
    }

    @Override
    public void onUnload() {
        deleting = true;
        markBodySegmentsForDeletion();
    }

    /**
     * Triggered by Caterkiller body contact (S1 React_Caterkiller).
     * This starts fragment behavior without awarding points or spawning explosions.
     */
    void triggerFragmentFromBodyHit() {
        if (deleting || destroyed || fragmenting) {
            return;
        }
        startHeadFragment();
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public boolean isPersistent() {
        if (deleting) {
            return false;
        }
        if (fragmenting) {
            // tst.b obRender(a0) / bpl.w Cat_ChkGone
            // ROM checks both X and Y via obRender on-screen flag.
            return isOnScreen(160);
        }
        return !destroyed && isOnScreenX(160);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4); // obPriority = 4
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (deleting) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.CATERKILLER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame = getHeadMappingFrame();
        // S1: default art faces left. hFlip when facing right (obStatus bit 0 set).
        renderer.drawFrameIndex(frame, currentX, currentY, !facingLeft, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        super.appendDebugRenderCommands(ctx);
        String label = "CatHead st=" + secondaryState
                + " wt=" + waitTimer
                + " rb=" + ringBufferWriteIndex
                + (fragmenting ? " FRAG" : "");
        ctx.drawWorldLabel(currentX, currentY, -12, label, Color.YELLOW);
    }

    // ---- Package-private accessors for body segments ----

    @Override
    public int getSecondaryState() {
        return secondaryState;
    }

    @Override
    public int getInertia() {
        return inertia;
    }

    @Override
    public int getXVelocity() {
        return xVelocity;
    }

    @Override
    public int getAnimControl() {
        return animControl;
    }

    boolean isDeleting() {
        return deleting;
    }

    boolean isFragmenting() {
        return fragmenting;
    }

    /**
     * Reads a value from the Y-delta ring buffer at the given index.
     * Body segments call this to read the head's recorded terrain deltas.
     */
    @Override
    public int readRingBuffer(int index) {
        return ringBuffer[index & 0x0F];
    }

    /**
     * Writes a value to the Y-delta ring buffer at the given index.
     * Body segments write their own Y-deltas for segments behind them.
     */
    void writeRingBuffer(int index, byte value) {
        ringBuffer[index & 0x0F] = value;
    }

    /**
     * Writes a value to the Y-delta ring buffer at the given index.
     * Convenience overload accepting int.
     */
    @Override
    public void writeRingBuffer(int index, int value) {
        ringBuffer[index & 0x0F] = (byte) value;
    }
}
