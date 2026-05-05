package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.MgzEndBossInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the MGZ Act 2 end-boss arena handoff.
 *
 * <p>ROM: {@code MGZ2_Resize} (sonic3k.asm:39343-39418) and
 * {@code Obj_MGZEndBoss} setup (sonic3k.asm:142715+).
 */
class TestSonic3kMgz2EndBossEvents {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        GameServices.configuration().resetToDefaults();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        RuntimeManager.createGameplay();
        GameServices.camera().setFocusedSprite(new TestablePlayableSprite("sonic", (short) 0x3A10, (short) 0x0680));
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
    }

    private AbstractPlayableSprite triggerBossTransitionWithTailsBelowLine(Sonic3kMGZEvents events) {
        events.triggerBossCollapseHandoff();
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        tails.setCentreY((short) 0x0780);
        runBossTransitionTimer(events);
        return tails;
    }

    private void runBossTransitionTimer(Sonic3kMGZEvents events) {
        for (int frame = 0; frame < 0x168; frame++) {
            events.update(1, frame);
        }
    }

    @Test
    void mgz2BossApproach_locksCameraYAndArenaMaxX() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        GameServices.camera().getFocusedSprite().setCentreY((short) 0x0668);
        camera.setX((short) 0x3A00);
        camera.setY((short) 0x0600);

        events.update(1, 0);

        assertEquals((short) 0x06A0, camera.getMinY());
        assertEquals((short) 0x06A0, camera.getMinYTarget());
        assertEquals((short) 0x06A0, camera.getMaxY());
        assertEquals((short) 0x06A0, camera.getMaxYTarget());
        assertEquals((short) 0x3C80, camera.getMaxX());
        assertEquals((short) 0x3C80, camera.getMaxXTarget());
    }

    @Test
    void mgz2BossApproachRetreat_restoresCameraTopBoundToZero() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        GameServices.camera().getFocusedSprite().setCentreY((short) 0x0668);
        camera.setX((short) 0x3A00);
        camera.setY((short) 0x0600);

        events.update(1, 0);
        camera.setX((short) 0x39FF);
        events.update(1, 1);

        assertEquals((short) 0, camera.getMinY(),
                "ROM move.l #$1000 restores Camera_min_Y_pos=0 and Camera_max_Y_pos=$1000");
        assertEquals((short) 0, camera.getMinYTarget());
        assertEquals((short) 0x1000, camera.getMaxY());
        assertEquals((short) 0x1000, camera.getMaxYTarget());
    }

    @Test
    void mgz2BossApproach_usesCameraBandNotPlayerY() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        GameServices.camera().getFocusedSprite().setCentreY((short) 0x06F8);
        camera.setX((short) 0x3A10);
        camera.setY((short) 0x0600);
        camera.setMaxX((short) 0x6000);
        camera.setMaxXTarget((short) 0x6000);

        events.update(1, 0);

        assertEquals((short) 0x3C80, camera.getMaxX(),
                "MGZ2_Resize gates on camera Y, not Sonic's centre Y");
        assertEquals((short) 0x3C80, camera.getMaxXTarget());
    }

    @Test
    void mgz2AfterSignpostLowerCameraBand_canContinuePastBossApproachCameraStop() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        GameServices.camera().getFocusedSprite().setCentreY((short) 0x06D0);
        camera.setX((short) 0x3A8C);
        camera.setY((short) 0x0700);
        camera.setMaxX((short) 0x6000);
        camera.setMaxXTarget((short) 0x6000);

        events.update(1, 0);

        assertEquals((short) 0x6000, camera.getMaxX(),
                "lower route after the signpost must not stop the camera at the pre-boss clamp");
        assertEquals(0, GameServices.gameState().getCurrentBossId());
    }

    @Test
    void mgz2BossSpawn_locksLeftEdgeAndMarksEndBossActive() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        GameServices.camera().getFocusedSprite().setCentreY((short) 0x0668);
        camera.setX((short) 0x3A00);
        camera.setY((short) 0x0600);
        events.update(1, 0);

        camera.setX((short) 0x3C80);
        events.update(1, 1);

        assertEquals((short) 0x3C80, camera.getMinX());
        assertEquals((short) 0x3C80, camera.getMinXTarget());
        assertEquals(Sonic3kObjectIds.MGZ_END_BOSS, GameServices.gameState().getCurrentBossId());
    }

    @Test
    void mgz2BossSpawn_insertsEndBossThroughObjectManager() throws Exception {
        ObjectManager objectManager = installObjectManager();
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        GameServices.camera().getFocusedSprite().setCentreY((short) 0x0668);
        camera.setX((short) 0x3A00);
        camera.setY((short) 0x0600);
        events.update(1, 0);

        camera.setX((short) 0x3C80);
        events.update(1, 1);

        ObjectInstance endBoss = objectManager.getActiveObjects().stream()
                .filter(MgzEndBossInstance.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals(Sonic3kObjectIds.MGZ_END_BOSS, endBoss.getSpawn().objectId(),
                "MGZ2 boss spawn should create the live Obj_MGZEndBoss object, not only mark currentBossId");
    }

    @Test
    void mgz2BossCollapseHandoff_usesRomPlayerModeForSonicAloneNotSpriteClass() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().setFocusedSprite(new TestablePlayableSprite("player1", (short) 0x3A10, (short) 0x0680));
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);

        events.triggerBossCollapseHandoff();

        assertEquals(1, GameServices.sprites().getSidekicks().size(),
                "Obj_MGZ2_BossTransition branches from Player_mode, not Player_1's Java class or sprite code");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        assertEquals("tails", GameServices.sprites().getSidekickCharacterName(tails));
        assertEquals("MGZ_RESCUE_WAIT", tails.getCpuController().getState().name());
    }

    @Test
    void mgz2BossCollapseHandoff_ignoresStaleNonMgzRuntimeStateForPlayerMode() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        Sonic3kHCZEvents staleHczEvents = new Sonic3kHCZEvents();
        staleHczEvents.init(0);
        GameServices.zoneRuntimeRegistry().install(
                new HczZoneRuntimeState(0, PlayerCharacter.KNUCKLES, staleHczEvents));
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);

        events.triggerBossCollapseHandoff();

        assertEquals(1, GameServices.sprites().getSidekicks().size(),
                "Obj_MGZ2_BossTransition reads global Player_mode; stale non-MGZ runtime state must not suppress Tails");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        assertEquals("tails", GameServices.sprites().getSidekickCharacterName(tails));
        assertEquals("MGZ_RESCUE_WAIT", tails.getCpuController().getState().name());
    }

    @Test
    void mgz2BossCollapseHandoff_leavesTailsFreeDuringRescueWait() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);

        events.triggerBossCollapseHandoff();

        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        assertFalse(tails.isObjectControlled(),
                "Obj_MGZ2_BossTransition puts Tails in CPU routine $12, which only clears Ctrl_2_logical");
        assertFalse(tails.isControlLocked(),
                "Routine $12 does not set object_control before the pickup routine starts");
    }

    @Test
    void mgz2BossCollapseHandoff_repositionsExistingTailsSidekick() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        Tails tails = new Tails("tails", (short) 0x0100, (short) 0x0100);
        tails.setDead(true);
        tails.setCpuControlled(true);
        GameServices.sprites().addSprite(tails, "tails");

        events.triggerBossCollapseHandoff();

        assertEquals(1, GameServices.sprites().getSidekicks().size());
        assertEquals((short) 0x3CC0, tails.getCentreX());
        assertEquals((short) 0x06FF, tails.getCentreY());
        assertTrue(tails.isCpuControlled());
        assertEquals("MGZ_RESCUE_WAIT", tails.getCpuController().getState().name());
    }

    @Test
    void mgz2BossCollapseHandoff_reusesExistingTailsEvenWithoutSidekickNameMetadata() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        Tails tails = new Tails("tails_p2", (short) 0x0100, (short) 0x0100);
        tails.setCpuControlled(true);
        tails.setCpuController(new SidekickCpuController(tails, camera.getFocusedSprite()));
        GameServices.sprites().addSprite(tails);

        events.triggerBossCollapseHandoff();

        assertEquals(1, GameServices.sprites().getSidekicks().size(),
                "The MGZ transition must take over an existing Tails sidekick instead of spawning a second Tails");
        assertEquals((short) 0x3CC0, tails.getCentreX());
        assertEquals((short) 0x06FF, tails.getCentreY());
        assertEquals("MGZ_RESCUE_WAIT", tails.getCpuController().getState().name());
    }

    @Test
    void mgz2BossTransition_clampsSonicAtTransitionHeightWhileWaitingForTailsCarry() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();
        sonic.setCentreY((short) 0x0760);
        sonic.setXSpeed((short) 0x0120);
        sonic.setYSpeed((short) 0x0340);
        sonic.setGSpeed((short) 0x0400);
        sonic.setSpindash(true);

        events.triggerBossCollapseHandoff();
        events.update(1, 0);

        assertEquals((short) 0x0700, sonic.getCentreY());
        assertEquals((short) 0, sonic.getXSpeed());
        assertEquals((short) 0, sonic.getYSpeed());
        assertEquals((short) 0, sonic.getGSpeed());
        assertEquals(false, sonic.getSpindash());
    }

    @Test
    void mgz2BossTransition_keepsCameraLockedWhileSonicWaitsForRescue() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0x6000);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 0x1000);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();
        sonic.setCentreX((short) 0x3A10);
        sonic.setCentreY((short) 0x0780);
        sonic.setAir(true);

        events.triggerBossCollapseHandoff();
        events.update(1, 0);
        camera.updatePosition();

        assertEquals((short) 0x3C80, camera.getX(),
                "Obj_MGZ2_BossTransition should hold the boss arena camera while Sonic waits below the screen");
        assertEquals((short) 0x0600, camera.getY(),
                "The rescue clamp should not let the camera chase Sonic's off-bottom position for one frame");
    }

    @Test
    void mgz2BossTransition_clampsTailsAloneAtTransitionHeight() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().setFocusedSprite(new Tails("tails_p1", (short) 0x3CC0, (short) 0x0780));
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite tails = camera.getFocusedSprite();
        tails.setCentreY((short) 0x0780);
        tails.setXSpeed((short) 0x0120);
        tails.setYSpeed((short) 0x0340);
        tails.setGSpeed((short) 0x0400);
        tails.setSpindash(true);

        events.triggerBossCollapseHandoff();
        events.update(1, 0);

        assertEquals(0, GameServices.sprites().getSidekicks().size(),
                "Player_mode 2 uses the Tails-alone transition path, not a spawned rescue sidekick");
        assertEquals((short) 0x0700, tails.getCentreY());
        assertEquals((short) 0, tails.getXSpeed());
        assertEquals((short) 0, tails.getYSpeed());
        assertEquals((short) 0, tails.getGSpeed());
        assertFalse(tails.getSpindash());
    }

    @Test
    void mgz2BossTransition_usesFocusedSonicForRescueEvenIfConfiguredModeIsStale() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().setFocusedSprite(new Sonic("sonic_p1", (short) 0x3CC0, (short) 0x0780));
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);

        events.triggerBossCollapseHandoff();

        assertEquals(1, GameServices.sprites().getSidekicks().size(),
                "The MGZ boss transition should spawn rescue Tails based on the actual focused Sonic, not stale config/session mode");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        assertEquals(SidekickCpuController.State.MGZ_RESCUE_WAIT, tails.getCpuController().getState());
    }

    @Test
    void mgz2BossTransition_clampsSonicAfterReleasedCarryUntilRegrab() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();

        AbstractPlayableSprite tails = triggerBossTransitionWithTailsBelowLine(events);
        SidekickCpuController controller = tails.getCpuController();
        controller.update(1);

        sonic.setJumpInputPressed(false);
        sonic.setJumpInputPressed(true);
        controller.update(2);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
        assertFalse(controller.isFlyingCarrying(),
                "Jump release clears Flying_carrying_Sonic_flag while leaving MGZ Tails in routine $18");

        sonic.setJumpInputPressed(false);
        sonic.setCentreY((short) 0x0780);
        sonic.setXSpeed((short) 0x0120);
        sonic.setYSpeed((short) 0x0340);
        sonic.setGSpeed((short) 0x0400);
        sonic.setSpindash(true);

        events.update(1, 0);

        assertEquals((short) 0x0700, sonic.getCentreY(),
                "Obj_MGZ2_BossTransition checks Flying_carrying_Sonic_flag, not the CPU routine number");
        assertEquals((short) 0, sonic.getXSpeed());
        assertEquals((short) 0, sonic.getYSpeed());
        assertEquals((short) 0, sonic.getGSpeed());
        assertFalse(sonic.getSpindash());
    }

    @Test
    void mgz2BossTransition_keepsActiveCarryWhenCarrierFallsBelowTransitionHeight() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);

        AbstractPlayableSprite tails = triggerBossTransitionWithTailsBelowLine(events);
        SidekickCpuController controller = tails.getCpuController();
        controller.update(1);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());

        tails.setCentreY((short) 0x0720);
        events.update(1, 0);

        assertEquals((short) 0x0720, tails.getCentreY(),
                "While Flying_carrying_Sonic_flag is set, Tails should stay in the active carry path");
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
        assertTrue(controller.isFlyingCarrying());
    }

    @Test
    void mgz2BossTransition_waitsForReleasedTailsToFallBelowTransitionHeightBeforeRearming() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();

        AbstractPlayableSprite tails = triggerBossTransitionWithTailsBelowLine(events);
        SidekickCpuController controller = tails.getCpuController();
        controller.update(1);
        assertTrue(controller.isFlyingCarrying());

        sonic.setJumpInputPressed(false);
        sonic.setJumpInputPressed(true);
        controller.update(2);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
        assertFalse(controller.isFlyingCarrying());

        sonic.setJumpInputPressed(false);
        sonic.setCentreX((short) 0x3CE0);
        sonic.setCentreY((short) 0x0780);
        sonic.setXSpeed((short) 0x0120);
        sonic.setYSpeed((short) 0x0340);
        sonic.setGSpeed((short) 0x0400);
        sonic.setSpindash(true);
        tails.setCentreX((short) 0x3C80);
        tails.setCentreY((short) 0x06D0);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);

        events.update(1, 0);

        assertEquals((short) 0x0700, sonic.getCentreY());
        assertEquals((short) 0x3C80, tails.getCentreX(),
                "Obj_MGZ2_BossTransition does not re-place Tails until Tails is below the transition y");
        assertEquals((short) 0x06D0, tails.getCentreY());
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
        assertFalse(controller.isFlyingCarrying());

        tails.setCentreY((short) 0x0720);
        events.update(1, 1);

        assertEquals((short) 0x3CE0, tails.getCentreX(),
                "Once Tails is below the transition y, routine $18 lets the object copy Sonic's current x before rearming");
        assertEquals((short) 0x0700, tails.getCentreY());
        assertEquals(SidekickCpuController.State.CARRY_INIT, controller.getState(),
                "Released MGZ rescue carry must be able to return to CPU routine $14 after the first pickup");
    }

    @Test
    void mgz2BossTransition_clearsSonicHurtRoutineWhenClampingForRescueRegrab() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();

        AbstractPlayableSprite tails = triggerBossTransitionWithTailsBelowLine(events);
        SidekickCpuController controller = tails.getCpuController();
        controller.update(1);
        assertTrue(controller.isFlyingCarrying());

        sonic.setHurt(true);
        controller.update(2);
        assertFalse(controller.isFlyingCarrying(),
                "Tails_Carry_Sonic clears Flying_carrying_Sonic_flag when Sonic is in routine >= 4");

        sonic.setCentreX((short) 0x3CE0);
        sonic.setCentreY((short) 0x0780);
        sonic.setXSpeed((short) 0x0120);
        sonic.setYSpeed((short) 0x0340);
        sonic.setGSpeed((short) 0x0400);
        tails.setCentreY((short) 0x0720);

        events.update(1, 0);
        controller.update(3);

        assertFalse(sonic.isHurt(),
                "Obj_MGZ2_BossTransition writes Player_1 routine=2 after clamping Sonic below the transition y");
        assertTrue(controller.isFlyingCarrying(),
                "Sonic must stay latched after routine=2 clears the hurt-state rejection in Tails_Carry_Sonic");
    }

    @Test
    void mgz2BossTransition_doesNotRestartAscentWhileTailsIsAlreadyCarryingSonic() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();

        AbstractPlayableSprite tails = triggerBossTransitionWithTailsBelowLine(events);
        SidekickCpuController controller = tails.getCpuController();
        controller.update(1);
        tails.setCentreY((short) 0x0690);
        controller.update(2);
        sonic.setDirectionalInputPressed(false, false, true, false);
        controller.update(3);
        assertTrue(controller.getInputLeft(),
                "At Camera_Y+$90, MGZ routine $18 should copy P1 left/right into Ctrl_2");

        tails.setCentreY((short) 0x0720);
        events.update(1, 4);

        sonic.setDirectionalInputPressed(false, false, true, false);
        controller.update(5);

        assertTrue(controller.getInputLeft(),
                "Once Flying_carrying_Sonic_flag is set, the transition object must not keep restarting routine $14 and suppress P1 steering");
    }

    @Test
    void mgz2BossTransition_usesCameraAnchoredXWhenCurrentTailsRoutineIsBelowCarryRoutine() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        AbstractPlayableSprite sonic = camera.getFocusedSprite();

        events.triggerBossCollapseHandoff();
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        runBossTransitionTimer(events);
        SidekickCpuController controller = tails.getCpuController();
        controller.setInitialState(SidekickCpuController.State.SPAWNING);
        tails.setCentreX((short) 0x4000);
        tails.setCentreY((short) 0x0720);

        sonic.setCentreX((short) 0x3CF0);
        sonic.setCentreY((short) 0x0780);
        sonic.setXSpeed((short) 0x0120);
        sonic.setYSpeed((short) 0x0340);
        sonic.setGSpeed((short) 0x0400);
        sonic.setSpindash(true);

        events.update(1, 0);

        assertEquals((short) 0x3CC0, tails.getCentreX(),
                "Obj_MGZ2_BossTransition only copies Sonic's x when Tails_CPU_routine is $14 or higher");
        assertEquals((short) 0x0700, tails.getCentreY());
        assertEquals(SidekickCpuController.State.CARRY_INIT, controller.getState());
    }

    @Test
    void mgz2BossCollapseHandoff_disablesPitDeathForRescueTransition() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameModuleRegistry.getCurrent().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_MGZ, 1);
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();

        manager.getMgzEvents().triggerBossCollapseHandoff();

        assertTrue(manager.interceptPitDeath(sonic),
                "Obj_MGZEndBoss floor impact sets Disable_death_plane before Obj_MGZ2_BossTransition runs");
    }

    @Test
    void mgzEndBossObjectIdCreatesEndBossInstance() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance instance = registry.create(new com.openggf.level.objects.ObjectSpawn(
                0x3D20, 0x0668, Sonic3kObjectIds.MGZ_END_BOSS, 0, 0, false, 0));

        assertInstanceOf(MgzEndBossInstance.class, instance,
                "MGZ_END_BOSS must be registered; otherwise level placement falls back to a placeholder");
    }

    @Test
    void mgz2BossTransition_startsTailsCarryAfterRomDelay() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);

        events.triggerBossCollapseHandoff();
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        tails.setCentreY((short) 0x0780);

        for (int frame = 0; frame < 0x168; frame++) {
            events.update(1, frame);
        }

        assertEquals(SidekickCpuController.State.CARRY_INIT, tails.getCpuController().getState(),
                "Obj_MGZ2_BossTransition switches Tails from CPU routine $12 to $14 after the $168-frame wait");
    }

    @Test
    void mgz2BossTransition_waitsForTailsToDescendPastTransitionYAfterDelay() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);

        events.triggerBossCollapseHandoff();
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        tails.setCentreY((short) 0x06D0);

        runBossTransitionTimer(events);

        assertFalse(tails.isObjectControlled(),
                "Routine $12 leaves object_control clear while Tails descends under normal flight/freespace physics");
        assertEquals((short) 0x06D0, tails.getCentreY());
        assertEquals(SidekickCpuController.State.MGZ_RESCUE_WAIT, tails.getCpuController().getState(),
                "The timeout alone is not enough; Obj_MGZ2_BossTransition also requires Tails below transition y");

        tails.setCentreY((short) 0x0701);
        events.update(1, 0);

        assertEquals((short) 0x0700, tails.getCentreY());
        assertEquals(SidekickCpuController.State.CARRY_INIT, tails.getCpuController().getState());
    }

    @Test
    void mgz2BossTransitionCarry_pulsesJumpNotRightInput() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);

        AbstractPlayableSprite tails = triggerBossTransitionWithTailsBelowLine(events);

        tails.getCpuController().update(7);

        assertEquals(SidekickCpuController.State.CARRYING, tails.getCpuController().getState());
        assertTrue(tails.getCpuController().getInputJump(),
                "MGZ rescue routine $16 pulses A/B/C every 8 frames so Tails keeps flying upward");
        assertTrue(tails.getCpuController().getInputJumpPress(),
                "Ctrl_2_logical includes the press bits, not just held bits");
        assertFalse(tails.getCpuController().getInputRight(),
                "MGZ rescue routine $16 does not reuse the CNZ carry's synthetic-right input");
    }

    private static ObjectManager installObjectManager() throws NoSuchFieldException, IllegalAccessException {
        ObjectManager objectManager = new ObjectManager(
                new ArrayList<>(),
                new Sonic3kObjectRegistry(),
                GameModuleRegistry.getCurrent().getPlaneSwitcherObjectId(),
                GameModuleRegistry.getCurrent().getPlaneSwitcherConfig(),
                null);
        Field field = GameServices.level().getClass().getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.level(), objectManager);
        return objectManager;
    }
}
