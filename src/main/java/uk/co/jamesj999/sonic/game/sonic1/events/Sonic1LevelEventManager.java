package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.game.AbstractLevelEventManager;
import uk.co.jamesj999.sonic.game.PlayerCharacter;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1LoopManager;
import uk.co.jamesj999.sonic.game.sonic1.scroll.Sonic1ZoneConstants;

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
 *
 * Note: S1 delegates eventRoutine to per-zone handler classes rather than
 * using the base class's eventRoutineFg. Each zone handler owns its own
 * counter, which is needed because S1 zones can revert routines independently.
 */
public class Sonic1LevelEventManager extends AbstractLevelEventManager {
    private static Sonic1LevelEventManager instance;

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
        super();
        ghzEvents = new Sonic1GHZEvents(camera);
        lzEvents = new Sonic1LZEvents(camera);
        mzEvents = new Sonic1MZEvents(camera);
        slzEvents = new Sonic1SLZEvents(camera);
        syzEvents = new Sonic1SYZEvents(camera);
        sbzEvents = new Sonic1SBZEvents(camera);
    }

    // =========================================================================
    // AbstractLevelEventManager contract
    // =========================================================================

    @Override
    protected int getRoutineStride() {
        return 2;
    }

    @Override
    protected int getEventDataFgSize() {
        return 0;
    }

    @Override
    protected int getEventDataBgSize() {
        return 0;
    }

    @Override
    public PlayerCharacter getPlayerCharacter() {
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    @Override
    protected void onInitLevel(int zone, int act) {
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
    protected void onUpdate() {
        // Dispatch to zone-specific event handler (ROM: DLE_Index)
        // currentZone is the gameplay progression index from LevelManager
        switch (currentZone) {
            case Sonic1ZoneConstants.ZONE_GHZ -> ghzEvents.update(currentAct);
            case Sonic1ZoneConstants.ZONE_LZ -> lzEvents.update(currentAct);
            case Sonic1ZoneConstants.ZONE_MZ -> mzEvents.update(currentAct);
            case Sonic1ZoneConstants.ZONE_SLZ -> slzEvents.update(currentAct);
            case Sonic1ZoneConstants.ZONE_SYZ -> syzEvents.update(currentAct);
            case Sonic1ZoneConstants.ZONE_SBZ -> sbzEvents.update(currentAct);
            // Zone 6 (FZ) = Final Zone in our engine
            // ROM treats FZ as SBZ act 2; our engine has it as zone 6
            case Sonic1ZoneConstants.ZONE_FZ -> sbzEvents.updateFZ();
            default -> { /* DLE_Ending: rts */ }
        }
    }

    // =========================================================================
    // S1-specific accessors
    // =========================================================================

    /**
     * Get the current zone's event routine counter.
     * S1 delegates routine counters to per-zone handlers.
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
            case Sonic1ZoneConstants.ZONE_GHZ -> ghzEvents;
            case Sonic1ZoneConstants.ZONE_LZ -> lzEvents;
            case Sonic1ZoneConstants.ZONE_MZ -> mzEvents;
            case Sonic1ZoneConstants.ZONE_SLZ -> slzEvents;
            case Sonic1ZoneConstants.ZONE_SYZ -> syzEvents;
            case Sonic1ZoneConstants.ZONE_SBZ, Sonic1ZoneConstants.ZONE_FZ -> sbzEvents;
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
