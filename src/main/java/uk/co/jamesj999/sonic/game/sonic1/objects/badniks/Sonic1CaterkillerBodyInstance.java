package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseListener;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * Shared movement/animation state exposed by each Caterkiller segment to its child.
 * In the disassembly this is the parent object pointer chain (a1 = cat_parent(a0)).
 */
interface CaterkillerParentState {
    int getSecondaryState();
    int getInertia();
    int getXVelocity();
    int getAnimControl();
    int readRingBuffer(int index);
    void writeRingBuffer(int index, int value);
}

/**
 * Caterkiller body segment (routines 4/6/8 in the disassembly).
 * Each body segment follows its parent (the segment ahead of it) by reading
 * Y-delta values from the parent's ring buffer and applying the parent's velocity
 * with an additional inertia offset.
 * <p>
 * Body segments use obColType = $CB: the $C0 flag means "hurt player on contact"
 * (Sonic takes damage even when rolling into a body segment - this is ROM-accurate).
 * <p>
 * There are two alternating types of body segment:
 * <ul>
 *   <li>BodySeg1 (routines 4 and 8): follows parent, no independent animation</li>
 *   <li>BodySeg2 (routine 6): follows parent AND has independent leg animation</li>
 * </ul>
 * <p>
 * Based on docs/s1disasm/_incObj/78 Caterkiller.asm (Cat_BodySeg1, Cat_BodySeg2).
 */
public class Sonic1CaterkillerBodyInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, CaterkillerParentState {

    // Collision size index from disassembly (obColType low bits = $0B).
    private static final int COLLISION_SIZE_INDEX = 0x0B;

    // Fragment velocities from Cat_FragSpeed: dc.w -$200, -$180, $180, $200
    // Indexed by (obRoutine - 2): routine 4 → -$200, 6 → -$180, 8 → $180
    private static final int[] FRAG_X_SPEEDS = { -0x200, -0x180, 0x180, 0x200 };

    // Frag Y velocity: move.w #-$400,obVelY(a0)
    private static final int FRAG_Y_VELOCITY = -0x400;

    // ObjectFall gravity: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;

    // Ring buffer marker for direction change
    private static final int DIRECTION_CHANGE_MARKER = 0x80;

    int currentX;
    int currentY;
    private int xSubpixel;
    private boolean facingLeft;
    private boolean destroyed;
    private boolean fragmenting;

    // Body segment type: true if this is a BodySeg2 (has independent animation)
    private final boolean isAnimatedSegment;

    // Index into Cat_FragSpeed for fragment X velocity (based on routine index)
    private final int fragSpeedIndex;

    // Ring buffer read pointer (cat_parent low byte)
    private int ringBufferIndex;

    // Root head reference used for lifecycle/despawn checks.
    private final Sonic1CaterkillerBadnikInstance head;
    // Immediate parent in the segment chain (head for seg1, seg1 for seg2, etc.).
    private final CaterkillerParentState parentState;

    // Velocity state
    private int xVelocity;
    private int yVelocity;
    private int ySubpixel;
    private int inertia;

    // Animation state (for BodySeg2 only)
    private int animAngle;
    private int secondaryState; // mirrors parent's ob2ndRout

    // Copy of parent's objoff_2B for animation control
    private int animControl;
    // Per-segment ring buffer (objoff_2C+0..15). Child segments read from this.
    private final byte[] ringBuffer = new byte[16];

    private final LevelManager levelManager;

    /**
     * Creates a Caterkiller body segment.
     *
     * @param head            the head object (parent chain root)
     * @param x               initial X position
     * @param y               initial Y position
     * @param facingLeft      initial facing direction (from obStatus bit 0)
     * @param isAnimated      true for BodySeg2 (routine 6), false for BodySeg1 (routine 4/8)
     * @param segmentIndex    0-based index (0=first body, 1=second, 2=third/tail)
     * @param ringBufferStart initial ring buffer read index
     * @param levelManager    level manager reference
     */
    public Sonic1CaterkillerBodyInstance(
            Sonic1CaterkillerBadnikInstance head,
            CaterkillerParentState parentState,
            int x, int y, boolean facingLeft,
            boolean isAnimated, int segmentIndex,
            int ringBufferStart,
            LevelManager levelManager) {
        super(new ObjectSpawn(x, y, 0x78, 0, 0, false, 0), "CaterkillerBody");
        this.head = head;
        this.parentState = parentState;
        this.currentX = x;
        this.currentY = y;
        this.facingLeft = facingLeft;
        this.isAnimatedSegment = isAnimated;
        this.ringBufferIndex = ringBufferStart;
        this.levelManager = levelManager;
        this.destroyed = false;
        this.fragmenting = false;
        this.xSubpixel = 0;
        this.ySubpixel = 0;
        this.xVelocity = 0;
        this.yVelocity = 0;
        this.inertia = 0;
        this.animAngle = 0;
        this.secondaryState = 0;
        this.animControl = 0;

        // Fragment speed index: segments alternate routine 4/6/4 → frag indices 0/1/2
        // (routine 4 → index 0, routine 6 → index 1, routine 8 → index 2)
        this.fragSpeedIndex = segmentIndex;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        if (fragmenting) {
            updateFragment();
            return;
        }

        // If the head has entered fragment mode (including body-hit trigger), this segment
        // should also fragment.
        if (head.isDestroyed() || head.isDeleting() || head.isFragmenting()) {
            startFragmenting();
            return;
        }

        // Copy animation control from immediate parent
        animControl = parentState.getAnimControl();

        if (isAnimatedSegment) {
            // Cat_BodySeg2: check animation, then fall through to Cat_BodySeg1
            updateBodySeg2Animation();
        }

        // Cat_BodySeg1: follow parent movement
        updateBodySeg1Movement();
    }

