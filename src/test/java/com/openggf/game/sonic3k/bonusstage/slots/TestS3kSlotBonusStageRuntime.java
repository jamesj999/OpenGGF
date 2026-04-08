package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusStageRuntime {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void bootstrapReplacesTailsMainCharacterWithTailsBasedSlotPlayerAtRawPositionAndTransfersCameraFocus() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(runtime.isInitialized());
        assertNotNull(runtime.activeSlotCageForTest());
        assertNotNull(runtime.activeSlotRingRewardForTest());
        assertNotNull(runtime.activeSlotSpikeRewardForTest());
        assertTrue(GameServices.sprites().getSprite("tails") instanceof S3kSlotBonusPlayer);

        AbstractPlayableSprite slotPlayer = assertInstanceOf(Tails.class, GameServices.sprites().getSprite("tails"));
        assertTrue(slotPlayer instanceof S3kSlotBonusPlayer);
        assertFalse(slotPlayer instanceof Sonic);
        assertEquals("tails", slotPlayer.getCode());
        assertEquals(originalPlayer.getCentreX(), slotPlayer.getX());
        assertEquals(originalPlayer.getCentreY(), slotPlayer.getY());
        assertEquals(originalPlayer.getCentreX(), runtime.activeSlotCageForTest().getSpawn().x());
        assertEquals(originalPlayer.getCentreY(), runtime.activeSlotCageForTest().getSpawn().y());
        assertNotSame(originalPlayer, slotPlayer);
        assertSame(slotPlayer, GameServices.camera().getFocusedSprite());
    }

    @Test
    void queuedRingRewardActivatesInsideRuntimeAndExpires() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        assertFalse(runtime.activeSlotRingRewardForTest().isActive());

        runtime.queueRingReward();
        for (int frame = 0; frame < 0x1A; frame++) {
            runtime.update(frame);
        }

        assertTrue(runtime.activeSlotRingRewardForTest().isDestroyed());
        assertFalse(runtime.activeSlotRingRewardForTest().isActive());
        assertSame(slotPlayer, GameServices.sprites().getSprite("tails"));
    }

    @Test
    void bootstrapPreservesLiveCollisionBitsOnSwappedSlotPlayer() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        originalPlayer.setTopSolidBit((byte) 0x02);
        originalPlayer.setLrbSolidBit((byte) 0x03);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        assertTrue(slotPlayer instanceof S3kSlotBonusPlayer);
        assertEquals((byte) 0x02, slotPlayer.getTopSolidBit());
        assertEquals((byte) 0x03, slotPlayer.getLrbSolidBit());
    }

    @Test
    void shutdownRestoresOriginalPlayerAndCameraFocus() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(GameServices.sprites().getSprite("tails") instanceof S3kSlotBonusPlayer);
        assertNotSame(originalPlayer, GameServices.sprites().getSprite("tails"));
        assertSame(GameServices.sprites().getSprite("tails"), GameServices.camera().getFocusedSprite());

        runtime.shutdown();

        assertSame(originalPlayer, GameServices.sprites().getSprite("tails"));
        assertSame(originalPlayer, GameServices.camera().getFocusedSprite());
        assertNull(runtime.activeSlotCageForTest());
        assertNull(runtime.activeSlotRingRewardForTest());
        assertNull(runtime.activeSlotSpikeRewardForTest());
    }

    @Test
    void shutdownRestoresOriginalPlayerOnBootstrapRuntimeAfterCurrentRuntimeRecreation() {
        GameRuntime bootstrapRuntime = RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        Tails originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertTrue(bootstrapRuntime.getSpriteManager().getSprite("tails") instanceof S3kSlotBonusPlayer);
        assertNotSame(originalPlayer, bootstrapRuntime.getSpriteManager().getSprite("tails"));
        assertSame(bootstrapRuntime.getSpriteManager().getSprite("tails"), bootstrapRuntime.getCamera().getFocusedSprite());

        GameRuntime recreatedRuntime = RuntimeManager.createGameplay();

        runtime.shutdown();

        assertSame(originalPlayer, bootstrapRuntime.getSpriteManager().getSprite("tails"));
        assertSame(originalPlayer, bootstrapRuntime.getCamera().getFocusedSprite());
        assertFalse(runtime.isInitialized());
        assertTrue(recreatedRuntime.getSpriteManager().getSprite("tails") == null);

        bootstrapRuntime.destroy();
    }

    @Test
    void bootstrapWithoutGameplayRuntimeDoesNotMarkInitialized() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertFalse(runtime.isInitialized());
    }
}
