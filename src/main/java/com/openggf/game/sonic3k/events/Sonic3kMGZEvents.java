package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LayoutMutationIntent;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance;
import com.openggf.game.sonic3k.scroll.SwScrlMgz;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.logging.Logger;

/**
 * Marble Garden Zone dynamic level events.
 *
 * <p>ROM: MGZ1_BackgroundEvent (sonic3k.asm lines 106269-106345),
 * MGZ2_QuakeEvent (sonic3k.asm lines 106579-106786),
 * MGZ2_QuakeEventArray (Lockon S3/Screen Events.asm lines 1027-1030).
 *
 * <h3>Act 1 BG (MGZ1_BackgroundEvent) — seamless act transition:</h3>
 * <ul>
 *   <li>Stage 0 (MGZ1BGE_Normal): normal scrolling; when Events_fg_5 set →
 *       queue MGZ2 art, advance to stage 4</li>
 *   <li>Stage 4 (MGZ1BGE_Transition): wait for Kos queue (engine: for
 *       endOfLevelFlag), then change zone to $201 (MGZ Act 2), reload level,
 *       offset player/camera/objects by (-$2E00, -$600)</li>
 * </ul>
 *
 * <h3>Act 2 FG (MGZ2_QuakeEvent) — Drilling Robotnik mini-events:</h3>
 * Three one-shot appearances of the drilling Robotnik, triggered by the
 * player entering specific boxes. Each appearance locks the camera into
 * a mini-arena, spawns Robotnik with screen shake, then releases when
 * the player moves past a release threshold. The third appearance's
 * release fires {@link #onMgz2BossArenaReached()} — the hook for the
 * (currently unimplemented) end-of-act boss.
 *
 */
