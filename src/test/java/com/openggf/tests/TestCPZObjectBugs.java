package com.openggf.tests;

import org.junit.Assume;
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
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Headless integration tests for CPZ (Chemical Plant Zone) object bugs.
 *
 * <p>These are bug reproduction tests that should <b>FAIL</b> initially (proving the
 * bugs exist), then <b>PASS</b> after the corresponding fixes are applied.
 *
 * <p><b>Test #1 - Spin Tube Forces Rolling:</b> CPZ spin tubes (object 0x1E) should
 * force Sonic into rolling/pinball state after exit. The bug is that rolling state
 * drops during or after tube transit. The spin tube entry sets
 * {@code player.setObjectControlled(true)} and {@code player.setRolling(true)}. On
 * exit, {@code releaseFromObjectControl()} is called and {@code setPinballMode(true)}
 * (for non-upward exits).
 *
 * <p><b>Test #2 - Staircase No False Balance:</b>
 * {@code PlayableSpriteMovement.checkObjectEdgeBalance()} uses
 * {@code ridingObject.getX()} (the BASE X of the parent staircase) instead of the
 * per-piece X. Since staircase pieces are 32px apart, Sonic's position relative to
 * the base X triggers false edge detection even when centered on a piece.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestCPZObjectBugs {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    // Zone/Act indices for loadZoneAndAct (not ROM zone IDs)
    // CPZ is zone index 1 in Sonic2ZoneRegistry (EHZ=0, CPZ=1, ARZ=2, CNZ=3, HTZ=4)
    private static final int ZONE_CPZ = 1;
    private static final int ACT_1 = 0;

    // Object IDs from Sonic 2 layout
    private static final int OBJ_SPIN_TUBE = 0x1E;
    private static final int OBJ_STAIRCASE = 0x78;

    @Before
    public void setUp() throws Exception {
        // Initialize headless graphics (no GL context needed)
        GraphicsManager.getInstance().initHeadless();

        // Create Sonic sprite at origin (position set after level load)
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 0, (short) 0);

        // Add sprite to SpriteManager
        SpriteManager.getInstance().addSprite(sprite);

        // Set camera focus - must be done BEFORE level load
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        // Reset camera to ensure clean state (frozen may be left over from death in other tests)
        camera.setFrozen(false);

        // Load CPZ Act 1 (zone index 1, act index 0)
        LevelManager.getInstance().loadZoneAndAct(ZONE_CPZ, ACT_1);

        // Ensure GroundSensor uses the current LevelManager instance
        // (static field may be stale from earlier tests)
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Fix camera position - loadZoneAndAct sets bounds AFTER updatePosition,
        // so the camera may have been clamped incorrectly. Force update again now
        // that bounds are set.
        camera.updatePosition(true);

        // Reset the object manager's spawn window to the new camera position
        // so objects near our test position are spawned
        LevelManager.getInstance().getObjectManager().reset(camera.getX());

        // Create the headless test runner
        testRunner = new HeadlessTestRunner(sprite);
    }

    /**
     * Tests that CPZ spin tubes force Sonic into rolling/pinball state after exit.
     *
     * <p>The spin tube entry sets {@code player.setObjectControlled(true)} and
     * {@code player.setRolling(true)}. On exit, {@code releaseFromObjectControl()}
     * is called and {@code setPinballMode(true)} should keep Sonic rolling through
     * the terrain after the tube.
     *
     * <p>Test approach: Position Sonic near the CPZ1 start and run right for up to
     * 600 frames, looking for a spin tube interaction (detected via
     * {@code isObjectControlled()}). After tube exit, verify rolling or pinball mode
     * is active and Sonic has exit velocity.
     *
     * <p>If no spin tube is encountered within the traversal range, the test is
     * skipped via {@code Assume} rather than failed.
     */
    @Test
    public void testSpinTubeForcesRolling() {
        // First CPZ1 spin tube is at (1920, 896) subtype 0x02.
        // Tube entry collision detects player within X range and Y 0-0x80 below object.
        // Position Sonic at the tube entrance with rightward speed so the tube captures him.
        sprite.setCentreX((short) 1920);
        sprite.setCentreY((short) 896);
        sprite.setAir(false);
        sprite.setGSpeed((short) 0x400);
        sprite.setXSpeed((short) 0x400);
        sprite.setYSpeed((short) 0);
        Camera.getInstance().updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(Camera.getInstance().getX());

        logState("Initial (at tube 1920,896)");

        boolean tubeEntered = false;
        boolean tubeExited = false;

        // Step frames — the tube should capture Sonic almost immediately
        for (int frame = 0; frame < 600; frame++) {
            testRunner.stepFrame(false, false, false, true, false);

            if (!tubeEntered && sprite.isObjectControlled()) {
                tubeEntered = true;
                logState("Tube entered at frame " + (frame + 1));
            }

            if (tubeEntered && !sprite.isObjectControlled()) {
                tubeExited = true;
                logState("Tube exited at frame " + (frame + 1));
                break;
            }

            if (frame % 50 == 0) {
                logState("Frame " + (frame + 1));
            }
        }

        Assume.assumeTrue("Spin tube at (1920,896) did not capture/release Sonic", tubeEntered && tubeExited);

        assertTrue("Sonic should be rolling or in pinball mode after tube exit",
            sprite.getRolling() || sprite.getPinballMode());

        // Sonic should have exit velocity after leaving the tube
        assertTrue("Sonic should have non-zero speed after tube exit (GSpeed=" +
            sprite.getGSpeed() + ", XSpeed=" + sprite.getXSpeed() + ", YSpeed=" +
            sprite.getYSpeed() + ")",
            sprite.getGSpeed() != 0 || sprite.getXSpeed() != 0 || sprite.getYSpeed() != 0);
    }

    /**
     * Tests that standing on a CPZ staircase does not trigger false edge balance.
     *
     * <p>The bug: {@code PlayableSpriteMovement.checkObjectEdgeBalance()} uses
     * {@code ridingObject.getX()} (the BASE X of the parent staircase object)
     * instead of the per-piece X. Since staircase pieces are 32px apart, Sonic's
     * position relative to the base X triggers false edge detection even when
     * centered on a piece.
     *
     * <p>Test approach: Walk Sonic right through CPZ1 until he lands on a staircase
     * object (ID 0x78). Stop all movement and verify that balance state is 0
     * (no balancing) when standing on a staircase piece.
     *
     * <p>If no staircase is encountered within the traversal range, the test is
     * skipped via {@code Assume} rather than failed.
     */
    @Test
    public void testStaircaseNoFalseBalance() {
        // CPZ1 staircase at (8336, 848) subtype 0x00. Teleport Sonic near it
        // and drop him onto it so he lands on a staircase piece.
        int staircaseX = 8336;
        int staircaseY = 848;

        // Position Sonic above the staircase center and let him fall onto it
        sprite.setCentreX((short) staircaseX);
        sprite.setCentreY((short) (staircaseY - 48));
        sprite.setAir(true);
        sprite.setGSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        Camera.getInstance().updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(Camera.getInstance().getX());

        logState("Initial (above staircase at " + staircaseX + "," + staircaseY + ")");

        ObjectManager objMgr = LevelManager.getInstance().getObjectManager();
        boolean foundStaircase = false;

        // Drop onto the staircase - wait for landing on an object
        for (int frame = 0; frame < 120; frame++) {
            testRunner.stepFrame(false, false, false, false, false);

            if (sprite.isOnObject() && !sprite.getAir()) {
                ObjectInstance ridingObj = objMgr.getRidingObject(sprite);
                if (ridingObj != null && ridingObj.getSpawn().objectId() == OBJ_STAIRCASE) {
                    foundStaircase = true;
                    logState("Landed on staircase at frame " + (frame + 1));

                    // Stop and settle on the staircase
                    sprite.setGSpeed((short) 0);
                    sprite.setXSpeed((short) 0);
                    sprite.setYSpeed((short) 0);
                    sprite.setRolling(false);
                    sprite.setBalanceState(0);

                    // Step a few idle frames to let physics settle
                    for (int settle = 0; settle < 10; settle++) {
                        testRunner.stepFrame(false, false, false, false, false);
                        sprite.setGSpeed((short) 0);
                        sprite.setXSpeed((short) 0);
                    }

                    logState("After settle");

                    // Final check frame - reset balance state and let one physics
                    // frame run to see if false balance is detected
                    sprite.setBalanceState(0);
                    testRunner.stepFrame(false, false, false, false, false);

                    logState("Final check");

                    assertEquals("Sonic should NOT show balance animation when standing " +
                        "on center of staircase piece (false edge detection due to " +
                        "wrong X reference). Balance state was " + sprite.getBalanceState(),
                        0, sprite.getBalanceState());
                    return;
                }
            }
        }

        Assume.assumeTrue("Could not land on staircase (0x78) at (" + staircaseX + "," + staircaseY + ")",
            foundStaircase);
    }

    /**
     * Helper method to log sprite state for debugging.
     */
    private void logState(String label) {
        System.out.printf("%s: X=%d (0x%04X), Y=%d (0x%04X), GSpeed=%d, XSpeed=%d, " +
            "YSpeed=%d, Air=%b, Rolling=%b, Pinball=%b, ObjCtrl=%b, OnObj=%b, " +
            "Balance=%d, Facing=%s%n",
            label,
            sprite.getX(), sprite.getX() & 0xFFFF,
            sprite.getY(), sprite.getY() & 0xFFFF,
            sprite.getGSpeed(),
            sprite.getXSpeed(),
            sprite.getYSpeed(),
            sprite.getAir(),
            sprite.getRolling(),
            sprite.getPinballMode(),
            sprite.isObjectControlled(),
            sprite.isOnObject(),
            sprite.getBalanceState(),
            sprite.getDirection());
    }
}
