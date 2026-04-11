package com.openggf.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke tests verifying multi-sidekick spawning, chain wiring,
 * following behavior, and chain healing in a real EHZ1 level.
 *
 * Requires the Sonic 2 ROM to be present; tests are skipped if unavailable.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestMultiSidekickSpawn {
    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static SharedLevel sharedLevel;

    @BeforeAll
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterAll
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;
    private Sonic mainPlayer;
    private AbstractPlayableSprite[] sidekicks;
    private SidekickCpuController[] controllers;

    @BeforeEach
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
        assertEquals(3, registered.size(), "Should have 3 sidekicks registered");

        // Verify chain leader assignment
        assertSame(mainPlayer, controllers[0].getLeader(), "sidekick[0]'s leader should be main player");
        assertSame(sidekicks[0], controllers[1].getLeader(), "sidekick[1]'s leader should be sidekick[0]");
        assertSame(sidekicks[1], controllers[2].getLeader(), "sidekick[2]'s leader should be sidekick[1]");

        // Verify all are CPU-controlled
        for (int i = 0; i < 3; i++) {
            assertTrue(sidekicks[i].isCpuControlled(), "sidekick[" + i + "] should be CPU-controlled");
            assertNotNull(sidekicks[i].getCpuController(), "sidekick[" + i + "] should have a CPU controller");
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

        assertTrue(mainX > sk0X, "mainPlayer.X (" + mainX + ") should be > sidekick[0].X (" + sk0X + ")");
        assertTrue(sk0X > sk1X, "sidekick[0].X (" + sk0X + ") should be > sidekick[1].X (" + sk1X + ")");
        assertTrue(sk1X > sk2X, "sidekick[1].X (" + sk1X + ") should be > sidekick[2].X (" + sk2X + ")");
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
            assertEquals(SidekickCpuController.State.NORMAL, controllers[i].getState(), "sidekick[" + i + "] should be in NORMAL state after 60 frames");
            assertTrue(controllers[i].isSettled(), "sidekick[" + i + "] should be settled after 60 frames");
        }

        // Force sidekick[1] to SPAWNING state (simulating despawn)
        controllers[1].setInitialState(SidekickCpuController.State.SPAWNING);

        assertFalse(controllers[1].isSettled(), "sidekick[1] should NOT be settled after forced SPAWNING");

        // Verify chain healing: sidekick[2]'s effective leader should skip
        // the unsettled sidekick[1] and resolve to sidekick[0] or main player
        AbstractPlayableSprite effectiveLeader = controllers[2].getEffectiveLeader();
        assertTrue(effectiveLeader == sidekicks[0] || effectiveLeader == mainPlayer, "sidekick[2]'s effective leader should skip unsettled sidekick[1] "
                        + "and resolve to sidekick[0] (settled) or mainPlayer. Got: " + effectiveLeader);

        // sidekick[0] is settled, so it should be the effective leader
        assertSame(sidekicks[0], effectiveLeader, "sidekick[2]'s effective leader should be sidekick[0] (first settled in chain)");
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


