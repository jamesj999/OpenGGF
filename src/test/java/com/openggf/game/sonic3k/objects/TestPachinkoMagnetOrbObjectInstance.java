package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestPachinkoMagnetOrbObjectInstance {

    @Test
    public void capturesMainPlayerAndSidekickIndependently() {
        PachinkoMagnetOrbObjectInstance orb = new PachinkoMagnetOrbObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xEC, 0, 0, false, 0));
        AbstractPlayableSprite main = mockPlayerAt(0x100, 0x100);
        AbstractPlayableSprite sidekick = mockPlayerAt(0x108, 0x108);

        orb.setServices(new TestObjectServices().withSidekicks(List.of(sidekick)));
        orb.update(0, main);

        verify(main).setControlLocked(true);
        verify(sidekick).setControlLocked(true);
        verify(main).setObjectControlled(true);
        verify(sidekick).setObjectControlled(true);
        verify(main).setAnimationId(Sonic3kAnimationIds.ROLL);
        verify(sidekick).setAnimationId(Sonic3kAnimationIds.ROLL);
        verify(main, never()).setRolling(true);
        verify(sidekick, never()).setRolling(true);
    }

    @Test
    public void launchReleaseAppliesRollState() {
        PachinkoMagnetOrbObjectInstance orb = new PachinkoMagnetOrbObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xEC, 0, 0, false, 0));
        AbstractPlayableSprite main = mockPlayerAt(0x100, 0x100);
        when(main.isJumpJustPressed()).thenReturn(true);

        orb.setServices(new TestObjectServices());
        orb.update(0, main);
        orb.update(1, main);

        verify(main).setControlLocked(false);
        verify(main).setRolling(true);
        verify(main, atLeastOnce()).getRollHeightAdjustment();
        verify(main, atLeastOnce()).setAnimationId(Sonic3kAnimationIds.ROLL);
        verify(main, atLeastOnce()).setJumping(false);
        verify(main).setFlipAngle(0);
        verify(main).setDoubleJumpFlag(0);
    }

    @Test
    public void releaseStartsCooldownBeforeSameOrbCanRecapture() {
        PachinkoMagnetOrbObjectInstance orb = new PachinkoMagnetOrbObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xEC, 0, 0, false, 0));
        AbstractPlayableSprite main = mockPlayerAt(0x100, 0x100);
        when(main.isJumpJustPressed()).thenReturn(true, false);

        orb.setServices(new TestObjectServices());
        orb.update(0, main);
        orb.update(1, main);
        orb.update(2, main);

        verify(main, times(1)).setObjectControlled(true);
    }

    @Test
    public void heldJumpDoesNotReleaseUntilRepressed() {
        PachinkoMagnetOrbObjectInstance orb = new PachinkoMagnetOrbObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xEC, 0, 0, false, 0));
        AbstractPlayableSprite main = mockPlayerAt(0x100, 0x100);
        when(main.isJumpPressed()).thenReturn(true, true, false, true);
        when(main.isJumpJustPressed()).thenReturn(false, false, true);

        orb.setServices(new TestObjectServices());
        orb.update(0, main);
        orb.update(1, main);
        orb.update(2, main);
        orb.update(3, main);

        verify(main, times(1)).releaseFromObjectControl(3);
    }

    @Test
    public void offscreenCapturedSidekickReleasesWithoutHoverSfx() {
        PachinkoMagnetOrbObjectInstance orb = new PachinkoMagnetOrbObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xEC, 0, 0, false, 0));
        AbstractPlayableSprite main = mockPlayerAt(0x400, 0x400);
        AbstractPlayableSprite sidekick = mockPlayerAt(0x100, 0x100);
        Camera camera = mock(Camera.class);
        when(sidekick.isCpuControlled()).thenReturn(true);
        when(camera.isOnScreen(sidekick)).thenReturn(false);

        CountingObjectServices services = new CountingObjectServices();
        services.withCamera(camera);
        services.withSidekicks(List.of(sidekick));
        orb.setServices(services);

        orb.update(0, main);
        services.resetSfxCount();
        orb.update(16, main);

        verify(sidekick).releaseFromObjectControl(16);
        assertEquals(0, services.sfxCount);
    }

    private static AbstractPlayableSprite mockPlayerAt(int x, int y) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getX()).thenReturn((short) x);
        when(player.getY()).thenReturn((short) y);
        when(player.getCentreX()).thenReturn((short) (x + 10));
        when(player.getCentreY()).thenReturn((short) (y + 20));
        when(player.getWidth()).thenReturn(20);
        when(player.getHeight()).thenReturn(40);
        when(player.getRollHeightAdjustment()).thenReturn((short) 10);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getDead()).thenReturn(false);
        when(player.isHurt()).thenReturn(false);
        when(player.isObjectControlled()).thenReturn(false);
        when(player.isControlLocked()).thenReturn(false);
        when(player.isJumpPressed()).thenReturn(false);
        when(player.isJumpJustPressed()).thenReturn(false);
        when(player.isLeftPressed()).thenReturn(false);
        when(player.isRightPressed()).thenReturn(false);
        when(player.isCpuControlled()).thenReturn(false);
        return player;
    }

    private static final class CountingObjectServices extends TestObjectServices {
        private int sfxCount;

        @Override
        public void playSfx(int soundId) {
            sfxCount++;
        }

        private CountingObjectServices resetSfxCount() {
            sfxCount = 0;
            return this;
        }
    }
}