    /**
     * Cat_BodySeg2 (routine 6): Independent leg animation for animated body segments.
     * Copies objoff_2B from parent. If bit 7 set (animating), reads from Ani_Cat table
     * at a phase offset. Advances obAngle by 4 each frame; if the value 4 entries ahead
     * is $FF, skips an extra 4.
     */
    private void updateBodySeg2Animation() {
        if ((animControl & 0x80) == 0) {
            return; // Not animating
        }

        int tableIndex = animAngle & 0x7F;
        animAngle += 4;

        // tst.b 4(a1,d0.w) / bpl.s Cat_AniBody
        // Check if the value 4 entries ahead is negative ($FF)
        int lookAheadIndex = (tableIndex + 4) & 0x7F;
        if (lookAheadIndex < Sonic1CaterkillerBadnikInstance.ANI_CAT.length
                && Sonic1CaterkillerBadnikInstance.ANI_CAT[lookAheadIndex] < 0) {
            animAngle += 4; // addq.b #4,obAngle(a0) - skip past $FF marker
        }
    }

    /**
     * Cat_BodySeg1 (routines 4/8): Follow parent by applying parent's velocity + inertia,
     * then read Y-delta from parent's ring buffer.
     * <p>
     * From disassembly:
     * <pre>
     * move.w obInertia(a1),obInertia(a0)
     * move.w obVelX(a1),d0
     * add.w  obInertia(a0),d0
     * move.w d0,obVelX(a0)
     * </pre>
     */
    private void updateBodySeg1Movement() {
        // Copy secondary state from immediate parent
        secondaryState = parentState.getSecondaryState();

        if (secondaryState == 0) {
            // Head is in wait state - no movement
            return;
        }

        // Copy inertia/velocity from immediate parent and compute effective velocity
        inertia = parentState.getInertia();
        int parentXVelocity = parentState.getXVelocity();
        xVelocity = parentXVelocity + inertia;

        // SpeedToPos: apply velocity to position
        int effectiveVel = xVelocity;
        // btst #0,obStatus(a0) / beq.s / neg.w d0
        // Negate when bit 0 = 1 (xFlip / facing right)
        if (!facingLeft) {
            effectiveVel = -effectiveVel;
        }

        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int oldXWhole = currentX;
        xPos24 += effectiveVel;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        // swap d3 / cmp.w obX(a0),d3 / beq.s loc_16C64
        // If X didn't change, skip terrain following
        if (currentX == oldXWhole) {
            return;
        }

        // Read Y-delta from parent ring buffer at this segment's read pointer
        int bufferIndex = ringBufferIndex;
        int yDelta = parentState.readRingBuffer(bufferIndex);

        if ((yDelta & 0xFF) == (DIRECTION_CHANGE_MARKER & 0xFF)) {
            // Direction change marker ($80): reverse direction and advance ring buffer
            // REV01: neg.w obX+2(a0) / beq.s / btst #0 / beq.s / cmpi.w #-$C0 / bne.s
            writeRingBuffer(bufferIndex, yDelta);
            xSubpixel = (-xSubpixel) & 0xFFFF;
            if (xSubpixel != 0 && !facingLeft && xVelocity == -0xC0) {
                // subq.w #1,obX(a0) - adjust when bit 0 = 1 (facing right)
                currentX--;
                ringBufferIndex = (ringBufferIndex + 1) & 0x0F;
                writeRingBuffer(ringBufferIndex, 0);
            }

            // bchg #0,obStatus(a0) - reverse direction
            facingLeft = !facingLeft;
            ringBufferIndex = (ringBufferIndex + 1) & 0x0F;
        } else {
            // Normal Y-delta: apply to position
            // ext.w d1 / add.w d1,obY(a0)
            currentY += (byte) yDelta; // sign-extend byte to int

            // Store the same delta for this segment's child.
            writeRingBuffer(bufferIndex, yDelta);

            // Advance ring buffer pointer
            ringBufferIndex = (ringBufferIndex + 1) & 0x0F;
        }
    }

