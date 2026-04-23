package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Level;

import java.util.logging.Logger;

/**
 * CNZ (Carnival Night Zone) dynamic level events.
 *
 * <p>This class models the ROM-shaped CNZ act-state split needed for the
 * current bring-up:
 * <ul>
 *   <li>Act 1 miniboss entry and post-boss handoff</li>
 *   <li>Act 1 seamless reload request</li>
 *   <li>Act 2 Knuckles teleporter route markers</li>
 * </ul>
 */
public class Sonic3kCNZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kCNZEvents.class.getName());

    /** CNZ1_BackgroundEvent stage 0. */
    public static final int BG_NORMAL = 0x00;
    /** CNZ1_BackgroundEvent stage 4. */
    public static final int BG_BOSS_START = 0x04;
    /** CNZ1_BackgroundEvent stage 8. */
    public static final int BG_BOSS = 0x08;
    /** CNZ1_BackgroundEvent stage 12. */
    public static final int BG_AFTER_BOSS = 0x0C;
    /** CNZ1_BackgroundEvent stage 16. */
    public static final int BG_FG_REFRESH = 0x10;
    /** CNZ1_BackgroundEvent stage 20. */
    public static final int BG_FG_REFRESH_2 = 0x14;
    /** CNZ1_BackgroundEvent stage 24. */
    public static final int BG_DO_TRANSITION = 0x18;

    /** CNZ2_ScreenEvent stage 0. */
    public static final int FG_ACT2_ENTRY = 0x00;
    /** CNZ2_ScreenEvent stage 4. */
    public static final int FG_ACT2_KNUCKLES_ROUTE = 0x04;
    /** CNZ2_ScreenEvent stage 8. */
    public static final int FG_ACT2_NORMAL = 0x08;

    /**
     * Camera X threshold that arms the miniboss arena gate.
     *
     * <p>ROM: {@code Obj_CNZMiniboss} (sonic3k.asm:144824) reads
     * {@code move.w #$31E0,d0} then {@code cmp.w (Camera_X_pos).w,d0} and
     * branches to {@code loc_6D9A8} when the camera reaches the threshold.
     * The ROM value is exposed through {@link Sonic3kConstants#CNZ_MINIBOSS_ARENA_MIN_X}
     * and reused below for the {@code Camera_min_X_pos} clamp so the arena lock
     * lines up with the same coordinate the ROM uses.
     *
     * <p>The arming threshold is held a little earlier than the arena clamp so
     * the scroll handler can observe {@link BossBackgroundMode#ACT1_MINIBOSS_PATH}
     * during the approach window that drives the early refresh phase the
     * {@code SwScrlCnz} boss scroll path covers.
     */
    private static final int MINIBOSS_CAM_X_THRESHOLD = 0x3000;
    private static final int KNUCKLES_ROUTE_MIN_X = 0x4750;
    private static final int KNUCKLES_ROUTE_MAX_X = 0x48E0;

    /**
     * Saved {@code Camera_max_X_pos} captured when the arena lock fires.
     *
     * <p>ROM: {@code loc_6D9A8} (sonic3k.asm:144831) writes
     * {@code Camera_max_X_pos} into {@code Camera_stored_max_X_pos}; we mirror
     * that here so the falling-edge release can restore the natural camera
     * extent when {@link CnzMinibossInstance#onEndGo} clears
     * {@link #bossFlag}.
     */
    private short cameraStoredMaxXPos;
    private short cameraStoredMinXPos;
    private short cameraStoredMinYPos;
    private short cameraStoredMaxYPos;
    private boolean cameraClampsActive;
    private boolean bossFlagPrev;

    /**
     * CNZ-local foreground routine mirror.
     *
     * <p>The ROM keeps separate FG/BG routines. CNZ stores the FG routine
     * locally so the event manager and runtime state can expose it without
     * relying on the base class counters.
     */
    private int fgRoutine;

    /**
     * CNZ-local background routine mirror.
     *
     * <p>This tracks the Act 1 boss chain and the later seamless reload gate.
     */
    private int bgRoutine;

    /** ROM: Events_fg_5. */
    private boolean eventsFg5;

    /**
     * Published deform phase source later consumed by AnimateTiles_CNZ.
     */
    private int deformPhaseBgX;

    /**
     * Published stabilized BG camera X copy.
     */
    private int publishedBgCameraX;

    /**
     * ROM-shaped boss scroll offset and velocity values.
     */
    private int bossScrollOffsetY;
    private int bossScrollVelocityY;

    /** Suppresses wall-grab interactions during the miniboss path. */
    private boolean wallGrabSuppressed;

    /** Latest requested water target. */
    private int waterTargetY;

    /** Bridge flag for the Act 1 water button helper. */
    private boolean waterButtonArmed;

    /** Boss ownership mirror used by later slices. */
    private boolean bossFlag;

    /** True once the Knuckles-only Act 2 teleporter route is active. */
    private boolean knucklesTeleporterRouteActive;

    /** True once the teleporter beam child has spawned. */
    private boolean teleporterBeamSpawned;

    /** True once the seamless Act 1 -> Act 2 reload has been requested. */
    private boolean act2TransitionRequested;
    private int pendingZoneActWord;
    private int transitionWorldOffsetX;
    private int transitionWorldOffsetY;

    /** Teleporter route clamp values mirrored for tests. */
    private int cameraMinXClamp;
    private int cameraMaxXClamp;

    /**
     * Last pending arena chunk coordinates. Slice 0 intentionally exposes a
     * single pending request instead of a queue.
     */
    private int arenaChunkWorldX;
    private int arenaChunkWorldY;
    private boolean arenaChunkDestructionQueued;
    /**
     * Accumulated destroyed arena height in pixels.
     *
     * <p>ROM: {@code Obj_CNZMinibossTop} only ever reports impacts snapped to the
     * arena block grid before {@code CNZMiniboss_BlockExplosion} removes the
     * touched chunk. Task 7 keeps a single scalar here instead of replaying the
     * full live-layout mutation, because the current tests only need to prove
     * that each top hit contributes one 0x20-pixel row toward the lowering
     * sequence consumed by the base object.
     */
    private int destroyedArenaRows;

    /** Current boss background scroll mode. */
    private BossBackgroundMode bossBackgroundMode = BossBackgroundMode.NORMAL;

    /**
     * Background scroll modes used by CNZ.
     */
    public enum BossBackgroundMode {
        NORMAL,
        ACT1_MINIBOSS_PATH,
        ACT1_POST_BOSS,
        ACT2_KNUCKLES_TELEPORTER
    }

    @Override
    public void init(int act) {
        super.init(act);
        fgRoutine = 0;
        bgRoutine = 0;
        eventsFg5 = false;
        deformPhaseBgX = 0;
        publishedBgCameraX = 0;
        bossScrollOffsetY = 0;
        bossScrollVelocityY = 0;
        wallGrabSuppressed = false;
        waterTargetY = 0;
        waterButtonArmed = false;
        bossFlag = false;
        bossFlagPrev = false;
        cameraStoredMaxXPos = 0;
        cameraStoredMinXPos = 0;
        cameraStoredMinYPos = 0;
        cameraStoredMaxYPos = 0;
        cameraClampsActive = false;
        knucklesTeleporterRouteActive = false;
        teleporterBeamSpawned = false;
        act2TransitionRequested = false;
        pendingZoneActWord = 0;
        transitionWorldOffsetX = 0;
        transitionWorldOffsetY = 0;
        cameraMinXClamp = 0;
        cameraMaxXClamp = 0;
        arenaChunkWorldX = 0;
        arenaChunkWorldY = 0;
        arenaChunkDestructionQueued = false;
        destroyedArenaRows = 0;
        bossBackgroundMode = BossBackgroundMode.NORMAL;
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Bg();
        } else {
            updateAct2Fg();
        }
        // Falling-edge: when the boss object clears Boss_flag (via
        // CnzMinibossInstance.onEndGo, ROM sonic3k.asm:144998), release the
        // arena camera clamp and wall-grab suppression so the post-boss
        // refresh chain can pan the camera forward toward the signpost.
        if (bossFlagPrev && !bossFlag) {
            releaseArenaCameraClamps();
            wallGrabSuppressed = false;
        }
        bossFlagPrev = bossFlag;
    }

    private void updateAct1Bg() {
        switch (bgRoutine) {
            case BG_AFTER_BOSS -> handleAfterBossStage();
            case BG_FG_REFRESH -> advanceRefreshStageToSecondPass();
            case BG_FG_REFRESH_2 -> advanceRefreshStageToTransitionGate();
            case BG_DO_TRANSITION -> handleSeamlessReloadStage();
            default -> handleAct1Entry();
        }
    }

    /**
     * Handles the normal Act 1 entry path and the miniboss threshold gate.
     *
     * <p>Parity note: in the ROM the arena setup runs from inside
     * {@code Obj_CNZMiniboss} (sonic3k.asm:144823), so it only fires if that
     * object is live in the active window. Tests and debug teleports that
     * drop the camera strictly past the arena's far wall
     * ({@link Sonic3kConstants#CNZ_MINIBOSS_ARENA_MAX_X}) would normally not
     * reach that object — mirror the ROM by short-circuiting straight to the
     * post-boss mode instead of tripping the one-shot arena clamp for a
     * player that was never gated through the entry window.
     */
    private void handleAct1Entry() {
        switch (bossBackgroundMode) {
            case NORMAL -> {
                int camX = camera().getX();
                if (camX > Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_X) {
                    // Camera already past the arena's right wall — the
                    // miniboss object would not be live here. Skip the
                    // arena lock and hand off to post-boss mode.
                    bossBackgroundMode = BossBackgroundMode.ACT1_POST_BOSS;
                    bgRoutine = BG_AFTER_BOSS;
                } else if (camX >= MINIBOSS_CAM_X_THRESHOLD) {
                    enterMinibossArena();
                }
            }
            case ACT1_MINIBOSS_PATH -> {
                if (eventsFg5) {
                    eventsFg5 = false;
                    bossBackgroundMode = BossBackgroundMode.ACT1_POST_BOSS;
                    bgRoutine = BG_FG_REFRESH;
                    LOG.info("CNZ: post-boss handoff entered");
                }
            }
            case ACT1_POST_BOSS -> handleAfterBossStage();
            case ACT2_KNUCKLES_TELEPORTER -> updateAct2Fg();
        }
    }

    /**
     * ROM: {@code loc_6D9A8} (sonic3k.asm:144830) — arena setup invoked
     * when {@code Obj_CNZMiniboss}'s outer gate succeeds.
     *
     * <p>Mirrors the ROM sequence: stash {@code Camera_max_X_pos}, clamp the
     * camera to the arena rectangle, fade the music, set
     * {@code Boss_flag}, suppress wall-grab, load PLC {@code 0x5D}, and
     * install {@code Pal_CNZMiniboss} into palette line 1.
     */
    private void enterMinibossArena() {
        Camera camera = camera();
        cameraStoredMaxXPos = camera.getMaxX();
        cameraStoredMinXPos = camera.getMinX();
        cameraStoredMinYPos = camera.getMinY();
        cameraStoredMaxYPos = camera.getMaxY();
        cameraClampsActive = true;

        camera.setMinX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        camera.setMaxX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_X);
        camera.setMinY((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y);
        camera.setMaxY((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_Y);
        camera.setMaxYTarget((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_Y);

        bossBackgroundMode = BossBackgroundMode.ACT1_MINIBOSS_PATH;
        bgRoutine = BG_BOSS_START;
        wallGrabSuppressed = true;
        // ROM sonic3k.asm:144843 — `move.b #1,(Boss_flag).w`. Setting the
        // mirrored event-state bit lets CnzMinibossInstance and downstream
        // CNZ scripts observe the lock without a separate global flag.
        bossFlag = true;
        bossFlagPrev = true;

        // ROM sonic3k.asm:144841 — `moveq #cmd_FadeOut,d0; jsr Play_Music`.
        // Mirror the music fade through the engine's helper. Sequencing the
        // miniboss theme that follows is reserved for the audio follow-up
        // (see TODO marker below).
        if (audio() != null) {
            audio().fadeOutMusic();
        }
        // TODO(audio-followup): wire miniboss audio fade-in
        // (Sonic3kMusic.MINIBOSS) once the boss music handoff lands; the
        // fade-out above already mirrors sonic3k.asm:144841. Workstream D
        // shipped the boss without this fade-in by design (out of scope for
        // D — see the workstream-D entries in CHANGELOG.md and the
        // post-D baseline doc at docs/s3k-zones/cnz-post-workstream-d-baseline.md).

        // ROM sonic3k.asm:144844 — `moveq #$5D,d0; jsr Load_PLC`.
        applyPlc(Sonic3kConstants.PLC_CNZ_MINIBOSS);

        // ROM sonic3k.asm:144846-144847 — `lea Pal_CNZMiniboss(pc),a1; jmp
        // (PalLoad_Line1).l`. PalLoad_Line1 writes one VDP palette line
        // (32 bytes) into line 1.
        installMinibossPalette();

        LOG.info("CNZ: camera reached miniboss threshold; arena lock + Boss_flag set");
    }

    private void installMinibossPalette() {
        try {
            byte[] line = rom().readBytes(Sonic3kConstants.PAL_CNZ_MINIBOSS_ADDR, 32);
            Level level = levelManager() != null ? levelManager().getCurrentLevel() : null;
            if (level == null) {
                return;
            }
            S3kPaletteWriteSupport.applyLine(
                    paletteRegistryOrNull(),
                    level,
                    graphics(),
                    S3kPaletteOwners.CNZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                    1,
                    line);
        } catch (Exception e) {
            LOG.warning("CNZ: failed to install Pal_CNZMiniboss: " + e.getMessage());
        }
    }

    /**
     * Falling-edge release of the arena camera lock.
     *
     * <p>ROM: the post-boss path restores the natural camera extents after
     * {@code Obj_CNZMinibossEnd} completes. The engine snapshots the prior
     * clamp values in {@link #enterMinibossArena()} and restores them here
     * once the boss object clears {@link #bossFlag} via
     * {@code CnzMinibossInstance.onEndGo}.
     */
    private void releaseArenaCameraClamps() {
        if (!cameraClampsActive) {
            return;
        }
        Camera camera = camera();
        camera.setMinX(cameraStoredMinXPos);
        camera.setMaxX(cameraStoredMaxXPos);
        camera.setMinY(cameraStoredMinYPos);
        camera.setMaxY(cameraStoredMaxYPos);
        camera.setMaxYTarget(cameraStoredMaxYPos);
        cameraClampsActive = false;
    }

    /**
     * ROM: CNZ1BGE_AfterBoss.
     *
     * <p>The first Events_fg_5 only advances the refresh chain. It does not
     * request the act reload.
     */
    private void handleAfterBossStage() {
        if (!eventsFg5) {
            return;
        }
        eventsFg5 = false;
        bgRoutine = BG_FG_REFRESH;
    }

    /**
     * ROM: CNZ1BGE_FGRefresh.
     *
     * <p>The real game copies arena data back into the foreground before the
     * signpost phase. This bring-up keeps the same sequencing contract by
     * advancing to the second refresh pass on the next update while leaving
     * the actual layout mutation to later slices.
     */
    private void advanceRefreshStageToSecondPass() {
        bgRoutine = BG_FG_REFRESH_2;
    }

    /**
     * ROM: CNZ1BGE_FGRefresh2.
     *
     * <p>The real game finishes the foreground handoff here. For the current
     * scope we only need the stage to become reachable and to progress to the
     * reload gate without an external test forcing BG_DO_TRANSITION.
     */
    private void advanceRefreshStageToTransitionGate() {
        bgRoutine = BG_DO_TRANSITION;
    }

    /**
     * ROM: CNZ1BGE_DoTransition.
     *
     * <p>The second Events_fg_5 loads the transition PLCs, publishes the
     * seamless reload metadata, and moves the handler into the Act 2 route
     * state.
     */
    private void handleSeamlessReloadStage() {
        if (!eventsFg5) {
            return;
        }
        eventsFg5 = false;
        applyPlc(0x18);
        applyPlc(0x19);
        act2TransitionRequested = true;
        pendingZoneActWord = 0x0301;
        transitionWorldOffsetX = -0x3000;
        transitionWorldOffsetY = 0x0200;
        fgRoutine = FG_ACT2_ENTRY;
        bgRoutine = BG_NORMAL;
        bossBackgroundMode = BossBackgroundMode.ACT2_KNUCKLES_TELEPORTER;
        wallGrabSuppressed = false;
    }

    /**
     * CNZ Act 2 foreground logic.
     *
     * <p>The current bring-up only needs the route selection and clamp
     * publication. Later slices will add the teleporter and capsule object
     * sequence.
     */
    private void updateAct2Fg() {
        switch (fgRoutine) {
            case FG_ACT2_ENTRY -> {
                if (knucklesTeleporterRouteActive) {
                    fgRoutine = FG_ACT2_KNUCKLES_ROUTE;
                    publishKnucklesTeleporterClamp();
                } else {
                    fgRoutine = FG_ACT2_NORMAL;
                }
            }
            case FG_ACT2_KNUCKLES_ROUTE -> publishKnucklesTeleporterClamp();
            case FG_ACT2_NORMAL -> {
                // Normal Act 2 draw path.
            }
            default -> {
                if (knucklesTeleporterRouteActive) {
                    fgRoutine = FG_ACT2_KNUCKLES_ROUTE;
                    publishKnucklesTeleporterClamp();
                }
            }
        }
    }

    /** Returns the current boss background scroll mode. */
    public BossBackgroundMode getBossBackgroundMode() {
        return bossBackgroundMode;
    }

    /** CNZ-local foreground routine mirror. */
    public int getForegroundRoutine() {
        return fgRoutine;
    }

    /** CNZ-local background routine mirror. */
    public int getBackgroundRoutine() {
        return bgRoutine;
    }

    /** Restores the CNZ-local background routine. */
    public void setBackgroundRoutine(int routine) {
        this.bgRoutine = routine;
    }

    /** Test hook for the foreground routine. */
    public void forceForegroundRoutine(int routine) {
        this.fgRoutine = routine;
    }

    /** Test hook for the background routine. */
    public void forceBackgroundRoutine(int routine) {
        this.bgRoutine = routine;
    }

    /** Test hook for the boss background mode. */
    public void forceBossBackgroundMode(BossBackgroundMode mode) {
        this.bossBackgroundMode = mode;
    }

    /** Publishes the deform inputs consumed by later CNZ systems. */
    public void setPublishedDeformInputs(int phaseSourceX, int bgCameraX) {
        this.deformPhaseBgX = phaseSourceX;
        this.publishedBgCameraX = bgCameraX;
    }

    public int getDeformPhaseBgX() {
        return deformPhaseBgX;
    }

    public int getPublishedBgCameraX() {
        return publishedBgCameraX;
    }

    /** Publishes the boss-scroll Y state used by the miniboss path. */
    public void setBossScrollState(int offsetY, int velocityY) {
        this.bossScrollOffsetY = offsetY;
        this.bossScrollVelocityY = velocityY;
    }

    public int getBossScrollOffsetY() {
        return bossScrollOffsetY;
    }

    public int getBossScrollVelocityY() {
        return bossScrollVelocityY;
    }

    public boolean isWallGrabSuppressed() {
        return wallGrabSuppressed;
    }

    public void setWallGrabSuppressed(boolean wallGrabSuppressed) {
        this.wallGrabSuppressed = wallGrabSuppressed;
    }

    public int getWaterTargetY() {
        return waterTargetY;
    }

    public void setWaterTargetY(int waterTargetY) {
        this.waterTargetY = waterTargetY;
        /**
         * ROM anchors:
         * {@code Obj_CNZWaterLevelCorkFloor} writes {@code Target_water_level}
         * directly to {@code $0958}, and {@code Obj_CNZWaterLevelButton} writes
         * {@code $0A58} once the arming flag has been set. The engine mirrors
         * those writes into both the CNZ event state and the shared water system
         * so tests and later runtime consumers observe the same explicit source
         * of truth.
         */
        waterSystem().setWaterLevelTarget(levelManager().getRomZoneId(),
                levelManager().getCurrentAct(),
                waterTargetY);
    }

    public boolean isWaterButtonArmed() {
        return waterButtonArmed;
    }

    public void setWaterButtonArmed(boolean waterButtonArmed) {
        this.waterButtonArmed = waterButtonArmed;
    }

    public boolean isBossFlag() {
        return bossFlag;
    }

    public void setBossFlag(boolean bossFlag) {
        /**
         * Task 8 boundary note:
         * CNZ's end-boss implementation currently owns only the startup gate and
         * defeat handoff, so this flag is the explicit shared seam between the
         * bounded boss wrapper and the wider CNZ event script.
         */
        this.bossFlag = bossFlag;
    }

    /**
     * Enters the Knuckles-only Act 2 teleporter route.
     *
     * <p>The route is represented by a dedicated FG routine plus the camera
     * clamp values the ROM applies while the teleporter sequence is active.
     */
    public void beginKnucklesTeleporterRoute() {
        knucklesTeleporterRouteActive = true;
        bossBackgroundMode = BossBackgroundMode.ACT2_KNUCKLES_TELEPORTER;
        fgRoutine = FG_ACT2_KNUCKLES_ROUTE;
        /**
         * ROM: the late Knuckles route clamps the camera to the teleporter lane
         * while {@code Obj_CNZTeleporter} owns the cutscene-specific player and
         * palette state. Publishing the route transition here keeps the object
         * dependency explicit instead of burying it in object-local booleans.
         */
        publishKnucklesTeleporterClamp();
    }

    public boolean isKnucklesTeleporterRouteActive() {
        return knucklesTeleporterRouteActive;
    }

    /**
     * Returns whether Task 8's teleporter object should own the route palette
     * override.
     *
     * <p>The override window begins when the Knuckles-only route starts and
     * ends once the beam object has been spawned. Publishing that seam here
     * keeps the later palette handoff explicit in CNZ event state instead of
     * introducing another hidden object-local flag.
     */
    public boolean shouldApplyTeleporterPaletteOverride() {
        return knucklesTeleporterRouteActive && !teleporterBeamSpawned;
    }

    public void markTeleporterBeamSpawned() {
        /**
         * Once the shared beam exists, the teleporter-specific palette override
         * is no longer the active owner. Task 8 uses this explicit event seam
         * so tests can observe the parent -> beam handoff without depending on
         * hidden object state.
         */
        teleporterBeamSpawned = true;
    }

    public boolean isTeleporterBeamSpawned() {
        return teleporterBeamSpawned;
    }

    public boolean isAct2TransitionRequested() {
        return act2TransitionRequested;
    }

    public int getPendingZoneActWord() {
        return pendingZoneActWord;
    }

    public int getTransitionWorldOffsetX() {
        return transitionWorldOffsetX;
    }

    public int getTransitionWorldOffsetY() {
        return transitionWorldOffsetY;
    }

    public int getCameraMinXClamp() {
        return cameraMinXClamp;
    }

    public int getCameraMaxXClamp() {
        return cameraMaxXClamp;
    }

    public void setPendingArenaChunkDestruction(int chunkWorldX, int chunkWorldY) {
        arenaChunkWorldX = chunkWorldX;
        arenaChunkWorldY = chunkWorldY;
        arenaChunkDestructionQueued = true;
        /**
         * ROM: {@code Obj_CNZMinibossTop} snaps the impact coordinates to the
         * 0x20-pixel block grid before calling {@code CNZMiniboss_BlockExplosion}.
         * Task 7 uses that same block height as the destroyed-row accumulator so
         * the miniboss base can react to a ROM-sized arena step without the full
         * mutation pipeline from later slices.
         */
        destroyedArenaRows += 0x20;
    }

    public boolean isArenaChunkDestructionQueued() {
        return arenaChunkDestructionQueued;
    }

    public int getArenaChunkWorldX() {
        return arenaChunkWorldX;
    }

    public int getArenaChunkWorldY() {
        return arenaChunkWorldY;
    }

    /**
     * Alias used by the Task 7 headless tests.
     *
     * <p>The plan originally named these accessors after the queue contract
     * rather than the world-coordinate storage field. Keeping both names avoids
     * forcing later CNZ slices to rewrite their state vocabulary.
     */
    public int getPendingArenaChunkX() {
        return arenaChunkWorldX;
    }

    /**
     * Alias used by the Task 7 headless tests.
     */
    public int getPendingArenaChunkY() {
        return arenaChunkWorldY;
    }

    /**
     * Returns the accumulated destroyed arena height in pixels.
     *
     * <p>Each queued top-piece impact contributes exactly one 0x20-pixel row,
     * matching the block-sized destruction seam exported from
     * {@code Obj_CNZMinibossTop}.
     */
    public int getDestroyedArenaRows() {
        return destroyedArenaRows;
    }

    public void setEventsFg5(boolean flag) {
        eventsFg5 = flag;
        if (flag) {
            LOG.info("CNZ: Events_fg_5 set externally");
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
        fgRoutine = routine;
    }

    /**
     * Publishes the clamp values used by the teleporter route.
     */
    private void publishKnucklesTeleporterClamp() {
        cameraMinXClamp = KNUCKLES_ROUTE_MIN_X;
        cameraMaxXClamp = KNUCKLES_ROUTE_MAX_X;
    }
}
