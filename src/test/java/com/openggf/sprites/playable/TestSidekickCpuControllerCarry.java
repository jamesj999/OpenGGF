package com.openggf.sprites.playable;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hydration parity tests: confirm the new state-enum values are accepted by
 * {@link SidekickCpuController#hydrateFromRomCpuState}. We reuse the AIZ1
 * shared fixture because the hydration method does not depend on zone layout;
 * any level that registers a Tails sidekick is enough.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSidekickCpuControllerCarry {

    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private SidekickCpuController controller;

    @BeforeEach
    void setUp() {
        HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        controller = GameServices.sprites().getSidekicks().get(0).getCpuController();
    }

    @Test
    void hydrateAccepts0x0CCarryInit() {
        assertDoesNotThrow(() -> controller.hydrateFromRomCpuState(0x0C, 0, 0, 0, false));
        assertEquals(SidekickCpuController.State.CARRY_INIT, controller.getState());
    }

    @Test
    void hydrateAccepts0x0ECarrying() {
        controller.hydrateFromRomCpuState(0x0E, 0, 0, 0, false);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
    }

    @Test
    void hydrateAccepts0x20Carrying() {
        controller.hydrateFromRomCpuState(0x20, 0, 0, 0, false);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
    }
}