    /**
     * Starts fragment mode when the head is destroyed.
     * Each segment gets a unique X velocity from Cat_FragSpeed and bounces.
     */
    private void startFragmenting() {
        fragmenting = true;

        // Get fragment X velocity based on segment routine index
        int fragIndex = Math.min(fragSpeedIndex, FRAG_X_SPEEDS.length - 1);
        int fragXVel = FRAG_X_SPEEDS[fragIndex];

        // btst #0,obStatus(a0) / beq.s / neg.w d0
        // Negate when bit 0 = 1 (xFlip / facing right)
        if (!facingLeft) {
            fragXVel = -fragXVel;
        }

        xVelocity = fragXVel;
        yVelocity = FRAG_Y_VELOCITY;
    }

    /**
     * Fragment physics: ObjectFall (gravity) + bounce off floor.
     * From loc_16CC0 in disassembly.
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

        // Floor bounce when falling (tst.w obVelY(a0) / bmi.s .nofloor)
        if (yVelocity >= 0) {
            var floorResult = uk.co.jamesj999.sonic.physics.ObjectTerrainUtils.checkFloorDist(
                    currentX, currentY, 8);
            if (floorResult.foundSurface() && floorResult.distance() < 0) {
                currentY += floorResult.distance();
                yVelocity = FRAG_Y_VELOCITY; // move.w #-$400,obVelY(a0)
            }
        }
    }

    /**
     * Returns the current mapping frame index for rendering.
     * <p>
     * For animated segments (BodySeg2), uses Ani_Cat lookup when animating.
     * For non-animated segments (BodySeg1), uses base body frame 8.
     * During fragmentation, uses base frame with legs stripped.
     */
    int getMappingFrame() {
        if (fragmenting) {
            // andi.b #$F8,obFrame(a0) - clear low 3 bits → base body frame
            return 8; // Body frame 8 (no Y animation)
        }

        if (isAnimatedSegment && (animControl & 0x80) != 0) {
            // Animated segment: look up in Ani_Cat, add 8 for body offset
            int tableIndex = animAngle & 0x7F;
            if (tableIndex < Sonic1CaterkillerBadnikInstance.ANI_CAT.length) {
                int frameVal = Sonic1CaterkillerBadnikInstance.ANI_CAT[tableIndex] & 0xFF;
                if (frameVal != 0xFF) {
                    // addq.b #8,d0 - offset to body frames
                    return frameVal + 8;
                }
            }
            return 8;
        }

        // Non-animated or not currently animating: static body frame
        // obFrame was set to 8 on creation (move.b #8,obFrame(a1))
        return 8;
    }

    @Override
    public int getCollisionFlags() {
        if (destroyed || fragmenting) {
            return 0; // No collision when destroyed or fragmenting
        }
        // S1 React_Caterkiller behavior: body contact hurts Sonic even while rolling.
        // Use HURT category so touch response doesn't route through attack-bounce handling.
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onTouchResponse(AbstractPlayableSprite player, TouchResponseResult result, int frameCounter) {
        if (destroyed || fragmenting) {
            return;
        }
        // S1 React_Caterkiller sets bit 7 on the contacted segment, which propagates
        // through the chain and transitions the Caterkiller into fragment behavior.
        head.triggerFragmentFromBodyHit();
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x78, 0, 0, false, 0);
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
    public boolean isPersistent() {
        if (destroyed) {
            return false;
        }
        if (fragmenting) {
            // tst.b obRender(a0) / bpl.w Cat_ChkGone
            return isOnScreenX(160);
        }
        // Body segments persist as long as head exists
        return !head.isDestroyed() && !head.isDeleting();
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5); // obPriority = 5 (behind head at priority 4)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
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

        int frame = getMappingFrame();
        // S1: default art faces left. hFlip when facing right (obStatus bit 0 set).
        renderer.drawFrameIndex(frame, currentX, currentY, !facingLeft, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawRect(currentX, currentY, 8, 8, 1f, 0.5f, 0f);
        String label = (isAnimatedSegment ? "CatSeg2" : "CatSeg1")
                + " rb=" + ringBufferIndex
                + (fragmenting ? " FRAG" : "");
        ctx.drawWorldLabel(currentX, currentY, -2, label, Color.ORANGE);
    }

    boolean isFragmenting() {
        return fragmenting;
    }

    void markDestroyed() {
        destroyed = true;
        setDestroyed(true);
    }

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

    @Override
    public int readRingBuffer(int index) {
        return ringBuffer[index & 0x0F];
    }

    @Override
    public void writeRingBuffer(int index, int value) {
        ringBuffer[index & 0x0F] = (byte) value;
    }
}
