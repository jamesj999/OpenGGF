package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.WaypointPathFollower;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x6C - Small platform on pulleys (like at the start of MTZ2).
 * <p>
 * Platforms follow a closed-loop path of waypoints. The path is determined by the upper bits
 * of the subtype, and the starting waypoint within the path is determined by the lower nibble.
 * Each platform moves at a constant speed of 1 pixel/frame along the dominant axis, with
 * proportional velocity on the minor axis.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 54189-54444 (Obj6C code)
 * <p>
 * <b>Subtype encoding (individual platforms, bit 7 clear):</b>
 * <ul>
 *   <li>Bits 4-6: Path table index (subtype >> 4, via off_28252)</li>
 *   <li>Bits 0-3: Starting waypoint (subtype & 0xF) * 4 = byte offset into path</li>
 * </ul>
 * <p>
 * <b>Subtype encoding (parent spawner, bit 7 set):</b>
 * <ul>
 *   <li>Bits 0-6: Child layout table index (subtype & 0x7F, via off_282D6)</li>
 * </ul>
 * <p>
 * <b>Collision:</b> Top-solid platform (JmpTo5_PlatformObject), width_pixels=0x10, d3=8.
 * <p>
 * <b>Art:</b> ArtNem_LavaCup, palette line 3, single 32x16 frame.
 */
