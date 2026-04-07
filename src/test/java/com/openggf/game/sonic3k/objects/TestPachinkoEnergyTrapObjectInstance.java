package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestPachinkoEnergyTrapObjectInstance {

    @Test
    public void captureLocksHorizontalMovement() {
        PachinkoEnergyTrapObjectInstance trap = new PachinkoEnergyTrapObjectInstance(
                new ObjectSpawn(0x78, 0xF30, 0xE8, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getCentreY()).thenReturn((short) 0xF30);

        trap.setServices(new TestObjectServices());
        trap.update(0, player);

        verify(player).setObjectControlled(true);
        verify(player).setControlLocked(true);
        verify(player).setXSpeed((short) 0);
        verify(player).setOnObject(false);
    }

    @Test
    public void mainCharacterEscapingOutTopRequestsImmediateExit() {
        PachinkoEnergyTrapObjectInstance trap = new PachinkoEnergyTrapObjectInstance(
                new ObjectSpawn(0x78, 0xF30, 0xE8, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getCentreY()).thenReturn((short) -0x21);
        when(player.getY()).thenReturn((short) -0x21);

        boolean[] exitRequested = {false};
        TestObjectServices services = new TestObjectServices() {
            @Override
            public void requestBonusStageExit() {
                exitRequested[0] = true;
            }
        };

        trap.setServices(services);
        trap.update(0, player);

        assertTrue(exitRequested[0]);
    }
}
