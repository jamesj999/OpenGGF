package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.LevelEventProvider;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1LoopManager;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;

/**
 * Sonic 1 implementation of dynamic level events.
 * ROM equivalent: DynamicLevelEvents (_inc/DynamicLevelEvents.asm)
 *
 * Each zone has its own event handler that adjusts camera boundaries
 * based on player/camera position. Act 3 boss zones use a state machine
 * pattern (eventRoutine incremented by 2, matching ROM behavior).
 *
 * The Camera class handles smooth boundary easing for the bottom boundary
 * (matching the ROM's +/-2px/frame behavior). Top, left, and right boundaries
 * are set immediately (no easing) to match the original.
 */
public class Sonic1LevelEventManager implements LevelEventProvider {
    private static Sonic1LevelEventManager instance;

    private int currentZone = -1;
    private int currentAct = -1;

    // Zone event handlers (one per zone, each owns its own eventRoutine)
    private final Sonic1GHZEvents ghzEvents;
    private final Sonic1LZEvents lzEvents;
    private final Sonic1MZEvents mzEvents;
    private final Sonic1SLZEvents slzEvents;
    private final Sonic1SYZEvents syzEvents;
    private final Sonic1SBZEvents sbzEvents;

    // Loop/plane switching manager
    private final Sonic1LoopManager loopManager = new Sonic1LoopManager();

    private Sonic1LevelEventManager() {
        Camera camera = Camera.getInstance();
        ghzEvents = new Sonic1GHZEvents(camera);
        lzEvents = new Sonic1LZEvents(camera);
        mzEvents = new Sonic1MZEvents(camera);
        slzEvents = new Sonic1SLZEvents(camera);
        syzEvents = new Sonic1SYZEvents(camera);
        sbzEvents = new Sonic1SBZEvents(camera);
    }

    @Override
    public void initLevel(int zone, int act) {
        this.currentZone = zone;
        this.currentAct = act;
        // Reset all zone handlers (only one is active at a time,
        // but reset all for clean state)
        ghzEvents.init();
        lzEvents.init();
        mzEvents.init();
        slzEvents.init();
        syzEvents.init();
        sbzEvents.init();
        loopManager.initLevel(zone, act);
    }

    @Override
    public void update() {
        if (currentZone < 0) {
            return;
        }
        // Dispatch to zone-specific event handler (ROM: DLE_Index)
        switch (currentZone) {
            case Sonic1Constants.ZONE_GHZ -> ghzEvents.update(currentAct);
            case Sonic1Constants.ZONE_LZ -> lzEvents.update(currentAct);
            case Sonic1Constants.ZONE_MZ -> mzEvents.update(currentAct);
            case Sonic1Constants.ZONE_SLZ -> slzEvents.update(currentAct);
            case Sonic1Constants.ZONE_SYZ -> syzEvents.update(currentAct);
            case Sonic1Constants.ZONE_SBZ -> sbzEvents.update(currentAct);
            // Zone 6 (ENDZ) = Final Zone in our engine
            // ROM treats FZ as SBZ act 2; our engine has it as zone 6
            case Sonic1Constants.ZONE_ENDZ -> sbzEvents.updateFZ();
            default -> { /* DLE_Ending: rts */ }
        }
    }

    /**
     * Get the current zone's event routine counter.
     * Used by lamppost save/restore (ROM: v_dle_routine).
     */
    public int getEventRoutine() {
        return getActiveHandler() != null ? getActiveHandler().getEventRoutine() : 0;
    }

    /**
     * Set the current zone's event routine counter.
     * Used by lamppost restore.
     */
    public void setEventRoutine(int routine) {
        var handler = getActiveHandler();
        if (handler != null) {
            handler.setEventRoutine(routine);
        }
    }

    private Sonic1ZoneEvents getActiveHandler() {
        return switch (currentZone) {
            case Sonic1Constants.ZONE_GHZ -> ghzEvents;
            case Sonic1Constants.ZONE_LZ -> lzEvents;
            case Sonic1Constants.ZONE_MZ -> mzEvents;
            case Sonic1Constants.ZONE_SLZ -> slzEvents;
            case Sonic1Constants.ZONE_SYZ -> syzEvents;
            case Sonic1Constants.ZONE_SBZ, Sonic1Constants.ZONE_ENDZ -> sbzEvents;
            default -> null;
        };
    }

    public Sonic1LoopManager getLoopManager() {
        return loopManager;
    }

    public static synchronized Sonic1LevelEventManager getInstance() {
        if (instance == null) {
            instance = new Sonic1LevelEventManager();
        }
        return instance;
    }
}