public class Sonic3kMGZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kMGZEvents.class.getName());

    private static final int BG_STAGE_NORMAL = 0;
    private static final int BG_STAGE_DO_TRANSITION = 4;

    /** ROM: MGZ1BGE_Transition applies (-$2E00, -$600) to player/camera/objects. */
    private static final int TRANSITION_OFFSET_X = -0x2E00;
    private static final int TRANSITION_OFFSET_Y = -0x600;

    // ========================================================================
    // Act 2 quake-event state machine (MGZ2_QuakeEvent)
    // ========================================================================

    /** ROM: Events_bg+$10 == 0 — scan MGZ2_QuakeEventArray for a matching player box. */
    private static final int QUAKE_CHECK = 0;
    /** ROM: Events_bg+$10 == 4 — first appearance active, waiting to spawn Robotnik. */
    private static final int QUAKE_EVENT_1 = 4;
    /** ROM: Events_bg+$10 == 8 — second appearance active. */
    private static final int QUAKE_EVENT_2 = 8;
    /** ROM: Events_bg+$10 == 12 — third appearance active. */
    private static final int QUAKE_EVENT_3 = 12;
    /** ROM: Events_bg+$10 == 16 — first appearance flee, waiting for release threshold. */
    private static final int QUAKE_EVENT_1_CONT = 16;
    /** ROM: Events_bg+$10 == 20 — second appearance flee. */
    private static final int QUAKE_EVENT_2_CONT = 20;
    /** ROM: Events_bg+$10 == 24 — third appearance flee (fires boss-arena hook). */
    private static final int QUAKE_EVENT_3_CONT = 24;

    /**
     * ROM: MGZ2_QuakeEventArray (Lockon S3/Screen Events.asm:1027-1030).
     * Each row: {minX, maxX, minY, maxY, cameraMaxY, cameraLockX}.
     * cameraLockX is a max-X lock for entry 0 (forces player right), and a
     * min-X lock for entries 1-2 (forces player left).
     */
    private static final int[][] QUAKE_EVENT_ARRAY = {
            {0x0780, 0x07C0, 0x0580, 0x0600, 0x05A0, 0x07E0},
            {0x31C0, 0x3200, 0x01C0, 0x0280, 0x01E0, 0x2F60},
            {0x3440, 0x3480, 0x0680, 0x0700, 0x06A0, 0x32C0},
    };

    /** ROM: QuakeEvent1 waits for player X >= $780 before locking. */
    private static final int EVENT1_PLAYER_X_THRESHOLD = 0x780;
    /** ROM: QuakeEvent2 cancels when player X >= $3200 (retreat). */
    private static final int EVENT2_PLAYER_X_RETREAT = 0x3200;
    /** ROM: QuakeEvent3 cancels when player X >= $3480 (retreat). */
    private static final int EVENT3_PLAYER_X_RETREAT = 0x3480;

    /** ROM: Robotnik spawn positions (x_pos, y_pos, flipX). */
    private static final int[] ROBOTNIK_SPAWN_X = {0x08E0, 0x2FA0, 0x3300};
    private static final int[] ROBOTNIK_SPAWN_Y = {0x0690, 0x02D0, 0x0790};

    /** ROM: QuakeEvent1Cont release when player X >= $980. */
    private static final int EVENT1_CONT_RELEASE_X = 0x980;
    /** ROM: QuakeEvent2Cont release when player Y < $100 AND X >= $2F80. */
    private static final int EVENT2_CONT_RELEASE_Y_MAX = 0x0100;
    private static final int EVENT2_CONT_RELEASE_X_MIN = 0x2F80;
    /** ROM: QuakeEvent3Cont release when player X < $3200. */
    private static final int EVENT3_CONT_RELEASE_X = 0x3200;

    /** ROM: loc_51656 / loc_516A2 — level-size defaults restored on release. */
    private static final int DEFAULT_CAMERA_MAX_Y = 0x1000;
    /** ROM: QuakeEvent2Cont resets Camera_max_X to $6000 when player escapes upward. */
    private static final int DEFAULT_CAMERA_MAX_X = 0x6000;
    /** ROM: loc_51656 resets Camera_min_X to $6000 high (open left bound). */
    private static final int DEFAULT_CAMERA_MIN_X = 0x0000;
    /** Match the standard player right-boundary margin against the live viewport while quake locks are active. */
    private static final int SCREEN_WIDTH = 320;
    private static final int PLAYER_RIGHT_SCREEN_MARGIN = 24;

    // ========================================================================
    // Act 2 chunk-event state machine (MGZ2_ChunkEvent)
    // ========================================================================

    private static final int CHUNK_EVENT_CHECK = 0;
    private static final int CHUNK_EVENT_1 = 4;
    private static final int CHUNK_EVENT_2 = 8;
    private static final int CHUNK_EVENT_3 = 12;
    private static final int CHUNK_EVENT_RESET = 16;
    private static final int CHUNK_EVENT_DONE = 20;
    private static final int CHUNK_EVENT_FINAL_REPLACE_INDEX = 0x5C;
    private static final int CHUNK_EVENT_DELAY_RESET = 6;
    private static final int MGZ_QUAKE_BLOCK_LEFT_INDEX = 0xB1;  // $FF5880 -> Chunk_table[$B1]
    private static final int MGZ_QUAKE_BLOCK_RIGHT_INDEX = 0xEA; // $FF7500 -> Chunk_table[$EA]
    private static final int MGZ_QUAKE_CHUNK_ROM_ADDR = 0x3CBBB4;
    private static final Path MGZ_QUAKE_CHUNK_FALLBACK =
            Path.of("docs/skdisasm/Levels/MGZ/Misc/Act 2 Quake Chunks.bin");

    /**
     * ROM: MGZ2_ChunkEventArray (Lockon S3/Screen Events.asm:1031-1033).
     * Each row: {minX, maxX, minY, maxY, redrawX, redrawY}.
     */
    private static final int[][] CHUNK_EVENT_ARRAY = {
            {0x0F68, 0x0F78, 0x0500, 0x0580, 0x0F00, 0x0500},
            {0x3680, 0x3700, 0x02F0, 0x0380, 0x3700, 0x0280},
            {0x3000, 0x3080, 0x0770, 0x0800, 0x3080, 0x0700},
    };

    /** ROM: MGZ2_ChunkReplaceArray (Lockon S3/Screen Events.asm:1059-1082). */
    private static final int[] CHUNK_REPLACE_ARRAY = {
            0x0100, 0x0500, 0x0180, 0x0580, 0x0200, 0x0600, 0x0280, 0x0680,
            0x0300, 0x0700, 0x0380, 0x0780, 0x0000, 0x0800, 0x0000, 0x0880,
            0x0000, 0x0900, 0x0000, 0x0980, 0x0000, 0x0A00, 0x0000, 0x0A80,
            0x0000, 0x0B00, 0x0000, 0x0B80, 0x0000, 0x0C00, 0x0000, 0x0C80,
            0x0000, 0x0D00, 0x0000, 0x0D80, 0x0000, 0x0E00, 0x0000, 0x0E80,
            0x0000, 0x0F00, 0x0000, 0x0F80, 0x0000, 0x1000, 0x0080, 0x0480,
    };

    private static volatile byte[] cachedMgzQuakeChunkData;

    // ========================================================================
    // Act 2 collapse / screen-event state
    // ========================================================================

    private static final int SCREEN_EVENT_NORMAL = 0;
    private static final int SCREEN_EVENT_COLLAPSE = 4;
    private static final int SCREEN_EVENT_MOVE_BG = 8;
    private static final int RUMBLE_SFX_INTERVAL_MASK = 0x0F;
    private static final int COLLAPSE_REGION_X = 121; // $3C80 / $80
    private static final int COLLAPSE_OPENING_Y = 14; // $700 / $80
    private static final int COLLAPSE_FINAL_Y = 11;   // $580 / $80
    private static final int COLLAPSE_REGION_WIDTH = 3;
    private static final int COLLAPSE_REGION_HEIGHT = 3;
    private static final int COLLAPSE_COLUMN_COUNT = 10;
    private static final int COLLAPSE_MAX_SCROLL = 0x2E0;
    private static final int COLLAPSE_SCROLL_ACCEL = 0x500;
    private static final int[] COLLAPSE_SCROLL_DELAYS = {0x0A, 0x10, 0x02, 0x08, 0x0E, 0x06, 0x00, 0x0C, 0x12, 0x04};

    // ========================================================================
    // Act 2 BG-rise state machine (MGZ2_BGEventTrigger + Obj_MGZ2BGMoveSonic)
    // ROM: sonic3k.asm:107117-107323. This is the "outrun the terrain as it
    // scrolls up" sequence. HCZ2 parallel: uses Background_collision_flag +
    // dual-path FindFloor in GroundSensor; BG plane Y offset drives the visual.
    // ========================================================================

    /** ROM: Events_bg+$00 == 0 — trigger check, BG collision off. */
    private static final int BG_RISE_NORMAL = 0;
    /** ROM: Events_bg+$00 == 8 — BG plane rising, BG collision on, player lifted in lockstep. */
    private static final int BG_RISE_SONIC = 8;
    /** ROM: Events_bg+$00 == 0xC — after BG rise completes, collision off, can re-enter. */
    private static final int BG_RISE_AFTER_MOVE = 0xC;
    /** ROM: MGZ2_BackgroundInit late-load path: Y in [$500,$800) and X >= $3900 enters state C. */
    private static final int BG_RISE_LATE_LOAD_AFTER_MOVE_Y_MIN = 0x0500;
    private static final int BG_RISE_LATE_LOAD_AFTER_MOVE_Y_MAX = 0x0800;
    private static final int BG_RISE_LATE_LOAD_AFTER_MOVE_X_MIN = 0x3900;
    /** ROM: MGZ2_BackgroundInit preloads the fully-raised offset once X >= $3800 in state 8. */
    private static final int BG_RISE_LATE_LOAD_FINISHED_X_MIN = 0x3800;

    /** ROM: loc_51A8A — BG_RISE_NORMAL trigger box for Sonic path (Y in [$800,$900), X >= $34C0). */
    private static final int BG_RISE_TRIGGER_Y_MIN = 0x0800;
    private static final int BG_RISE_TRIGGER_Y_MAX = 0x0900;
    private static final int BG_RISE_TRIGGER_X_MIN = 0x34C0;
    /** ROM: Obj_MGZ2BGMoveSonic — d1=$A80, d2=$36D0 thresholds to actually begin rising. */
    private static final int BG_RISE_MOTION_X_MIN = 0x36D0;
    private static final int BG_RISE_MOTION_Y_MIN = 0x0A80;
    /** ROM: loc_51B6C — accel latch kicks in at X >= $3D50. */
    private static final int BG_RISE_ACCEL_X_MIN = 0x3D50;
    /** ROM: Obj_MGZ2BGMoveSonic — d3=$1D0 target offset. */
    private static final int BG_RISE_TARGET_SONIC = 0x01D0;
    /** ROM: Obj_MGZ2BGMoveSonic — d4=$6000 subpixel velocity per frame (16:16 fixed-point). */
    private static final int BG_RISE_SUBPIXEL_VELOCITY = 0x6000;
    /** ROM: state 8 exit to state 0 — BG_RISE_SONIC Y still in [$800,$900) but X dropped below $34C0. */
    private static final int BG_RISE_EXIT_BACKWARD_X_MAX = 0x34C0;
    /** ROM: loc_51A04 — BG_RISE_SONIC Y below $800 AND X >= $3900: transition to AFTER_MOVE. */
    private static final int BG_RISE_EXIT_FORWARD_X_MIN = 0x3900;
    /** ROM: state C jump-table — AFTER_MOVE Y >= $800 AND X >= $3A40: return to SONIC_RISE. */
    private static final int BG_RISE_REENTRY_Y_MIN = 0x0800;
    private static final int BG_RISE_REENTRY_X_MIN = 0x3A40;
    /** ROM: loc_51B1C — target reached: Screen_shake_flag = $E (timed countdown). */
    private static final int BG_RISE_FINAL_SHAKE_FRAMES = 0x0E;

    private int bgRoutine;

    /** ROM: Events_fg_5 — set by Obj_LevelResultsCreate to trigger BG act transition. */
    private boolean eventsFg5;

    /** Prevents requesting the transition more than once. */
    private boolean transitionRequested;

    /** ROM: Events_bg+$10 — MGZ2 quake event state machine counter. */
    private int quakeEventRoutine;

    /** ROM: Events_bg+$04 / +$06 / +$08 / +$0A / +$0C. */
    private int chunkEventRoutine;
    private int chunkReplaceIndex;
    private int chunkEventDelay;
    private int chunkRedrawX;
    private int chunkRedrawY;
    private int screenEventRoutine;
    private boolean collapseRequested;
    private boolean collapseInitialized;
    private boolean collapseFinished;
    private int collapseMutationCount;
    private int collapseFrameCounter;
    private final int[] collapseScrollVelocity = new int[COLLAPSE_COLUMN_COUNT];
    private final int[] collapseScrollPosition = new int[COLLAPSE_COLUMN_COUNT];

    /** ROM: Events_bg+$00 — MGZ2 BG-rise state (0 / 8 / 0xC). */
    private int bgRiseRoutine;
    /** ROM: Events_bg+$02 — current BG Y offset, clamped to BG_RISE_TARGET_SONIC. */
    private int bgRiseOffset;
    /** ROM: Obj_MGZ2BGMoveSonic $34(a0) — subpixel accumulator (16:16 fixed-point). */
    private int bgRiseSubpixelAccum;
    /** ROM: Obj_MGZ2BGMoveSonic control-flow latch — motion only starts once player passes both thresholds. */
    private boolean bgRiseMotionStarted;
    /** ROM: Obj_MGZ2BGMoveSonic $39(a0) — accel latch; once true, +1 pixel/frame. */
    private boolean bgRiseAccelLatched;
    /** ROM: Screen_shake_flag timed countdown ($E frames) armed when target reached. */
    private int bgRiseFinalShakeTimer;
    /** One-shot MGZ2_BackgroundInit parity path for late checkpoint/death loads. */
    private boolean bgRiseLoadStateInitialised;

    /** ROM: Events_bg+$12, +$13, +$14 — one-shot flags per appearance. */
    private boolean appearance1Complete;
    private boolean appearance2Complete;
    private boolean appearance3Complete;

    /**
     * ROM: Camera_stored_max_X_pos / Camera_stored_min_X_pos — natural level
     * bounds saved before a quake event clamps the camera. Restored on release
     * via the same target-driven unwind the camera uses elsewhere, matching
     * the ROM's Obj_IncLevEndXGradual / Obj_DecLevStartXGradual behavior more
     * closely than a hard unlock.
     */
    private short savedCameraMinX;
    private short savedCameraMaxX;
    private boolean savedCameraBoundsValid;

    /**
     * Active drilling-Robotnik instance for the current mini-event. Cleared
     * when the Robotnik object destroys itself at the end of its flee.
     */
    private MgzDrillingRobotnikInstance activeRobotnik;
    /** Set once activeRobotnik's destruction has been observed and bounds restored. */
    private boolean postFleeUnlockDone;
    private static final int CAMERA_BOUND_RESTORE_STEP = 0x40;
    private static final int GRADUAL_UNLOCK_NONE = 0;
    private static final int GRADUAL_UNLOCK_MAX_X = 1;
    private static final int GRADUAL_UNLOCK_MIN_X = -1;
    private int gradualUnlockDirection;

    public Sonic3kMGZEvents() {
        super();
    }

    @Override
    public void init(int act) {
        super.init(act);
        bgRoutine = BG_STAGE_NORMAL;
        eventsFg5 = false;
        transitionRequested = false;
        quakeEventRoutine = QUAKE_CHECK;
        appearance1Complete = false;
        appearance2Complete = false;
        appearance3Complete = false;
        screenShakeActive = false;
        chunkEventRoutine = CHUNK_EVENT_CHECK;
        chunkReplaceIndex = 0;
        chunkEventDelay = 0;
        chunkRedrawX = 0;
        chunkRedrawY = 0;
        screenEventRoutine = SCREEN_EVENT_NORMAL;
        collapseRequested = false;
        collapseInitialized = false;
        collapseFinished = false;
        collapseMutationCount = 0;
        collapseFrameCounter = 0;
        for (int i = 0; i < COLLAPSE_COLUMN_COUNT; i++) {
            collapseScrollVelocity[i] = 0;
            collapseScrollPosition[i] = 0;
        }
        bgRiseRoutine = BG_RISE_NORMAL;
        bgRiseOffset = 0;
        bgRiseSubpixelAccum = 0;
        bgRiseMotionStarted = false;
        bgRiseAccelLatched = false;
        bgRiseFinalShakeTimer = 0;
        bgRiseLoadStateInitialised = false;
        savedCameraMinX = 0;
        savedCameraMaxX = 0;
        savedCameraBoundsValid = false;
        activeRobotnik = null;
        postFleeUnlockDone = false;
        gradualUnlockDirection = GRADUAL_UNLOCK_NONE;
    }

    public void setEventsFg5(boolean value) {
        this.eventsFg5 = value;
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Bg();
        } else if (act == 1) {
            updateAct2QuakeEvent();
            updateAct2ChunkEvent();
            updateAct2Collapse();
            updateAct2Rumble(frameCounter);
            applyScreenShake(frameCounter);
        }
    }

    // ========================================================================
    // Act 2 quake-event state machine
    // ========================================================================

    /**
     * ROM: MGZ2_QuakeEvent (sonic3k.asm:106579-106786). Reads player position
     * and dispatches on {@link #quakeEventRoutine}.
     */
    private void updateAct2QuakeEvent() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null) {
            return;
        }
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        maybeStartUnlockAfterRobotnikDestroyed();
        updateGradualCameraUnlock();

        switch (quakeEventRoutine) {
            case QUAKE_CHECK -> quakeEventCheck(playerX, playerY);
            case QUAKE_EVENT_1 -> quakeEvent1(playerX);
            case QUAKE_EVENT_2 -> quakeEvent2(playerX);
            case QUAKE_EVENT_3 -> quakeEvent3(playerX);
            case QUAKE_EVENT_1_CONT -> quakeEvent1Cont(playerX);
            case QUAKE_EVENT_2_CONT -> quakeEvent2Cont(playerX, playerY);
            case QUAKE_EVENT_3_CONT -> quakeEvent3Cont(playerX);
            default -> {
            }
        }

        clampPlayerToCurrentViewportRightEdge(player);
    }

    /**
     * ROM: MGZ2_QuakeEventCheck (sonic3k.asm:106625-106663). Scans
     * {@link #QUAKE_EVENT_ARRAY} for a matching player position; on the first
     * incomplete entry that contains the player, locks camera bounds and
     * transitions to {@link #QUAKE_EVENT_1}, {@link #QUAKE_EVENT_2}, or
     * {@link #QUAKE_EVENT_3}.
     */
    private void quakeEventCheck(int playerX, int playerY) {
        Camera camera = camera();
        for (int i = 0; i < QUAKE_EVENT_ARRAY.length; i++) {
            if (isAppearanceComplete(i)) {
                continue;
            }
            int[] entry = QUAKE_EVENT_ARRAY[i];
            int minX = entry[0];
            int maxX = entry[1];
            int minY = entry[2];
            int maxY = entry[3];
            if (playerX < minX || playerX >= maxX || playerY < minY || playerY >= maxY) {
                continue;
            }
            int cameraMaxY = entry[4];
            int cameraLockX = entry[5];
            // Snapshot natural level bounds before mutating — the ROM stores these in
            // Camera_stored_min/max_X_pos and restores them gradually on release.
            savedCameraMinX = camera.getMinX();
            savedCameraMaxX = camera.getMaxX();
            savedCameraBoundsValid = true;
            quakeEventRoutine = QUAKE_EVENT_1 + (i * 4);
            camera.setMaxY((short) cameraMaxY);
            camera.setMaxYTarget((short) cameraMaxY);
            if (i == 0) {
                camera.setMaxX((short) cameraLockX);
            } else {
                camera.setMinX((short) cameraLockX);
            }
            return;
        }
    }

    private boolean isAppearanceComplete(int index) {
        return switch (index) {
            case 0 -> appearance1Complete;
            case 1 -> appearance2Complete;
            case 2 -> appearance3Complete;
            default -> false;
        };
    }

    private void clampPlayerToCurrentViewportRightEdge(AbstractPlayableSprite player) {
        if (!isQuakeSequenceActive() || player.isObjectControlled()) {
            return;
        }
        int rightBoundary = (camera().getX() & 0xFFFF) + SCREEN_WIDTH - PLAYER_RIGHT_SCREEN_MARGIN;
        if ((player.getCentreX() & 0xFFFF) < rightBoundary) {
            return;
        }
        player.setCentreX((short) rightBoundary);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
    }

    private boolean isQuakeSequenceActive() {
        return quakeEventRoutine == QUAKE_EVENT_1
                || quakeEventRoutine == QUAKE_EVENT_2
                || quakeEventRoutine == QUAKE_EVENT_3
                || quakeEventRoutine == QUAKE_EVENT_1_CONT
                || quakeEventRoutine == QUAKE_EVENT_2_CONT
                || quakeEventRoutine == QUAKE_EVENT_3_CONT;
    }

    /**
     * ROM: MGZ2_QuakeEvent1 (sonic3k.asm:106666-106684). Waits for camera X
     * to reach {@link Camera#getMaxX()} (the lock). On arrival: freezes the
     * screen, spawns Robotnik, triggers shake, advances to QuakeEvent1Cont.
     * Retreat (player X &lt; {@link #EVENT1_PLAYER_X_THRESHOLD}) reverts to
     * {@link #QUAKE_CHECK} via {@link #resetBoundsAndState()}.
     */
    private void quakeEvent1(int playerX) {
        if (playerX < EVENT1_PLAYER_X_THRESHOLD) {
            resetBoundsAndState();
            return;
        }
        Camera camera = camera();
        int camMaxX = camera.getMaxX() & 0xFFFF;
        if ((camera.getX() & 0xFFFF) < camMaxX) {
            return; // camera hasn't reached the lock yet
        }
        camera.setMinX((short) camMaxX);
        appearance1Complete = true;
        quakeEventRoutine = QUAKE_EVENT_1_CONT;
        spawnDrillingRobotnik(0);
    }

    /**
     * ROM: MGZ2_QuakeEvent2 (sonic3k.asm:106687-106720). Waits for camera X
     * to reach {@link Camera#getMinX()} (the forced-left lock). Retreat
     * (player X &gt;= {@link #EVENT2_PLAYER_X_RETREAT}) reverts.
     */
    private void quakeEvent2(int playerX) {
        if (playerX >= EVENT2_PLAYER_X_RETREAT) {
            camera().setMinY((short) 0x01DF);
            resetBoundsAndState();
            return;
        }
        Camera camera = camera();
        int camMaxY = camera.getMaxY() & 0xFFFF;
        if ((camera.getY() & 0xFFFF) == camMaxY && (camera.getMinY() & 0xFFFF) != camMaxY) {
            camera.setMinY((short) camMaxY);
        }
        int camMinX = camera.getMinX() & 0xFFFF;
        if ((camera.getX() & 0xFFFF) > camMinX) {
            return;
        }
        camera.setMaxX((short) camMinX);
        appearance2Complete = true;
        quakeEventRoutine = QUAKE_EVENT_2_CONT;
        spawnDrillingRobotnik(1);
    }

    /**
     * ROM: MGZ2_QuakeEvent3 (sonic3k.asm:106723-106742). Retreat threshold is
     * {@link #EVENT3_PLAYER_X_RETREAT}.
     */
    private void quakeEvent3(int playerX) {
        if (playerX >= EVENT3_PLAYER_X_RETREAT) {
            resetBoundsAndState();
            return;
        }
        Camera camera = camera();
        int camMinX = camera.getMinX() & 0xFFFF;
        if ((camera.getX() & 0xFFFF) > camMinX) {
            return;
        }
        camera.setMaxX((short) camMinX);
        appearance3Complete = true;
        quakeEventRoutine = QUAKE_EVENT_3_CONT;
        spawnDrillingRobotnik(2);
    }

    /**
     * ROM: MGZ2_QuakeEvent1Cont (sonic3k.asm:106755-106759). Once the player
     * passes {@link #EVENT1_CONT_RELEASE_X}, restore default camera_max_Y and
     * return to {@link #QUAKE_CHECK} so the remaining quakes can still trigger.
     */
    private void quakeEvent1Cont(int playerX) {
        if (playerX < EVENT1_CONT_RELEASE_X) {
            return;
        }
        restoreBoundsAfterFlee();
        quakeEventRoutine = QUAKE_CHECK;
    }

    /**
     * ROM: MGZ2_QuakeEvent2Cont (sonic3k.asm:106761-106769). Release requires
     * player Y &lt; $100 AND X &gt;= $2F80; additionally resets
     * Camera_max_X to $6000.
     */
    private void quakeEvent2Cont(int playerX, int playerY) {
        if (playerY >= EVENT2_CONT_RELEASE_Y_MAX || playerX < EVENT2_CONT_RELEASE_X_MIN) {
            return;
        }
        camera().setMaxX((short) DEFAULT_CAMERA_MAX_X);
        restoreBoundsAfterFlee();
        quakeEventRoutine = QUAKE_CHECK;
    }

    /**
     * ROM: MGZ2_QuakeEvent3Cont (sonic3k.asm:106775-106778). Release when the
     * player moves back past {@link #EVENT3_CONT_RELEASE_X}. In the ROM this
     * is where the end-of-act boss fight would begin; the engine fires
     * {@link #onMgz2BossArenaReached()} for that stubbed step.
     */
    private void quakeEvent3Cont(int playerX) {
        if (playerX >= EVENT3_CONT_RELEASE_X) {
            return;
        }
        restoreBoundsAfterFlee();
        quakeEventRoutine = QUAKE_CHECK;
        onMgz2BossArenaReached();
    }

    /**
     * ROM: loc_51656 — retreat cancels the pending mini-event and restores
     * level-wide boundary defaults before returning to state 0.
     */
    private void resetBoundsAndState() {
        Camera camera = camera();
        camera.setMaxY((short) DEFAULT_CAMERA_MAX_Y);
        camera.setMaxYTarget((short) DEFAULT_CAMERA_MAX_Y);
        camera.setMinX((short) DEFAULT_CAMERA_MIN_X);
        camera.setMaxX((short) DEFAULT_CAMERA_MAX_X);
        savedCameraBoundsValid = false;
        quakeEventRoutine = QUAKE_CHECK;
        screenShakeActive = false;
        setGenericBossFlag(false);
        activeRobotnik = null;
        postFleeUnlockDone = false;
        gradualUnlockDirection = GRADUAL_UNLOCK_NONE;
    }

    /**
     * ROM: loc_516A2 — restore Camera_max_Y to $1000 after a flee completes.
     * Called from each QuakeEventNCont state once its release condition fires.
     * Also restores the camera X bounds (the ROM does this gradually via
     * Obj_IncLevEndXGradual / Obj_DecLevStartXGradual spawned by the drilling
     * Robotnik's flee routine).
     */
    private void restoreBoundsAfterFlee() {
        Camera camera = camera();
        camera.setMaxY((short) DEFAULT_CAMERA_MAX_Y);
        camera.setMaxYTarget((short) DEFAULT_CAMERA_MAX_Y);
        savedCameraBoundsValid = false;
        setGenericBossFlag(false);
        activeRobotnik = null;
        postFleeUnlockDone = false;
        // Screen shake is NOT cleared here — the quake-continuous shake persists
        // until MGZ2_ChunkEvent owns the shutdown later in the sequence (ROM:
        // Events_fg_0 stays set across the mini-event release).
    }

    /**
     * Spawns the Drilling Robotnik for the given appearance index.
     * ROM: QuakeEvent{1,2,3} {@code move.l #Obj_MGZ2DrillingRobotnik,(a1)}.
     * Also raises {@code Screen_shake_flag} / {@code Events_fg_0} so the
     * scroll handler applies the continuous-shake offset table each frame.
     */
    private void spawnDrillingRobotnik(int appearanceIndex) {
        int spawnX = ROBOTNIK_SPAWN_X[appearanceIndex];
        int spawnY = ROBOTNIK_SPAWN_Y[appearanceIndex];
        boolean flipX = (appearanceIndex != 0); // entries 2 and 3 face left
        ObjectSpawn spawn = new ObjectSpawn(spawnX, spawnY, 0, 0, 0, false, 0);
        activeRobotnik = spawnObject(() -> new MgzDrillingRobotnikInstance(spawn, flipX));
        postFleeUnlockDone = false;
        screenShakeActive = true;
        setGenericBossFlag(true);
        LOG.info("MGZ2 drilling Robotnik appearance " + (appearanceIndex + 1)
                + " spawned at (0x" + Integer.toHexString(spawnX)
                + ", 0x" + Integer.toHexString(spawnY) + ")");
    }

    private void maybeStartUnlockAfterRobotnikDestroyed() {
        if (postFleeUnlockDone || activeRobotnik == null || !activeRobotnik.isDestroyed()) {
            return;
        }
        gradualUnlockDirection = switch (quakeEventRoutine) {
            case QUAKE_EVENT_1_CONT -> GRADUAL_UNLOCK_MAX_X;
            case QUAKE_EVENT_2_CONT, QUAKE_EVENT_3_CONT -> GRADUAL_UNLOCK_MIN_X;
            default -> GRADUAL_UNLOCK_NONE;
        };
        setGenericBossFlag(false);
        activeRobotnik = null;
        postFleeUnlockDone = true;
    }

    private void updateGradualCameraUnlock() {
        if (gradualUnlockDirection == GRADUAL_UNLOCK_NONE) {
            return;
        }
        Camera camera = camera();
        if (gradualUnlockDirection == GRADUAL_UNLOCK_MAX_X) {
            int current = camera.getMaxX() & 0xFFFF;
            if (current >= DEFAULT_CAMERA_MAX_X) {
                camera.setMaxX((short) DEFAULT_CAMERA_MAX_X);
                gradualUnlockDirection = GRADUAL_UNLOCK_NONE;
                return;
            }
            camera.setMaxX((short) Math.min(DEFAULT_CAMERA_MAX_X, current + CAMERA_BOUND_RESTORE_STEP));
            return;
        }
        int current = camera.getMinX() & 0xFFFF;
        if (current <= DEFAULT_CAMERA_MIN_X) {
            camera.setMinX((short) DEFAULT_CAMERA_MIN_X);
            gradualUnlockDirection = GRADUAL_UNLOCK_NONE;
            return;
        }
        camera.setMinX((short) Math.max(DEFAULT_CAMERA_MIN_X, current - CAMERA_BOUND_RESTORE_STEP));
    }

    private void setGenericBossFlag(boolean active) {
        try {
            if (module().getLevelEventProvider() instanceof AbstractLevelEventManager manager) {
                manager.setBossActive(active);
            }
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kMGZEvents.setGenericBossFlag: " + e.getMessage());
        }
    }

    /** ROM: ScreenShakeArray2 — 64-entry continuous-shake table. */
    private static final byte[] SCREEN_SHAKE_CONTINUOUS = {
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3,
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3
    };

    /** ROM: Events_fg_0 / Screen_shake_flag active while Robotnik is on-screen. */
    private boolean screenShakeActive;

    /**
     * ROM: MGZ2_ScreenEvent calls Do_ShakeSound while continuous shake is
     * active, and MGZ2_LevelCollapse plays BigRumble on the same 16-frame
     * cadence while the collapse is running.
     */
    private void updateAct2Rumble(int frameCounter) {
        var audioManager = audio();
        if (audioManager == null) {
            return;
        }
        if (((frameCounter - 1) & RUMBLE_SFX_INTERVAL_MASK) != 0) {
            return;
        }
        if (screenEventRoutine == SCREEN_EVENT_COLLAPSE && !collapseFinished && screenShakeActive) {
            audioManager.playSfx(Sonic3kSfx.BIG_RUMBLE.id);
            return;
        }
        if (screenEventRoutine == SCREEN_EVENT_NORMAL && screenShakeActive) {
            audioManager.playSfx(Sonic3kSfx.RUMBLE_2.id);
        }
    }

    private void applyScreenShake(int frameCounter) {
        SwScrlMgz scrollHandler = resolveMgzScrollHandler();
        if (scrollHandler == null) {
            return;
        }
        int offset = isVisualShakeActive()
                ? SCREEN_SHAKE_CONTINUOUS[frameCounter & 0x3F]
                : 0;
        scrollHandler.setScreenShakeOffset(offset);
    }

    private boolean isVisualShakeActive() {
        return screenShakeActive || bgRiseFinalShakeTimer > 0;
    }

    private SwScrlMgz resolveMgzScrollHandler() {
        try {
            if (!hasRuntime()) {
                return null;
            }
            var parallax = parallaxOrNull();
            if (parallax == null) {
                return null;
            }
            ZoneScrollHandler handler = parallax.getHandler(Sonic3kZoneIds.ZONE_MGZ);
            return (handler instanceof SwScrlMgz mgz) ? mgz : null;
        } catch (Exception e) {
            return null;
        }
    }

    public int getQuakeEventRoutine() {
        return quakeEventRoutine;
    }

    public boolean isAppearance1Complete() {
        return appearance1Complete;
    }

    public boolean isAppearance2Complete() {
        return appearance2Complete;
    }

    public boolean isAppearance3Complete() {
        return appearance3Complete;
    }

    /**
     * ROM: Events_fg_0 / Screen_shake_flag — true while Robotnik is on-screen
     * (continuous) or while the BG-rise final timed shake ($E frames) is counting down.
     */
    public boolean isScreenShakeActive() {
        return isVisualShakeActive();
    }

    public int getBgRiseRoutine() {
        return bgRiseRoutine;
    }

    public int getBgRiseOffset() {
        return bgRiseOffset;
    }

    /**
     * ROM: MGZ2_BGEventTrigger (sonic3k.asm:107117-107222) + Obj_MGZ2BGMoveSonic
     * (sonic3k.asm:107241-107323). Runs before player physics so that
     * {@code Background_collision_flag} and the player lift land before
     * {@code FindFloor}. The ROM runs this in the object phase (before
     * ExecuteObjects resolves physics), same as {@code HCZWaterTunnelHandler}.
     */
    public void updatePrePhysics(int act) {
        if (act != 1) {
            return;
        }
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null) {
            return;
        }
        primeBgRiseFromLoadPosition(player.getCentreX(), player.getCentreY());
        if (bgRiseFinalShakeTimer > 0) {
            bgRiseFinalShakeTimer--;
        }
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        switch (bgRiseRoutine) {
            case BG_RISE_NORMAL -> bgRiseNormal(playerX, playerY);
            case BG_RISE_SONIC -> bgRiseSonic(player, playerX, playerY);
            case BG_RISE_AFTER_MOVE -> bgRiseAfterMove(playerX, playerY);
            default -> {
            }
        }
        SwScrlMgz scrollHandler = resolveMgzScrollHandler();
        if (scrollHandler == null) {
            if (bgRiseRoutine == BG_RISE_SONIC) {
                LOG.warning("MGZ BG-rise: state SONIC armed but scroll handler is null — "
                        + "BG formula will NOT shift in-game");
            }
        } else {
            scrollHandler.setBgRiseState(bgRiseRoutine, bgRiseOffset);
        }
    }

    /**
     * ROM: MGZ2_BackgroundInit reconstructs the BG-rise route from the loaded
     * player position. That means late starpost/death reloads skip straight to
     * the finished raised-terrain state instead of replaying the whole lift.
     */
    private void primeBgRiseFromLoadPosition(int playerX, int playerY) {
        if (bgRiseLoadStateInitialised) {
            return;
        }
        bgRiseLoadStateInitialised = true;

        if (playerY >= BG_RISE_LATE_LOAD_AFTER_MOVE_Y_MIN
                && playerY < BG_RISE_LATE_LOAD_AFTER_MOVE_Y_MAX
                && playerX >= BG_RISE_LATE_LOAD_AFTER_MOVE_X_MIN) {
            bgRiseRoutine = BG_RISE_AFTER_MOVE;
            bgRiseOffset = BG_RISE_TARGET_SONIC;
            bgRiseSubpixelAccum = 0;
            bgRiseMotionStarted = false;
            bgRiseAccelLatched = false;
            gameState().setBackgroundCollisionFlag(false);
            LOG.info(String.format(
                    "MGZ BG-rise: late-load init -> AFTER_MOVE at player (0x%04X, 0x%04X)",
                    playerX, playerY));
            return;
        }

        if (playerY >= BG_RISE_TRIGGER_Y_MIN && playerX >= BG_RISE_TRIGGER_X_MIN) {
            bgRiseRoutine = BG_RISE_SONIC;
            bgRiseOffset = playerX >= BG_RISE_LATE_LOAD_FINISHED_X_MIN ? BG_RISE_TARGET_SONIC : 0;
            bgRiseSubpixelAccum = 0;
            bgRiseMotionStarted = false;
            bgRiseAccelLatched = false;
            gameState().setBackgroundCollisionFlag(true);
            LOG.info(String.format(
                    "MGZ BG-rise: late-load init -> SONIC offset=0x%03X at player (0x%04X, 0x%04X)",
                    bgRiseOffset, playerX, playerY));
            return;
        }

        gameState().setBackgroundCollisionFlag(false);
    }

    /**
     * ROM: loc_51A6A — BG collision off; Sonic trigger box is Y in [$800,$900)
     * AND X >= $34C0. Knuckles variant (Y in [$80,$180)) is not ported; the
     * engine currently runs the Sonic/Tails route here.
     */
    private void bgRiseNormal(int playerX, int playerY) {
        gameState().setBackgroundCollisionFlag(false);
        if (playerY >= BG_RISE_TRIGGER_Y_MIN && playerY < BG_RISE_TRIGGER_Y_MAX
                && playerX >= BG_RISE_TRIGGER_X_MIN) {
            bgRiseRoutine = BG_RISE_SONIC;
            bgRiseOffset = 0;
            bgRiseSubpixelAccum = 0;
            bgRiseMotionStarted = false;
            bgRiseAccelLatched = false;
            gameState().setBackgroundCollisionFlag(true);
            LOG.info(String.format(
                    "MGZ BG-rise: state 0 -> SONIC at player (0x%04X, 0x%04X)",
                    playerX, playerY));
        }
    }

    /**
     * ROM: loc_51A04 ({@code MGZ2_BGEventTrigger} state 8) + {@code Obj_MGZ2BGMoveSonic}
     * per-frame body. The trigger unconditionally sets
     * {@code Background_collision_flag}=ON, then decides state transitions.
     * The object runs its motion body regardless of the trigger's return:
     * once motion has started (player past X>$36D0 AND Y>$A80) it advances
     * every frame until the offset reaches {@code $1D0}. Accel latch fires
     * at X>=$3D50 (ROM: loc_51B6C) and flips from subpixel accumulator to
     * integer +1 pixel/frame.
     */
    private void bgRiseSonic(AbstractPlayableSprite player, int playerX, int playerY) {
        gameState().setBackgroundCollisionFlag(true);
        // MGZ2_BGEventTrigger state-8 transition logic:
        //   Y < $800 AND X >= $3900 → state C
        //   Y in [$800,$900) AND X < $34C0 → state 0
        //   otherwise: stay in state 8 (motion keeps running via the object)
        if (playerY < BG_RISE_TRIGGER_Y_MIN) {
            if (playerX >= BG_RISE_EXIT_FORWARD_X_MIN) {
                bgRiseRoutine = BG_RISE_AFTER_MOVE;
                gameState().setBackgroundCollisionFlag(false);
                return;
            }
        } else if (playerY < BG_RISE_TRIGGER_Y_MAX
                && playerX < BG_RISE_EXIT_BACKWARD_X_MAX) {
            bgRiseRoutine = BG_RISE_NORMAL;
            gameState().setBackgroundCollisionFlag(false);
            return;
        }
        // Obj_MGZ2BGMoveSonic body runs while state remains 8, decoupled from
        // the trigger's transition check. Motion start requires crossing both
        // BG_RISE_MOTION_X_MIN and BG_RISE_MOTION_Y_MIN (ROM: loc_51AF2).
        if (!bgRiseMotionStarted) {
            if (playerX <= BG_RISE_MOTION_X_MIN || playerY <= BG_RISE_MOTION_Y_MIN) {
                return;
            }
            bgRiseMotionStarted = true;
        }
        advanceBgRise(player, playerX);
    }

    /**
     * ROM: MGZ2_BGEventTrigger_Index state C — clear
     * {@code Background_collision_flag}; re-enter state 8 when the player
     * drops back into Y >= $800 AND X >= $3A40.
     */
    private void bgRiseAfterMove(int playerX, int playerY) {
        gameState().setBackgroundCollisionFlag(false);
        if (playerY >= BG_RISE_REENTRY_Y_MIN && playerX >= BG_RISE_REENTRY_X_MIN) {
            bgRiseRoutine = BG_RISE_SONIC;
            gameState().setBackgroundCollisionFlag(true);
        }
    }

    /**
     * ROM: Obj_MGZ2BGMoveSonic loc_51B1C-loc_51B84.
     *
     * <p>Per-frame: if accel latch active, newOffset = current + 1. Otherwise
     * add $6000 to the 16:16 subpixel accumulator and take its upper word.
     * If the new offset crosses the target, clamp at $1D0, play CRASH, arm the
     * $E-frame timed shake. Lift the focused player by the exact delta
     * (ROM: {@code sub.w d1,(Player_1+y_pos).w}).
     */
    private void advanceBgRise(AbstractPlayableSprite player, int playerX) {
        if (bgRiseOffset >= BG_RISE_TARGET_SONIC) {
            return;
        }
        if (!bgRiseAccelLatched && playerX >= BG_RISE_ACCEL_X_MIN) {
            bgRiseAccelLatched = true;
        }
        int newOffset;
        if (bgRiseAccelLatched) {
            newOffset = bgRiseOffset + 1;
        } else {
            bgRiseSubpixelAccum += BG_RISE_SUBPIXEL_VELOCITY;
            newOffset = bgRiseSubpixelAccum >>> 16;
        }
        if (newOffset >= BG_RISE_TARGET_SONIC) {
            newOffset = BG_RISE_TARGET_SONIC;
            var audioManager = audio();
            if (audioManager != null) {
                audioManager.playSfx(Sonic3kSfx.CRASH.id);
            }
            bgRiseFinalShakeTimer = BG_RISE_FINAL_SHAKE_FRAMES;
            screenShakeActive = false;
        }
        int delta = newOffset - bgRiseOffset;
        if (delta <= 0) {
            return;
        }
        bgRiseOffset = newOffset;
        player.setCentreY((short) (player.getCentreY() - delta));
    }

    public int getChunkEventRoutine() {
        return chunkEventRoutine;
    }

    void triggerCollapseForTest() {
        collapseRequested = true;
    }

    boolean isCollapseActive() {
        return screenEventRoutine == SCREEN_EVENT_COLLAPSE && !collapseFinished;
    }

    boolean isCollapseFinished() {
        return collapseFinished;
    }

    int getCollapseMutationCount() {
        return collapseMutationCount;
    }

    int getScreenEventRoutine() {
        return screenEventRoutine;
    }

    @Override
    public int getDynamicResizeRoutine() {
        return screenEventRoutine;
    }

    @Override
    public void setDynamicResizeRoutine(int routine) {
        screenEventRoutine = routine;
    }

    private void updateAct2ChunkEvent() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null) {
            return;
        }
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        switch (chunkEventRoutine) {
            case CHUNK_EVENT_CHECK -> {
                chunkEventCheck(playerX, playerY);
                if (chunkEventRoutine != CHUNK_EVENT_CHECK) {
                    dispatchChunkEventRoutine(playerX);
                }
            }
            case CHUNK_EVENT_1, CHUNK_EVENT_2, CHUNK_EVENT_3, CHUNK_EVENT_RESET ->
                    dispatchChunkEventRoutine(playerX);
            default -> {
            }
        }
    }

    private void dispatchChunkEventRoutine(int playerX) {
        switch (chunkEventRoutine) {
            case CHUNK_EVENT_1 -> runChunkEvent1();
            case CHUNK_EVENT_2, CHUNK_EVENT_3 -> runChunkEvent2Or3();
            case CHUNK_EVENT_RESET -> runChunkEventReset(playerX);
            default -> {
            }
        }
    }

    private void chunkEventCheck(int playerX, int playerY) {
        int nextRoutine = CHUNK_EVENT_1;
        for (int[] entry : CHUNK_EVENT_ARRAY) {
            if (playerX >= entry[0] && playerX < entry[1]
                    && playerY >= entry[2] && playerY < entry[3]) {
                if (nextRoutine == CHUNK_EVENT_1 && !screenShakeActive) {
                    return;
                }
                chunkEventRoutine = nextRoutine;
                chunkReplaceIndex = 0;
                chunkEventDelay = 0;
                chunkRedrawX = entry[4];
                chunkRedrawY = entry[5];
                return;
            }
            nextRoutine += 4;
        }
    }

    private void runChunkEvent1() {
        if (chunkReplaceIndex < CHUNK_EVENT_FINAL_REPLACE_INDEX) {
            advanceChunkEventMutation();
            return;
        }
        screenShakeActive = false;
        quakeEventRoutine = QUAKE_CHECK;
        camera().setMinX((short) DEFAULT_CAMERA_MIN_X);
        chunkEventRoutine = CHUNK_EVENT_RESET;
    }

    private void runChunkEvent2Or3() {
        if (chunkReplaceIndex < CHUNK_EVENT_FINAL_REPLACE_INDEX) {
            advanceChunkEventMutation();
            return;
        }
        camera().setMaxX((short) DEFAULT_CAMERA_MAX_X);
        chunkEventRoutine = CHUNK_EVENT_DONE;
    }

    private void runChunkEventReset(int playerX) {
        if (playerX < 0x2A00) {
            return;
        }
        chunkEventRoutine = CHUNK_EVENT_CHECK;
        applyChunkMutationPair(CHUNK_EVENT_FINAL_REPLACE_INDEX);
        chunkReplaceIndex = CHUNK_EVENT_FINAL_REPLACE_INDEX;
    }

    private void advanceChunkEventMutation() {
        chunkEventDelay--;
        if (chunkEventDelay >= 0) {
            return;
        }
        chunkEventDelay = CHUNK_EVENT_DELAY_RESET;
        applyChunkMutationPair(chunkReplaceIndex);
        chunkReplaceIndex += 4;
    }

    private void applyChunkMutationPair(int replaceIndex) {
        int entryIndex = replaceIndex / 2;
        if (entryIndex < 0 || entryIndex + 1 >= CHUNK_REPLACE_ARRAY.length) {
            return;
        }
        int leftOffset = CHUNK_REPLACE_ARRAY[entryIndex];
        int rightOffset = CHUNK_REPLACE_ARRAY[entryIndex + 1];
        int[] leftState = readQuakeBlockState(leftOffset);
        int[] rightState = readQuakeBlockState(rightOffset);
        if (leftState == null || rightState == null) {
            return;
        }
        applyImmediateMgzMutation(context -> mergeEffects(
                context.surface().restoreBlockState(MGZ_QUAKE_BLOCK_LEFT_INDEX, leftState),
                context.surface().restoreBlockState(MGZ_QUAKE_BLOCK_RIGHT_INDEX, rightState)));
    }

    private int[] readQuakeBlockState(int offset) {
        byte[] data = loadMgzQuakeChunkData();
        if (data == null || offset < 0 || offset + 0x80 > data.length) {
            return null;
        }
        int[] state = new int[64];
        for (int i = 0; i < state.length; i++) {
            int byteIndex = offset + i * 2;
            state[i] = ((data[byteIndex] & 0xFF) << 8) | (data[byteIndex + 1] & 0xFF);
        }
        return state;
    }

    private byte[] loadMgzQuakeChunkData() {
        byte[] cached = cachedMgzQuakeChunkData;
        if (cached != null) {
            return cached;
        }
        synchronized (Sonic3kMGZEvents.class) {
            if (cachedMgzQuakeChunkData != null) {
                return cachedMgzQuakeChunkData;
            }
            try {
                byte[] romBytes = rom().readBytes(MGZ_QUAKE_CHUNK_ROM_ADDR, 0x1080);
                cachedMgzQuakeChunkData = romBytes;
                return romBytes;
            } catch (Exception romFailure) {
                try {
                    byte[] fallback = Files.readAllBytes(MGZ_QUAKE_CHUNK_FALLBACK);
                    cachedMgzQuakeChunkData = fallback;
                    return fallback;
                } catch (IOException fileFailure) {
                    LOG.warning("Failed to load MGZ2 quake chunk data from ROM or fallback file: "
                            + fileFailure.getMessage());
                    return null;
                }
            }
        }
    }

    private void applyImmediateMgzMutation(LayoutMutationIntent intent) {
        LevelManager levelManager = levelManager();
        Level level = levelManager != null ? levelManager.getCurrentLevel() : null;
        if (level == null) {
            return;
        }
        LayoutMutationContext context = new LayoutMutationContext(
                LevelMutationSurface.forLevel(level),
                levelManager::applyMutationEffects);
        if (hasRuntime()) {
            zoneLayoutMutationPipeline().applyImmediately(intent, context);
            return;
        }
        levelManager.applyMutationEffects(intent.apply(context));
    }

    private static MutationEffects mergeEffects(MutationEffects... effects) {
        BitSet dirtyPatterns = new BitSet();
        boolean dirtyRegions = false;
        boolean redraw = false;
        boolean redrawAll = false;
        boolean objectResync = false;
        boolean ringResync = false;
        if (effects != null) {
            for (MutationEffects effect : effects) {
                if (effect == null) {
                    continue;
                }
                dirtyPatterns.or(effect.dirtyPatterns());
                dirtyRegions |= effect.dirtyRegionProcessingRequired();
                redraw |= effect.foregroundRedrawRequired();
                redrawAll |= effect.allTilemapsRedrawRequired();
                objectResync |= effect.objectResyncRequired();
                ringResync |= effect.ringResyncRequired();
            }
        }
        return new MutationEffects(dirtyPatterns, dirtyRegions, redraw, redrawAll, objectResync, ringResync);
    }

    private void updateAct2Collapse() {
        if (collapseRequested && screenEventRoutine == SCREEN_EVENT_NORMAL) {
            screenEventRoutine = SCREEN_EVENT_COLLAPSE;
            screenShakeActive = true;
        }
        if (screenEventRoutine != SCREEN_EVENT_COLLAPSE || collapseFinished) {
            return;
        }
        if (!collapseInitialized) {
            clearForegroundRegion(COLLAPSE_REGION_X, COLLAPSE_OPENING_Y,
                    COLLAPSE_REGION_WIDTH, COLLAPSE_REGION_HEIGHT);
            collapseMutationCount++;
            collapseInitialized = true;
            return;
        }

        collapseFrameCounter++;
        boolean allColumnsFinished = true;
        for (int i = 0; i < COLLAPSE_COLUMN_COUNT; i++) {
            if (collapseScrollPosition[i] >= COLLAPSE_MAX_SCROLL) {
                continue;
            }
            allColumnsFinished = false;
            if (collapseFrameCounter >= COLLAPSE_SCROLL_DELAYS[i]) {
                collapseScrollVelocity[i] += COLLAPSE_SCROLL_ACCEL;
                collapseScrollPosition[i] = Math.min(
                        COLLAPSE_MAX_SCROLL,
                        collapseScrollPosition[i] + (collapseScrollVelocity[i] >> 8));
            }
        }
        if (!allColumnsFinished) {
            return;
        }

        clearForegroundRegion(COLLAPSE_REGION_X, COLLAPSE_FINAL_Y,
                COLLAPSE_REGION_WIDTH, COLLAPSE_REGION_HEIGHT);
        collapseMutationCount++;
        collapseFinished = true;
        collapseRequested = false;
        screenShakeActive = false;
        screenEventRoutine = SCREEN_EVENT_MOVE_BG;
    }

    private void clearForegroundRegion(int startX, int startY, int width, int height) {
        applyImmediateMgzMutation(context -> {
            MutationEffects combined = MutationEffects.NONE;
            for (int y = startY; y < startY + height; y++) {
                for (int x = startX; x < startX + width; x++) {
                    combined = mergeEffects(combined, context.surface().setBlockInMap(0, x, y, 0));
                }
            }
            return combined;
        });
    }

    /**
     * Hook fired when the third drilling Robotnik mini-event completes its
     * flee sequence (ROM: QuakeEvent3Cont — where the end-of-act boss fight
     * normally begins). The default implementation logs and does nothing;
     * when the MGZ2 end boss is implemented, override this to spawn the
     * boss and set up its arena.
     */
    protected void onMgz2BossArenaReached() {
        LOG.info("MGZ2 boss arena reached — end boss not yet implemented");
    }

    private void updateAct1Bg() {
        switch (bgRoutine) {
            case BG_STAGE_NORMAL -> {
                if (eventsFg5) {
                    eventsFg5 = false;
                    bgRoutine = BG_STAGE_DO_TRANSITION;
                    LOG.info("MGZ1 BG: Events_fg_5 detected, advancing to transition stage");
                }
            }
            case BG_STAGE_DO_TRANSITION -> {
                // ROM gate: tst.b (Kos_modules_left) — we have no Kos queue, so
                // gate on endOfLevelFlag (results screen has finished exiting)
                // to ensure the tally completes before the reload.
                if (!transitionRequested && gameState().isEndOfLevelFlag()) {
                    requestMgz2Transition();
                }
            }
            default -> { }
        }
    }

    /**
     * Requests the seamless transition from MGZ Act 1 to MGZ Act 2.
     * ROM: MGZ1BGE_Transition (sonic3k.asm lines 106307-106345).
     */
    private void requestMgz2Transition() {
        transitionRequested = true;

        // The player is still in the signpost victory pose (objectControlled).
        // After the seamless reload lands in MGZ Act 2, the level event manager
        // releases them so normal play can resume.
        S3kTransitionWriteSupport.requestMgzPostTransitionRelease(
                module().getLevelEventProvider());

        LevelManager lm = levelManager();
        SessionSaveRequests.requestCurrentSessionSave(SaveReason.PROGRESSION_SAVE);
        lm.requestSeamlessTransition(
                SeamlessLevelTransitionRequest.builder(
                                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                        .targetZoneAct(Sonic3kZoneIds.ZONE_MGZ, 1)
                        .deactivateLevelNow(false)
                        // Results screen already started act 2 music.
                        .preserveMusic(true)
                        // Title card skipped during the results path for seamless
                        // transitions; show it after the reload completes.
                        .showInLevelTitleCard(true)
                        .playerOffset(TRANSITION_OFFSET_X, TRANSITION_OFFSET_Y)
                        .cameraOffset(TRANSITION_OFFSET_X, TRANSITION_OFFSET_Y)
                        .build());

        LOG.info("MGZ1: requested seamless transition to Act 2 (offset X="
                + Integer.toHexString(TRANSITION_OFFSET_X) + " Y="
                + Integer.toHexString(TRANSITION_OFFSET_Y) + ")");
    }

    public boolean isTransitionRequested() {
        return transitionRequested;
    }
}
