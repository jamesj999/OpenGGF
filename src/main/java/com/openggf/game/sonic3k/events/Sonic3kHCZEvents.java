package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.HCZ2WallObjectInstance;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.logging.Logger;

/**
 * HCZ (Hydrocity Zone) dynamic level events.
 *
 * <p>ROM: HCZ1_Resize / HCZ2_Resize (sonic3k.asm lines 39244-39320)
 * and HCZ1_BackgroundEvent / HCZ2_BackgroundEvent (sonic3k.asm lines 105702-106121).
 *
 * <h3>Act 1 FG (HCZ1_Resize) — 3 stages:</h3>
 * <ul>
 *   <li>Stage 0: Camera X < $360 AND Camera Y >= $3E0 → write underwater palette mutation, advance to 2</li>
 *   <li>Stage 2: Camera moved back above water → revert to 0; OR Camera Y >= $500 AND X >= $900 → revert, advance to 4</li>
 *   <li>Stage 4: terminal idle</li>
 * </ul>
 *
 * <h3>Act 1 BG (HCZ1_BackgroundEvent) — seamless act transition:</h3>
 * <ul>
 *   <li>Stage 0: normal scrolling; when Events_fg_5 set → queue transition, advance to 4</li>
 *   <li>Stage 4: requestSeamlessTransition to HCZ2 with -$3600 X offset, water $6A0</li>
 * </ul>
 *
 * <h3>Act 2 FG (HCZ2_Resize) — 2 stages:</h3>
 * <ul>
 *   <li>Stage 0: Camera X >= $C00 → set Events_fg_5, advance to 2</li>
 *   <li>Stage 2: terminal idle</li>
 * </ul>
 */
