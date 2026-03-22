package com.openggf.game.sonic1.objects;
import com.openggf.game.GameServices;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Object 0x4E - Advancing wall of lava (MZ).
 * <p>
 * An imposing wall of lava that advances toward Sonic when he enters its proximity zone.
 * Appears once in MZ Act 2. Creates two sub-objects: the main wall with an animated leading
 * edge and a trailing section that follows behind.
 * <p>
 * <b>Routine state machine:</b>
 * <ul>
 *   <li><b>Routine 0 (LWall_Main):</b> Initialization. Creates trailing child piece, advances
 *       to routine 4.</li>
 *   <li><b>Routine 4 (LWall_Action):</b> Idle/proximity check. When Sonic is within $C0 px
 *       horizontally and $60 px vertically, sets the move flag. When the move flag is set
 *       AND Sonic leaves proximity, starts advancing at velocity $180.</li>
 *   <li><b>Routine 2 (LWall_Solid):</b> Active movement. Applies SolidObject collision
 *       (d1=$2B, d2=$18, d3=$19), animates leading edge (Ani_LWall: speed 9, frames 0-3),
 *       applies SpeedToPos. Stops at X=$6A0.</li>
 *   <li><b>Routine 6 (LWall_Move):</b> Trailing child follows main wall at X-$80.</li>
 *   <li><b>Routine 8 (LWall_Delete):</b> Deletion.</li>
 * </ul>
 * <p>
 * <b>Solid collision:</b> The ROM saves/restores obRoutine around the SolidObject call to
 * prevent it from advancing the routine. Engine SolidContacts handle this automatically.
 * <p>
 * <b>Touch collision:</b> obColType=$94 (HURT category $80 | size index $14).
 * <p>
 * Reference: docs/s1disasm/_incObj/4E Wall of Lava.asm
 */
public class Sonic1LavaWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, TouchResponseProvider {

    // ========================================================================
    // Role enum
    // ========================================================================

    /** Which piece of the two-piece wall this instance represents. */
    enum Role {
        /** Main wall piece (routines 0/2/4): has leading edge animation, solid collision,
         *  movement, and proximity detection. */
        MAIN,
        /** Trailing section (routine 6): follows MAIN piece at X-$80, body-only frame. */
        TRAIL
    }

    // ========================================================================
    // Constants from disassembly
    // ========================================================================

    /**
     * X velocity when wall starts advancing.
     * From: move.w #$180,obVelX(a0)
     */
    private static final int ADVANCE_VELOCITY = 0x180;

    /**
     * X position where wall stops.
     * From: cmpi.w #$6A0,obX(a0)
     */
    private static final int STOP_X = 0x6A0;

    /**
     * Proximity X range for triggering the move flag.
     * From: cmpi.w #$C0,d0
     */
    private static final int PROXIMITY_X = 0xC0;

    /**
     * Proximity Y range for triggering the move flag.
     * From: cmpi.w #$60,d0
     */
    private static final int PROXIMITY_Y = 0x60;

    /**
     * Trailing piece X offset from main wall.
     * From: subi.w #$80,obX(a0) (relative to main wall X)
     */
    private static final int TRAIL_X_OFFSET = 0x80;

    /**
     * Active width for rendering visibility.
     * From: move.b #$50,obActWid(a1)
     */
    private static final int ACTIVE_WIDTH = 0x50;

    /**
     * Render priority.
     * From: move.b #1,obPriority(a1)
     */
    private static final int PRIORITY = 1;

    /**
     * Solid collision half-width.
     * From: move.w #$2B,d1
     */
    private static final int SOLID_HALF_WIDTH = 0x2B;

    /**
     * Solid collision air half-height.
     * From: move.w #$18,d2
     */
    private static final int SOLID_AIR_HALF_HEIGHT = 0x18;

    /**
     * Solid collision ground half-height.
     * From: move.w d2,d3 / addq.w #1,d3 -> $19
     */
    private static final int SOLID_GROUND_HALF_HEIGHT = 0x19;

    /**
     * Touch collision flags: HURT category ($80) | size index $14.
     * From: move.b #$94,obColType(a1)
     */
    private static final int COLLISION_FLAGS = 0x94;

