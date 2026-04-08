package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusPlayer;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomCondition;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(RequiresRomCondition.class)
class TestS3kSlotBonusStageRuntime {

    private HeadlessTestFixture fixture;

    @AfterEach
    void tearDown() {
        fixture = null;
    }

    @Test
    void slotDeferredSetupReplacesMainPlayerAndBuildsLayoutGridInZone15() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x15, 0)
                .build();

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        fixture.runtime().setActiveBonusStageProvider(coordinator);
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());

        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);
        assertTrue(runtime.isInitialized());
        assertInstanceOf(S3kSlotBonusPlayer.class, GameServices.sprites().getSprite("sonic"));

        coordinator.onFrameUpdate();

        short[] pointGrid = runtime.activePointGridForTest();
        assertNotNull(pointGrid);
        assertEquals(16 * 16 * 2, pointGrid.length);
    }

    private static BonusStageState savedState() {
        return new BonusStageState(0x0001, 0x0001, 40, 0, 1, 0, 0, 0,
                0x460, 0x430, 0x400, 0x400, (byte) 0x0C, (byte) 0x0D, 0x600, 0L);
    }
}
