package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kMusic;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Cutscene Knuckles object for the AIZ1 intro cinematic.
 *
 * Disassembly reference: sonic3k.asm CutsceneKnux_AIZ1 (loc_61DBE onward).
 *
 * The ROM uses stride-2 routine dispatch (0, 2, 4, 6, 8, 10, 12).
 * We use the raw stride-2 values so routine IDs match the disassembly.
 *
 * Knuckles is spawned by the AizPlaneIntroInstance at routine 0x14
 * (when player X >= 0x918). He falls in, stands, paces, laughs while
 * collecting the scattered emeralds, then exits to trigger the title card.
 *
 * Routine overview:
 *   0  (0x00) - Init: set position, mapping_frame, y_radius, spawn rock child, load palette
 *   2  (0x02) - Wait trigger: poll parent status bit 7, then set velocity and become visible
 *   4  (0x04) - Fall: animate fall, MoveSprite with gravity, land on floor
 *   6  (0x06) - Stand: wait 0x7F frames, then flip facing and start pacing
 *   8  (0x08) - Pace: walk left then right, collecting emeralds, then laugh
 *  10  (0x0A) - Laugh: animate laugh for 0x3F frames, then start exit walk
 *  12  (0x0C) - Exit: walk offscreen, unlock controls, spawn title card, delete self
 */