    /**
     * Animation speed (frame duration in engine frames).
     * From Ani_LWall: dc.b 9, 0, 1, 2, 3, afEnd
     * Speed byte 9 means each animation frame lasts 10 engine frames.
     */
    private static final int ANIM_SPEED = 10;

    /** Animation frame sequence (from Ani_LWall: 0, 1, 2, 3). */
    private static final int[] ANIM_FRAMES = {0, 1, 2, 3};

    /** Trailing piece mapping frame index. From: move.b #4,obFrame(a1). */
    private static final int TRAIL_FRAME = 4;

    /** Out-of-range distance (ROM: 128+320+192 for wide out_of_range check). */
    private static final int OUT_OF_RANGE_DISTANCE = 128 + 320 + 192;

    /** Debug color (deep orange-red for lava). */
    private static final DebugColor DEBUG_COLOR = new DebugColor(255, 80, 0);

    // ========================================================================
    // Instance State
    // ========================================================================

    private final Role role;

    /** Current X position (updated during movement). */
    private int currentX;

    /** Current Y position (constant for this object). */
    private final int currentY;

    /** X velocity in subpixels (signed 16-bit). */
    private int velX;

    /** X subpixel accumulator for SpeedToPos. */
    private int xSubpixel;

    /**
     * Flag to start wall moving (lwall_flag = objoff_36).
     * Set when Sonic enters proximity, triggers advance when Sonic leaves.
     */
    private boolean moveFlag;

    /** Current routine state (0=init, 2=solid/moving, 4=proximity check, 6=trail, 8=delete). */
    private int routine;

    /** Animation frame index into ANIM_FRAMES. */
    private int animFrameIndex;

    /** Animation timer (counts up to ANIM_SPEED). */
    private int animTimer;

    /** Current display frame index for rendering. */
    private int displayFrame;

    /** Reference to the main wall piece (used by TRAIL role). */
    private Sonic1LavaWallObjectInstance mainWall;

