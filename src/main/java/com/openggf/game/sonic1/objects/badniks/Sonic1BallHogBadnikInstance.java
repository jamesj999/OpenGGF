package com.openggf.game.sonic1.objects.badniks;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Ball Hog (0x1E) - Cannon-wielding enemy from Scrap Brain Zone.
 * <p>
 * The Ball Hog stands on a platform and periodically launches cannonballs
 * from its hatch. The animation cycles through standing, squatting, and
 * leaping poses; when frame 1 (Open / hatch-open) is displayed, it spawns
 * a cannonball projectile.
 * <p>
 * Based on docs/s1disasm/_incObj/1E Ball Hog.asm.
 * <p>
 * Routine index:
 * <ul>
 *   <li>0 (Hog_Main): Initialization - ObjectFall + ObjFloorDist until floor found</li>
 *   <li>2 (Hog_Action): Animate + spawn cannonballs on frame 1 + RememberState</li>
 * </ul>
 * <p>
 * Animation script (Ani_Hog):
 * <pre>
 * .hog: dc.b 9, 0, 0, 2, 2, 3, 2, 0, 0, 2, 2, 3, 2, 0, 0, 2, 2, 3, 2, 0, 0, 1, afEnd
 * </pre>
 * Single animation at speed 9 (10 frames per step), 22 frames total before looping.
 * <p>
 * The cannonball is spawned as a separate dynamic object ({@link Sonic1CannonballInstance}).
 * The Ball Hog copies its subtype to the cannonball, which uses it as an explosion timer
 * multiplier (subtype * 60 frames).
 */
