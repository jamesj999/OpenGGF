package com.openggf.game.sonic3k.events;

import java.util.logging.Logger;

/**
 * CNZ (Carnival Night Zone) dynamic level events.
 *
 * <p>ROM: CNZ1_BackgroundEvent (sonic3k.asm lines 107421-107659)
 * and CNZ2_ScreenEvent / CNZ2_BackgroundEvent (sonic3k.asm lines 107876-108030).
 *
 * <h3>Act 1 BG state machine (CNZ1_BackgroundEvent):</h3>
 * <ul>
 *   <li>Stage 0 (NORMAL): Camera X &lt; $3000 → normal deform/scroll</li>
 *   <li>Stage 0 → miniboss: Camera X &gt;= $3000 → enter miniboss path,
 *       load boss palette, clamp Camera_min_Y_pos</li>
 *   <li>Stages 4-8: Boss scroll, arena destruction, BG refresh</li>
 *   <li>Stage 12 (ACT1_POST_BOSS): Events_fg_5 → FG refresh / collision handoff</li>
 *   <li>Stage 24: seamless Act 1 → Act 2 transition</li>
 * </ul>
 *
 * <h3>Act 2 FG (CNZ2_ScreenEvent):</h3>
 * <ul>
 *   <li>Stage 0: Knuckles branch — X &gt;= $4880, Y &gt;= $0B00 →
 *       spawn teleporter + egg capsule</li>
 *   <li>Sonic/Tails skip directly to normal draw</li>
 * </ul>
 *
 * <p>This class currently models the boss background mode transitions
 * used by {@link com.openggf.game.sonic3k.scroll.SwScrlCnz} to switch
 * between normal deform scroll and the fixed boss scroll path.
 */
