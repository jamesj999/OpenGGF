package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic sonic;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
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
