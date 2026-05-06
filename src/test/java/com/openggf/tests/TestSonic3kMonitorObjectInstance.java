package com.openggf.tests;

import com.openggf.game.RuntimeManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class TestSonic3kMonitorObjectInstance {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void noContactClearsStaleSidekickStandingBeforeMonitorBreakRelease() {
        Sonic3kMonitorObjectInstance monitor = new Sonic3kMonitorObjectInstance(
                new ObjectSpawn(0x840, 0x6E9, 0x01, 6, 0, false, 0));
        monitor.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return mock(ObjectManager.class);
            }
        });

        AbstractPlayableSprite sonic = new Tails("sonic", (short) 0x100, (short) 0x100);
        sonic.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        sonic.setYSpeed((short) 0x300);
        AbstractPlayableSprite tails = new Tails("tails", (short) 0x100, (short) 0x100);
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setOnObject(false);

        monitor.onSolidContact(tails, new SolidContact(true, false, false, false, false), 341);
        monitor.onSolidContactCleared(tails, 342);

        monitor.onTouchResponse(sonic, mock(TouchResponseResult.class), 342);

        assertFalse(tails.getAir(),
                "ROM no-contact cleanup clears the monitor's P2 standing bit before Obj_MonitorBreak releases players");
    }

}
