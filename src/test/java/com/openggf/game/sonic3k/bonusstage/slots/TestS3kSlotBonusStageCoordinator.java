package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSlotBonusStageCoordinator {

    @Test
    void deferredSetupCreatesRuntimeOnlyForSlots() {
        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());

        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);
        assertTrue(runtime.isInitialized());
    }

    @Test
    void deferredSetupDoesNotCreateRuntimeForNonSlotStages() {
        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.GUMBALL, savedState());

        coordinator.onDeferredSetupComplete();

        assertNull(coordinator.activeSlotRuntimeForTest());
    }

    @Test
    void exitClearsActiveRuntime() {
        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);
        assertTrue(runtime.isInitialized());

        coordinator.onExit();

        assertFalse(runtime.isInitialized());
        assertNull(coordinator.activeSlotRuntimeForTest());
    }

    private static BonusStageState savedState() {
        return new BonusStageState(0x0001, 0x0001, 40, 0, 1, 0, 0, 0,
                0x460, 0x430, 0x400, 0x400, (byte) 0x0C, (byte) 0x0D, 0x600, 0L);
    }
}
