package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.badniks.JawzBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestS3kJawzBadnik {

    @Test
    public void jawzInitializesVelocityTowardPlayerOnFirstVisibleFrame() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn(Short.valueOf((short) 80));
        when(player.getDead()).thenReturn(false);

        jawz.update(0, player);
        assertEquals("Jawz should not move on the initialization frame", 160, jawz.getX());

        jawz.update(1, player);
        assertEquals("Jawz should move toward the player on the next frame", 158, jawz.getX());
        assertEquals("Jawz should advance to the second animation frame after moving",
                1, readMappingFrame(jawz));
    }

    @Test
    public void jawzTracksRightWhenPlayerIsToTheRight() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn(Short.valueOf((short) 240));
        when(player.getDead()).thenReturn(false);

        jawz.update(0, player);
        jawz.update(1, player);

        assertEquals("Jawz should move right when the player is on the right", 162, jawz.getX());
    }

    private static int readMappingFrame(JawzBadnikInstance jawz) {
        try {
            Field field = jawz.getClass().getSuperclass().getDeclaredField("mappingFrame");
            field.setAccessible(true);
            return field.getInt(jawz);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read Jawz mapping frame", e);
        }
    }
}
