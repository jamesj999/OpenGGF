package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kCnzAct1EventFlow {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void firstEventsFg5StartsFgRefresh_notActReload() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);
        events.setEventsFg5(true);

        events.update(0, 0);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine());
        assertFalse(events.isAct2TransitionRequested());
        assertFalse(events.isEventsFg5());
    }

    @Test
    void secondEventsFg5AtTransitionStageRequestsSeamlessActSwap() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_DO_TRANSITION);
        events.setEventsFg5(true);

        events.update(0, 1);

        assertTrue(events.isAct2TransitionRequested());
        assertEquals(0x0301, events.getPendingZoneActWord());
        assertEquals(-0x3000, events.getTransitionWorldOffsetX());
        assertEquals(0x0200, events.getTransitionWorldOffsetY());
    }

    @Test
    void act2KnucklesEntryStartsTeleporterRoute_notModeOnly() {
        Sonic3kCNZEvents events = initCnzEvents(1);

        events.beginKnucklesTeleporterRoute();

        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine());
        assertTrue(events.isKnucklesTeleporterRouteActive());
        assertEquals(0x4750, events.getCameraMinXClamp());
        assertEquals(0x48E0, events.getCameraMaxXClamp());
    }

    private Sonic3kCNZEvents initCnzEvents(int act) {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, act);
        return manager.getCnzEvents();
    }
}
