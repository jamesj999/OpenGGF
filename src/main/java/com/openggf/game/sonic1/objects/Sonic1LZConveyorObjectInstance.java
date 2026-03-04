package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.Sonic1ConveyorState;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
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

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 63 - Platforms on a conveyor belt (LZ).
 * <p>
 * This object has three distinct modes selected by the subtype byte:
 * <ul>
 *   <li><b>Spawner mode</b> (subtype bit 7 set, i.e. >= $80): Reads child platform positions
 *       from hardcoded path tables and spawns individual platform instances. Uses v_obj63
 *       to prevent duplicate spawning. The spawner itself has no visual representation.</li>
 *   <li><b>Platform mode</b> (subtype < $80, subtype != $7F): A top-solid moving platform
 *       that follows waypoints from one of 6 path data tables. Platforms move between
 *       waypoints with velocity-based interpolation, and can reverse direction when
 *       switch $E is triggered (f_conveyrev). Mapping frame 4 (32x16 platform),
 *       palette line 2, priority 4.</li>
 *   <li><b>Wheel mode</b> (subtype == $7F): An animated wheel sprite that cycles through
 *       4 animation frames (0-3) every 4th game frame. Palette line 0, priority 1.
 *       Uses RememberState (persists when off-screen).</li>
 * </ul>
 * <p>
 * Path data tables (LCon_Data): Each of 6 paths defines a closed loop of X/Y waypoints.
 * Subtype bits 4-6 select the path table. Subtype bits 0-3 select the starting waypoint
 * index. The platform interpolates velocity toward each target waypoint using
 * LCon_ChangeDir, then advances to the next waypoint when it arrives.
 * <p>
 * Reference: docs/s1disasm/_incObj/63 LZ Conveyor.asm
 */
