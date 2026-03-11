package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Object 70 - Large girder block (SBZ).
 * <p>
 * A large 192x48 pixel solid platform that moves in a continuous 4-phase loop:
 * <ol>
 *   <li>Right: xVel=0x100, yVel=0, duration=0x60 (96 frames)</li>
 *   <li>Down: xVel=0, yVel=0x100, duration=0x30 (48 frames)</li>
 *   <li>Up-left: xVel=-0x100, yVel=-0x40, duration=0x60 (96 frames)</li>
 *   <li>Up: xVel=0, yVel=-0x100, duration=0x18 (24 frames)</li>
 * </ol>
 * After each phase completes, a 7-frame delay occurs before the next begins.
 * <p>
 * All 12 instances in SBZ1 use subtype 0x00. The subtype is unused;
 * behavior is entirely determined by the cycling movement settings.
 * <p>
 * The girder provides full-sided solid object collision (not top-solid-only).
 * <p>
 * Reference: docs/s1disasm/_incObj/70 Girder Block.asm
 */
public class Sonic1GirderBlockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.b #$60,obActWid(a0)
    private static final int ACTIVE_WIDTH = 0x60;

    // From disassembly: move.b #$18,obHeight(a0)
    private static final int HEIGHT = 0x18;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    // Solid collision parameters from disassembly (.solid):
    // d1 = obActWid + $B = $60 + $B = $6B
    // d2 = obHeight = $18
    // d3 = d2 + 1 = $19
    private static final int SOLID_HALF_WIDTH = ACTIVE_WIDTH + 0x0B;
    private static final int SOLID_AIR_HALF_HEIGHT = HEIGHT;
    private static final int SOLID_GND_HALF_HEIGHT = HEIGHT + 1;

    // From disassembly: move.w #7,gird_delay(a0) — 7-frame delay between movement phases
    private static final int PHASE_DELAY = 7;

    /**
     * Movement settings table from disassembly (Gird_ChgMove .settings).
     * Each entry: {xVelocity, yVelocity, duration}.
     * <pre>
     *   dc.w   $100,    0,  $60, 0  ; right
     *   dc.w      0, $100,  $30, 0  ; down
     *   dc.w  -$100, -$40,  $60, 0  ; up/left
     *   dc.w      0,-$100,  $18, 0  ; up
     * </pre>
     */
    private static final int[][] MOVEMENT_SETTINGS = {
            { 0x100,     0,  0x60},  // Phase 0: right
            {     0, 0x100,  0x30},  // Phase 1: down
            {-0x100, -0x40,  0x60},  // Phase 2: up/left
            {     0,-0x100,  0x18},  // Phase 3: up
    };

    // Dynamic position (updated by SpeedToPos)
    private int x;
    private int y;

    // Sub-pixel accumulators for SpeedToPos (16.8 fixed-point: velocity << 8 added to 24-bit pos)
    private int xSubpixel;
    private int ySubpixel;

    // Original position for out-of-range deletion check (gird_origX = objoff_32, gird_origY = objoff_30)
    private final int origX;
    private final int origY;

    // Current X and Y velocities (obVelX, obVelY) — 8.8 fixed-point
    private int velX;
    private int velY;

    // Movement duration countdown (gird_time = objoff_34)
    private int moveTime;

    // Movement phase index (gird_set = objoff_38), cycles 0..3
    // In ROM this is byte-level (0/8/16/24) and masked with $18; we use 0..3 and mod 4.
    private int movePhase;

    // Delay countdown (gird_delay = objoff_3A) — frames to wait before next movement starts
    private int delay;

    // Dynamic spawn for position tracking
    private ObjectSpawn dynamicSpawn;

    public Sonic1GirderBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Girder");

        this.x = spawn.x();
        this.y = spawn.y();
        this.origX = spawn.x();
        this.origY = spawn.y();
        this.xSubpixel = 0;
        this.ySubpixel = 0;

        // Gird_Main calls Gird_ChgMove to initialize first movement phase
        this.movePhase = 0;
        applyPhaseSettings();

        refreshDynamicSpawn();
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Gird_Action (routine 2):
        // First check delay, then SpeedToPos + duration countdown
        if (delay > 0) {
            // subq.w #1,gird_delay(a0) / bne.s .solid
            delay--;
            if (delay > 0) {
                // Still waiting; skip movement but still provide solidity
                refreshDynamicSpawn();
                return;
            }
            // Delay just hit zero — fall through to begin movement
        }

        // jsr (SpeedToPos).l — apply velocity to position
        applySpeedToPos();

        // subq.w #1,gird_time(a0) — decrement movement duration
        moveTime--;
        if (moveTime <= 0) {
            // bsr.w Gird_ChgMove — advance to next movement phase
            advancePhase();
        }

        refreshDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // tst.b obRender(a0) / bpl.s .chkdel — only render when on-screen (bit 7 set)
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SBZ_GIRDER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Single mapping frame (frame 0), no flip
        renderer.drawFrameIndex(0, x, y, false, false);
    }

    // ---- SolidObjectProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly .solid section:
        // d1 = obActWid + $B = $6B (half-width for collision)
        // d2 = obHeight = $18 (air half-height)
        // d3 = d2 + 1 = $19 (ground half-height)
        // bsr.w SolidObject — full-sided solid
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_AIR_HALF_HEIGHT, SOLID_GND_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        // Uses SolidObject (not PlatformObject), so all-sides solid
        return false;
    }

    // ---- SolidObjectListener ----

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special contact behavior; solidity is handled by the engine's SolidContacts system
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public int getPriorityBucket() {
        // move.b #4,obPriority(a0)
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        // out_of_range.s .delete,gird_origX(a0)
        // Uses original X position for range check (not current position)
        return !isDestroyed() && isInRange(origX);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw solid collision box
        ctx.drawRect(x, y, SOLID_HALF_WIDTH, SOLID_AIR_HALF_HEIGHT, 0.3f, 0.6f, 1.0f);

        // Label with phase and timer info
        String label = String.format("Gird p%d t%d d%d", movePhase, moveTime, delay);
        ctx.drawWorldLabel(x, y - SOLID_AIR_HALF_HEIGHT - 8, 0, label, DebugColor.CYAN);
    }

    // ========================================
    // Movement logic
    // ========================================

    /**
     * Applies the current phase's settings to velocity/duration/delay.
     * Mirrors Gird_ChgMove in the disassembly.
     * <pre>
     * Gird_ChgMove:
     *   move.b  gird_set(a0),d0
     *   andi.w  #$18,d0
     *   lea     (.settings).l,a1
     *   lea     (a1,d0.w),a1
     *   move.w  (a1)+,obVelX(a0)
     *   move.w  (a1)+,obVelY(a0)
     *   move.w  (a1)+,gird_time(a0)
     *   addq.b  #8,gird_set(a0)
     *   move.w  #7,gird_delay(a0)
     * </pre>
     */
    private void applyPhaseSettings() {
        int[] settings = MOVEMENT_SETTINGS[movePhase & 0x03];
        velX = settings[0];
        velY = settings[1];
        moveTime = settings[2];
        delay = PHASE_DELAY;
    }

    /**
     * Advances to the next movement phase and applies its settings.
     * In ROM: addq.b #8,gird_set(a0) with andi.w #$18 masking gives 4 phases cycling.
     */
    private void advancePhase() {
        movePhase = (movePhase + 1) & 0x03;
        applyPhaseSettings();
    }

    /**
     * SpeedToPos — applies X and Y velocities to position using 16.8 fixed-point.
     * <p>
     * ROM SpeedToPos:
     * <pre>
     *   move.l  obX(a0),d0      ; 16.16 position (X.subpixel)
     *   move.w  obVelX(a0),d1   ; 8.8 velocity
     *   ext.l   d1
     *   asl.l   #8,d1           ; shift to 16.16
     *   add.l   d1,d0
     *   move.l  d0,obX(a0)
     *   (same for Y)
     * </pre>
     */
    private void applySpeedToPos() {
        // X axis
        if (velX != 0) {
            long xPos32 = ((long) x << 16) | (xSubpixel & 0xFFFF);
            long xVel32 = (long) (short) velX << 8;
            xPos32 += xVel32;
            x = (int) (xPos32 >> 16);
            xSubpixel = (int) (xPos32 & 0xFFFF);
        }

        // Y axis
        if (velY != 0) {
            long yPos32 = ((long) y << 16) | (ySubpixel & 0xFFFF);
            long yVel32 = (long) (short) velY << 8;
            yPos32 += yVel32;
            y = (int) (yPos32 >> 16);
            ySubpixel = (int) (yPos32 & 0xFFFF);
        }
    }

    /**
     * Checks if the object is within out-of-range distance from camera using the given X.
     * Matches the S1 out_of_range macro behavior.
     */
    private boolean isInRange(int objectX) {
        Camera camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    private void refreshDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = new ObjectSpawn(
                    x, y,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
    }
}
