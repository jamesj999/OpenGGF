package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.objects.AizEmeraldScatterInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz1Instance;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

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
 * Root cause was a ROM mismatch in object slot processing order: the S3K ROM's
 * CreateChild6_Simple places emeralds in later object slots. The emerald init
 * code (loc_67900) reads Player_1.x_pos when that slot is first processed —
 * AFTER the plane's scrollVelocity (sub_45DE4) has already added scrollSpeed
 * (16) to Player_1. Java's constructor ran before scrollVelocity, so emeralds
 * spawned 16px too far left. The fix adds scrollSpeed to the spawn X in
 * routine26Explode to compensate for this ordering difference.
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