public class Sonic1LZConveyorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(Sonic1LZConveyorObjectInstance.class.getName());

    // ---- Constants from disassembly ----

    // From disassembly: move.b #$10,obActWid(a0)
    private static final int HALF_WIDTH = 0x10;

    // Platform surface height for SolidObjectParams
    private static final int HALF_HEIGHT = 0x08;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PLATFORM_PRIORITY = 4;

    // From disassembly: move.b #1,obPriority(a0) (for wheel subtype 0x7F)
    private static final int WHEEL_PRIORITY = 1;

    // Velocity magnitude for LCon_ChangeDir: move.w #-$100,d2 / move.w #-$100,d3
    private static final int MOVE_SPEED = 0x100;

    // Waypoint step size in bytes: 4 bytes per waypoint (2 words: X, Y)
    private static final int WAYPOINT_STEP = 4;

    // Switch index checked for conveyor reversal: tst.b (f_switch+$E).w
    private static final int REVERSAL_SWITCH_INDEX = 0x0E;

    // Frame mask for wheel animation timing: andi.w #3,d0
    private static final int WHEEL_ANIM_FRAME_MASK = 3;

    // Platform mapping frame index: move.b #4,obFrame(a0)
    private static final int PLATFORM_FRAME = 4;

    // ---- Path data tables (from LCon_Data in disassembly) ----
    // Each entry: {waypointCount, baseX, waypoints[][2]}
    // waypoints are (x, y) pairs forming a closed loop.

    // word_125F4: path 0 (6 waypoints)
    private static final int[][] PATH_0 = {
            {0x1078, 0x021A}, {0x10BE, 0x0260}, {0x10BE, 0x0393},
            {0x108C, 0x03C5}, {0x1022, 0x0390}, {0x1022, 0x0244}
    };
    private static final int PATH_0_BASE_X = 0x1070;

    // word_12610: path 1 (5 waypoints)
    private static final int[][] PATH_1 = {
            {0x127E, 0x0280}, {0x12CE, 0x02D0}, {0x12CE, 0x046E},
            {0x1232, 0x0420}, {0x1232, 0x02CC}
    };
    private static final int PATH_1_BASE_X = 0x1280;

    // word_12628: path 2 (4 waypoints)
    private static final int[][] PATH_2 = {
            {0x0D22, 0x0482}, {0x0D22, 0x05DE}, {0x0DAE, 0x05DE}, {0x0DAE, 0x0482}
    };
    private static final int PATH_2_BASE_X = 0x0D68;

    // word_1263C: path 3 (4 waypoints)
    private static final int[][] PATH_3 = {
            {0x0D62, 0x03A2}, {0x0DEE, 0x03A2}, {0x0DEE, 0x04DE}, {0x0D62, 0x04DE}
    };
    private static final int PATH_3_BASE_X = 0x0DA0;

    // word_12650: path 4 (5 waypoints)
    private static final int[][] PATH_4 = {
            {0x0CAC, 0x0242}, {0x0DDE, 0x0242}, {0x0DDE, 0x03DE},
            {0x0C52, 0x03DE}, {0x0C52, 0x029C}
    };
    private static final int PATH_4_BASE_X = 0x0D00;

    // word_12668: path 5 (4 waypoints)
    private static final int[][] PATH_5 = {
            {0x1252, 0x020A}, {0x13DE, 0x020A}, {0x13DE, 0x02BE}, {0x1252, 0x02BE}
    };
    private static final int PATH_5_BASE_X = 0x1300;

    private static final int[][][] ALL_PATHS = {PATH_0, PATH_1, PATH_2, PATH_3, PATH_4, PATH_5};
    private static final int[] ALL_BASE_X = {PATH_0_BASE_X, PATH_1_BASE_X, PATH_2_BASE_X,
            PATH_3_BASE_X, PATH_4_BASE_X, PATH_5_BASE_X};

    // ---- Instance state ----

    /** Object mode: SPAWNER, PLATFORM, or WHEEL. */
    private enum Mode { SPAWNER, PLATFORM, WHEEL }

    private final Mode mode;

    // Current position (updated by movement for platforms)
    private int x;
    private int y;

    // Platform mode state
    private int[][] waypoints;       // waypoint array for this platform's path
    private int waypointCount;       // number of waypoints in the path
    private int currentWaypointIdx;  // current waypoint byte offset (objoff_38), always multiple of 4
    private int waypointStep;        // +4 forward, -4 reverse (objoff_3A)
    private int targetX;             // current target X (objoff_34)
    private int targetY;             // current target Y (objoff_36)
    private int velX;                // X velocity (obVelX)
    private int velY;                // Y velocity (obVelY)
    private int xFrac;               // X subpixel fractional (obX+2 low word)
    private int yFrac;               // Y subpixel fractional (obY+2 low word)
    private int baseX;               // base X for out_of_range check (objoff_30)
    private boolean dirReversed;     // local tracking of f_conveyrev state (objoff_3B)
    private int routine;             // platform routine: 2 = PlatformObject, 4 = ExitPlatform+MvSonicOnPtfm2

    // Spawner mode state
    private int spawnerSlotIndex;    // v_obj63 slot index (objoff_2F & 0x7F)
    private boolean spawnerDone;     // set after spawning children

    // Wheel mode state
    private int wheelFrame;          // current animation frame (0-3)

    // Dynamic spawn for position updates
    private ObjectSpawn dynamicSpawn;

    /**
     * Creates a conveyor belt object instance.
     *
     * @param spawn the object spawn data from the level
     */
    public Sonic1LZConveyorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LZConveyor");
        int subtype = spawn.subtype() & 0xFF;

        this.x = spawn.x();
        this.y = spawn.y();

        if ((subtype & 0x80) != 0) {
            // Subtype bit 7 set: spawner mode
            // From disassembly loc_12460: move.b d0,objoff_2F(a0)
            this.mode = Mode.SPAWNER;
            this.spawnerSlotIndex = subtype & 0x7F;
            this.spawnerDone = false;
            this.routine = 0;
        } else if (subtype == 0x7F) {
            // Subtype 0x7F: wheel mode (routine 6)
            // From disassembly: addq.b #4,obRoutine(a0) -> routine 6 immediately
            this.mode = Mode.WHEEL;
            this.wheelFrame = 0;
            this.routine = 6;
        } else {
            // Subtype 0x00-0x7E (excluding 0x7F): platform mode
            this.mode = Mode.PLATFORM;
            this.routine = 2; // starts in PlatformObject routine

            // Parse path index from subtype bits 4-6
            // From disassembly: lsr.w #3,d0 / andi.w #$1E,d0
            int pathIndex = (subtype >> 4) & 0x07;
            if (pathIndex >= ALL_PATHS.length) {
                pathIndex = 0; // safety fallback
            }

            this.waypoints = ALL_PATHS[pathIndex];
            this.waypointCount = waypoints.length * WAYPOINT_STEP;
            this.baseX = ALL_BASE_X[pathIndex];

            // Starting waypoint from subtype bits 0-3
            // From disassembly: andi.w #$F,d1 / lsl.w #2,d1
            int startWaypointIdx = (subtype & 0x0F) * WAYPOINT_STEP;
            this.currentWaypointIdx = startWaypointIdx;

            // Default step: +4 (forward)
            // From disassembly: move.b #4,objoff_3A(a0)
            this.waypointStep = WAYPOINT_STEP;

            // Check f_conveyrev at init time
            Sonic1ConveyorState conveyorState = Sonic1ConveyorState.getInstance();
            if (conveyorState.isReversed()) {
                // From disassembly: move.b #1,objoff_3B(a0) / neg.b objoff_3A(a0)
                this.dirReversed = true;
                this.waypointStep = -WAYPOINT_STEP;

                // Advance to next waypoint in reverse direction
                int nextIdx = currentWaypointIdx + waypointStep;
                if (nextIdx < 0 || nextIdx >= waypointCount) {
                    // Wrap: if negative, use last waypoint; if >= count, use 0
                    if (nextIdx < 0) {
                        nextIdx = waypointCount - WAYPOINT_STEP;
                    } else {
                        nextIdx = 0;
                    }
                }
                currentWaypointIdx = nextIdx;
            }

            // Set initial target from current waypoint
            // From disassembly loc_1244C:
            //   move.w (a2,d1.w),objoff_34(a0)  ; target X
            //   move.w 2(a2,d1.w),objoff_36(a0)  ; target Y
            int wpArrayIdx = currentWaypointIdx / WAYPOINT_STEP;
            if (wpArrayIdx >= 0 && wpArrayIdx < waypoints.length) {
                targetX = waypoints[wpArrayIdx][0];
                targetY = waypoints[wpArrayIdx][1];
            }

            // Initialize velocity and subpixel fractions
            this.velX = 0;
            this.velY = 0;
            this.xFrac = 0;
            this.yFrac = 0;

            // Calculate initial velocity toward target (bsr.w LCon_ChangeDir)
            changeDirection();
        }

        refreshDynamicSpawn();
    }

    /**
     * Package-private constructor for platforms spawned by the spawner.
     * Creates a platform at the specified position with the given subtype.
     */
    Sonic1LZConveyorObjectInstance(int spawnX, int spawnY, int subtype) {
        this(new ObjectSpawn(spawnX, spawnY,
                Sonic1ObjectIds.LZ_CONVEYOR, subtype, 0, false, 0));
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
        switch (mode) {
            case SPAWNER -> updateSpawner();
            case PLATFORM -> updatePlatform(frameCounter, player);
            case WHEEL -> updateWheel(frameCounter);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (mode == Mode.SPAWNER) {
            return; // spawner has no visual
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.LZ_CONVEYOR);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame;
        if (mode == Mode.WHEEL) {
            frame = wheelFrame & WHEEL_ANIM_FRAME_MASK;
            // Wheels use palette 0: make_art_tile(ArtTile_LZ_Conveyor_Belt,0,0)
            renderer.drawFrameIndex(frame, x, y, false, false, 0);
        } else {
            frame = PLATFORM_FRAME;
            // Platforms use palette 2 (sheet default)
            renderer.drawFrameIndex(frame, x, y, false, false);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (mode == Mode.PLATFORM) {
            return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
        }
        return null;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Platform contact is managed via ObjectManager riding checks.
        // When player stands on us, routine transitions from 2 to 4.
        if (mode == Mode.PLATFORM && contact.standing()) {
            routine = 4; // ExitPlatform + MvSonicOnPtfm2 mode
        }
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return mode == Mode.PLATFORM && !isDestroyed();
    }

    @Override
    public int getPriorityBucket() {
        if (mode == Mode.WHEEL) {
            return RenderPriority.clamp(WHEEL_PRIORITY);
        }
        return RenderPriority.clamp(PLATFORM_PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        if (isDestroyed()) {
            return false;
        }
        if (mode == Mode.WHEEL) {
            // Wheel uses RememberState: always persistent
            return true;
        }
        if (mode == Mode.SPAWNER) {
            // Spawner is transient - only needs one frame to spawn children
            return !spawnerDone;
        }
        // Platform: out_of_range uses objoff_30 (base X)
        return isOnScreenX(baseX, 320);
    }

    // ---- Spawner mode ----

    /**
     * Spawner routine (subtype bit 7 set).
     * Reads child platform entries from the hardcoded path data and spawns
     * individual platform objects. Each spawner slot is tracked in v_obj63
     * to prevent re-spawning.
     * <p>
     * From disassembly loc_12460:
     * <pre>
     *   move.b  d0,objoff_2F(a0)         ; save spawner slot info
     *   andi.w  #$7F,d0
     *   lea     (v_obj63).w,a2
     *   bset    #0,(a2,d0.w)             ; test-and-set spawned flag
     *   bne.w   DeleteObject             ; already spawned? delete
     *   add.w   d0,d0                    ; d0 = slot * 2
     *   andi.w  #$1E,d0
     *   addi.w  #ObjPosLZPlatform_Index-ObjPos_Index,d0
     *   lea     (ObjPos_Index).l,a2
     *   adda.w  (a2,d0.w),a2             ; a2 -> platform position list
     *   move.w  (a2)+,d1                 ; d1 = count - 1
     *   ...                              ; spawn loop: X, Y, subtype words
     * </pre>
     * <p>
     * Rather than reading from the binary objpos files in ROM, we embed the
     * platform position data directly (it is small and static).
     */
    private void updateSpawner() {
        if (spawnerDone) {
            return;
        }
        spawnerDone = true;

        Sonic1ConveyorState conveyorState = Sonic1ConveyorState.getInstance();
        if (conveyorState.testAndSetSpawned(spawnerSlotIndex)) {
            // Already spawned - delete self (FixBugs: avoid returning to main loop)
            setDestroyed(true);
            return;
        }

        // Get platform position data for this spawner slot
        int[][] positionData = getSpawnerPositionData(spawnerSlotIndex);
        if (positionData == null) {
            setDestroyed(true);
            return;
        }

        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            setDestroyed(true);
            return;
        }

        // Spawn child platforms - first one replaces this object's identity,
        // rest are new dynamic objects
        for (int i = 0; i < positionData.length; i++) {
            int childX = positionData[i][0];
            int childY = positionData[i][1];
            int childSubtype = positionData[i][2];

            Sonic1LZConveyorObjectInstance child =
                    new Sonic1LZConveyorObjectInstance(childX, childY, childSubtype);
            levelManager.getObjectManager().addDynamicObject(child);
        }

        // Spawner itself is consumed after spawning
        setDestroyed(true);
    }

    /**
     * Returns platform position data for a given spawner slot index.
     * Data sourced from the binary objpos/lzNpfN.bin files in the ROM.
     * <p>
     * Each entry is {x, y, subtype}. The subtype word from ROM has the subtype
     * in the low byte; the high byte is the render/status flags word.
     * <p>
     * Data extracted from docs/s1disasm/objpos/lz*pf*.bin files.
     * Format per entry: word X, word Y, word (subtype in low byte).
     */
    private static int[][] getSpawnerPositionData(int slotIndex) {
        // Data extracted from docs/s1disasm/objpos/lzNpfN.bin files.
        // Format per ROM entry: word count-1, then per platform: word X, word Y, word (subtype in low byte).
        return switch (slotIndex) {
            // LZ1 pf1 (objpos/lz1pf1.bin): 8 platforms
            case 0 -> new int[][] {
                    {0x1078, 0x021A, 0x00}, {0x10BE, 0x0291, 0x02},
                    {0x10BE, 0x0307, 0x02}, {0x10BE, 0x037E, 0x02},
                    {0x105C, 0x0390, 0x04}, {0x1022, 0x0352, 0x05},
                    {0x1022, 0x02DB, 0x05}, {0x1022, 0x0265, 0x05}
            };
            // LZ1 pf2 (objpos/lz1pf2.bin): 8 platforms
            case 1 -> new int[][] {
                    {0x127E, 0x0280, 0x10}, {0x12CE, 0x0305, 0x12},
                    {0x12CE, 0x038A, 0x12}, {0x12CE, 0x040F, 0x12},
                    {0x12A7, 0x046E, 0x13}, {0x1232, 0x040F, 0x14},
                    {0x1232, 0x038A, 0x14}, {0x1232, 0x0305, 0x14}
            };
            // LZ2 pf1 (objpos/lz2pf1.bin): 8 platforms
            case 2 -> new int[][] {
                    {0x0D22, 0x0483, 0x21}, {0x0D9C, 0x0482, 0x20},
                    {0x0DAE, 0x04EA, 0x23}, {0x0DAE, 0x0564, 0x23},
                    {0x0DAE, 0x05DD, 0x23}, {0x0D34, 0x05DE, 0x22},
                    {0x0D22, 0x0576, 0x21}, {0x0D22, 0x04FC, 0x21}
            };
            // LZ2 pf2 (objpos/lz2pf2.bin): 8 platforms
            case 3 -> new int[][] {
                    {0x0D62, 0x03A2, 0x30}, {0x0DD4, 0x03A2, 0x31},
                    {0x0DEE, 0x03FA, 0x32}, {0x0DEE, 0x046C, 0x32},
                    {0x0DEE, 0x04DD, 0x32}, {0x0D7C, 0x04DE, 0x33},
                    {0x0D62, 0x0486, 0x30}, {0x0D62, 0x0414, 0x30}
            };
            // LZ3 pf1 (objpos/lz3pf1.bin): 12 platforms
            case 4 -> new int[][] {
                    {0x0CAD, 0x0242, 0x41}, {0x0D2D, 0x0242, 0x41},
                    {0x0DAC, 0x0242, 0x41}, {0x0DDE, 0x028F, 0x42},
                    {0x0DDE, 0x030E, 0x42}, {0x0DDE, 0x038D, 0x42},
                    {0x0DB0, 0x03DE, 0x43}, {0x0D31, 0x03DE, 0x43},
                    {0x0CB2, 0x03DE, 0x43}, {0x0C52, 0x03BF, 0x44},
                    {0x0C52, 0x0340, 0x44}, {0x0C52, 0x02C1, 0x44}
            };
            // LZ3 pf2 (objpos/lz3pf2.bin): 9 platforms
            case 5 -> new int[][] {
                    {0x1252, 0x020A, 0x50}, {0x12D2, 0x020A, 0x51},
                    {0x1352, 0x020A, 0x51}, {0x13D2, 0x020A, 0x51},
                    {0x13DE, 0x027E, 0x52}, {0x139E, 0x02BE, 0x53},
                    {0x131E, 0x02BE, 0x53}, {0x129E, 0x02BE, 0x53},
                    {0x1252, 0x028A, 0x50}
            };
            default -> null;
        };
    }

    // ---- Platform mode ----

    /**
     * Platform routine: handles waypoint movement, collision, and switch-triggered reversal.
     * <p>
     * Routines 2 and 4 from disassembly:
     * <pre>
     * loc_124B2 (routine 2): PlatformObject + sub_12502
     * loc_124C2 (routine 4): ExitPlatform + sub_12502 + MvSonicOnPtfm2
     * </pre>
     */
    private void updatePlatform(int frameCounter, AbstractPlayableSprite player) {
        // Check for player standing (transitions between routine 2 and 4)
        boolean playerRiding = isPlayerRiding();
        if (playerRiding) {
            routine = 4;
        } else {
            routine = 2;
        }

        // Apply waypoint movement (sub_12502)
        applyConveyorMovement();

        refreshDynamicSpawn();
    }

    /**
     * Sub_12502: Check switch $E for reversal, advance waypoints, apply velocity.
     * <p>
     * From disassembly sub_12502:
     * <pre>
     *   tst.b   (f_switch+$E).w           ; switch $E pressed?
     *   beq.s   loc_12520                  ; no -> check waypoint arrival
     *   tst.b   objoff_3B(a0)              ; already reversed?
     *   bne.s   loc_12520                  ; yes -> skip
     *   move.b  #1,objoff_3B(a0)           ; mark reversed
     *   move.b  #1,(f_conveyrev).w         ; set global reversal flag
     *   neg.b   objoff_3A(a0)              ; reverse step direction
     *   bra.s   loc_12534                  ; advance waypoint
     * </pre>
     */
    private void applyConveyorMovement() {
        Sonic1ConveyorState conveyorState = Sonic1ConveyorState.getInstance();

        // Check switch $E for reversal trigger
        if (Sonic1SwitchManager.getInstance().isPressed(REVERSAL_SWITCH_INDEX)
                && !dirReversed) {
            // First time switch $E is triggered
            dirReversed = true;
            conveyorState.setReversed(true);
            waypointStep = -waypointStep; // neg.b objoff_3A(a0)
            advanceWaypoint();
        } else {
            // Check if we've arrived at the target waypoint
            if (x == targetX && y == targetY) {
                advanceWaypoint();
            }
        }

        // Apply velocity to position (SpeedToPos)
        applySpeedToPos();
    }

    /**
     * Advances to the next waypoint and recalculates velocity.
     * <p>
     * From disassembly loc_12534:
     * <pre>
     *   moveq   #0,d1
     *   move.b  objoff_38(a0),d1           ; current waypoint offset
     *   add.b   objoff_3A(a0),d1           ; advance by step
     *   cmp.b   objoff_39(a0),d1           ; past end?
     *   blo.s   loc_12552                  ; no -> use new index
     *   move.b  d1,d0
     *   moveq   #0,d1
     *   tst.b   d0                         ; check if negative (underflow)
     *   bpl.s   loc_12552                  ; positive -> use 0
     *   move.b  objoff_39(a0),d1           ; negative -> wrap to last
     *   subq.b  #4,d1
     * </pre>
     */
    private void advanceWaypoint() {
        int nextIdx = currentWaypointIdx + waypointStep;

        // Wrap logic matching disassembly unsigned byte comparison
        // cmp.b objoff_39(a0),d1 — compares against total waypoint byte count
        // blo.s (below/unsigned lower): if nextIdx < waypointCount, use it
        // Otherwise: if byte value went negative (>= 0x80), wrap to last waypoint
        //            if byte value is positive but >= count, wrap to 0
        if (nextIdx < 0 || nextIdx >= waypointCount) {
            if (nextIdx < 0) {
                // Underflow: wrap to last waypoint
                nextIdx = waypointCount - WAYPOINT_STEP;
            } else {
                // Overflow: wrap to first waypoint
                nextIdx = 0;
            }
        }

        currentWaypointIdx = nextIdx;

        // Update target from new waypoint
        int wpArrayIdx = nextIdx / WAYPOINT_STEP;
        if (wpArrayIdx >= 0 && wpArrayIdx < waypoints.length) {
            targetX = waypoints[wpArrayIdx][0];
            targetY = waypoints[wpArrayIdx][1];
        }

        // Recalculate velocity
        changeDirection();
    }

    /**
     * LCon_ChangeDir: Calculates velocity components to move from current
     * position toward the target waypoint.
     * <p>
     * Algorithm: Determine which axis has the greater absolute distance.
     * The dominant axis gets a fixed speed of $100 (256 subpixels/frame).
     * The minor axis gets a proportionally scaled speed via 68000 signed division.
     * The division remainder initializes the subpixel fractional accumulator.
     * <p>
     * From disassembly LCon_ChangeDir (lines 226-280):
     * <pre>
     *   d0 = |obX - targetX|, d2 = signed X speed (+-$100 toward target)
     *   d1 = |obY - targetY|, d3 = signed Y speed (+-$100 toward target)
     *   if |deltaY| >= |deltaX|:
     *       velX = -((obX-targetX) << 8) / |deltaY|, velY = d3
     *       xFrac = division remainder, yFrac = 0
     *   else:
     *       velY = -((obY-targetY) << 8) / |deltaX|, velX = d2
     *       yFrac = division remainder, xFrac = 0
     * </pre>
     */
    private void changeDirection() {
        // Step 1: Calculate absolute deltas and signed speed directions
        // d0 = obX - targetX; d2 = -$100 initially
        int deltaX = (short) (x - targetX); // signed word subtraction
        int absDeltaX = Math.abs(deltaX);
        int speedDirX = -MOVE_SPEED; // d2 starts as -$100
        if (deltaX < 0) {
            // bcc not taken -> neg.w d0, neg.w d2
            speedDirX = MOVE_SPEED; // move right toward target
        }

        // d1 = obY - targetY; d3 = -$100 initially
        int deltaY = (short) (y - targetY); // signed word subtraction
        int absDeltaY = Math.abs(deltaY);
        int speedDirY = -MOVE_SPEED; // d3 starts as -$100
        if (deltaY < 0) {
            // bcc not taken -> neg.w d1, neg.w d3
            speedDirY = MOVE_SPEED; // move down toward target
        }

        // Step 2: Compare magnitudes
        // cmp.w d0,d1 / blo.s loc_125C2 -> if absDeltaY < absDeltaX, X-dominant
        if (absDeltaY < absDeltaX) {
            // X-dominant path (loc_125C2): X gets fixed speed, Y is proportional
            velX = speedDirX; // move.w d2,obVelX(a0)

            // d1 = obY - targetY (re-read as signed)
            int signedDeltaY = (short) (y - targetY);
            if (signedDeltaY == 0) {
                velY = 0;
            } else {
                // ext.l d1 / asl.l #8,d1 / divs.w d0,d1 / neg.w d1
                int numerator = signedDeltaY << 8;
                int quotient = numerator / absDeltaX;
                int remainder = numerator % absDeltaX;
                velY = (short) (-quotient); // neg.w d1, truncate to 16-bit

                // swap d1 -> move.w d1,obY+2(a0): remainder becomes Y subpixel
                yFrac = (remainder & 0xFFFF) << 8; // approximate: the swap gets the high word
            }

            xFrac = 0; // clr.w obX+2(a0)
        } else {
            // Y-dominant path (loc_12598 fall-through): Y gets fixed speed, X is proportional
            velY = speedDirY; // move.w d3,obVelY(a0)

            // d0 = obX - targetX (re-read as signed)
            int signedDeltaX = (short) (x - targetX);
            if (signedDeltaX == 0) {
                velX = 0;
            } else {
                // ext.l d0 / asl.l #8,d0 / divs.w d1,d0 / neg.w d0
                int numerator = signedDeltaX << 8;
                int quotient = numerator / absDeltaY;
                int remainder = numerator % absDeltaY;
                velX = (short) (-quotient); // neg.w d0, truncate to 16-bit

                // swap d0 -> move.w d0,obX+2(a0): remainder becomes X subpixel
                xFrac = (remainder & 0xFFFF) << 8; // approximate
            }

            yFrac = 0; // clr.w obY+2(a0)
        }
    }

    /**
     * SpeedToPos: Applies velocity to position using 16.8 fixed-point arithmetic.
     * <p>
     * From disassembly (sub SpeedToPos.asm):
     * <pre>
     *   move.w  obVelX(a0),d0
     *   ext.l   d0
     *   lsl.l   #8,d0
     *   add.l   d0,obX(a0)    ; adds to 32-bit X (16.16 effectively)
     *   move.w  obVelY(a0),d0
     *   ext.l   d0
     *   lsl.l   #8,d0
     *   add.l   d0,obY(a0)    ; adds to 32-bit Y (16.16 effectively)
     * </pre>
     * <p>
     * On the 68000, obX is a 32-bit field: high word = integer position,
     * low word = subpixel fraction. The velocity is sign-extended to 32 bits
     * and shifted left by 8, giving an effective 8.8 -> 16.16 conversion.
     */
    private void applySpeedToPos() {
        // X: 32-bit position = (x << 16) | xFrac
        long xPos32 = ((long) x << 16) | (xFrac & 0xFFFF);
        xPos32 += (long) (short) velX << 8;
        x = (int) (xPos32 >> 16);
        xFrac = (int) (xPos32 & 0xFFFF);

        // Y: 32-bit position = (y << 16) | yFrac
        long yPos32 = ((long) y << 16) | (yFrac & 0xFFFF);
        yPos32 += (long) (short) velY << 8;
        y = (int) (yPos32 >> 16);
        yFrac = (int) (yPos32 & 0xFFFF);
    }

    // ---- Wheel mode ----

    /**
     * Wheel routine (subtype 0x7F, routine 6).
     * Animates through 4 wheel frames, advancing every 4th game frame.
     * Direction depends on f_conveyrev global flag.
     * <p>
     * From disassembly loc_124DE:
     * <pre>
     *   move.w  (v_framecount).w,d0
     *   andi.w  #3,d0                  ; every 4th frame
     *   bne.s   loc_124FC              ; not time yet -> skip
     *   moveq   #1,d1                  ; default direction = +1
     *   tst.b   (f_conveyrev).w
     *   beq.s   loc_124F2
     *   neg.b   d1                     ; reversed -> direction = -1
     * loc_124F2:
     *   add.b   d1,obFrame(a0)
     *   andi.b  #3,obFrame(a0)         ; wrap to 0-3
     * </pre>
     */
    private void updateWheel(int frameCounter) {
        // Every 4th frame: advance animation
        if ((frameCounter & WHEEL_ANIM_FRAME_MASK) == 0) {
            int step = 1;
            if (Sonic1ConveyorState.getInstance().isReversed()) {
                step = -1;
            }
            wheelFrame = (wheelFrame + step) & WHEEL_ANIM_FRAME_MASK;
        }
    }

    // ---- Utility methods ----

    /**
     * Check if any player is riding this platform, via ObjectManager.
     */
    private boolean isPlayerRiding() {
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null) return false;
        var objectManager = levelManager.getObjectManager();
        return objectManager != null && objectManager.isAnyPlayerRiding(this);
    }

    /**
     * Check if the object is within out-of-range distance from camera.
     * Matches the S1 out_of_range.s macro with objoff_30 as the reference X.
     */
    private boolean isOnScreenX(int objectX, int range) {
        var camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    @Override
    public boolean isHighPriority() {
        // Spawner objects need to run immediately to create children
        return mode == Mode.SPAWNER;
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (mode == Mode.SPAWNER) {
            return;
        }

        if (mode == Mode.PLATFORM) {
            // Draw solid collision box
            ctx.drawRect(x, y, HALF_WIDTH, HALF_HEIGHT, 0.3f, 0.6f, 1.0f);

            // Draw target waypoint indicator
            if (waypoints != null) {
                ctx.drawRect(targetX, targetY, 3, 3, 1.0f, 0.3f, 0.3f);
            }

            // Label with state info
            String label = String.format("CV wp%d/%d %s",
                    currentWaypointIdx / WAYPOINT_STEP,
                    waypoints != null ? waypoints.length : 0,
                    dirReversed ? "REV" : "FWD");
            ctx.drawWorldLabel(x, y - HALF_HEIGHT - 8, 0, label, java.awt.Color.CYAN);
        } else if (mode == Mode.WHEEL) {
            // Draw wheel position
            ctx.drawRect(x, y, 0x10, 0x10, 0.5f, 0.5f, 0.2f);
            String label = String.format("WHL f%d", wheelFrame);
            ctx.drawWorldLabel(x, y - 0x10 - 8, 0, label, java.awt.Color.YELLOW);
        }
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
