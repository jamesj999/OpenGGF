package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration smoke tests verifying multi-sidekick spawning, chain wiring,
 * following behavior, and chain healing in a real EHZ1 level.
 *
 * Requires the Sonic 2 ROM to be present; tests are skipped if unavailable.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestMultiSidekickSpawn {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;
    private Sonic mainPlayer;
    private AbstractPlayableSprite[] sidekicks;
    private SidekickCpuController[] controllers;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        mainPlayer = (Sonic) fixture.sprite();
        mainPlayer.setCentreX((short) 96);
        mainPlayer.setCentreY((short) 655);
        mainPlayer.setAir(false);

        // Manually create 3 sidekick sprites and chain their leaders,
        // mirroring what Engine's spawn loop would do with config
        // SIDEKICK_CHARACTER_CODE = "tails,sonic,sonic".
        sidekicks = new AbstractPlayableSprite[3];
        controllers = new SidekickCpuController[3];

        sidekicks[0] = new Tails("tails_p2", (short) 56, (short) 655);
        sidekicks[1] = new Sonic("sonic_p3", (short) 36, (short) 655);
        sidekicks[2] = new Sonic("sonic_p4", (short) 16, (short) 655);

        SpriteManager sm = GameServices.sprites();
        for (int i = 0; i < 3; i++) {
            sidekicks[i].setCpuControlled(true);

            AbstractPlayableSprite leader = (i == 0) ? mainPlayer : sidekicks[i - 1];
            controllers[i] = new SidekickCpuController(sidekicks[i], leader);
            controllers[i].setSidekickCount(3);
            sidekicks[i].setCpuController(controllers[i]);

            String charName = (i == 0) ? "tails" : "sonic";
            sm.addSprite(sidekicks[i], charName);
        }
    }

    // ========== Test 1: Multi-sidekick spawn verification ==========

    @Test
    public void testMultiSidekickSpawn() {
        List<AbstractPlayableSprite> registered = GameServices.sprites().getSidekicks();
        assertEquals("Should have 3 sidekicks registered", 3, registered.size());

        // Verify chain leader assignment
        assertSame("sidekick[0]'s leader should be main player",
                mainPlayer, controllers[0].getLeader());
        assertSame("sidekick[1]'s leader should be sidekick[0]",
                sidekicks[0], controllers[1].getLeader());
        assertSame("sidekick[2]'s leader should be sidekick[1]",
                sidekicks[1], controllers[2].getLeader());

        // Verify all are CPU-controlled
        for (int i = 0; i < 3; i++) {
            assertTrue("sidekick[" + i + "] should be CPU-controlled",
                    sidekicks[i].isCpuControlled());
            assertNotNull("sidekick[" + i + "] should have a CPU controller",
                    sidekicks[i].getCpuController());
        }
    }

    // ========== Test 2: Multi-sidekick following / X ordering ==========

    @Test
    public void testMultiSidekickFollowing() {
        // Walk right for 120 frames, stepping sidekicks each frame
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            stepAllSidekicks();
        }

        int mainX = mainPlayer.getCentreX();
        int sk0X = sidekicks[0].getCentreX();
        int sk1X = sidekicks[1].getCentreX();
        int sk2X = sidekicks[2].getCentreX();

        assertTrue("mainPlayer.X (" + mainX + ") should be > sidekick[0].X (" + sk0X + ")",
                mainX > sk0X);
        assertTrue("sidekick[0].X (" + sk0X + ") should be > sidekick[1].X (" + sk1X + ")",
                sk0X > sk1X);
        assertTrue("sidekick[1].X (" + sk1X + ") should be > sidekick[2].X (" + sk2X + ")",
                sk1X > sk2X);
    }

    // ========== Test 3: Chain healing integration ==========

    @Test
    public void testSidekickChainHealingIntegration() {
        // Step enough frames for all sidekicks to reach NORMAL and settle
        for (int frame = 0; frame < 60; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            stepAllSidekicks();
        }

        // Verify all sidekicks are in NORMAL state and settled
        for (int i = 0; i < 3; i++) {
            assertEquals("sidekick[" + i + "] should be in NORMAL state after 60 frames",
                    SidekickCpuController.State.NORMAL, controllers[i].getState());
            assertTrue("sidekick[" + i + "] should be settled after 60 frames",
                    controllers[i].isSettled());
        }

        // Force sidekick[1] to SPAWNING state (simulating despawn)
        controllers[1].setInitialState(SidekickCpuController.State.SPAWNING);

        assertFalse("sidekick[1] should NOT be settled after forced SPAWNING",
                controllers[1].isSettled());

        // Verify chain healing: sidekick[2]'s effective leader should skip
        // the unsettled sidekick[1] and resolve to sidekick[0] or main player
        AbstractPlayableSprite effectiveLeader = controllers[2].getEffectiveLeader();
        assertTrue("sidekick[2]'s effective leader should skip unsettled sidekick[1] "
                        + "and resolve to sidekick[0] (settled) or mainPlayer. Got: " + effectiveLeader,
                effectiveLeader == sidekicks[0] || effectiveLeader == mainPlayer);

        // sidekick[0] is settled, so it should be the effective leader
        assertSame("sidekick[2]'s effective leader should be sidekick[0] (first settled in chain)",
                sidekicks[0], effectiveLeader);
    }

    // ========== Helper methods ==========

    /**
     * Steps all sidekick sprites through one frame of AI + physics.
     * Mirrors the SpriteManager CPU-controlled sidekick update path.
     */
    private void stepAllSidekicks() {
        int frameCount = fixture.frameCount();
        for (int i = 0; i < sidekicks.length; i++) {
            controllers[i].update(frameCount);
            if (!controllers[i].isApproaching()) {
                sidekicks[i].getMovementManager().handleMovement(
                        controllers[i].getInputUp(),
                        controllers[i].getInputDown(),
                        controllers[i].getInputLeft(),
                        controllers[i].getInputRight(),
                        controllers[i].getInputJump(),
                        false, false, false);
            }
            sidekicks[i].tickStatus();
            sidekicks[i].endOfTick();
        }
    }
}
