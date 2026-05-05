package com.openggf.tests;

import com.openggf.game.session.EngineContext;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MGZTwistingLoopObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kMgzTwistingLoopObject {
    private static final int LOOP_X = 0x1200;
    private static final int LOOP_Y = 0x0600;

    @BeforeEach
    void setUp() {
        RuntimeManager.destroyCurrent();
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void mgzTwistingLoopDirectEntry_advancesDownwardOnFirstActiveFrame() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue(player.isObjectControlled(), "Loop should still own the player after the first active frame");
        assertTrue(player.getCentreY() > LOOP_Y,
                "Captured MGZ loop entry should advance downward on the first active frame");
    }

    @Test
    void mgzTwistingLoopDirectEntry_doesNotReleaseBeforeItsConfiguredThreshold() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x01, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        for (int frame = 1; frame <= 3; frame++) {
            loop.update(frame, player);
        }

        assertTrue(player.isObjectControlled(),
                "MGZ loop should still be carrying an on-foot entry before its configured release threshold");
    }

    @Test
    void mgzTwistingLoopRollingEntry_keepsRollingRadiiUntilLoopReleases() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x01, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();
        player.move((short) 0, (short) 0x80); // seed y_sub = $8000 without changing pixel Y
        player.setRolling(true);

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue(player.getRolling(),
                "MGZ loop should keep rolling state while the object is carrying the player");
        assertEquals(14, player.getYRadius(),
                "MGZ loop should keep using the rolling radius for spiral positioning while captured");
        assertTrue(player.getYSubpixelRaw() != 0,
                "MGZ loop should continue carrying subpixel progress while the player is captured");

        for (int frame = 2; frame <= 12 && player.isObjectControlled(); frame++) {
            loop.update(frame, player);
        }

        assertFalse(player.getRolling(),
                "MGZ loop should restore standing state in the shared release path");
        assertEquals(player.getStandYRadius(), player.getYRadius(),
                "MGZ loop should restore the standing radius in the shared release path");
    }

    @Test
    void mgzTwistingLoopNegativeEntrySpeed_keepsNegativeMinimumGroundSpeed() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();
        player.setGSpeed((short) -8);

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertEquals(-0x0400, player.getGSpeed(),
                "MGZ loop should preserve a negative entry direction when clamping to minimum speed");
        assertEquals(0x0400, player.getYSpeed() & 0xFFFF,
                "MGZ loop should still use the minimum downward carry speed");
    }

    @Test
    void mgzTwistingLoopCapture_keepsEntryWallAngleWhileCarried() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue((player.getAngle() & 0xFF) == 0x40,
                "MGZ loop should keep the entry wall angle while carrying the player");
    }

    @Test
    void mgzTwistingLoopOnFootRelease_doesNotUseCompensatedHandoff() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x00, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        loop.update(1, player); // immediate threshold release

        assertFalse(player.isObjectControlled(),
                "Plain MGZ direct entries should release immediately at the configured threshold");
        assertFalse(player.isStickToConvex(),
                "Plain MGZ direct entries should not inherit the compensated convex handoff");
        assertFalse(player.getAir(),
                "Plain MGZ direct entries should remain grounded on release");
    }

    private static TestablePlayableSprite createDirectEntryPlayer() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) (LOOP_X + 1));
        player.setCentreY((short) LOOP_Y);
        player.setAir(false);
        player.setAngle((byte) 0x40);
        player.setGSpeed((short) 0x0800);
        player.setYSpeed((short) 0x0200);
        player.setXSpeed((short) 0);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setOnObject(false);
        player.setRolling(false);
        player.setJumping(false);
        return player;
    }
}
