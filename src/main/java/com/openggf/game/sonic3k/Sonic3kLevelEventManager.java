package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;

import java.util.logging.Logger;

/**
 * Sonic 3&K implementation of dynamic level events.
 * ROM equivalent: ScreenEvents (sonic3k.asm:102228)
 *
 * S3K uses dual foreground/background event routines (Events_routine_fg
 * and Events_routine_bg) with a stride of 4 per state transition.
 * Each zone has two parallel event handlers:
 * <ul>
 *   <li>ScreenEvent (FG) - terrain changes, object spawning, camera boundaries</li>
 *   <li>BackgroundEvent (BG) - parallax, deformation, water level, boss arenas</li>
 * </ul>
 *
 * S3K also uses Boss_flag to gate FG events during boss fights, and
 * branches on Player_mode for character-specific event paths (Sonic/Tails
 * vs Knuckles take different routes through most zones).
 *
 * Phase 1 implements bootstrap selection for AIZ1 intro-skip parity.
 * Zone event handlers will be added incrementally per zone.
 */
public class Sonic3kLevelEventManager extends AbstractLevelEventManager {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevelEventManager.class.getName());
    private static Sonic3kLevelEventManager instance;

    private Sonic3kLoadBootstrap bootstrap = Sonic3kLoadBootstrap.NORMAL;
    private Sonic3kAIZEvents aizEvents;

    private Sonic3kLevelEventManager() {
        super();
    }

    // =========================================================================
    // AbstractLevelEventManager contract
    // =========================================================================

    @Override
    protected int getRoutineStride() {
        return 4;
    }

    @Override
    protected int getEventDataFgSize() {
        return 6; // Events_fg_0..5
    }

    @Override
    protected int getEventDataBgSize() {
        return 24; // Events_bg[24]
    }

    @Override
    public PlayerCharacter getPlayerCharacter() {
        // Defaults to Sonic+Tails until character selection screen is implemented.
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    @Override
    protected void onInitLevel(int zone, int act) {
        bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);
        if (bootstrap.isSkipIntro()) {
            LOG.info("S3K bootstrap: skipping intro for zone " + zone + " act " + act);
        }

        // Create zone-specific event handlers after bootstrap resolution
        if (zone == Sonic3kZoneIds.ZONE_AIZ) {
            aizEvents = new Sonic3kAIZEvents(bootstrap);
            aizEvents.init(act);
        } else {
            aizEvents = null;
        }
    }

    @Override
    protected void onUpdate() {
        // ROM: ScreenEvents dispatches to both FG and BG handlers each frame.
        // Boss_flag gates FG events during boss fights.
        if (aizEvents != null && currentZone == Sonic3kZoneIds.ZONE_AIZ) {
            aizEvents.update(currentAct, frameCounter);
        }
    }

    // =========================================================================
    // S3K-specific accessors
    // =========================================================================

    public Sonic3kLoadBootstrap getBootstrap() {
        return bootstrap;
    }

    /** Returns the AIZ zone events handler, or null if not in AIZ. */
    public Sonic3kAIZEvents getAizEvents() {
        return aizEvents;
    }

    /**
     * Resets mutable state including static/global state in AIZ event helpers.
     * Extends the base {@link AbstractLevelEventManager#resetState()} to also
     * clear fire wall handoff, tree reveal counter, and intro phase state that
     * would otherwise leak across level loads and test iterations.
     */
    @Override
    public void resetState() {
        super.resetState();
        Sonic3kAIZEvents.resetGlobalState();
        AizHollowTreeObjectInstance.resetTreeRevealCounter();
        AizPlaneIntroInstance.resetIntroPhaseState();
    }

    public static synchronized Sonic3kLevelEventManager getInstance() {
        if (instance == null) {
            instance = new Sonic3kLevelEventManager();
        }
        return instance;
    }
}