public class ConveyorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(ConveyorObjectInstance.class.getName());

    // From disassembly: move.b #$10,width_pixels(a0)
    private static final int WIDTH_PIXELS = 0x10;

    // From disassembly: moveq #8,d3 (y_radius for PlatformObject)
    private static final int Y_RADIUS = 8;

    // From disassembly: move.b #4,priority(a0)
    private static final int PRIORITY = 4;

    // Velocity magnitude (1 pixel/frame in 8.8 fixed point)
    // From disassembly: move.w #-$100,d2 / move.w #-$100,d3
    private static final int MOVE_SPEED = 0x100;

    // Movement step size for waypoint advancement
    // From disassembly: move.b #4,objoff_3A(a0)
    private static final int WAYPOINT_STEP = 4;

    /**
     * Path waypoint tables from off_28252.
     * Each path is an array of (x_offset, y_offset) pairs relative to the base position.
     * Format: first 2 bytes = total byte length, then waypoints in 4-byte groups (x_word, y_word).
     * <p>
     * From disassembly: byte_28258, byte_28282, byte_282AC
     */
    private static final int[][][] PATH_WAYPOINTS = {
            // Path 0 (byte_28258): length=0x28 = 40 bytes = 10 waypoints
            {
                    {0x0000, 0x0000}, {0xFFEA, 0x000A}, {0xFFE0, 0x0020}, {0xFFE0, 0x00E0},
                    {0xFFEA, 0x00F6}, {0x0000, 0x0100}, {0x0016, 0x00F6}, {0x0020, 0x00E0},
                    {0x0020, 0x0020}, {0x0016, 0x000A},
            },
            // Path 1 (byte_28282): length=0x28 = 40 bytes = 10 waypoints
            {
                    {0x0000, 0x0000}, {0xFFEA, 0x000A}, {0xFFE0, 0x0020}, {0xFFE0, 0x0160},
                    {0xFFEA, 0x0176}, {0x0000, 0x0180}, {0x0016, 0x0176}, {0x0020, 0x0160},
                    {0x0020, 0x0020}, {0x0016, 0x000A},
            },
            // Path 2 (byte_282AC): length=0x28 = 40 bytes = 10 waypoints
            {
                    {0x0000, 0x0000}, {0xFFEA, 0x000A}, {0xFFE0, 0x0020}, {0xFFE0, 0x01E0},
                    {0xFFEA, 0x01F6}, {0x0000, 0x0200}, {0x0016, 0x01F6}, {0x0020, 0x01E0},
                    {0x0020, 0x0020}, {0x0016, 0x000A},
            },
    };

    /**
     * Child layout tables from off_282D6.
     * Each entry: {x_offset, y_offset, subtype}
     * <p>
     * From disassembly: byte_282DC, byte_2830E, byte_28340
     */
    private static final int[][][] CHILD_LAYOUTS = {
            // Layout 0 (byte_282DC): 8 children
            {
                    {0x0000, 0x0000, 0x01}, {0xFFE0, 0x003A, 0x03}, {0xFFE0, 0x0080, 0x03},
                    {0xFFE0, 0x00C6, 0x03}, {0x0000, 0x0100, 0x06}, {0x0020, 0x00C6, 0x08},
                    {0x0020, 0x0080, 0x08}, {0x0020, 0x003A, 0x08},
            },
            // Layout 1 (byte_2830E): 8 children
            {
                    {0x0000, 0x0000, 0x11}, {0xFFE0, 0x005A, 0x13}, {0xFFE0, 0x00C0, 0x13},
                    {0xFFE0, 0x0126, 0x13}, {0x0000, 0x0180, 0x16}, {0x0020, 0x0126, 0x18},
                    {0x0020, 0x00C0, 0x18}, {0x0020, 0x005A, 0x18},
            },
            // Layout 2 (byte_28340): 8 children
            {
                    {0x0000, 0x0000, 0x21}, {0xFFE0, 0x007A, 0x23}, {0xFFE0, 0x0100, 0x23},
                    {0xFFE0, 0x0186, 0x23}, {0x0000, 0x0200, 0x26}, {0x0020, 0x0186, 0x28},
                    {0x0020, 0x0100, 0x28}, {0x0020, 0x007A, 0x28},
            },
    };

    // Instance state

    /** Current pixel position. */
    private int x;
    private int y;

    /** Base position (objoff_30, objoff_32) - original spawn position. */
    private final int baseX;
    private final int baseY;

    /** Target waypoint position (objoff_34, objoff_36). */
    private int targetX;
    private int targetY;

    /** Current waypoint index in bytes (objoff_38). Low byte only. */
    private int waypointOffset;

    /** Total path byte length (objoff_39). High byte of objoff_38 word. */
    private final int pathLength;

    /** Waypoint advance direction (objoff_3A): +4 or -4. */
    private int waypointDelta;

    /** Path waypoint data (objoff_3C pointer). */
    private final int[][] pathData;

    /** X/Y velocity in 8.8 fixed point. */
    private int xVel;
    private int yVel;

    /** Sub-pixel accumulators for 16.16 fixed point movement. */
    private int xSub;
    private int ySub;

    /** Reusable state for SubpixelMotion calls (avoids per-frame allocation). */
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    /** X-flip from status byte. */
    private final boolean xFlip;

    /** Collision params: half-width = width_pixels, d3 = 8. */
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(WIDTH_PIXELS, Y_RADIUS, Y_RADIUS + 1);

    public ConveyorObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;

        int subtype = spawn.subtype();

        // Determine path table from upper nibble: (subtype >> 3) & 0x1E gives word offset
        // into off_28252, then load path from that table
        int pathIndex = (subtype >> 4) & 0x07;
        if (pathIndex >= PATH_WAYPOINTS.length) {
            pathIndex = 0;
        }
        this.pathData = PATH_WAYPOINTS[pathIndex];

        // Path length in bytes = pathData.length * 4
        // From disassembly: move.w (a2)+,objoff_38(a0) reads count word
        // The first word of each path table (e.g. 0x0028) is the byte length
        this.pathLength = pathData.length * 4;

        // Starting waypoint from lower nibble: (subtype & 0xF) * 4
        // From disassembly: andi.w #$F,d1; lsl.w #2,d1
        this.waypointOffset = (subtype & 0x0F) * 4;

        // Waypoint advance direction: +4 normally, -4 if x-flipped
        // From disassembly: move.b #4,objoff_3A(a0); btst status.npc.x_flip; neg.b
        this.waypointDelta = WAYPOINT_STEP;
        if (xFlip) {
            waypointDelta = -waypointDelta;
            // Advance one step and wrap (disassembly lines 54239-54249)
            waypointOffset = wrapWaypointOffset(waypointOffset + waypointDelta);
        }

        // Set initial position to base + waypoint offset
        int wpIndex = waypointOffset / 4;
        this.targetX = baseX + signExtend16(pathData[wpIndex][0]);
        this.targetY = baseY + signExtend16(pathData[wpIndex][1]);

        // Set current position and calculate initial velocity
        this.x = spawn.x();
        this.y = spawn.y();
        this.xSub = 0;
        this.ySub = 0;
        calculateVelocity();

        updateDynamicSpawn(x, y);
    }

    /**
     * Static factory method to spawn children for parent subtypes (bit 7 set).
     * The parent spawner reads child layout data and creates individual conveyor
     * platform instances, then the parent itself is not added to the object list.
     *
     * @param spawn The parent spawner's ObjectSpawn
     * @return null (children are added via ObjectManager.addDynamicObject)
     */
    public static ConveyorObjectInstance createOrSpawnChildren(ObjectSpawn spawn) {
        int subtype = spawn.subtype();

        if ((subtype & 0x80) == 0) {
            // Individual platform (bit 7 clear) - create normally
            return new ConveyorObjectInstance(spawn, "Conveyor");
        }

        // Parent spawner (bit 7 set) - spawn children and return null
        int layoutIndex = subtype & 0x7F;
        if (layoutIndex >= CHILD_LAYOUTS.length) {
            LOGGER.warning("Conveyor parent subtype 0x" + Integer.toHexString(subtype)
                    + " has invalid layout index " + layoutIndex);
            return null;
        }

        ObjectManager manager = LevelManager.getInstance().getObjectManager();
        if (manager == null) {
            return null;
        }

        int[][] layout = CHILD_LAYOUTS[layoutIndex];
        int parentX = spawn.x();
        int parentY = spawn.y();
        int parentStatus = spawn.renderFlags();

        for (int[] child : layout) {
            int childX = parentX + signExtend16(child[0]);
            int childY = parentY + signExtend16(child[1]);
            int childSubtype = child[2] & 0xFF;

            ObjectSpawn childSpawn = new ObjectSpawn(
                    childX, childY,
                    Sonic2ObjectIds.CONVEYOR,
                    childSubtype,
                    parentStatus,
                    false,
                    spawn.rawYWord());

            ConveyorObjectInstance childInstance = new ConveyorObjectInstance(childSpawn, "Conveyor");
            manager.addDynamicObject(childInstance);
        }

        // Parent removes itself: addq.l #4,sp; rts (skips back to caller)
        return null;
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
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        // Obj6C uses PlatformObject (JmpTo5_PlatformObject) - top-solid only
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special contact handling needed
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Obj6C_Main (line 54300): save old x, move, then PlatformObject
        // loc_2817E: check if arrived at target, advance waypoint if so
        checkAndAdvanceWaypoint();

        // ObjectMove: apply velocity to position
        applyVelocity();

        // Off-screen despawn check using base position (objoff_30)
        // From disassembly: (objoff_30 & $FF80) - Camera_X_pos_coarse > $280
        if (!isBasePositionOnScreen()) {
            setDestroyed(true);
            return;
        }

        updateDynamicSpawn(x, y);
    }

    /**
     * Check if the base position (objoff_30) is within the despawn range of the camera.
     * Mirrors the ROM's MarkObjGone check: ((baseX & 0xFF80) - cameraCoarse) <= 0x280.
     */
    private boolean isBasePositionOnScreen() {
        Camera camera =
                Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int camXCoarse = camera.getX() & 0xFF80;
        int diff = (baseX & 0xFF80) - camXCoarse;
        // Unsigned comparison: diff treated as unsigned 16-bit must be <= 0x280
        return (diff & 0xFFFF) <= 0x280;
    }

    @Override
    public int getPriorityBucket() {
        // From disassembly: move.b #4,priority(a0)
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        PatternSpriteRenderer renderer = null;

        if (renderManager != null) {
            renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_LAVA_CUP);
        }

        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
    }

    /**
     * Check if the platform has arrived at its target waypoint.
     * If so, advance to the next waypoint and recalculate velocity.
     * <p>
     * From disassembly loc_2817E (line 54310-54338):
     * Compares x_pos to target X and y_pos to target Y. If both match,
     * advances the waypoint offset by waypointDelta, wraps around the
     * path length, reads the next waypoint, and recalculates velocity.
     */
    private void checkAndAdvanceWaypoint() {
        if (x != targetX || y != targetY) {
            return;
        }

        // Advance waypoint offset and wrap
        waypointOffset = wrapWaypointOffset(waypointOffset + waypointDelta);

        // Read new target from path data
        int wpIndex = waypointOffset / 4;
        targetX = baseX + signExtend16(pathData[wpIndex][0]);
        targetY = baseY + signExtend16(pathData[wpIndex][1]);

        // Recalculate velocity toward new target
        calculateVelocity();
    }

    /**
     * Calculate velocity to move from current position to target using
     * the shared LCon_ChangeDir dominant-axis algorithm.
     * <p>
     * From disassembly loc_281DA (lines 54345-54398).
     *
     * @see WaypointPathFollower#calculateWaypointVelocity
     */
    private void calculateVelocity() {
        var vel = WaypointPathFollower.calculateWaypointVelocity(x, y, targetX, targetY, MOVE_SPEED);
        xVel = vel.xVel();
        yVel = vel.yVel();
        xSub = vel.xSub();
        ySub = vel.ySub();
    }

    /**
     * Apply velocity to position (ObjectMove equivalent).
     * <p>
     * From disassembly ObjectMove (s2.asm line 29990):
     * Position is stored as 16.16 fixed point (x_pos:x_sub as 32-bit long).
     * Velocity (x_vel) is sign-extended to 32 bits, shifted left 8, then added.
     * <pre>
     * d2 = x_pos:x_sub              ; 32-bit position
     * d0 = ext.l(x_vel) << 8        ; velocity shifted into middle 16 bits
     * d2 += d0                       ; add velocity
     * x_pos:x_sub = d2              ; store back
     * </pre>
     */
    private void applyVelocity() {
        // Delegates to SubpixelMotion.speedToPos for ROM-accurate 16.16 integration
        motion.x = x; motion.y = y;
        motion.xSub = xSub; motion.ySub = ySub;
        motion.xVel = xVel; motion.yVel = yVel;
        SubpixelMotion.speedToPos(motion);
        x = motion.x; y = motion.y;
        xSub = motion.xSub; ySub = motion.ySub;
    }

    /**
     * Wrap waypoint offset around the path length.
     * When advancing past the end, wraps to 0. When going before 0, wraps to end.
     * <p>
     * From disassembly (lines 54319-54327).
     *
     * @see WaypointPathFollower#wrapWaypointIndex
     */
    private int wrapWaypointOffset(int offset) {
        return WaypointPathFollower.wrapWaypointIndex(offset, pathLength, WAYPOINT_STEP);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int halfWidth = WIDTH_PIXELS;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - Y_RADIUS;
        int bottom = y + Y_RADIUS + 1;

        // Green box for platform collision bounds
        ctx.drawLine(left, top, right, top, 0.4f, 0.9f, 0.4f);
        ctx.drawLine(right, top, right, bottom, 0.4f, 0.9f, 0.4f);
        ctx.drawLine(right, bottom, left, bottom, 0.4f, 0.9f, 0.4f);
        ctx.drawLine(left, bottom, left, top, 0.4f, 0.9f, 0.4f);

        // Center cross
        ctx.drawLine(x - 4, y, x + 4, y, 0.4f, 0.9f, 0.4f);
        ctx.drawLine(x, y - 4, x, y + 4, 0.4f, 0.9f, 0.4f);
    }


    /**
     * Sign-extend a 16-bit value stored as int to a signed int.
     */
    private static int signExtend16(int value) {
        return (short) value;
    }
}
