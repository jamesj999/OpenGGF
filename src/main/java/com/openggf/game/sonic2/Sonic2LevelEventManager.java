package com.openggf.game.sonic2;

import com.openggf.game.sonic2.events.*;
import com.openggf.game.sonic2.runtime.CnzRuntimeState;
import com.openggf.game.sonic2.runtime.CnzRuntimeStateView;
import com.openggf.game.sonic2.runtime.HtzRuntimeState;
import com.openggf.game.sonic2.runtime.HtzRuntimeStateView;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameRuntime;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RuntimeManager;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.zone.NoOpZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;

import java.util.logging.Logger;

/**
 * Sonic 2 implementation of dynamic level events.
 * ROM equivalent: RunDynamicLevelEvents (s2.asm:20297-20340)
 *
 * This system allows levels to dynamically adjust camera boundaries
 * based on player position, triggering boss arenas, vertical section
 * transitions, and other gameplay sequences.
 *
 * Each zone has its own event handler dispatched via the zone index.
 * Zone-specific logic is delegated to per-zone handler classes in
 * the {@code events} subpackage, following the S1 pattern.
 */
public class Sonic2LevelEventManager extends AbstractLevelEventManager {
    // Zone constants (matches Sonic2ZoneRegistry ordering: game progression, 0-based)
    public static final int ZONE_EHZ = 0;
    public static final int ZONE_CPZ = 1;
    public static final int ZONE_ARZ = 2;
    public static final int ZONE_CNZ = 3;
    public static final int ZONE_HTZ = 4;
    public static final int ZONE_MCZ = 5;
    public static final int ZONE_OOZ = 6;
    public static final int ZONE_MTZ = 7;
    public static final int ZONE_SCZ = 8;
    public static final int ZONE_WFZ = 9;
    public static final int ZONE_DEZ = 10;

    private static final Logger LOGGER = Logger.getLogger(Sonic2LevelEventManager.class.getName());

    /** Cached player character resolved from config (lazy init). */
    private PlayerCharacter resolvedPlayerCharacter;

    // Zone event handlers (one per zone, each owns its own eventRoutine)
    private final Sonic2EHZEvents ehzEvents;
    private final Sonic2CPZEvents cpzEvents;
    private final Sonic2HTZEvents htzEvents;
    private final Sonic2MCZEvents mczEvents;
    private final Sonic2ARZEvents arzEvents;
    private final Sonic2CNZEvents cnzEvents;
    private final Sonic2OOZEvents oozEvents;
    private final Sonic2MTZEvents mtzEvents;
    private final Sonic2WFZEvents wfzEvents;
    private final Sonic2DEZEvents dezEvents;
    private final Sonic2SCZEvents sczEvents;

    public Sonic2LevelEventManager() {
        super();
        ehzEvents = new Sonic2EHZEvents();
        cpzEvents = new Sonic2CPZEvents();
        htzEvents = new Sonic2HTZEvents();
        mczEvents = new Sonic2MCZEvents();
        arzEvents = new Sonic2ARZEvents();
        cnzEvents = new Sonic2CNZEvents();
        oozEvents = new Sonic2OOZEvents();
        mtzEvents = new Sonic2MTZEvents();
        wfzEvents = new Sonic2WFZEvents();
        dezEvents = new Sonic2DEZEvents();
        sczEvents = new Sonic2SCZEvents();
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
        return 6;
    }

    @Override
    protected int getEventDataBgSize() {
        return 0;
    }

    @Override
    public PlayerCharacter getPlayerCharacter() {
        if (resolvedPlayerCharacter == null) {
            SonicConfigurationService config = GameServices.configuration();
            resolvedPlayerCharacter = config != null
                    ? ActiveGameplayTeamResolver.resolvePlayerCharacter(config)
                    : PlayerCharacter.SONIC_AND_TAILS;
        }
        return resolvedPlayerCharacter;
    }

    @Override
    protected void onInitLevel(int zone, int act) {
        Sonic2ZoneEvents handler = getActiveHandler();
        if (handler != null) {
            handler.init(act);
        }
        GameRuntime runtime = RuntimeManager.getActiveRuntime();
        if (runtime == null) {
            return;
        }
        ZoneRuntimeRegistry registry = runtime.getZoneRuntimeRegistry();
        if (zone == ZONE_HTZ) {
            installOwnedRuntimeState(registry, new HtzRuntimeStateView(zone, act, htzEvents));
        } else if (registry.currentAs(HtzRuntimeState.class).isPresent()) {
            registry.clear();
        }
        if (zone == ZONE_CNZ) {
            installOwnedRuntimeState(registry, new CnzRuntimeStateView(zone, act, cnzEvents));
        } else if (registry.currentAs(CnzRuntimeState.class).isPresent()) {
            registry.clear();
        }
    }

    private static void installOwnedRuntimeState(ZoneRuntimeRegistry registry, ZoneRuntimeState state) {
        if (registry.current() == NoOpZoneRuntimeState.INSTANCE || isOwnedSonic2RuntimeState(registry.current())) {
            registry.install(state);
        }
    }

    private static boolean isOwnedSonic2RuntimeState(ZoneRuntimeState state) {
        return state instanceof HtzRuntimeState || state instanceof CnzRuntimeState;
    }

    @Override
    protected void onUpdate() {
        // Dispatch to zone-specific event handler
        Sonic2ZoneEvents handler = getActiveHandler();
        if (handler != null) {
            handler.update(currentAct, frameCounter);
        }
    }

    // =========================================================================
    // Zone handler dispatch
    // =========================================================================

    private Sonic2ZoneEvents getActiveHandler() {
        return switch (currentZone) {
            case ZONE_EHZ -> ehzEvents;
            case ZONE_CPZ -> cpzEvents;
            case ZONE_HTZ -> htzEvents;
            case ZONE_MCZ -> mczEvents;
            case ZONE_ARZ -> arzEvents;
            case ZONE_CNZ -> cnzEvents;
            case ZONE_OOZ -> oozEvents;
            case ZONE_MTZ -> mtzEvents;
            case ZONE_WFZ -> wfzEvents;
            case ZONE_DEZ -> dezEvents;
            case ZONE_SCZ -> sczEvents;
            default -> null;
        };
    }

    // =========================================================================
    // Public API - event routine delegation
    // =========================================================================

    /**
     * Get the current zone's event routine counter.
     * S2 delegates routine counters to per-zone handlers.
     */
    public int getEventRoutine() {
        Sonic2ZoneEvents handler = getActiveHandler();
        return handler != null ? handler.getEventRoutine() : 0;
    }

    /**
     * Set the current zone's event routine counter.
     */
    public void setEventRoutine(int routine) {
        Sonic2ZoneEvents handler = getActiveHandler();
        if (handler != null) {
            handler.setEventRoutine(routine);
        }
    }

}
