package com.openggf.game.sonic3k.events;

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

    private static final int MINIBOSS_CAM_X_THRESHOLD = 0x3000;
    private static final int KNUCKLES_ROUTE_MIN_X = 0x4750;
    private static final int KNUCKLES_ROUTE_MAX_X = 0x48E0;

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
        bossBackgroundMode = BossBackgroundMode.NORMAL;
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Bg();
        } else {
            updateAct2Fg();
        }
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
     */
    private void handleAct1Entry() {
        switch (bossBackgroundMode) {
            case NORMAL -> {
                if (camera().getX() >= MINIBOSS_CAM_X_THRESHOLD) {
                    bossBackgroundMode = BossBackgroundMode.ACT1_MINIBOSS_PATH;
                    bgRoutine = BG_BOSS_START;
                    wallGrabSuppressed = true;
                    LOG.info("CNZ: camera reached miniboss threshold");
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
        publishKnucklesTeleporterClamp();
    }

    public boolean isKnucklesTeleporterRouteActive() {
        return knucklesTeleporterRouteActive;
    }

    public void markTeleporterBeamSpawned() {
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