public class Sonic3kCNZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kCNZEvents.class.getName());

    // =========================================================================
    // Act 1 BG miniboss camera threshold (ROM: CNZ1BGE_Normal)
    // =========================================================================
    private static final int MINIBOSS_CAM_X_THRESHOLD = 0x3000;

    // =========================================================================
    // State
    // =========================================================================

    /**
     * CNZ-local foreground routine mirror.
     *
     * <p>CNZ will eventually split FG and BG state the same way the ROM keeps
     * separate {@code Events_routine_fg} / {@code Events_routine_bg} counters.
     * This must stay local to the CNZ handler rather than reaching into
     * {@link com.openggf.game.AbstractLevelEventManager}'s protected counters.
     */
    private int fgRoutine;

    /**
     * CNZ-local background routine mirror.
     *
     * <p>The later boss and teleporter slices use this to model
     * {@code CNZ1_BackgroundEvent} / {@code CNZ2_BackgroundEvent} phases
     * directly on the zone event object.
     */
    private int bgRoutine;

    /** ROM: Events_fg_5 — set by results screen / signpost to trigger BG act transition. */
    private boolean eventsFg5;

    /**
     * Published deform phase source corresponding to the ROM value later read by
     * {@code AnimateTiles_CNZ} via {@code Events_bg+$10}.
     */
    private int deformPhaseBgX;

    /**
     * Published CNZ background camera X corresponding to
     * {@code Camera_X_pos_BG_copy}. Scroll/parallax code publishes this so tile
     * animation and other systems consume the same stabilized BG X input.
     */
    private int publishedBgCameraX;

    /**
     * Boss scroll offset Y published by the CNZ miniboss scroll-control object.
     * This is the ROM-shaped event value used by {@code CNZ1_BossLevelScroll}
     * and {@code CNZ1_BossLevelScroll2}.
     */
    private int bossScrollOffsetY;

    /**
     * Boss scroll velocity Y accumulator written by the scroll-control object.
     * Stored as a raw fixed-point integer so later slices can preserve the ROM's
     * acceleration math exactly.
     */
    private int bossScrollVelocityY;

    /**
     * Suppresses wall-grab interactions during CNZ object/event sequences that
     * would otherwise let the player cling to geometry the ROM temporarily
     * treats as non-grabbable.
     */
    private boolean wallGrabSuppressed;

    /**
     * Latest requested target water level in world Y coordinates.
     *
     * <p>CNZ helper objects write the same target values the ROM stores in its
     * event workspace, such as the Slice 0 canary target {@code $0A58}.
     */
    private int waterTargetY;

    /**
     * Object-event bridge flag used by the Act 1 water button helper before it
     * commits the final target-Y write.
     */
    private boolean waterButtonArmed;

    /**
     * Boss flag mirror for CNZ objects. Later slices use this to gate event
     * progression during miniboss and end-boss ownership windows.
     */
    private boolean bossFlag;

    /**
     * Tracks whether the Knuckles-only Act 2 teleporter route has been entered.
     */
    private boolean knucklesTeleporterRouteActive;

    /**
     * Tracks whether the teleporter beam child has been spawned after the route
     * finishes loading its art and settles the player.
     */
    private boolean teleporterBeamSpawned;

    /**
     * Last queued arena chunk coordinates. This is a narrow Slice 0 scaffold for
     * the miniboss arena destruction bridge; later slices will replace the
     * single-slot storage with the real queued write pipeline.
     */
    private int arenaChunkWorldX;
    private int arenaChunkWorldY;
    private boolean arenaChunkDestructionQueued;

    /** Current boss background scroll mode, derived from BG event state. */
    private BossBackgroundMode bossBackgroundMode = BossBackgroundMode.NORMAL;

    /**
     * Describes which scroll path the CNZ background should use.
     * {@link com.openggf.game.sonic3k.scroll.SwScrlCnz} reads this to
     * switch between normal deform math and fixed boss arena scroll.
     */
    public enum BossBackgroundMode {
        /** Standard CNZ1_Deform deformation (7/16 X ratio, 13/128 Y ratio). */
        NORMAL,
        /** Act 1 miniboss arena path — CNZ1_BossLevelScroll / CNZ1_BossLevelScroll2. */
        ACT1_MINIBOSS_PATH,
        /** Post-boss FG refresh / collision handoff (still uses boss scroll). */
        ACT1_POST_BOSS,
        /** Act 2 Knuckles teleporter sequence (normal scroll continues). */
        ACT2_KNUCKLES_TELEPORTER
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

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
        arenaChunkWorldX = 0;
        arenaChunkWorldY = 0;
        arenaChunkDestructionQueued = false;
        bossBackgroundMode = BossBackgroundMode.NORMAL;
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Bg();
        }
        // Act 2 BG uses the shared CNZ1_Deform path (NORMAL mode).
        // Act 2 FG Knuckles teleporter is not yet implemented.
    }

    // =========================================================================
    // Act 1 BG — boss background mode transitions
    // =========================================================================

    /**
     * ROM: CNZ1_BackgroundEvent state 0 (CNZ1BGE_Normal).
     * Checks Camera X against $3000 threshold to enter miniboss path.
     * Post-boss transition triggered by Events_fg_5.
     */
    private void updateAct1Bg() {
        switch (bossBackgroundMode) {
            case NORMAL -> {
                // ROM: CNZ1BGE_Normal — Camera X >= $3000 enters miniboss path
                if (camera().getX() >= MINIBOSS_CAM_X_THRESHOLD) {
                    bossBackgroundMode = BossBackgroundMode.ACT1_MINIBOSS_PATH;
                    LOG.info("CNZ1 BG: Camera X >= $3000, entering miniboss path");
                }
            }
            case ACT1_MINIBOSS_PATH -> {
                // ROM: CNZ1BGE_AfterBoss — Events_fg_5 triggers post-boss handoff
                if (eventsFg5) {
                    eventsFg5 = false;
                    bossBackgroundMode = BossBackgroundMode.ACT1_POST_BOSS;
                    LOG.info("CNZ1 BG: Events_fg_5 set, entering post-boss phase");
                }
            }
            case ACT1_POST_BOSS -> {
                // Post-boss phases (FG refresh, signpost, seamless transition)
                // will be expanded as arena destruction and transition are implemented.
            }
            case ACT2_KNUCKLES_TELEPORTER -> {
                // Knuckles Act 2 teleporter — not yet implemented.
            }
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** Returns the current boss background scroll mode. */
    public BossBackgroundMode getBossBackgroundMode() {
        return bossBackgroundMode;
    }

    /**
     * CNZ-local foreground routine mirroring the ROM FG routine counter.
     */
    public int getForegroundRoutine() {
        return fgRoutine;
    }

    /**
     * CNZ-local background routine mirroring the ROM BG routine counter.
     */
    public int getBackgroundRoutine() {
        return bgRoutine;
    }

    /**
     * Test and bootstrap hook for restoring the CNZ foreground routine without
     * touching {@link com.openggf.game.AbstractLevelEventManager} internals.
     */
    public void forceForegroundRoutine(int routine) {
        this.fgRoutine = routine;
    }

    /**
     * Test and bootstrap hook for restoring the CNZ background routine.
     */
    public void forceBackgroundRoutine(int routine) {
        this.bgRoutine = routine;
    }

    /**
     * Publishes the ROM-equivalent deform inputs consumed by later CNZ systems:
     * the phase source derived from {@code Events_bg+$10} and the stabilized
     * background camera X copy.
     */
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

    /**
     * Stores the CNZ boss-scroll Y offset and velocity exactly as the miniboss
     * scroll-control object would publish them into the ROM event workspace.
     */
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

    public void beginKnucklesTeleporterRoute() {
        knucklesTeleporterRouteActive = true;
        bossBackgroundMode = BossBackgroundMode.ACT2_KNUCKLES_TELEPORTER;
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

    public void queueArenaChunkDestruction(int chunkWorldX, int chunkWorldY) {
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

    /** Set the Events_fg_5 flag (called by results screen / signpost). */
    public void setEventsFg5(boolean flag) {
        this.eventsFg5 = flag;
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
        this.fgRoutine = routine;
    }
}
