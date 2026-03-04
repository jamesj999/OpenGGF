package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
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
 *
 * <p>Level data is loaded once via {@link SharedLevel#load} in {@code @BeforeClass};
 * sprite, camera, and game state are reset per test via {@link HeadlessTestFixture}.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestCPZObjectBugs {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    // Zone/Act indices for loadZoneAndAct (not ROM zone IDs)
    // CPZ is zone index 1 in Sonic2ZoneRegistry (EHZ=0, CPZ=1, ARZ=2, CNZ=3, HTZ=4)
    private static final int ZONE_CPZ = 1;
    private static final int ACT_1 = 0;

    // Object IDs from Sonic 2 layout
    private static final int OBJ_SPIN_TUBE = 0x1E;
    private static final int OBJ_STAIRCASE = 0x78;

    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic sprite;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_CPZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sprite = (Sonic) fixture.sprite();

        // Reset the object manager's spawn window so objects near our test position are spawned
        LevelManager.getInstance().getObjectManager().reset(fixture.camera().getX());
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
        fixture.camera().updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(fixture.camera().getX());

        logState("Initial (at tube 1920,896)");

        boolean tubeEntered = false;
        boolean tubeExited = false;

        // Step frames — the tube should capture Sonic almost immediately
        for (int frame = 0; frame < 600; frame++) {
            fixture.stepFrame(false, false, false, true, false);

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
        fixture.camera().updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(fixture.camera().getX());

        logState("Initial (above staircase at " + staircaseX + "," + staircaseY + ")");

        ObjectManager objMgr = LevelManager.getInstance().getObjectManager();
        boolean foundStaircase = false;

        // Drop onto the staircase - wait for landing on an object
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepFrame(false, false, false, false, false);

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
                        fixture.stepFrame(false, false, false, false, false);
                        sprite.setGSpeed((short) 0);
                        sprite.setXSpeed((short) 0);
                    }

                    logState("After settle");

                    // Final check frame - reset balance state and let one physics
                    // frame run to see if false balance is detected
                    sprite.setBalanceState(0);
                    fixture.stepFrame(false, false, false, false, false);

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
