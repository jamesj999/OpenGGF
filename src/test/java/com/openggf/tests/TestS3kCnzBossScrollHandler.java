package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlCnz;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kCnzBossScrollHandler {

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
    void normalCnzDeformPublishesBothAnimatedTileInputs() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);

        SwScrlCnz handler = new SwScrlCnz();
        int[] hscroll = new int[224];

        handler.update(hscroll, 0x2000, 0x0300, 0, 0);

        CnzZoneRuntimeState state = GameServices.zoneRuntimeRegistry()
                .currentAs(CnzZoneRuntimeState.class)
                .orElseThrow();

        assertEquals(0x0A00, state.deformPhaseBgX());
        assertEquals(0x0E00, state.publishedBgCameraX());
    }
}
