package com.openggf.game.sonic3k;

import com.openggf.GameLoop;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.InstaShieldObjectInstance;
import com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestPachinkoTitleCardIntegration {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    @BeforeClass
    public static void configure() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @Test
    public void trapContinuesUpdatingAfterBonusTitleCardExit() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x00, 0)
                .build();

        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        BonusStageProvider provider = GameModuleRegistry.getCurrent().getBonusStageProvider();
        BonusStageState savedState = captureSavedState(fixture.sprite(), fixture.camera());

        Method doEnterBonusStage = GameLoop.class.getDeclaredMethod(
                "doEnterBonusStage", BonusStageProvider.class, BonusStageType.class, BonusStageState.class);
        doEnterBonusStage.setAccessible(true);
        doEnterBonusStage.invoke(loop, provider, BonusStageType.GLOWING_SPHERE, savedState);

        PachinkoEnergyTrapObjectInstance trap = null;
        int titleCardExitFrame = -1;
        for (int i = 0; i < 240; i++) {
            loop.step();
            trap = findTrap();
            if (trap != null && loop.getCurrentGameMode() == GameMode.BONUS_STAGE) {
                titleCardExitFrame = i;
                break;
            }
        }

        assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
        assertNotNull("Trap should exist after title card exit", trap);

        int updatesAtExit = trap.getUpdateCount();
        int riseDelayAtExit = trap.getRiseDelayFrames();

        for (int i = 0; i < 120; i++) {
            loop.step();
        }

        assertTrue("Trap update count should continue advancing after title card exit (exit frame="
                        + titleCardExitFrame + ", updatesAtExit=" + updatesAtExit
                        + ", final=" + trap.getUpdateCount() + ")",
                trap.getUpdateCount() > updatesAtExit);
        assertTrue("Trap rise delay should continue decreasing after title card exit (exit frame="
                        + titleCardExitFrame + ", delayAtExit=" + riseDelayAtExit
                        + ", final=" + trap.getRiseDelayFrames() + ")",
                trap.getRiseDelayFrames() < riseDelayAtExit);
    }

    @Test
    public void trapContinuesUpdatingWhenInstaShieldWasRegisteredBeforeBonusEntry() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x00, 0)
                .build();

        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        AbstractPlayableSprite player = fixture.sprite();
        DefaultPowerUpSpawner spawner = new DefaultPowerUpSpawner(GameServices.level().getObjectManager());
        if (player.getInstaShieldObject() == null) {
            player.setInstaShieldObject(new InstaShieldObjectInstance(player));
        }
        assertNotNull("Sonic should have a persistent insta-shield object", player.getInstaShieldObject());
        spawner.registerObject(player.getInstaShieldObject());

        assertTrue("Insta-shield should be registered in the level before bonus entry",
                ((AbstractObjectInstance) player.getInstaShieldObject()).getSlotIndex() >= 32);

        BonusStageProvider provider = GameModuleRegistry.getCurrent().getBonusStageProvider();
        BonusStageState savedState = captureSavedState(player, fixture.camera());

        Method doEnterBonusStage = GameLoop.class.getDeclaredMethod(
                "doEnterBonusStage", BonusStageProvider.class, BonusStageType.class, BonusStageState.class);
        doEnterBonusStage.setAccessible(true);
        doEnterBonusStage.invoke(loop, provider, BonusStageType.GLOWING_SPHERE, savedState);

        PachinkoEnergyTrapObjectInstance trap = null;
        for (int i = 0; i < 240; i++) {
            loop.step();
            trap = findTrap();
            if (trap != null && loop.getCurrentGameMode() == GameMode.BONUS_STAGE) {
                break;
            }
        }

        assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
        assertNotNull("Trap should exist after bonus title card exit", trap);

        int updatesAtExit = trap.getUpdateCount();
        int trapSlot = trap.getSlotIndex();

        for (int i = 0; i < 120; i++) {
            loop.step();
        }

        assertTrue("Trap update count should continue advancing after title card exit",
                trap.getUpdateCount() > updatesAtExit);
        assertEquals("Trap slot should not be shared after insta-shield re-registration",
                1, countObjectsAtSlot(trapSlot));
    }

    private static BonusStageState captureSavedState(AbstractPlayableSprite player, Camera camera) {
        LevelManager levelManager = GameServices.level();
        int zoneAndAct = (levelManager.getCurrentZone() << 8) | levelManager.getCurrentAct();
        int apparentZoneAndAct = (levelManager.getCurrentZone() << 8) | levelManager.getApparentAct();
        int ringCount = levelManager.getLevelGamestate() != null
                ? levelManager.getLevelGamestate().getRings()
                : 0;
        long timerFrames = levelManager.getLevelGamestate() != null
                ? levelManager.getLevelGamestate().getTimerFrames()
                : 0;
        return new BonusStageState(
                zoneAndAct,
                apparentZoneAndAct,
                ringCount,
                0,
                0,
                0,
                0,
                0,
                player.getCentreX(),
                player.getCentreY(),
                camera.getX(),
                camera.getY(),
                player.getTopSolidBit(),
                player.getLrbSolidBit(),
                camera.getMaxY(),
                timerFrames
        );
    }

    private static PachinkoEnergyTrapObjectInstance findTrap() {
        if (GameServices.level().getObjectManager() == null) {
            return null;
        }
        for (ObjectInstance instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof PachinkoEnergyTrapObjectInstance trap) {
                return trap;
            }
        }
        return null;
    }

    private static int countObjectsAtSlot(int slotIndex) {
        int count = 0;
        for (ObjectInstance instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() == slotIndex) {
                count++;
            }
        }
        return count;
    }
}
