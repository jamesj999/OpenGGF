package uk.co.jamesj999.sonic.game.sonic3k;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizEmeraldScatterInstance;
import uk.co.jamesj999.sonic.game.sonic3k.objects.CutsceneKnucklesAiz1Instance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.HeadlessTestRunner;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test: Knuckles must collect all 7 scattered emeralds during
 * his pacing routine in the AIZ1 intro cutscene.
 *
 * The check happens at routine 10 (laugh), BEFORE Knuckles exits (routine 12).
 * Once he walks offscreen, leftover emeralds are despawned by ObjectManager,
 * hiding any collection failure.
 *
 * Known bug: The Obj_Wait off-by-one ({@code <= 0} vs {@code < 0}) shortens
 * Knuckles' pacing by 6 pixels per direction. The leftmost emerald (subtype 12,
 * xVel = -0x300) lands at the edge of his reach, so this 6px shortfall leaves
 * it uncollected. This test detects that failure.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestAizIntroEmeraldCollection {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Object oldSkipIntros;
    private Object oldMainCharacter;
    private Sonic sonic;
    private HeadlessTestRunner runner;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

        GraphicsManager.getInstance().initHeadless();

        sonic = new Sonic("sonic", (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(sonic);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);

        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        runner = new HeadlessTestRunner(sonic);
    }

    @After
    public void tearDown() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
    }

    @Test
    public void allEmeraldsCollectedBeforeKnucklesExit() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();

        // Phase 1: Run until emeralds are spawned.
        boolean emeraldsSpawned = false;
        int guard = 2000;
        while (guard-- > 0) {
            runner.stepFrame(false, false, false, false, false);
            if (countEmeralds(objectManager) > 0) {
                emeraldsSpawned = true;
                break;
            }
        }
        assertTrue("Emerald scatter objects should spawn during AIZ intro", emeraldsSpawned);

        int totalEmeralds = countEmeralds(objectManager);
        System.out.println("Emeralds spawned: " + totalEmeralds);
        assertEquals("All 7 emeralds should be spawned", 7, totalEmeralds);

        // Phase 2: Find Knuckles.
        CutsceneKnucklesAiz1Instance knuckles = findKnuckles(objectManager);
        assertNotNull("Knuckles cutscene object should exist when emeralds are present", knuckles);
        System.out.println("Knuckles found at routine " + knuckles.getRoutine()
                + " pos=(" + knuckles.getX() + ", " + knuckles.getY() + ")");

        // Phase 3: Run until Knuckles reaches routine 10 (laugh).
        // This covers fall (~30 frames), stand (128 frames), pace (84 frames).
        guard = 500;
        while (guard-- > 0 && knuckles.getRoutine() < 10) {
            runner.stepFrame(false, false, false, false, false);
        }
        assertTrue("Knuckles should reach routine 10 (laugh) after pacing",
                knuckles.getRoutine() >= 10);
        assertFalse("Knuckles should not be destroyed during laugh phase",
                knuckles.isDestroyed());

        // Phase 4: Check emerald collection DURING routine 10 (laugh).
        // Pacing is complete but Knuckles hasn't exited yet.
        // Any uncollected emeralds are still alive in the active objects list.
        List<AizEmeraldScatterInstance> uncollected = new ArrayList<>();
        for (ObjectInstance obj : objectManager.getActiveObjects()) {
            if (obj instanceof AizEmeraldScatterInstance emerald && !emerald.isDestroyed()) {
                uncollected.add(emerald);
            }
        }

        // Log diagnostics for any uncollected emeralds.
        if (!uncollected.isEmpty()) {
            System.out.println("=== UNCOLLECTED EMERALDS at Knuckles routine "
                    + knuckles.getRoutine() + " ===");
            System.out.println("Knuckles X=" + knuckles.getX()
                    + " facingLeft=" + knuckles.isFacingLeft());
            for (AizEmeraldScatterInstance e : uncollected) {
                System.out.println("  Emerald mappingFrame=" + e.getMappingFrame()
                        + " X=" + e.getX() + " Y=" + e.getY()
                        + " phase=" + e.getPhase()
                        + " xVel=" + e.getXVel());
            }
        }

        assertEquals("All 7 emeralds should be collected by Knuckles before he laughs "
                + "(uncollected: " + uncollected.size() + ")", 0, uncollected.size());
    }

    private static int countEmeralds(ObjectManager objectManager) {
        int count = 0;
        for (ObjectInstance obj : objectManager.getActiveObjects()) {
            if (obj instanceof AizEmeraldScatterInstance && !obj.isDestroyed()) {
                count++;
            }
        }
        return count;
    }

    private static CutsceneKnucklesAiz1Instance findKnuckles(ObjectManager objectManager) {
        for (ObjectInstance obj : objectManager.getActiveObjects()) {
            if (obj instanceof CutsceneKnucklesAiz1Instance knux) {
                return knux;
            }
        }
        return null;
    }
}
