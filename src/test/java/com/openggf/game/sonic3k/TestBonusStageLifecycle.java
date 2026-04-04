package com.openggf.game.sonic3k;

import com.openggf.game.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestBonusStageLifecycle {

    @Test
    void testSelectBonusStage_ringFormula() {
        var coordinator = new Sonic3kBonusStageCoordinator();
        assertEquals(BonusStageType.NONE, coordinator.selectBonusStage(19));
        assertEquals(BonusStageType.GUMBALL, coordinator.selectBonusStage(20));
        assertEquals(BonusStageType.GUMBALL, coordinator.selectBonusStage(34));
        assertEquals(BonusStageType.GLOWING_SPHERE, coordinator.selectBonusStage(35));
        assertEquals(BonusStageType.GLOWING_SPHERE, coordinator.selectBonusStage(49));
        assertEquals(BonusStageType.SLOT_MACHINE, coordinator.selectBonusStage(50));
        assertEquals(BonusStageType.SLOT_MACHINE, coordinator.selectBonusStage(64));
        assertEquals(BonusStageType.GUMBALL, coordinator.selectBonusStage(65));
    }

    @Test
    void testZoneIdMapping() {
        var coordinator = new Sonic3kBonusStageCoordinator();
        assertEquals(0x1300, coordinator.getZoneId(BonusStageType.GUMBALL));
        assertEquals(0x1400, coordinator.getZoneId(BonusStageType.GLOWING_SPHERE));
        assertEquals(0x1500, coordinator.getZoneId(BonusStageType.SLOT_MACHINE));
        assertEquals(-1, coordinator.getZoneId(BonusStageType.NONE));
    }

    @Test
    void testMusicIdMapping() {
        var coordinator = new Sonic3kBonusStageCoordinator();
        assertEquals(0x1E, coordinator.getMusicId(BonusStageType.GUMBALL));
        assertEquals(0x1F, coordinator.getMusicId(BonusStageType.GLOWING_SPHERE));
        assertEquals(0x20, coordinator.getMusicId(BonusStageType.SLOT_MACHINE));
    }

    @Test
    void testEntryExitLifecycle() {
        var coordinator = new Sonic3kBonusStageCoordinator();
        var savedState = new BonusStageState(
                0x0001, 0x0001, 50, 0, 1, 0,
                4, 0,
                0x100, 0x200, 0x80, 0x100,
                (byte) 0x0C, (byte) 0x0E, 0x300
        );

        coordinator.onEnter(BonusStageType.GUMBALL, savedState);
        assertEquals(BonusStageType.GUMBALL, coordinator.getActiveType());
        assertFalse(coordinator.isStageComplete());
        assertSame(savedState, coordinator.getSavedState());

        coordinator.addRings(30);
        coordinator.addRings(20);

        coordinator.requestExit();
        assertTrue(coordinator.isStageComplete());

        var rewards = coordinator.getRewards();
        assertEquals(50, rewards.rings());

        coordinator.onExit();
        assertEquals(BonusStageType.NONE, coordinator.getActiveType());
        assertFalse(coordinator.isStageComplete());
    }

    @Test
    void testNoOpProvider() {
        var noop = NoOpBonusStageProvider.INSTANCE;
        assertFalse(noop.hasBonusStages());
        assertEquals(BonusStageType.NONE, noop.selectBonusStage(100));
        assertFalse(noop.isStageComplete());
        assertNull(noop.getSavedState());
    }
}
