package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzMinibossArenaEntry {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void cameraAtThresholdLocksArenaAndSetsBossFlag() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);

        Sonic3kCNZEvents cnz = getCnzEvents();
        // Invoke the normal event update loop
        cnz.update(0, 0);

        assertTrue(cnz.isBossFlag(),  "Boss_flag must be set");
        assertTrue(cnz.isWallGrabSuppressed(),
                "Disable_wall_grab bit 7 must be set (wall-grab suppression)");
        assertEquals(Sonic3kCNZEvents.BG_BOSS_START, cnz.getBackgroundRoutine(),
                "BG routine must be BG_BOSS_START after threshold crossing");
    }

    @Test
    void arenaThresholdMatchesRom() {
        // The hard number: ROM sonic3k.asm:144824 reads `move.w #$31E0,d0`.
        // The scaffold previously held 0x3000; workstream D corrects it.
        assertEquals(0x31E0, Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
    }

    private static Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager events =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return events.getCnzEvents();
    }
}
