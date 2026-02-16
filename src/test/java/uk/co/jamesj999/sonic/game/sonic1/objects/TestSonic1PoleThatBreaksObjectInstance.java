package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1AnimationIds;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchCategory;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1PoleThatBreaksObjectInstance {

    private static final TouchResponseResult TOUCH_RESULT =
            new TouchResponseResult(0x21, 0x20, 0x20, TouchCategory.SPECIAL);

    @Test
    public void grabsPlayerFromRightAndLocksToHangState() {
        Sonic1PoleThatBreaksObjectInstance pole = createPole(200, 320, 4);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 240);
        player.setCentreY((short) 320);

        pole.onTouchResponse(player, TOUCH_RESULT, 1);
        pole.update(1, player);

        assertTrue(player.isObjectControlled());
        assertEquals(200 + 0x14, player.getCentreX());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(Sonic1AnimationIds.HANG, player.getAnimationId());
        assertEquals(Direction.RIGHT, player.getDirection());
    }

    @Test
    public void subtypeZeroNeverAutoBreaksWhileGrabbed() {
        Sonic1PoleThatBreaksObjectInstance pole = createPole(200, 320, 0);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 240);
        player.setCentreY((short) 320);

        pole.onTouchResponse(player, TOUCH_RESULT, 1);
        pole.update(1, player);
        for (int i = 2; i <= 240; i++) {
            pole.update(i, player);
        }

        assertTrue(player.isObjectControlled());
        assertEquals(0x61, pole.getCollisionFlags());
    }

    @Test
    public void subtypeOneBreaksNextFrameAfterGrab() throws Exception {
        Sonic1PoleThatBreaksObjectInstance pole = createPole(200, 320, 1);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 240);
        player.setCentreY((short) 320);

        pole.onTouchResponse(player, TOUCH_RESULT, 1);
        pole.update(1, player); // grab
        pole.update(2, player); // timer 1 -> 0, break + release

        assertFalse(player.isObjectControlled());
        assertEquals(0, pole.getCollisionFlags());
        assertEquals(1, getPrivateInt(pole, "mappingFrame"));
    }

    @Test
    public void jumpPressReleasesPole() {
        Sonic1PoleThatBreaksObjectInstance pole = createPole(200, 320, 0);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 240);
        player.setCentreY((short) 320);

        pole.onTouchResponse(player, TOUCH_RESULT, 1);
        pole.update(1, player); // grab
        player.setJumpInputPressed(true);
        pole.update(2, player); // edge-trigger release

        assertFalse(player.isObjectControlled());
        assertEquals(0, pole.getCollisionFlags());
    }

    @Test
    public void upDownInputClampsToRomRange() {
        Sonic1PoleThatBreaksObjectInstance pole = createPole(200, 320, 0);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 240);
        player.setCentreY((short) 320);

        pole.onTouchResponse(player, TOUCH_RESULT, 1);
        pole.update(1, player); // grab

        player.setDirectionalInputPressed(true, false, false, false);
        for (int i = 2; i <= 80; i++) {
            pole.update(i, player);
        }
        assertEquals(320 - 0x18, player.getCentreY());

        player.setDirectionalInputPressed(false, true, false, false);
        for (int i = 81; i <= 180; i++) {
            pole.update(i, player);
        }
        assertEquals(320 + 0x0C, player.getCentreY());
    }

    private static Sonic1PoleThatBreaksObjectInstance createPole(int x, int y, int subtype) {
        return new Sonic1PoleThatBreaksObjectInstance(
                new ObjectSpawn(x, y, 0x0B, subtype, 0, false, 0));
    }

    private static int getPrivateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 0;
            runHeight = 0;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
            // No-op for tests.
        }
    }
}
