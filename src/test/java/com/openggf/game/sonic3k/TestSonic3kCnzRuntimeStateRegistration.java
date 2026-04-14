package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kCnzRuntimeStateRegistration {

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
    void initLevelInstallsCnzRuntimeState() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();

        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);

        assertTrue(GameServices.zoneRuntimeRegistry().currentAs(CnzZoneRuntimeState.class).isPresent());
        assertEquals(Sonic3kZoneIds.ZONE_CNZ, GameServices.zoneRuntimeRegistry().current().zoneIndex());
        assertEquals(0, GameServices.zoneRuntimeRegistry().current().actIndex());
        assertEquals("s3k", GameServices.zoneRuntimeRegistry().current().gameId());
    }
}