public class Sonic3kHCZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kHCZEvents.class.getName());

    // =========================================================================
    // FG event state machine stages (stride 4, matching S3K convention)
    // =========================================================================
    private static final int FG_STAGE_0 = 0;
    private static final int FG_STAGE_2 = 4;   // stride 4 → "stage 2" = offset 4
    private static final int FG_STAGE_4 = 8;   // stride 4 → "stage 4" = offset 8

    // =========================================================================
    // BG event state machine stages
    // =========================================================================
    private static final int BG_STAGE_NORMAL = 0;
    private static final int BG_STAGE_DO_TRANSITION = 4;

    // =========================================================================
    // Act 1 palette mutation thresholds (ROM: HCZ1_Resize)
    // =========================================================================
    private static final int PAL_MUT_CAM_X_THRESHOLD = 0x360;
    private static final int PAL_MUT_CAM_Y_UNDERWATER = 0x3E0;
    private static final int PAL_MUT_CAM_Y_PAST = 0x500;
    private static final int PAL_MUT_CAM_X_PAST = 0x900;

    // Underwater palette colors: $0680, $0240, $0220
    // ROM bug: writes $0B80 instead of $0680; FixBugs corrects to $0680.
    private static final int[] PALETTE_UNDERWATER = {0x0680, 0x0240, 0x0220};
    // Revert palette colors: $0CEE, $0ACE, $008A
    private static final int[] PALETTE_NORMAL = {0x0CEE, 0x0ACE, 0x008A};
    // Target: Normal_palette_line_4+$10 = palette line 3 (0-indexed), color offset 8 (3 colors)
    // ROM labels are 1-indexed: Normal_palette_line_4 = palette index 3
    private static final int PALETTE_LINE = 3;
    private static final int PALETTE_COLOR_OFFSET = 8;  // $10 / 2 = 8th color

    // =========================================================================
    // Act 2 FG threshold (ROM: HCZ2_Resize)
    // =========================================================================
    private static final int ACT2_CAM_X_WALL_CHASE_END = 0xC00;

    // =========================================================================
    // Act 2 BG: Wall-chase event constants (ROM: HCZ2_BackgroundEvent/HCZ2_WallMove)
    // =========================================================================

    /** BG event state 0: init — check activation conditions, spawn wall. */
    private static final int BG_WALL_INIT = 0;
    /** BG event state 4: active wall movement. */
    private static final int BG_WALL_MOVE = 4;
    /** BG event state 8: transition from wall-chase to normal deformation. */
    private static final int BG_WALL_TRANSITION = 8;
    /** BG event state $C: BG tile refresh frame. */
    private static final int BG_WALL_REFRESH = 0xC;
    /** BG event state $10: normal Act 2 deformation. */
    private static final int BG_NORMAL = 0x10;

    /** ROM: Camera_X_pos < $C00 for wall event activation. */
    private static final int WALL_ACTIVATE_CAM_X_MAX = 0xC00;
    /** ROM: Camera_Y_pos >= $500 for wall event activation. */
    private static final int WALL_ACTIVATE_CAM_Y_MIN = 0x500;

    /** ROM: Wall starts moving when player X >= $680. */
    private static final int WALL_TRIGGER_PLAYER_X = 0x680;
    /** ROM: Wall speed increases when player X > $A88. */
    private static final int WALL_SPEED_UP_PLAYER_X = 0xA88;

    /** ROM: Base wall speed $E000 (16.16 fixed-point). */
    private static final int WALL_BASE_SPEED = 0xE000;
    /** ROM: Fast wall speed $14000 (16.16 fixed-point). */
    private static final int WALL_FAST_SPEED = 0x14000;

    /** ROM: Wall stops when offset reaches -$600 (-1536 pixels). */
    private static final int WALL_STOP_OFFSET = -0x600;

    /** ROM: Screen_shake_flag = $0E (14 frames) on wall stop. */
    private static final int WALL_STOP_SHAKE_FRAMES = 0x0E;

    /** ROM: BG collision gating — player X range. */
    private static final int BG_COLL_X_MIN = 0x3F0;
    private static final int BG_COLL_X_MAX = 0xC10;
    /** ROM: BG collision gating — player Y range. */
    private static final int BG_COLL_Y_MIN = 0x600;
    private static final int BG_COLL_Y_MAX = 0x840;

    // =========================================================================
    // Seamless transition offsets (ROM: HCZ1BGE_DoTransition)
    // =========================================================================
    private static final int TRANSITION_OFFSET_X = -0x3600;
    private static final int TRANSITION_WATER_LEVEL = 0x6A0;

    // =========================================================================
    // State
    // =========================================================================
    private int fgRoutine;
    private int bgRoutine;

    /** ROM: Events_fg_5 — set by Obj_LevelResultsCreate to trigger BG act transition. */
    private boolean eventsFg5;

    /** Prevents requesting the transition more than once. */
    private boolean transitionRequested;

    // =========================================================================
    // Act 2 BG wall-chase state
    // =========================================================================

    /** Act 2 BG event routine counter (stride 4). */
    private int act2BgRoutine;

    /**
     * Wall movement offset accumulator (ROM: Events_bg+$00).
     * 16.16 fixed-point. Negative values mean the wall has advanced leftward.
     */
    private int wallOffsetFixed;

    /** Integer pixel offset extracted from wallOffsetFixed. */
    private int wallOffsetPixels;

    /** Whether the wall has started moving (player crossed trigger X). */
    private boolean wallMoving;

    /** Whether the wall has reached its stop position (prevents restart loop). */
    private boolean wallStopped;

    /** Timed screen shake countdown (frames remaining, 0 = inactive). */
    private int shakeTimer;

    /** Reference to the spawned wall collision object. */
    private HCZ2WallObjectInstance wallObject;

    // =========================================================================
    // Post-transition whirlpool descent cutscene
    // =========================================================================

    /** Whether the whirlpool descent cutscene is currently playing. */
    private boolean cutsceneActive;

    /** Frame counter for the cutscene (drives sine oscillation). */
    private int cutsceneFrame;

    /** Fixed center X for the whirlpool spiral (player's X at cutscene start). */
    private int cutsceneCenterX;

    /** Current Y position in the descent (top-left coords, updated each frame). */
    private int cutsceneCurrentY;

    /** Target Y position where the cutscene ends and player is released.
     *  Act 2 floor at the starting area (X~$80) is at Y~$820 based on
     *  object placements (breakable walls, platforms at Y=$0820). */
    private static final int CUTSCENE_TARGET_Y = 0x0800;

    /** Descent speed in pixels per frame. */
    private static final int CUTSCENE_DESCENT_SPEED = 4;

    /** Horizontal oscillation amplitude in pixels (side-to-side whirlpool). */
    private static final int CUTSCENE_X_AMPLITUDE = 24;

    /** Oscillation speed — higher = faster side-to-side motion. */
    private static final int CUTSCENE_OSCILLATION_SPEED = 8;

    public Sonic3kHCZEvents() {
        super();
    }

    @Override
    public void init(int act) {
        super.init(act);
        fgRoutine = FG_STAGE_0;
        bgRoutine = BG_STAGE_NORMAL;
        eventsFg5 = false;
        transitionRequested = false;
        cutsceneActive = false;
        cutsceneFrame = 0;

        // Act 2 BG wall-chase state
        act2BgRoutine = BG_WALL_INIT;
        wallOffsetFixed = 0;
        wallOffsetPixels = 0;
        wallMoving = false;
        wallStopped = false;
        shakeTimer = 0;
        wallObject = null;
    }

    @Override
    public void update(int act, int frameCounter) {
        // Whirlpool descent cutscene runs independently of act-specific logic
        if (cutsceneActive) {
            updateCutscene();
            return;
        }

        if (act == 0) {
            updateAct1Fg();
            updateAct1Bg();
        } else {
            updateAct2Fg();
            updateAct2Bg(frameCounter);
        }
    }

    // =========================================================================
    // Post-transition whirlpool descent cutscene
    //
    // After the seamless transition reloads Act 2, this cutscene takes control
    // of Sonic and spirals him downward into the Act 2 starting area.
    // The player oscillates side-to-side while descending — matching the
    // whirlpool visual style used throughout HCZ.
    // =========================================================================

    /**
     * Called by Sonic3kLevelEventManager on the first update after the seamless
     * transition to Act 2 completes. Starts the whirlpool descent cutscene.
     */
    public void startPostTransitionCutscene() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null) {
            LOG.warning("HCZ: cannot start cutscene, no player");
            return;
        }

        cutsceneActive = true;
        cutsceneFrame = 0;
        // Use getY() (top-left) consistently — avoid getCentreY()/setY() mismatch
        cutsceneCenterX = player.getX();
        cutsceneCurrentY = player.getY();

        // Keep player locked in float animation during descent
        player.setObjectControlled(true);
        player.setControlLocked(true);
        player.setAir(true);
        player.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);

        // Do the same for sidekick
        for (AbstractPlayableSprite sidekick : spriteManager().getSidekicks()) {
            sidekick.setObjectControlled(true);
            sidekick.setControlLocked(true);
            sidekick.setAir(true);
            sidekick.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
        }

        LOG.info("HCZ: started whirlpool descent cutscene from Y=" + cutsceneCurrentY
                + " to Y=" + CUTSCENE_TARGET_Y);
    }

    /**
     * Per-frame update for the whirlpool descent cutscene.
     * Oscillates the player side-to-side while moving them downward.
     * All coordinates use top-left (getY/setY) to avoid centre/top-left mismatch.
     */
    private void updateCutscene() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null) {
            cutsceneActive = false;
            return;
        }

        cutsceneFrame++;

        // Descend
        cutsceneCurrentY += CUTSCENE_DESCENT_SPEED;

        // Side-to-side oscillation using sine wave
        int sineAngle = (cutsceneFrame * CUTSCENE_OSCILLATION_SPEED) & 0xFF;
        int xOffset = (TrigLookupTable.sinHex(sineAngle) * CUTSCENE_X_AMPLITUDE) >> 8;

        // Position player — all using top-left coordinates
        player.setX((short) (cutsceneCenterX + xOffset));
        player.setY((short) cutsceneCurrentY);
        // Zero velocities so physics don't interfere
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        // Move sidekick along the same path (slight delay)
        for (AbstractPlayableSprite sidekick : spriteManager().getSidekicks()) {
            int sidekickAngle = ((cutsceneFrame - 8) * CUTSCENE_OSCILLATION_SPEED) & 0xFF;
            int sidekickXOffset = (TrigLookupTable.sinHex(sidekickAngle) * CUTSCENE_X_AMPLITUDE) >> 8;
            sidekick.setX((short) (cutsceneCenterX + sidekickXOffset));
            sidekick.setY((short) (cutsceneCurrentY + 16));
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
        }

        // Check if we've reached the target depth
        if (cutsceneCurrentY >= CUTSCENE_TARGET_Y) {
            endCutscene(player);
        }
    }

    /**
     * Ends the whirlpool descent cutscene and releases the player.
     */
    private void endCutscene(AbstractPlayableSprite player) {
        cutsceneActive = false;

        // Release player
        player.setControlLocked(false);
        player.setObjectControlled(false);
        player.setForcedAnimationId(-1);
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        // Release sidekick
        for (AbstractPlayableSprite sidekick : spriteManager().getSidekicks()) {
            sidekick.setControlLocked(false);
            sidekick.setObjectControlled(false);
            sidekick.setForcedAnimationId(-1);
            sidekick.setAir(true);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
        }

        LOG.info("HCZ: whirlpool descent cutscene ended at Y=" + player.getCentreY());
    }

    // =========================================================================
    // Act 1 FG: Palette mutations (HCZ1_Resize)
    // =========================================================================

    private void updateAct1Fg() {
        int camX = camera().getX();
        int camY = camera().getY();

        switch (fgRoutine) {
            case FG_STAGE_0 -> {
                // ROM: loc_1C892 — underwater palette correction
                if (camX < PAL_MUT_CAM_X_THRESHOLD && camY >= PAL_MUT_CAM_Y_UNDERWATER) {
                    writePaletteColors(PALETTE_UNDERWATER);
                    fgRoutine = FG_STAGE_2;
                    LOG.fine("HCZ1 FG: underwater palette applied, advancing to stage 2");
                }
            }
            case FG_STAGE_2 -> {
                // ROM: loc_1C8B8 — two exit conditions
                if (camX < PAL_MUT_CAM_X_THRESHOLD && camY < PAL_MUT_CAM_Y_UNDERWATER) {
                    // Player moved back above water — revert palette, go back to stage 0
                    writePaletteColors(PALETTE_NORMAL);
                    fgRoutine = FG_STAGE_0;
                    LOG.fine("HCZ1 FG: above water, reverting palette to stage 0");
                } else if (camY >= PAL_MUT_CAM_Y_PAST && camX >= PAL_MUT_CAM_X_PAST) {
                    // Player progressed past the initial underwater area
                    writePaletteColors(PALETTE_NORMAL);
                    fgRoutine = FG_STAGE_4;
                    LOG.fine("HCZ1 FG: past underwater area, advancing to terminal stage 4");
                }
            }
            case FG_STAGE_4 -> {
                // Terminal — idle
            }
        }
    }

    /**
     * Writes 3 Mega Drive palette colors to Normal_palette_line_4 at color offset 8.
     * ROM: move.w #$xxxx,(Normal_palette_line_4+$10).w (and +$12, +$14)
     */
    private void writePaletteColors(int[] mdColors) {
        LevelManager lm = levelManager();
        if (lm == null) return;
        Level level = lm.getCurrentLevel();
        if (level == null) return;
        Palette palette = level.getPalette(PALETTE_LINE);
        if (palette == null) return;

        for (int i = 0; i < mdColors.length; i++) {
            // Convert Mega Drive color word to 2-byte big-endian array
            byte[] segaBytes = {
                    (byte) ((mdColors[i] >> 8) & 0xFF),
                    (byte) (mdColors[i] & 0xFF)
            };
            Palette.Color color = new Palette.Color();
            color.fromSegaFormat(segaBytes, 0);
            palette.setColor(PALETTE_COLOR_OFFSET + i, color);
        }

        // Refresh the GPU palette texture
        cachePaletteTextureIfReady(palette, PALETTE_LINE);
    }

    // =========================================================================
    // Act 1 BG: Seamless act transition (HCZ1_BackgroundEvent)
    // =========================================================================

    private void updateAct1Bg() {
        switch (bgRoutine) {
            case BG_STAGE_NORMAL -> {
                // ROM: HCZ1BGE_Normal — check Events_fg_5 flag
                // Events_fg_5 is set by Obj_LevelResultsCreate at results creation time.
                if (eventsFg5) {
                    eventsFg5 = false;
                    bgRoutine = BG_STAGE_DO_TRANSITION;
                    LOG.info("HCZ1 BG: Events_fg_5 detected, advancing to transition stage");
                }
                // Normal scrolling handled by SwScrlHcz
            }
            case BG_STAGE_DO_TRANSITION -> {
                // ROM: HCZ1BGE_DoTransition waits for Kos_modules_left == 0.
                // We don't have a Kos queue. Instead, wait until endOfLevelFlag is
                // set (results screen has exited) before requesting the transition.
                // This ensures the tally completes first, and critically, the player
                // is still in the victory pose (objectControlled) during the level
                // reload so they don't land on the old terrain. When onExitReady()
                // releases them on the same frame, the terrain is already Act 2's
                // layout and they fall through the gap naturally.
                if (!transitionRequested && gameState().isEndOfLevelFlag()) {
                    requestHcz2Transition();
                }
            }
        }
    }

    /**
     * Requests the seamless transition from HCZ Act 1 to HCZ Act 2.
     * ROM: HCZ1BGE_DoTransition (sonic3k.asm lines 105747-105780).
     *
     * <p>Actions in the ROM:
     * <ul>
     *   <li>Change zone to $101 (HCZ Act 2)</li>
     *   <li>Clear Dynamic_resize_routine, Object_load_routine, etc.</li>
     *   <li>Load_Level (HCZ2 layout), LoadSolids, CheckLevelForWater</li>
     *   <li>Set water to $6A0</li>
     *   <li>Load HCZ2 palette (PalPointers #$D)</li>
     *   <li>Offset all objects and camera by -$3600 X</li>
     * </ul>
     */
    private void requestHcz2Transition() {
        transitionRequested = true;

        // Tell the event manager that the next HCZ Act 2 init should release the player.
        // The player is still in the victory pose (objectControlled) so they don't land
        // on the old terrain. After the level reloads as Act 2, releasing them lets them
        // fall through the gap in Act 2's terrain.
        Sonic3kLevelEventManager levelEventManager = levelEventManagerOrNull();
        if (levelEventManager != null) {
            levelEventManager.setHczPendingPostTransitionCutscene(true);
        }

        LevelManager lm = levelManager();
        lm.requestSeamlessTransition(
                SeamlessLevelTransitionRequest.builder(
                                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                        .targetZoneAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                        .deactivateLevelNow(false)
                        // Results screen already started act 2 music
                        .preserveMusic(true)
                        // Show act 2 title card after the level reloads
                        .showInLevelTitleCard(true)
                        .playerOffset(TRANSITION_OFFSET_X, 0)
                        .cameraOffset(TRANSITION_OFFSET_X, 0)
                        .build());

        LOG.info("HCZ1: requested seamless transition to Act 2 (offset X=" +
                Integer.toHexString(TRANSITION_OFFSET_X) + ")");
    }

    // =========================================================================
    // Act 2 FG: Wall chase end signal (HCZ2_Resize)
    // =========================================================================

    private void updateAct2Fg() {
        switch (fgRoutine) {
            case FG_STAGE_0 -> {
                // ROM: loc_1C908 — signals end of wall-chase section
                if (camera().getX() >= ACT2_CAM_X_WALL_CHASE_END) {
                    eventsFg5 = true;
                    fgRoutine = FG_STAGE_2;
                    LOG.fine("HCZ2 FG: Camera X >= $C00, Events_fg_5 set");
                }
            }
            case FG_STAGE_2 -> {
                // Terminal — idle
            }
        }
    }

    // =========================================================================
    // Act 2 BG: Wall-chase event (HCZ2_BackgroundEvent)
    //
    // ROM: sonic3k.asm lines 106023-106170.
    // 5-state dispatch: init → wall move → transition → refresh → normal.
    // The wall-chase drives a moving solid collision wall from the left side,
    // with screen shake, speed ramping, and BG collision gating.
    // =========================================================================

    private void updateAct2Bg(int frameCounter) {
        // Timed screen shake countdown (ROM: ShakeScreen_Setup countdown)
        if (shakeTimer > 0) {
            shakeTimer--;
            updateScreenShakeFromTimer();
        }

        switch (act2BgRoutine) {
            case BG_WALL_INIT -> act2BgInit();
            case BG_WALL_MOVE -> act2BgWallMove(frameCounter);
            case BG_WALL_TRANSITION -> act2BgTransition();
            case BG_WALL_REFRESH -> act2BgRefresh();
            case BG_NORMAL -> {
                // Normal deformation — scroll handler handles everything
            }
        }
    }

    /**
     * BG state 0: HCZ2BGE_WallMoveInit.
     * ROM: sonic3k.asm line ~105995.
     * Check activation conditions and either spawn the wall or skip to normal.
     */
    private void act2BgInit() {
        int camX = camera().getX();
        int camY = camera().getY();

        if (camX < WALL_ACTIVATE_CAM_X_MAX && camY >= WALL_ACTIVATE_CAM_Y_MIN) {
            // Activation conditions met — start wall chase
            SwScrlHcz scrollHandler = resolveHczScrollHandler();
            if (scrollHandler != null) {
                scrollHandler.setHcz2BgPhase(SwScrlHcz.Hcz2BgPhase.WALL_CHASE);
            }

            // Enable BG high-priority overlay so wall tiles render in front of FG
            gameState().setBgHighPriorityOverlayActive(true);

            // Spawn wall collision object
            wallObject = new HCZ2WallObjectInstance();
            spawnObject(wallObject);

            act2BgRoutine = BG_WALL_MOVE;
            LOG.info("HCZ2 BG: wall-chase activated, wall spawned");
        } else {
            // Conditions not met — skip to normal deformation
            act2BgRoutine = BG_NORMAL;
            LOG.info("HCZ2 BG: wall-chase conditions not met (camX=0x"
                    + Integer.toHexString(camX) + " camY=0x"
                    + Integer.toHexString(camY) + "), skipping to normal");
        }
    }

    /**
     * BG state 4: HCZ2BGE_WallMove.
     * ROM: sonic3k.asm lines 106048-106070 (dispatch) and 106129-106170 (HCZ2_WallMove).
     * Runs wall movement logic each frame and gates BG collision.
     */
    private void act2BgWallMove(int frameCounter) {
        // Check if the wall chase has ended (Events_fg_5 set by FG routine)
        if (eventsFg5) {
            // Clean up wall
            if (wallObject != null) {
                wallObject.deactivate();
                wallObject = null;
            }
            gameState().setBackgroundCollisionFlag(false);
            act2BgRoutine = BG_WALL_TRANSITION;
            LOG.info("HCZ2 BG: wall-chase ended (Events_fg_5), transitioning");
            return;
        }

        // Gate Background_collision_flag by player position
        // ROM: sonic3k.asm lines 106051-106067
        updateBgCollisionGating();

        // Run wall movement logic
        updateWallMove(frameCounter);

        // Update scroll handler with current wall offset
        SwScrlHcz scrollHandler = resolveHczScrollHandler();
        if (scrollHandler != null) {
            scrollHandler.setWallChaseOffsetX(wallOffsetPixels);
            scrollHandler.setScreenShakeOffset(
                    wallMoving ? getShakeOffset(frameCounter) : 0);
        }

        // Update wall object position
        if (wallObject != null) {
            wallObject.updateWallPosition(wallOffsetPixels);
        }
    }

    /**
     * BG state 8: HCZ2BGE_NormalTransition.
     * ROM: sonic3k.asm line ~106080.
     * Switch scroll handler to normal mode, clear BG collision.
     */
    private void act2BgTransition() {
        SwScrlHcz scrollHandler = resolveHczScrollHandler();
        if (scrollHandler != null) {
            scrollHandler.setHcz2BgPhase(SwScrlHcz.Hcz2BgPhase.NORMAL);
            scrollHandler.setScreenShakeOffset(0);
            scrollHandler.setWallChaseOffsetX(0);
        }
        gameState().setBackgroundCollisionFlag(false);
        gameState().setBgHighPriorityOverlayActive(false);
        act2BgRoutine = BG_WALL_REFRESH;
        LOG.fine("HCZ2 BG: transitioning to normal deformation");
    }

    /**
     * BG state $C: HCZ2BGE_NormalRefresh.
     * ROM: BG tile refresh frame, then advance to normal.
     */
    private void act2BgRefresh() {
        act2BgRoutine = BG_NORMAL;
        LOG.fine("HCZ2 BG: normal deformation active");
    }

    /**
     * HCZ2_WallMove — core wall movement logic.
     * ROM: sonic3k.asm lines 106129-106170.
     *
     * <p>Wall movement sequence:
     * <ol>
     *   <li>Wait until player X >= $680 to start</li>
     *   <li>Base speed $E000, increases to $14000 when player X > $A88</li>
     *   <li>Subtract speed from offset accumulator each frame</li>
     *   <li>Play sfx_Rumble2 every 16 frames</li>
     *   <li>Stop at offset -$600, play sfx_Crash, set timed shake</li>
     * </ol>
     */
    private void updateWallMove(int frameCounter) {
        // Once the wall has reached its stop position, don't restart
        if (wallStopped) {
            return;
        }

        if (!wallMoving) {
            // ROM: Wall only starts moving when player X >= $680
            AbstractPlayableSprite player = camera().getFocusedSprite();
            if (player != null && player.getCentreX() >= WALL_TRIGGER_PLAYER_X) {
                wallMoving = true;
                LOG.info("HCZ2: wall started moving (player X >= 0x"
                        + Integer.toHexString(WALL_TRIGGER_PLAYER_X) + ")");
            }
            return;
        }

        // Check if wall has reached its stop position
        if (wallOffsetPixels <= WALL_STOP_OFFSET) {
            // Wall has stopped — permanently
            wallMoving = false;
            wallStopped = true;
            shakeTimer = WALL_STOP_SHAKE_FRAMES;

            // Play crash sound
            var audioManager = audio();
            if (audioManager != null) {
                audioManager.playSfx(Sonic3kSfx.CRASH.id);
            }
            LOG.info("HCZ2: wall stopped at offset " + wallOffsetPixels);
            return;
        }

        // Calculate speed — ROM: base $E000, fast $14000 when player X > $A88
        int speed = WALL_BASE_SPEED;
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player != null && player.getCentreX() > WALL_SPEED_UP_PLAYER_X) {
            speed = WALL_FAST_SPEED;
        }

        // Advance wall (subtract speed from offset — wall moves leftward)
        wallOffsetFixed -= speed;
        wallOffsetPixels = wallOffsetFixed >> 16;

        // Clamp to stop position
        if (wallOffsetPixels < WALL_STOP_OFFSET) {
            wallOffsetPixels = WALL_STOP_OFFSET;
            wallOffsetFixed = WALL_STOP_OFFSET << 16;
        }

        // Play rumble sound every 16 frames
        // ROM: move.w (Level_frame_counter).w,d0 / andi.w #$F,d0 / bne.s + / move.w #sfx_Rumble2,...
        if ((frameCounter & 0x0F) == 0) {
            var audioManager = audio();
            if (audioManager != null) {
                audioManager.playSfx(Sonic3kSfx.RUMBLE_2.id);
            }
        }
    }

    /**
     * Gate Background_collision_flag based on player position.
     * ROM: sonic3k.asm lines 106051-106067.
     * BG collision is only enabled when the player is within the wall-chase corridor.
     */
    private void updateBgCollisionGating() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null) {
            gameState().setBackgroundCollisionFlag(false);
            return;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // ROM: cmpi.w #$3F0,d0 / blo.s clr / cmpi.w #$C10,d0 / bhs.s clr
        //      cmpi.w #$600,d1 / blo.s clr / cmpi.w #$840,d1 / bhs.s clr
        //      st (Background_collision_flag).w
        boolean inRange = playerX >= BG_COLL_X_MIN && playerX < BG_COLL_X_MAX
                && playerY >= BG_COLL_Y_MIN && playerY < BG_COLL_Y_MAX;

        gameState().setBackgroundCollisionFlag(inRange);
    }

    /**
     * ROM: ScreenShakeArray2 (sonic3k.asm line ~104229).
     * 64-entry table used for continuous shake (Screen_shake_flag = -1).
     * Indexed by {@code Level_frame_counter & 0x3F}. Values are unsigned (0-3px).
     */
    private static final byte[] SCREEN_SHAKE_ARRAY_CONTINUOUS = {
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3,
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3
    };

    /**
     * ROM: ScreenShakeArray (sonic3k.asm line ~104226).
     * 20-entry table used for timed shake (Screen_shake_flag > 0).
     * Indexed by countdown value. Values are signed — amplitude increases
     * with index, so shake starts strong (high countdown) and weakens.
     */
    private static final byte[] SCREEN_SHAKE_ARRAY_TIMED = {
            1, -1, 1, -1, 2, -2, 2, -2, 3, -3, 3, -3, 4, -4, 4, -4,
            5, -5, 5, -5
    };

    /**
     * Get continuous screen shake offset based on frame counter.
     * ROM: ShakeScreen_Setup with Screen_shake_flag = -1 (bmi.s branch).
     * Uses ScreenShakeArray2 indexed by {@code frameCounter & 0x3F}.
     */
    private int getShakeOffset(int frameCounter) {
        return SCREEN_SHAKE_ARRAY_CONTINUOUS[frameCounter & 0x3F];
    }

    /**
     * Apply timed screen shake offset during the countdown.
     * ROM: ShakeScreen_Setup with Screen_shake_flag > 0.
     * Decrements flag, reads ScreenShakeArray[flag] as signed offset.
     */
    private void updateScreenShakeFromTimer() {
        SwScrlHcz scrollHandler = resolveHczScrollHandler();
        if (scrollHandler != null) {
            if (shakeTimer > 0 && shakeTimer <= SCREEN_SHAKE_ARRAY_TIMED.length) {
                // ROM: move.b ScreenShakeArray(pc,d0.w),d1 / ext.w d1
                scrollHandler.setScreenShakeOffset(SCREEN_SHAKE_ARRAY_TIMED[shakeTimer - 1]);
            } else {
                scrollHandler.setScreenShakeOffset(0);
            }
        }
    }

    /**
     * Resolve the SwScrlHcz scroll handler from the current game module.
     */
    private SwScrlHcz resolveHczScrollHandler() {
        try {
            if (!hasRuntime()) return null;
            var parallax = parallaxOrNull();
            if (parallax == null) return null;
            ZoneScrollHandler handler = parallax.getHandler(Sonic3kZoneIds.ZONE_HCZ);
            return (handler instanceof SwScrlHcz hcz) ? hcz : null;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Public accessors
    // =========================================================================

    /** Set the Events_fg_5 flag (called by results screen to trigger act transition). */
    public void setEventsFg5(boolean flag) {
        this.eventsFg5 = flag;
        if (flag) {
            LOG.info("HCZ: Events_fg_5 set externally (results screen trigger)");
        }
    }

    public boolean isEventsFg5() {
        return eventsFg5;
    }

    @Override
    public int getDynamicResizeRoutine() {
        return fgRoutine;
    }

    @Override
    public void setDynamicResizeRoutine(int routine) {
        this.fgRoutine = routine;
    }
}
