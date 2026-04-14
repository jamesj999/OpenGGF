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

    /** ROM: Events_fg_5 — set by results screen / signpost to trigger BG act transition. */
    private boolean eventsFg5;

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
        eventsFg5 = false;
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
}
