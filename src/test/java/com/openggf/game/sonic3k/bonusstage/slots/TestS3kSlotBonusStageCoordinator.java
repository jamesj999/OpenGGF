package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestS3kSlotBonusStageCoordinator {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void deferredSetupCreatesInitializedRuntimeOnlyForSlotsWhenLiveGameplayRuntimeExists() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());

        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);
        assertTrue(runtime.isInitialized());
        AbstractPlayableSprite swappedPlayer = assertInstanceOf(Tails.class, GameServices.sprites().getSprite("tails"));
        assertTrue(swappedPlayer instanceof S3kSlotBonusPlayer);
        assertNotSame(originalPlayer, swappedPlayer);
        assertSame(swappedPlayer, GameServices.camera().getFocusedSprite());
    }

    @Test
    void deferredSetupDoesNotCreateRuntimeForNonSlotStages() {
        RuntimeManager.createGameplay();
        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.GUMBALL, savedState());

        coordinator.onDeferredSetupComplete();

        assertNull(coordinator.activeSlotRuntimeForTest());
    }

    @Test
    void exitClearsActiveRuntime() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);
        assertTrue(runtime.isInitialized());

        coordinator.onExit();

        assertFalse(runtime.isInitialized());
        assertNull(coordinator.activeSlotRuntimeForTest());
        assertSame(originalPlayer, GameServices.sprites().getSprite("tails"));
        assertSame(originalPlayer, GameServices.camera().getFocusedSprite());
    }

    @Test
    void frameUpdateAdvancesSlotRuntimeFrameCounter() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);

        coordinator.onFrameUpdate();
        int firstFrame = runtime.lastFrameCounterForTest();
        coordinator.onFrameUpdate();
        int secondFrame = runtime.lastFrameCounterForTest();

        assertTrue(firstFrame >= 0);
        assertTrue(secondFrame > firstFrame);
    }

    @Test
    void slotRuntimeSeedsFrameCounterFromLiveLevelFrameCounter() throws Exception {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        Field frameCounterField = LevelManager.class.getDeclaredField("frameCounter");
        frameCounterField.setAccessible(true);
        frameCounterField.setInt(GameServices.level(), 0x35);

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);

        coordinator.onFrameUpdate();

        assertSame(GameServices.level(), GameServices.level());
        assertTrue(runtime.lastFrameCounterForTest() >= 0x35);
    }

    @Test
    void frameUpdateRequestsBonusStageExitWhenSlotRuntimeCompletes() throws Exception {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        coordinator.onDeferredSetupComplete();

        S3kSlotBonusStageRuntime runtime = coordinator.activeSlotRuntimeForTest();
        assertNotNull(runtime);

        Field exitTriggered = S3kSlotBonusStageRuntime.class.getDeclaredField("exitTriggered");
        exitTriggered.setAccessible(true);
        exitTriggered.setBoolean(runtime, true);

        coordinator.onFrameUpdate();

        assertTrue(coordinator.isStageComplete());
    }

    @Test
    void slotRuntimeUsesIntegratedLevelFrameUpdateAndOwnCameraStep() {
        RuntimeManager.createGameplay();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        AbstractPlayableSprite originalPlayer = new Tails("tails", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(originalPlayer);
        GameServices.camera().setFocusedSprite(originalPlayer);

        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        coordinator.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        coordinator.onDeferredSetupComplete();

        assertTrue(coordinator.updateDuringLevelFrame());
        assertTrue(coordinator.suppressesDefaultCameraStep());
    }

    private static BonusStageState savedState() {
        return new BonusStageState(0x0001, 0x0001, 40, 0, 1, 0, 0, 0,
                0x460, 0x430, 0x400, 0x400, (byte) 0x0C, (byte) 0x0D, 0x600, 0L);
    }
}


