package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class TestPachinkoFlipperObjectInstance {

    @Test
    public void heldJumpDoesNotLaunchImmediatelyOnContact() {
        PachinkoFlipperObjectInstance flipper = new PachinkoFlipperObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xE7, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getRolling()).thenReturn(false);
        when(player.getY()).thenReturn((short) 0x100);
        when(player.getX()).thenReturn((short) 0x100);
        when(player.getRollHeightAdjustment()).thenReturn((short) 10);
        when(player.isJumpPressed()).thenReturn(true, true, false);
        when(player.isJumpJustPressed()).thenReturn(false, false, false);

        flipper.setServices(new TestObjectServices());
        SolidContact standing = new SolidContact(true, false, false, false, false, 0);

        flipper.onSolidContact(player, standing, 0);
        flipper.update(0, player);
        flipper.onSolidContact(player, standing, 1);
        flipper.update(1, player);
        flipper.onSolidContact(player, standing, 2);

        verify(player, never()).setYSpeed(anyShort());
        verify(player, never()).setXSpeed(anyShort());
    }
}
