package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;
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
    void bootstrapReplacesTailsMainCharacterAtRawPositionTransfersRendererStateAndRemovesSidekicks() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(SpriteArtSet.EMPTY);
        originalPlayer.setSpriteRenderer(renderer);
        originalPlayer.setMappingFrame(3);
        originalPlayer.setAnimationFrameCount(5);
        originalPlayer.setAnimationId(7);
        originalPlayer.setAnimationFrameIndex(2);
        originalPlayer.setAnimationTick(11);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        AbstractPlayableSprite sidekick = new Sonic("sonic_p2", (short) 0x420, (short) 0x430);
        sidekick.setCpuControlled(true);
        GameServices.sprites().addSprite(sidekick, "sonic");

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
        assertEquals((short) 0x460, slotPlayer.getX());
        assertEquals((short) 0x430, slotPlayer.getY());
        assertSame(renderer, slotPlayer.getSpriteRenderer());
        assertEquals(3, slotPlayer.getMappingFrame());
        assertEquals(5, slotPlayer.getAnimationFrameCount());
        assertEquals(7, slotPlayer.getAnimationId());
        assertEquals(2, slotPlayer.getAnimationFrameIndex());
        assertEquals(11, slotPlayer.getAnimationTick());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertNull(GameServices.sprites().getSprite("sonic_p2"));
        assertNotSame(originalPlayer, slotPlayer);
        assertSame(slotPlayer, GameServices.camera().getFocusedSprite());
        assertNotNull(runtime.activeLayoutForTest());
        assertEquals(32 * 32, runtime.activeLayoutForTest().length);

        runtime.shutdown();

        assertSame(sidekick, GameServices.sprites().getSprite("sonic_p2"));
        assertEquals(1, GameServices.sprites().getSidekicks().size());
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
    void runtimeUpdateDoesNotImmediatelyCaptureAndFreezeBootstrapPlayer() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        AbstractPlayableSprite slotPlayer = assertInstanceOf(
                AbstractPlayableSprite.class, GameServices.sprites().getSprite("tails"));
        runtime.update(0);

        assertFalse(slotPlayer.isControlLocked());
        assertFalse(slotPlayer.isObjectControlled());
        assertFalse(slotPlayer.getAir());
    }

    @Test
    void runtimeUpdateBuildsVisibleSemanticCellsForSlotLayout() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");

        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.update(0);

        assertNotNull(runtime.activeVisibleCellsForTest());
        assertFalse(runtime.activeVisibleCellsForTest().isEmpty());
        assertTrue(runtime.activeVisibleCellsForTest().stream().anyMatch(cell -> cell.cellId() == 1));
        assertTrue(runtime.activeVisibleCellsForTest().stream().anyMatch(cell -> cell.cellId() == 5));
        assertTrue(runtime.activeVisibleCellsForTest().stream().anyMatch(cell -> cell.cellId() == 7));
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