    /** Whether the trailing child has been spawned (prevents double-spawn). */
    private boolean childSpawned;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Creates the main lava wall piece from a level object spawn.
     * The trailing child is created during the first update (routine 0).
     */
    public Sonic1LavaWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LavaWall");
        this.role = Role.MAIN;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.routine = 0; // LWall_Main
        this.displayFrame = 0;
    }

    /**
     * Internal constructor for the trailing piece.
     */
    private Sonic1LavaWallObjectInstance(ObjectSpawn spawn, Sonic1LavaWallObjectInstance parent) {
        super(spawn, "LavaWall");
        this.role = Role.TRAIL;
        this.currentX = parent.currentX - TRAIL_X_OFFSET;
        this.currentY = parent.currentY;
        this.mainWall = parent;
        this.routine = 6; // LWall_Move
        this.displayFrame = TRAIL_FRAME;
    }

    // ========================================================================
    // Update Logic
    // ========================================================================

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 0 -> updateInit();
            case 4 -> updateProximityCheck(player);
            case 2 -> updateSolidMoving(player);
            case 6 -> updateTrail();
            case 8 -> setDestroyed(true);
        }
    }

    /**
     * LWall_Main (Routine 0): Spawn trailing child, advance to routine 4 (proximity check).
     * <p>
     * ROM creates a second object via FindNextFreeObj and copies all properties,
     * then sets routine 6 and frame 4 on the child. The child stores objoff_3C = a0 (parent).
     */
    private void updateInit() {
        if (!childSpawned) {
            childSpawned = true;
            if (services().objectManager() != null) {
                ObjectSpawn trailSpawn = new ObjectSpawn(
                        currentX - TRAIL_X_OFFSET, currentY,
                        0x4E, spawn.subtype(), 0, false, 0);
                Sonic1LavaWallObjectInstance trail = new Sonic1LavaWallObjectInstance(trailSpawn, this);
                services().objectManager().addDynamicObject(trail);
            }
        }
        // addq.b #4,obRoutine(a0) -> routine 0 + 4 = 4
        routine = 4;
    }

    /**
     * LWall_Action (Routine 4): Check Sonic proximity, set/act on move flag.
     * <pre>
     * move.w (v_player+obX).w,d0
     * sub.w  obX(a0),d0          ; d0 = signed X distance (player - wall)
     * bcc.s  .rangechk            ; if positive, skip negate
     * neg.w  d0                   ; d0 = |X distance|
     * .rangechk:
     * cmpi.w #$C0,d0             ; within $C0 pixels X?
     * bhs.s  .movewall            ; if not, check move flag
     * ...Y check...
     * move.b #1,lwall_flag(a0)    ; set move flag
     * bra.s  LWall_Solid
     * .movewall:
     * tst.b  lwall_flag(a0)       ; is flag set?
     * beq.s  LWall_Solid          ; if not, just do solid
     * move.w #$180,obVelX(a0)     ; start moving
     * subq.b #2,obRoutine(a0)     ; routine 4-2 = 2 (LWall_Solid active)
     * </pre>
     */
    private void updateProximityCheck(AbstractPlayableSprite player) {
        if (player != null) {
            int distX = Math.abs(player.getCentreX() - currentX);
            int distY = Math.abs(player.getCentreY() - currentY);

            if (distX < PROXIMITY_X && distY < PROXIMITY_Y) {
                // Sonic is within proximity - set the move flag
                moveFlag = true;
            } else if (moveFlag) {
                // Sonic left proximity with flag set - start advancing
                velX = ADVANCE_VELOCITY;
                // subq.b #2,obRoutine(a0): routine 4 -> 2
                routine = 2;
            }
        }

        // Fall through to solid collision + rendering (LWall_Solid)
        updateSolidAndAnimate(player);
    }

    /**
     * LWall_Solid (Routine 2): Active wall movement with solid collision and animation.
     * <p>
     * The ROM saves/restores obRoutine around the SolidObject call to prevent it
     * from modifying the routine. In our engine, the SolidContacts system handles
     * solid collision externally, so we just handle animation and movement here.
     * <pre>
     * cmpi.w #$6A0,obX(a0)   ; reached stop position?
     * bne.s  .animate
     * clr.w  obVelX(a0)       ; stop moving
     * clr.b  lwall_flag(a0)   ; clear flag
     * </pre>
     */
    private void updateSolidMoving(AbstractPlayableSprite player) {
        updateSolidAndAnimate(player);
    }

    /**
     * Shared animation and movement logic for routines 2 and 4.
     * <pre>
     * lea    (Ani_LWall).l,a1
     * bsr.w  AnimateSprite         ; animate leading edge
     * cmpi.b #4,(v_player+obRoutine).w
     * bhs.s  .rangechk             ; skip SpeedToPos if player dead
     * bsr.w  SpeedToPos            ; apply velocity
     * .rangechk:
     * bsr.w  DisplaySprite
     * tst.b  lwall_flag(a0)
     * bne.s  .moving               ; if moving, skip out-of-range check
     * out_of_range.s .chkgone
     * </pre>
     */
    private void updateSolidAndAnimate(AbstractPlayableSprite player) {
        // Check stop position
        if (currentX >= STOP_X) {
            currentX = STOP_X;
            velX = 0;
            moveFlag = false;
        }

        // AnimateSprite (Ani_LWall: speed 9, frames 0,1,2,3, afEnd)
        animTimer++;
        if (animTimer >= ANIM_SPEED) {
            animTimer = 0;
            animFrameIndex = (animFrameIndex + 1) % ANIM_FRAMES.length;
        }
        displayFrame = ANIM_FRAMES[animFrameIndex];

        // SpeedToPos: only if player is alive (routine < 4)
        // cmpi.b #4,(v_player+obRoutine).w / bhs.s .rangechk
        boolean playerAlive = (player != null && !player.getDead());
        if (playerAlive && velX != 0) {
            applySpeedToPosX();
        }

        // Out-of-range check: only when NOT moving (lwall_flag == 0)
        if (!moveFlag) {
            if (!isWithinOutOfRangeWindow(currentX)) {
                handleOutOfRange();
            }
        }
    }

    /**
     * LWall_Move (Routine 6): Trailing section follows main wall at X-$80.
     * <pre>
     * movea.l objoff_3C(a0),a1     ; load parent reference
     * cmpi.b  #8,obRoutine(a1)     ; parent deleted?
     * beq.s   LWall_Delete
     * move.w  obX(a1),obX(a0)      ; copy parent X
     * subi.w  #$80,obX(a0)         ; offset by $80
     * bra.w   DisplaySprite
     * </pre>
     */
    private void updateTrail() {
        if (mainWall == null || mainWall.isDestroyed() || mainWall.routine == 8) {
            setDestroyed(true);
            return;
        }
        currentX = mainWall.currentX - TRAIL_X_OFFSET;
    }

    /**
     * Handle going out of range: clear respawn flag and mark for deletion.
     * <pre>
     * .chkgone:
     *   lea    (v_objstate).w,a2
     *   moveq  #0,d0
     *   move.b obRespawnNo(a0),d0
     *   bclr   #7,2(a2,d0.w)
     *   move.b #8,obRoutine(a0)
     * </pre>
     */
    private void handleOutOfRange() {
        routine = 8;
        setDestroyed(true);
    }

    /**
     * SpeedToPos for X axis.
     * <pre>
     * move.l obX(a0),d2 / ext.l d0 / asl.l #8,d0 / add.l d0,d2 / move.l d2,obX(a0)
     * </pre>
     */
    private void applySpeedToPosX() {
        int xPos32 = (currentX << 16) | (xSubpixel & 0xFFFF);
        int vel32 = (int) (short) velX;
        xPos32 += vel32 << 8;
        currentX = xPos32 >> 16;
        xSubpixel = xPos32 & 0xFFFF;
    }

    /**
     * ROM out_of_range macro: round both X positions to $80 boundaries
     * and compare distance against 128+320+192.
     */
    private boolean isWithinOutOfRangeWindow(int objectX) {
        Camera camera = GameServices.camera();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= OUT_OF_RANGE_DISTANCE;
    }

    // ========================================================================
    // SolidObjectProvider / SolidObjectListener
    // ========================================================================

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_AIR_HALF_HEIGHT, SOLID_GROUND_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        // Full solidity (sides + top + bottom) like a normal solid object
        return false;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Solid in routines 2 (active) and 4 (proximity check)
        return role == Role.MAIN && (routine == 2 || routine == 4);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // The ROM saves/restores obRoutine around SolidObject to prevent routine changes.
        // Our SolidContacts system doesn't modify routine, so no special handling needed.
        // The solid collision itself (pushing player, blocking) is handled by the engine.
    }

    // ========================================================================
    // TouchResponseProvider (hurt collision)
    // ========================================================================

    @Override
    public int getCollisionFlags() {
        if (isDestroyed()) {
            return 0;
        }
        // Both main and trail pieces have obColType=$94
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 8) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.MZ_LAVA_WALL);
        if (renderer == null) return;

        renderer.drawFrameIndex(displayFrame, currentX, currentY, false, false);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x4E, spawn.subtype(), 0, false, 0);
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
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        if (isDestroyed()) {
            return false;
        }
        // When moving (moveFlag set), always persistent (ROM: tst.b lwall_flag / bne.s .moving)
        if (moveFlag || (role == Role.TRAIL && mainWall != null && mainWall.moveFlag)) {
            return true;
        }
        return isWithinOutOfRangeWindow(currentX);
    }

    // ========================================================================
    // Debug Rendering
    // ========================================================================

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (routine == 8) {
            return;
        }

        String roleStr = role.name();
        float r = moveFlag ? 1.0f : 0.8f;
        float g = moveFlag ? 0.2f : 0.5f;
        float b = 0.0f;

        // Draw solid collision bounds for main piece
        if (role == Role.MAIN) {
            ctx.drawRect(currentX, currentY, SOLID_HALF_WIDTH, SOLID_AIR_HALF_HEIGHT, r, g, b);
        } else {
            // Trail piece: draw body outline
            ctx.drawRect(currentX, currentY, 0x40, 0x20, 0.6f, 0.3f, 0.0f);
        }

        ctx.drawWorldLabel(currentX, currentY, -1,
                String.format("LWall[%s] r=%d vx=%d flg=%s frm=%d",
                        roleStr, routine, velX, moveFlag, displayFrame),
                DEBUG_COLOR);
    }
}
