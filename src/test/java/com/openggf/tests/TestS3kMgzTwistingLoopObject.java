package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MGZTwistingLoopObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kMgzTwistingLoopObject {
    private static final int LOOP_X = 0x1200;
    private static final int LOOP_Y = 0x0600;

    @BeforeEach
    void setUp() {
        RuntimeManager.destroyCurrent();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
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
    void mgzTwistingLoopDirectEntry_doesNotReleaseAfterOnlyAFewLoops() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x01, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        for (int frame = 1; frame <= 6; frame++) {
            loop.update(frame, player);
        }

        assertTrue(player.isObjectControlled(),
                "MGZ loop should still be carrying an on-foot entry after 6 active frames");
    }

    @Test
    void mgzTwistingLoopCapture_preservesRollingStateAndSubpixelProgress() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();
        player.move((short) 0, (short) 0x80); // seed y_sub = $8000 without changing pixel Y
        player.setRolling(true);

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue(player.getRolling(),
                "MGZ loop should preserve a rolling entry instead of forcing standing state");
        assertTrue(player.getYSubpixelRaw() != 0,
                "MGZ loop should continue carrying subpixel progress while the player is captured");
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
