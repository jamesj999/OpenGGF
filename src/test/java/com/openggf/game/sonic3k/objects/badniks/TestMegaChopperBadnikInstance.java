package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.LevelGamestate;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestMegaChopperBadnikInstance {

    @Before
    public void setUp() throws Exception {
        SessionManager.clear();
        RuntimeManager.destroyCurrent();
        RuntimeManager.createGameplay(SessionManager.openGameplaySession(new Sonic3kGameModule()));

        Field levelStateField = RuntimeManager.getCurrent().getLevelManager().getClass().getDeclaredField("levelGamestate");
        levelStateField.setAccessible(true);
        levelStateField.set(RuntimeManager.getCurrent().getLevelManager(), new LevelGamestate());
    }

    @After
    public void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
    }

    @Test
    public void captureKeepsPlayerMobileAndDrainsOneRingAfterSixtyFrames() throws Exception {
        RecordingServices services = new RecordingServices();
        MegaChopperBadnikInstance megaChopper = new MegaChopperBadnikInstance(
                new ObjectSpawn(0x208, 0x180, Sonic3kObjectIds.MEGA_CHOPPER, 0, 0, false, 0));
        megaChopper.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x200, (short) 0x180);
        player.setRingCount(3);
        RuntimeManager.getCurrent().getCamera().setFocusedSprite(player);

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
    }

    @Test
    public void alternatingLeftRightInputReleasesCapturedPlayer() throws Exception {
        MegaChopperBadnikInstance megaChopper = new MegaChopperBadnikInstance(
                new ObjectSpawn(0x200, 0x180, Sonic3kObjectIds.MEGA_CHOPPER, 0, 0, false, 0));
        megaChopper.setServices(new RecordingServices());

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x200, (short) 0x180);
        player.setRingCount(10);
        RuntimeManager.getCurrent().getCamera().setFocusedSprite(player);

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
    }

    private static String readState(MegaChopperBadnikInstance megaChopper) throws Exception {
        Field field = MegaChopperBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return String.valueOf(field.get(megaChopper));
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> playedSfx = new ArrayList<>();

        @Override
        public void playSfx(int soundId) {
            playedSfx.add(soundId);
        }
    }
}
