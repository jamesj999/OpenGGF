package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
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
        when(player.isObjectControlled()).thenReturn(false);

        trap.setServices(new TestObjectServices());
        trap.update(0, player);

        verify(player).setObjectControlled(true);
        verify(player).setControlLocked(true);
        verify(player).setXSpeed((short) 0);
        verify(player).setOnObject(false);
        verify(player).setCentreY((short) 0xF30);
    }

    @Test
    public void mainCharacterEscapingOutTopRequestsImmediateExit() {
        PachinkoEnergyTrapObjectInstance trap = new PachinkoEnergyTrapObjectInstance(
                new ObjectSpawn(0x78, 0xF30, 0xE8, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getCentreY()).thenReturn((short) -0x21);
        when(player.isObjectControlled()).thenReturn(false);

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
        verify(player, never()).setCentreY(anyShort());
    }

    @Test
    public void trapRisesUntilMainPlayerCaptureThenStopsRising() {
        PachinkoEnergyTrapObjectInstance trap = new PachinkoEnergyTrapObjectInstance(
                new ObjectSpawn(0x78, 0xF30, 0xE8, 0, 0, false, 0));
        AtomicInteger playerY = new AtomicInteger(0x2000);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getCentreY()).thenAnswer(invocation -> (short) playerY.get());
        when(player.isObjectControlled()).thenReturn(false);

        trap.setServices(new TestObjectServices());

        for (int frame = 0; frame <= 240; frame++) {
            trap.update(frame, player);
        }
        assertEquals(0xF2F, trap.getY());

        playerY.set(trap.getY());
        trap.update(241, player);
        int yAfterCapture = trap.getY();

        for (int frame = 242; frame < 260; frame++) {
            trap.update(frame, player);
        }
        assertEquals(yAfterCapture, trap.getY());
    }
}
