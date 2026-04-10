package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.SeamlessLevelTransitionRequest;
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
        GraphicsManager gm = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics();
        if (gm.isGlInitialized()) {
            gm.cachePaletteTexture(palette, PALETTE_LINE);
        }
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
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .setHczPendingPostTransitionCutscene(true);

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
