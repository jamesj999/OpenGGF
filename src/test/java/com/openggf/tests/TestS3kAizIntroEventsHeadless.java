package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizIntroEventsHeadless {
    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;
    private static final short AIZ1_INTRO_CENTRE_X = 0x40;
    private static final short AIZ1_INTRO_CENTRE_Y = 0x420;
    private static final int MAX_FRAMES = 4000;

    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic sonic;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(
                SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(
                SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sonic = (Sonic) fixture.sprite();
        sonic.setCentreX(AIZ1_INTRO_CENTRE_X);
        sonic.setCentreY(AIZ1_INTRO_CENTRE_Y);
        fixture.camera().updatePosition(true);

        LevelEventProvider levelEventProvider = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEventProvider != null) {
            levelEventProvider.initLevel(ZONE_AIZ, ACT_1);
        }
        GameServices.level().getObjectManager().reset(0);
    }

    @Test
    void aizIntroSidekickBootstrapParksTailsAtDormantMarkerUntilResizeRelease() {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        controller.setInitialState(SidekickCpuController.State.INIT);
        fixture.stepFrame(false, false, false, false, false);

        assertEquals(0x0020, tails.getCentreX() & 0xFFFF,
                "Trace frame 289 keeps AIZ intro Tails at Player_1-$20 before loc_13A10 parks him");
        assertEquals(0x0424, tails.getCentreY() & 0xFFFF,
                "Trace frame 289 keeps AIZ intro Tails at Player_1+4 before loc_13A10 parks him");
        assertFalse(tails.getAir(), "Trace frame 289 still has Player_2 on the ground");

        fixture.stepFrame(false, false, false, false, false);

        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                "ROM loc_13A10/sub_13ECA parks AIZ intro Tails at x_pos=$7F00");
        assertEquals(0, tails.getCentreY() & 0xFFFF,
                "ROM loc_13A10/sub_13ECA parks AIZ intro Tails at y_pos=0");
        assertEquals(0, tails.getYSubpixelRaw() & 0xFFFF,
                "AIZ intro marker is reached before Tails accumulates native subpixel drift");
        assertTrue(tails.isObjectControlled(),
                "ROM writes object_control=$83 while AIZ intro Tails is dormant");
        assertTrue(tails.isControlLocked(),
                "Engine object-control model should keep dormant intro Tails out of normal control");
        assertTrue(tails.getAir(), "ROM sub_13ECA sets Status_InAir");

        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        fixture.camera().setX((short) 0x1308);
        aizEvents.update(ACT_1, fixture.frameCount());

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "ROM AIZ1_Resize loc_1C4C4 writes Tails_CPU_routine=2 at camera X >= $1308");
    }

    @Test
    void releasedAizIntroSidekickWaitsForRomLevelFrameCounterBeforeCatchUpWarp() throws Exception {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        sonic.setCentreX((short) 0x13CE);
        sonic.setCentreY((short) 0x0402);
        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0);
        tails.setAir(true);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        controller.releaseAizIntroDormantMarker();

        setLevelFrameCounter(0x02FF);
        controller.update(0x0300);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "ROM Tails_Catch_Up_Flying reads Level_frame_counter=$02FF here, so the $3F gate waits");
        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                "AIZ intro Tails should remain parked for trace frame $0420");
        assertEquals(0, tails.getCentreY() & 0xFFFF,
                "AIZ intro marker y_pos remains zero until the $0300 cadence frame");

        setLevelFrameCounter(0x0300);
        controller.update(0x0301);

        assertEquals(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                "ROM Tails_Catch_Up_Flying runs loc_13B50 when Level_frame_counter reaches $0300");
        assertEquals(0x13CE, tails.getCentreX() & 0xFFFF,
                "Catch-up warp copies Sonic x_pos on the ROM cadence frame");
        assertEquals(0x0342, tails.getCentreY() & 0xFFFF,
                "Catch-up warp copies Sonic y_pos-$C0 on the ROM cadence frame");
    }

    private static void setLevelFrameCounter(int value) throws Exception {
        Field frameCounter = GameServices.level().getClass().getDeclaredField("frameCounter");
        frameCounter.setAccessible(true);
        frameCounter.setInt(GameServices.level(), value);
    }

    @Test
    void introMainLevelHandoffPulsesEventsFg5BeforeNormalPhase() {
        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertNotNull(levelEvents, "Sonic3kLevelEventManager should exist");
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        assertNotNull(aizEvents, "AIZ events should be initialized");
        assertFalse(aizEvents.isEventsFg5(), "AIZ Events_fg_5 should start clear");

        boolean sawGameplayStart = false;
        boolean sawEventsPulse = false;
        boolean sawEventsClearAfterPulse = false;
        int pulseCameraX = -1;
        int pulseFrame = -1;

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            boolean holdRight = fixture.camera().isLevelStarted();
            fixture.stepFrame(false, false, false, holdRight, false);

            if (fixture.camera().isLevelStarted()) {
                sawGameplayStart = true;
            }

            if (!sawEventsPulse && aizEvents.isEventsFg5()) {
                sawEventsPulse = true;
                pulseFrame = frame;
                pulseCameraX = fixture.camera().getX();
            }

            if (sawEventsPulse && AizPlaneIntroInstance.isMainLevelPhaseActive() && !aizEvents.isEventsFg5()) {
                sawEventsClearAfterPulse = true;
                break;
            }
        }

        String diagnostics = "gameplayStart=" + sawGameplayStart
                + " pulse=" + sawEventsPulse
                + " pulseFrame=" + pulseFrame
                + " pulseCameraX=0x" + Integer.toHexString(pulseCameraX)
                + " cameraX=0x" + Integer.toHexString(fixture.camera().getX())
                + " levelStarted=" + fixture.camera().isLevelStarted()
                + " mainLevelPhaseActive=" + AizPlaneIntroInstance.isMainLevelPhaseActive()
                + " eventsFg5=" + aizEvents.isEventsFg5()
                + " sonicX=0x" + Integer.toHexString(sonic.getCentreX());

        assertTrue(sawGameplayStart, "AIZ intro never reached gameplay start. " + diagnostics);
        assertTrue(sawEventsPulse, "AIZ intro never raised Events_fg_5 at the main-level handoff. " + diagnostics);
        assertTrue(pulseCameraX >= 0x1400,
                "AIZ intro raised Events_fg_5 before the $1400 handoff seam. " + diagnostics);
        assertTrue(sawEventsClearAfterPulse,
                "AIZ intro never cleared Events_fg_5 after entering the normal main-level phase. " + diagnostics);
    }
}
