package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.LevelGamestate;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestMegaChopperBadnikInstance {

    @Test
    public void captureKeepsPlayerMobileAndDrainsOneRingAfterSixtyFrames() throws Exception {
        LevelManager levelManager = mock(LevelManager.class);
        LevelGamestate levelState = new LevelGamestate();
        when(levelManager.getLevelGamestate()).thenReturn(levelState);

        LevelManager original = installLevelManager(levelManager);
        try {
            RecordingServices services = new RecordingServices();
            MegaChopperBadnikInstance megaChopper = new MegaChopperBadnikInstance(
                    new ObjectSpawn(0x208, 0x180, Sonic3kObjectIds.MEGA_CHOPPER, 0, 0, false, 0));
            megaChopper.setServices(services);

            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x200, (short) 0x180);
            player.setRingCount(3);

            megaChopper.onTouchResponse(player, new TouchResponseResult(0x17, 0x20, 0x20, TouchCategory.SPECIAL), 0);
            megaChopper.update(0, player);

            assertFalse(player.isControlLocked());
            assertFalse(player.isObjectControlled());
            assertEquals("CARRY", readState(megaChopper));
            assertEquals(0x208, megaChopper.getX());
            assertEquals(0x180, megaChopper.getY());

            player.setCentreX((short) 0x218);
            player.setCentreY((short) 0x184);
            megaChopper.update(1, player);

            assertEquals(0x220, megaChopper.getX());
            assertEquals(0x184, megaChopper.getY());

            for (int frame = 2; frame <= 61; frame++) {
                megaChopper.update(frame, player);
            }

            assertEquals(2, player.getRingCount());
            assertTrue(services.playedSfx.contains(Sonic3kSfx.RING_RIGHT.id));
        } finally {
            restoreLevelManager(original);
        }
    }

    @Test
    public void alternatingLeftRightInputReleasesCapturedPlayer() throws Exception {
        LevelManager levelManager = mock(LevelManager.class);
        LevelGamestate levelState = new LevelGamestate();
        when(levelManager.getLevelGamestate()).thenReturn(levelState);

        LevelManager original = installLevelManager(levelManager);
        try {
            MegaChopperBadnikInstance megaChopper = new MegaChopperBadnikInstance(
                    new ObjectSpawn(0x200, 0x180, Sonic3kObjectIds.MEGA_CHOPPER, 0, 0, false, 0));
            megaChopper.setServices(new RecordingServices());

            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x200, (short) 0x180);
            player.setRingCount(10);

            megaChopper.onTouchResponse(player, new TouchResponseResult(0x17, 0x20, 0x20, TouchCategory.SPECIAL), 0);
            megaChopper.update(0, player);

            boolean[] leftInputs = {true, false, true, false, true, false};
            boolean[] rightInputs = {false, true, false, true, false, true};
            for (int i = 0; i < leftInputs.length; i++) {
                player.setDirectionalInputPressed(false, false, leftInputs[i], rightInputs[i]);
                megaChopper.update(i + 1, player);
            }

            assertFalse(player.isControlLocked());
            assertFalse(player.isObjectControlled());
            assertEquals("RELEASED", readState(megaChopper));
            assertTrue(megaChopper.getCollisionFlags() != 0);
        } finally {
            restoreLevelManager(original);
        }
    }

    private static String readState(MegaChopperBadnikInstance megaChopper) throws Exception {
        Field field = MegaChopperBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return String.valueOf(field.get(megaChopper));
    }

    private static LevelManager installLevelManager(LevelManager replacement) throws Exception {
        Field field = LevelManager.class.getDeclaredField("levelManager");
        field.setAccessible(true);
        LevelManager original = (LevelManager) field.get(null);
        field.set(null, replacement);
        return original;
    }

    private static void restoreLevelManager(LevelManager original) throws Exception {
        Field field = LevelManager.class.getDeclaredField("levelManager");
        field.setAccessible(true);
        field.set(null, original);
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> playedSfx = new ArrayList<>();

        @Override
        public void playSfx(int soundId) {
            playedSfx.add(soundId);
        }
    }
}