public class CutsceneKnucklesAiz1Instance extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(CutsceneKnucklesAiz1Instance.class.getName());

    // -----------------------------------------------------------------------
    // ROM constants (sonic3k.asm CutsceneKnux_AIZ1)
    // -----------------------------------------------------------------------

    /** Frames per pace direction (0x29 = 41 frames). */
    public static final int PACE_TIMER = 0x29;

    /** Stand-still countdown frames before pacing (0x7F = 127 frames). */
    public static final int STAND_TIMER = 0x7F;

    /** Laugh animation countdown frames (0x3F = 63 frames). */
    public static final int LAUGH_TIMER = 0x3F;

    /** Pace walk speed in subpixels (0x600 = 6 pixels/frame). */
    public static final int PACE_VELOCITY = 0x600;

    /** Initial fall Y velocity in subpixels (upward = negative). */
    public static final int FALL_INIT_Y_VEL = -0x600;

    /** Initial fall X velocity in subpixels (rightward drift). */
    public static final int FALL_INIT_X_VEL = 0x80;

    /** Initial X position (world coordinates). */
    public static final int INIT_X = 0x1400;

    /** Initial Y position (world coordinates). */
    public static final int INIT_Y = 0x440;

    /** Standard S3K gravity in subpixels per frame. */
    private static final int GRAVITY = SubpixelMotion.S3K_GRAVITY;

    /** Y collision radius from ROM (y_radius = 0x13). */
    private static final int Y_RADIUS = 0x13;

    /** Initial mapping frame from ROM. */
    private static final int INIT_MAPPING_FRAME = 8;

    /** Mapping frame set after landing. */
    private static final int LANDED_MAPPING_FRAME = 0x16;

    // -----------------------------------------------------------------------
    // Mutable state
    // -----------------------------------------------------------------------

    /** Routine counter (stride-2: 0, 2, 4, 6, 8, 10, 12). */
    private int routine;

    /** Current world X position (pixels). */
    private int currentX;

    /** Current world Y position (pixels). */
    private int currentY;

    /** Fractional X accumulator (subpixels, 0-255). */
    private int xSub;

    /** Fractional Y accumulator (subpixels, 0-255). */
    private int ySub;

    /** X velocity in subpixels (256 = 1 pixel per frame). */
    private int xVel;

    /** Y velocity in subpixels (256 = 1 pixel per frame). */
    private int yVel;

    /** General-purpose countdown timer. */
    private int waitTimer;

    /** Current mapping frame index. */
    private int mappingFrame;

    /** Whether Knuckles is facing left. */
    private boolean facingLeft;

    /** Pace phase: false = first pass (left), true = return pass (right). */
    private boolean paceReturnPhase;

    /** Whether the parent has signaled the trigger (status bit 7). */
    private boolean triggered;

    /** Whether Knuckles is visible (render_flags bit 7). */
    private boolean visible;

    /** Shared state object for SubpixelMotion calls. */
    private final SubpixelMotion.State motionState = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    // -----------------------------------------------------------------------
    // Animation state (Animate_RawNoSST equivalent)
    // -----------------------------------------------------------------------

    /** Frame countdown before advancing to next animation frame. */
    private int animTimer;

    /** Current index into the active animation frame array. */
    private int animIndex;

    /** Active animation frame sequence (mapping frame indices). */
    private int[] currentAnimFrames;

    /** Duration (in game frames) per animation frame, from ROM script byte 0. */
    private int animDuration;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public CutsceneKnucklesAiz1Instance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnucklesAIZ1");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.routine = 0;
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = 0;
        this.yVel = 0;
        this.waitTimer = 0;
        this.mappingFrame = INIT_MAPPING_FRAME;
        this.facingLeft = false;
        this.paceReturnPhase = false;
        this.triggered = false;
        this.visible = false;
    }

    // -----------------------------------------------------------------------
    // ObjectInstance interface
    // -----------------------------------------------------------------------

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
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 0  -> routine0Init();
            case 2  -> routine2WaitTrigger();
            case 4  -> routine4Fall();
            case 6  -> routine6Stand();
            case 8  -> routine8Pace();
            case 10 -> routine10Laugh();
            case 12 -> routine12Exit();
            default -> {
                // Invalid routine - no-op
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) return;
        PatternSpriteRenderer renderer = AizIntroArtLoader.getKnucklesRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, facingLeft, false);
    }

    // -----------------------------------------------------------------------
    // Routine accessors (for test and external use)
    // -----------------------------------------------------------------------

    public int getRoutine() {
        return routine;
    }

    public int getXVel() {
        return xVel;
    }

    public int getYVel() {
        return yVel;
    }

    public int getWaitTimer() {
        return waitTimer;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    public boolean isFacingLeft() {
        return facingLeft;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Returns whether the trigger flag (status bit 7) has been set.
     * Polled by the rock child to know when to break.
     */
    public boolean isTriggered() {
        return triggered;
    }

    /**
     * Called by the parent intro object to signal the trigger (status bit 7).
     * Knuckles transitions from wait to fall on the next update.
     */
    public void trigger() {
        this.triggered = true;
    }

    // -----------------------------------------------------------------------
    // Animation helpers (Animate_RawNoSST equivalent)
    // -----------------------------------------------------------------------

    /**
     * Loads a ROM animation script and starts playing it.
     *
     * <p>ROM script format: first byte = duration, subsequent bytes = frame indices
     * until terminator $FC (loop) or $F4 (end/hold last frame).
     *
     * <p>Special command $F8 (AnimateRaw_Jump): next byte is an offset from the
     * script start. Continues reading from the target address (which starts with
     * a new duration byte, then more frames). Only one jump level is supported.
     *
     * @param romAddr ROM address of the animation script
     */
    private void loadAnimScript(int romAddr) {
        try {
            Rom rom = GameServices.rom().getRom();
            animDuration = rom.readByte(romAddr) & 0xFF;
            animTimer = animDuration;
            animIndex = 0;

            // Read frame indices until terminator
            int addr = romAddr + 1;
            int count = 0;
            int[] temp = new int[64];
            while (count < temp.length) {
                int b = rom.readByte(addr + count) & 0xFF;
                if (b == 0xFC || b == 0xF4) break;
                if (b == 0xF8) {
                    // AnimateRaw_Jump: read offset, jump to scriptStart + offset
                    int jumpOffset = rom.readByte(addr + count + 1) & 0xFF;
                    int target = romAddr + jumpOffset;
                    // Target starts with a new duration byte, then frames
                    // Use the new duration (overwrite if different)
                    animDuration = rom.readByte(target) & 0xFF;
                    animTimer = animDuration;
                    // Continue reading frames from target + 1
                    addr = target + 1;
                    count = collectFrames(rom, addr, temp, count);
                    break;
                }
                temp[count++] = b;
            }

            currentAnimFrames = new int[count];
            System.arraycopy(temp, 0, currentAnimFrames, 0, count);
            if (count > 0) {
                mappingFrame = currentAnimFrames[0];
            }
        } catch (Exception e) {
            LOG.fine("Could not load anim script at 0x" + Integer.toHexString(romAddr) + ": " + e.getMessage());
            currentAnimFrames = null;
        }
    }

    /**
     * Reads frame indices from ROM into the temp array starting at the given offset.
     * Returns the new count after appending frames until a terminator is found.
     */
    private int collectFrames(Rom rom, int addr, int[] temp, int startCount) throws java.io.IOException {
        int count = startCount;
        int pos = 0;
        while (count < temp.length) {
            int b = rom.readByte(addr + pos) & 0xFF;
            if (b == 0xFC || b == 0xF4 || b == 0xF8) break;
            temp[count++] = b;
            pos++;
        }
        return count;
    }

    /**
     * Advances the animation by one game frame (Animate_RawNoSST).
     * Decrements timer; on expiry, advances to next frame index and loops.
     */
    private void tickAnimation() {
        if (currentAnimFrames == null || currentAnimFrames.length == 0) return;
        animTimer--;
        if (animTimer < 0) {
            animTimer = animDuration;
            animIndex++;
            if (animIndex >= currentAnimFrames.length) {
                animIndex = 0;
            }
            mappingFrame = currentAnimFrames[animIndex];
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0 (loc_61DBE): Init
    // -----------------------------------------------------------------------

    /**
     * Set position to (0x1400, 0x440), mapping_frame = 8, y_radius = 0x13.
     * Spawn rock child object and load palette.
     */
    private void routine0Init() {
        LOG.fine("Routine 0: init Knuckles cutscene at (" + INIT_X + ", " + INIT_Y + ")");

        currentX = INIT_X;
        currentY = INIT_Y;
        mappingFrame = INIT_MAPPING_FRAME;

        // Spawn rock child object (ChildObjDat_6659A via CreateChild1_Normal)
        ObjectSpawn rockSpawn = new ObjectSpawn(
                INIT_X, INIT_Y + 0x20, 0, 0, 0, false, 0);
        CutsceneKnucklesRockChild rock = new CutsceneKnucklesRockChild(rockSpawn, this);
        spawnDynamicObject(rock);

        // Palettes are NOT overwritten here — the intro level's LevelLoadBlock
        // already loads the correct palette lines during level init.

        // ROM: sub_65DD6 (line 134086) — fade out current music, play Knuckles' theme
        // after 90 frames. Spawned as independent object so it outlives Knuckles if needed.
        spawnDynamicObject(new SongFadeTransitionInstance(90, Sonic3kMusic.KNUCKLES.id));

        routine = 2;
    }

    // -----------------------------------------------------------------------
    // Routine 2 (loc_61DF4): Wait Trigger
    // -----------------------------------------------------------------------

    /**
     * Poll parent's status bit 7. When triggered:
     * - Set visible
     * - Set y_vel = -0x600, x_vel = 0x80
     * - Load Pal_CutsceneKnux to palette line 1
     * - Advance to fall routine
     */
    private void routine2WaitTrigger() {
        if (!triggered) {
            return;
        }

        LOG.fine("Routine 2: trigger received, starting fall");

        visible = true;
        yVel = FALL_INIT_Y_VEL;
        xVel = FALL_INIT_X_VEL;

        // Apply Knuckles palette (Pal_CutsceneKnux → palette line 1).
        // ROM does this in init, but GL may not be ready then; trigger time is equivalent.
        AizIntroArtLoader.applyKnucklesPalette();

        // Load react/fall animation (byte_666AF)
        loadAnimScript(Sonic3kConstants.ANIM_CUTSCENE_KNUX_REACT_ADDR);

        routine = 4;
    }

    // -----------------------------------------------------------------------
    // Routine 4 (loc_61E24): Fall
    // -----------------------------------------------------------------------

    /**
     * Animate fall frames (byte_666AF). Apply MoveSprite with gravity.
     * On floor collision: snap Y, mapping_frame = 0x16, timer = 0x7F.
     */
    private void routine4Fall() {
        tickAnimation();

        // MoveSprite: apply velocity to position with subpixel accumulation + gravity.
        int oldYVel = yVel;
        motionState.x = currentX; motionState.y = currentY;
        motionState.xSub = xSub;  motionState.ySub = ySub;
        motionState.xVel = xVel;  motionState.yVel = yVel;
        SubpixelMotion.moveSprite(motionState, GRAVITY);
        currentX = motionState.x; currentY = motionState.y;
        xSub = motionState.xSub;  ySub = motionState.ySub;
        yVel = motionState.yVel;

        // ROM: tst.l d0 / bmi.s locret_61E42 — skip floor check while moving upward.
        // d0 after MoveSprite = old_yVel << 8, so negative when yVel was negative.
        if (oldYVel < 0) {
            return;
        }

        // ObjCheckFloorDist terrain collision.
        // ROM uses bmi (d1 < 0) — snap only when distance is strictly negative.
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        if (floor.foundSurface() && floor.distance() < 0) {
            currentY += floor.distance();
            landOnGround(currentY);
        }
    }

    /**
     * Called externally when terrain collision detects floor contact during fall.
     * Snaps Y to the given ground position and transitions to stand routine.
     *
     * @param groundY the Y coordinate to snap to
     */
    public void landOnGround(int groundY) {
        currentY = groundY;
        yVel = 0;
        xVel = 0;
        mappingFrame = LANDED_MAPPING_FRAME;
        waitTimer = STAND_TIMER;
        routine = 6;
        LOG.fine("Routine 4: landed at Y=" + groundY + ", transitioning to stand");
    }

    // -----------------------------------------------------------------------
    // Routine 6 (loc_61E64): Stand
    // -----------------------------------------------------------------------

    /**
     * Countdown waitTimer (0x7F frames). When expired:
     * - Flip facing direction
     * - Set walk animation (byte_666A9)
     * - x_vel = -0x600 (walk left)
     * - timer = 0x29 (pace frames)
     */
    private void routine6Stand() {
        waitTimer--;
        if (waitTimer < 0) {
            waitTimer = PACE_TIMER;
            facingLeft = true;
            xVel = -PACE_VELOCITY;
            paceReturnPhase = false;

            // Load walk animation (byte_666A9)
            loadAnimScript(Sonic3kConstants.ANIM_CUTSCENE_KNUX_WALK_ADDR);

            routine = 8;
            LOG.fine("Routine 6: stand complete, starting pace left");
        }
    }

    // -----------------------------------------------------------------------
    // Routine 8 (loc_61E96): Pace
    // -----------------------------------------------------------------------

    /**
     * Animate walk + MoveSprite2 + countdown timer.
     *
     * First pass (left): walk left for 0x29 frames, then reverse direction.
     * Return pass (right): walk right for 0x29 frames, then transition to laugh.
     */
    private void routine8Pace() {
        tickAnimation();

        // MoveX: apply X velocity to position (no gravity, no Y movement).
        motionState.x = currentX; motionState.xSub = xSub; motionState.xVel = xVel;
        SubpixelMotion.moveX(motionState);
        currentX = motionState.x; xSub = motionState.xSub;

        waitTimer--;
        if (waitTimer < 0) {
            if (!paceReturnPhase) {
                // First pass complete: reverse to walk right
                xVel = PACE_VELOCITY;
                facingLeft = false;
                waitTimer = PACE_TIMER;
                paceReturnPhase = true;
                LOG.fine("Routine 8: pace left complete, reversing to right");
            } else {
                // Return pass complete: transition to laugh
                xVel = 0;
                waitTimer = LAUGH_TIMER;
                routine = 10;
                LOG.fine("Routine 8: pace right complete, transitioning to laugh");

                // Load look/laugh animation (byte_666B9)
                loadAnimScript(Sonic3kConstants.ANIM_CUTSCENE_KNUX_LOOK_ADDR);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Routine 10 (loc_61EE0): Laugh
    // -----------------------------------------------------------------------

    /**
     * Animate laugh + countdown timer (0x3F frames).
     * When expired: set walk animation, x_vel = 0x600 (walk right to exit).
     * Spawn Obj_Song_Fade_ToLevelMusic (fade to AIZ music).
     */
    private void routine10Laugh() {
        tickAnimation();

        waitTimer--;
        if (waitTimer < 0) {
            xVel = PACE_VELOCITY;
            facingLeft = false;

            // Load walk animation for exit (byte_666A9)
            loadAnimScript(Sonic3kConstants.ANIM_CUTSCENE_KNUX_WALK_ADDR);

            // ROM: Obj_Song_Fade_ToLevelMusic (line 180305) — fade out Knuckles' theme,
            // play AIZ1 music after 120 frames. Independent object survives Knuckles' destruction.
            spawnDynamicObject(new SongFadeTransitionInstance(120, Sonic3kMusic.AIZ1.id));

            routine = 12;
            LOG.fine("Routine 10: laugh complete, transitioning to exit");
        }
    }

    // -----------------------------------------------------------------------
    // Routine 12 (loc_61F10): Exit
    // -----------------------------------------------------------------------

    /**
     * Animate + MoveSprite2 until offscreen (render_flags bit 7 clear).
     * When offscreen:
     * - Clear Palette_cycle_counters
     * - Unlock player controls
     * - Spawn title card
     * - Set Level_started_flag = 0x91
     * - Delete self
     */
    private void routine12Exit() {
        tickAnimation();

        // MoveX: apply X velocity to position (no gravity, no Y movement).
        motionState.x = currentX; motionState.xSub = xSub; motionState.xVel = xVel;
        SubpixelMotion.moveX(motionState);
        currentX = motionState.x; xSub = motionState.xSub;

        // Check if offscreen (render_flags bit 7 clear).
        if (!isOnScreen()) {
            LOG.fine("Routine 12: offscreen, cleaning up");

            // Unlock player controls (ROM: player.object_control = 0)
            unlockPlayerControls();

            // ROM: Level_started_flag = 0x91 — re-enable camera tracking
            // ROM does NOT change level boundaries here — intro bounds stay in effect
            Camera.getInstance().setLevelStarted(true);
            Camera.getInstance().updatePosition(true);

            // TODO: Spawn title card (S3K title card system not yet implemented)

            setDestroyed(true);
        }
    }

    // -----------------------------------------------------------------------
    // Intro→gameplay transition helpers
    // -----------------------------------------------------------------------

    @Override
    public void onUnload() {
        // Knuckles is the last intro object to destroy itself (routine 12),
        // so it owns cleanup of the shared intro art cache.
        try {
            AizIntroArtLoader.reset();
        } catch (Exception ignored) {}
    }

    /**
     * Unlocks player movement controls after the intro cutscene completes.
     * ROM equivalent: clearing player.object_control and control_locked.
     */
    private void unlockPlayerControls() {
        try {
            var sprite = Camera.getInstance().getFocusedSprite();
            if (sprite instanceof AbstractPlayableSprite ps) {
                ps.setControlLocked(false);
                ps.setObjectControlled(false);
                ps.setHidden(false);
            }
        } catch (Exception ignored) {}
    }

}
