package com.openggf.game.sonic1.credits;

import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1LzCreditsDemoReplay {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(
                SonicGame.SONIC_1,
                Sonic1CreditsDemoData.DEMO_ZONE[3],
                Sonic1CreditsDemoData.DEMO_ACT[3]);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
    }

    @Test
    public void lzCreditsDemoShouldNotHurtSonicWithinRomClipWindow() throws Exception {
        runLzReplay(0);
    }

    @Test
    public void lzCreditsDemoShouldNotHurtAfterRuntimeStyleFrozenPreroll() throws Exception {
        AbstractPlayableSprite player = fixture.sprite();
        setupLzCreditsStart(player);

        LevelManager levelManager = GameServices.level();
        if (levelManager.getObjectManager() != null) {
            levelManager.getObjectManager().reset(fixture.camera().getX());
        }
        if (levelManager.getRingManager() != null) {
            levelManager.getRingManager().reset(fixture.camera().getX());
        }

        GameServices.sprites().primePlayableVisualState();

        for (int i = 0; i < Sonic1CreditsDemoData.DEMO_LOAD_DELAY_FRAMES; i++) {
            levelManager.updateObjectPositionsWithoutTouches();
            levelManager.updateEndingDemoScene();
        }

        replayDemoAndAssertNoHurt(player);
    }

    private void runLzReplay(int prerollFrames) throws Exception {
        AbstractPlayableSprite player = fixture.sprite();
        setupLzCreditsStart(player);

        for (int i = 0; i < prerollFrames; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        replayDemoAndAssertNoHurt(player);
    }

    private void setupLzCreditsStart(AbstractPlayableSprite player) {
        player.setCentreX((short) Sonic1CreditsDemoData.LZ_LAMP_X);
        player.setCentreY((short) Sonic1CreditsDemoData.LZ_LAMP_Y);
        player.setRingCount(Sonic1CreditsDemoData.LZ_LAMP_RINGS);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(false);
        player.setForcedInputMask(0);

        fixture.camera().setX((short) Sonic1CreditsDemoData.LZ_LAMP_CAMERA_X);
        fixture.camera().setY((short) Sonic1CreditsDemoData.LZ_LAMP_CAMERA_Y);
        fixture.camera().setMaxY((short) Sonic1CreditsDemoData.LZ_LAMP_BOTTOM_BND);

        WaterSystem waterSystem = GameServices.water();
        int featureZone = GameServices.level().getFeatureZoneId();
        int featureAct = GameServices.level().getFeatureActId();
        waterSystem.setWaterLevelDirect(featureZone, featureAct, Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);
        waterSystem.setWaterLevelTarget(featureZone, featureAct, Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);

        ZoneFeatureProvider featureProvider = GameServices.level().getZoneFeatureProvider();
        if (featureProvider != null) {
            featureProvider.setWaterRoutine(Sonic1CreditsDemoData.LZ_LAMP_WATER_ROUTINE);
        }
    }

    private void replayDemoAndAssertNoHurt(AbstractPlayableSprite player) throws Exception {
        byte[] demoData = GameServices.rom().getRom().readBytes(
                Sonic1CreditsDemoData.DEMO_DATA_ADDR[3],
                Sonic1CreditsDemoData.DEMO_DATA_SIZE[3]);
        DemoInputPlayer demo = new DemoInputPlayer(demoData);

        for (int frame = 1; frame <= Sonic1CreditsDemoData.DEMO_TIMER[3]; frame++) {
            demo.advanceFrame();
            int mask = demo.getInputMask();
            fixture.stepFrame(
                    (mask & AbstractPlayableSprite.INPUT_UP) != 0,
                    (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                    (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);

            assertFalse("LZ credits demo hurt Sonic at frame " + frame
                            + " x=" + player.getCentreX()
                            + " y=" + player.getCentreY()
                            + " anim=" + player.getAnimationId()
                            + " rings=" + player.getRingCount(),
                    player.isHurt() || player.getDead());
        }
    }
}