public class Sonic1BallHogBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    // --- Collision ---
    // From disassembly: move.b #5,obColType(a0)
    // $00 = standard enemy category, $05 = size index
    private static final int COLLISION_SIZE_INDEX = 0x05;

    // --- Dimensions ---
    // From disassembly: move.b #$13,obHeight(a0), move.b #8,obWidth(a0)
    private static final int Y_RADIUS = 0x13;

    // --- Physics ---
    // From ObjectFall: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;

    // --- Cannonball spawn offsets ---
    // From disassembly: moveq #-4,d0 (X offset), addi.w #$C,obY(a1) (Y offset)
    private static final int CANNONBALL_X_OFFSET = -4;
    private static final int CANNONBALL_Y_OFFSET = 0x0C;

    // From disassembly: move.w #-$100,obVelX(a1)
    private static final int CANNONBALL_X_VELOCITY = -0x100;

    // --- Render ---
    // From disassembly: move.b #4,obPriority(a0)
    private static final int RENDER_PRIORITY = 4;

    // --- Animation ---
    // Single animation from Ani_Hog: speed 9, then frame sequence
    private static final int ANIM_SPEED = 9 + 1; // ROM speed byte + 1 = ticks per frame step
    // Frame sequence: 0, 0, 2, 2, 3, 2, 0, 0, 2, 2, 3, 2, 0, 0, 2, 2, 3, 2, 0, 0, 1
    private static final int[] ANIM_FRAMES = {
            0, 0, 2, 2, 3, 2, 0, 0, 2, 2,
            3, 2, 0, 0, 2, 2, 3, 2, 0, 0, 1
    };
    // Frame index 1 = Open (hatch open) is the cannonball spawn trigger
    private static final int OPEN_FRAME = 1;

    // --- Instance state ---
    private int currentX;
    private int currentY;
    private int yVelocity;
    private int ySubpixel;
    private boolean facingLeft;
    private boolean initialized;
    private boolean destroyed;

    // Animation state
    private int animTickCounter;
    private int animStepIndex;

    // hog_launchflag (objoff_32): 0 = ready to launch, nonzero = already launched this cycle
    private boolean launchFlag;

    public Sonic1BallHogBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "BallHog");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.yVelocity = 0;
        this.ySubpixel = 0;

        // obRender bit 2 = use obStatus for flipping
        // obStatus bit 0 determines facing: 0 = facing right, 1 = facing left
        this.facingLeft = (spawn.renderFlags() & 1) != 0;
        this.initialized = false;
        this.destroyed = false;
        this.animTickCounter = 0;
        this.animStepIndex = 0;
        this.launchFlag = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        if (!initialized) {
            initialize();
        } else {
            updateAction(frameCounter, player);
        }
    }

    /**
     * Routine 0: Hog_Main - ObjectFall + ObjFloorDist until floor is found.
     * <pre>
     * Hog_Main:
     *     bsr.w  ObjectFall
     *     jsr    (ObjFloorDist).l
     *     tst.w  d1
     *     bpl.s  .floornotfound
     *     add.w  d1,obY(a0)
     *     move.w #0,obVelY(a0)
     *     addq.b #2,obRoutine(a0)
     * </pre>
     */
    private void initialize() {
        // ObjectFall: VelY += gravity, then Y += VelY (gravity applied before Y movement)
        yVelocity += GRAVITY;
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += yVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;

        // ObjFloorDist: find floor from feet (obY + obHeight)
        TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);

        // tst.w d1 / bpl.s .floornotfound
        if (floorResult.foundSurface() && floorResult.distance() < 0) {
            currentY += floorResult.distance(); // add.w d1,obY(a0)
            yVelocity = 0;                       // move.w #0,obVelY(a0)
            initialized = true;                   // addq.b #2,obRoutine(a0)
        }
    }

    /**
     * Routine 2: Hog_Action - Animate sprite, check for cannonball spawn on frame 1.
     * <pre>
     * Hog_Action:
     *     lea    (Ani_Hog).l,a1
     *     bsr.w  AnimateSprite
     *     cmpi.b #1,obFrame(a0)        ; is Open frame displayed?
     *     bne.s  .setlaunchflag          ; if not, clear flag
     *     tst.b  hog_launchflag(a0)     ; ready to launch?
     *     beq.s  .makeball               ; if flag=0, spawn cannonball
     *     bra.s  .remember
     *
     * .setlaunchflag:
     *     clr.b  hog_launchflag(a0)     ; reset flag for next cycle
     *
     * .remember:
     *     bra.w  RememberState
     *
     * .makeball:
     *     move.b #1,hog_launchflag(a0)  ; mark as launched
     *     [spawn cannonball]
     * </pre>
     */
    private void updateAction(int frameCounter, AbstractPlayableSprite player) {
        // AnimateSprite: advance animation
        updateAnimation();

        int currentFrame = ANIM_FRAMES[animStepIndex];

        if (currentFrame == OPEN_FRAME) {
            // cmpi.b #1,obFrame(a0) / bne.s .setlaunchflag
            if (!launchFlag) {
                // tst.b hog_launchflag(a0) / beq.s .makeball
                launchFlag = true;
                spawnCannonball();
            }
            // else: already launched this cycle, skip to RememberState
        } else {
            // .setlaunchflag: clr.b hog_launchflag(a0)
            launchFlag = false;
        }

        // RememberState is implicit (isPersistent handles on-screen check)
    }

    /**
     * Advance animation by one tick. The animation uses speed byte 9 (10 ticks per step).
     * After reaching the end of the frame sequence, it loops back to the start (afEnd).
     */
    private void updateAnimation() {
        animTickCounter++;
        if (animTickCounter >= ANIM_SPEED) {
            animTickCounter = 0;
            animStepIndex++;
            if (animStepIndex >= ANIM_FRAMES.length) {
                animStepIndex = 0; // afEnd - loop animation
            }
        }
    }

    /**
     * Spawns a cannonball (Object $20) at the Ball Hog's position with offsets.
     * <pre>
     * .makeball:
     *     move.b #1,hog_launchflag(a0)
     *     bsr.w  FindFreeObj
     *     bne.s  .fail
     *     _move.b #id_Cannonball,obID(a1)
     *     move.w obX(a0),obX(a1)
     *     move.w obY(a0),obY(a1)
     *     move.w #-$100,obVelX(a1)       ; cannonball bounces to the left
     *     move.w #0,obVelY(a1)
     *     moveq  #-4,d0
     *     btst   #0,obStatus(a0)         ; is Ball Hog facing right?
     *     beq.s  .noflip                  ; if not, branch
     *     neg.w  d0
     *     neg.w  obVelX(a1)              ; cannonball bounces to the right
     *
     * .noflip:
     *     add.w  d0,obX(a1)
     *     addi.w #$C,obY(a1)
     *     move.b obSubtype(a0),obSubtype(a1)
     * </pre>
     */
    private void spawnCannonball() {
        var objectManager = services() != null ? services().objectManager() : null;
        if (objectManager == null) {
            return;
        }

        int ballXVel = CANNONBALL_X_VELOCITY;
        int xOffset = CANNONBALL_X_OFFSET;

        // btst #0,obStatus(a0) / beq.s .noflip
        // When obStatus bit 0 = 1 (X-flipped / facingLeft): negate offset and velocity
        // Default (bit 0 = 0): ball goes left at -$100 with offset -4
        // Flipped (bit 0 = 1): ball goes right at +$100 with offset +4
        if (facingLeft) {
            xOffset = -xOffset;     // neg.w d0
            ballXVel = -ballXVel;   // neg.w obVelX(a1)
        }

        int ballX = currentX + xOffset;
        int ballY = currentY + CANNONBALL_Y_OFFSET;
        int subtype = spawn.subtype();

        Sonic1CannonballInstance cannonball = new Sonic1CannonballInstance(
                ballX, ballY, ballXVel, subtype, com.openggf.game.GameServices.level());
        objectManager.addDynamicObject(cannonball);
    }

    /**
     * Returns the current mapping frame index from the animation sequence.
     */
    private int getMappingFrame() {
        return ANIM_FRAMES[animStepIndex];
    }

    // --- TouchResponseProvider / TouchResponseAttackable ---

    @Override
    public int getCollisionFlags() {
        if (destroyed || !initialized) {
            return 0;
        }
        // obColType = $05: standard enemy category ($00) + size index $05
        return COLLISION_SIZE_INDEX & 0x3F;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (destroyed) {
            return;
        }
        destroyBadnik(player);
    }

    /**
     * Handles badnik destruction via the centralised DestructionEffects system.
     */
    private void destroyBadnik(AbstractPlayableSprite player) {
        destroyed = true;
        setDestroyed(true);
        DestructionEffects.destroyBadnik(currentX, currentY, spawn, player, services(),
                Sonic1DestructionConfig.S1_DESTRUCTION_CONFIG);
    }

    // --- Rendering ---

    @Override
    public boolean isPersistent() {
        // RememberState: persists while on screen
        return !destroyed && isOnScreenX(160);
    }

    @Override
    public int getPriorityBucket() {
        // obPriority = 4
        return RenderPriority.clamp(RENDER_PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.BALL_HOG);
        if (renderer == null) return;

        int frame = getMappingFrame();
        // ori.b #4,obRender(a0): bit 2 set = use obStatus for flipping
        // obStatus bit 0 = 1 = X flip (facing left), no bchg toggle in Ball Hog
        // hFlip parameter directly matches obStatus bit 0 = facingLeft
        renderer.drawFrameIndex(frame, currentX, currentY, facingLeft, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Yellow hitbox rectangle (collision size index $05)
        ctx.drawRect(currentX, currentY, 8, 0x13, 1f, 1f, 0f);

        // State label
        String state = initialized ? "ACTIVE" : "INIT";
        String dir = facingLeft ? "L" : "R";
        int frame = getMappingFrame();
        String label = "BallHog " + state + " f" + frame + " s" + animStepIndex + " " + dir;
        ctx.drawWorldLabel(currentX, currentY, -2, label, DebugColor.YELLOW);
    }

    // --- Position accessors ---

    @Override
    public ObjectSpawn getSpawn() {
        return buildSpawnAt(currentX, currentY);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }
}
