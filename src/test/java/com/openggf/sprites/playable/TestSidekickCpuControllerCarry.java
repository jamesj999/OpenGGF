package com.openggf.sprites.playable;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hydration parity tests: confirm the new state-enum values are accepted by
 * {@link SidekickCpuController#hydrateFromRomCpuState}. We reuse the AIZ1
 * shared fixture because the hydration method does not depend on zone layout;
 * any level that registers a Tails sidekick is enough.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSidekickCpuControllerCarry {

    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private SidekickCpuController controller;
    private HeadlessTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        controller = GameServices.sprites().getSidekicks().get(0).getCpuController();
    }

    @Test
    void hydrateAccepts0x0CCarryInit() {
        assertDoesNotThrow(() -> controller.hydrateFromRomCpuState(0x0C, 0, 0, 0, false));
        assertEquals(SidekickCpuController.State.CARRY_INIT, controller.getState());
    }

    @Test
    void hydrateAccepts0x0ECarrying() {
        controller.hydrateFromRomCpuState(0x0E, 0, 0, 0, false);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
    }

    @Test
    void hydrateAccepts0x20Carrying() {
        controller.hydrateFromRomCpuState(0x20, 0, 0, 0, false);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
    }

    // =====================================================================
    // Task 6: Tails-carry state-machine behavioural tests
    // =====================================================================

    /** Stub trigger that always enters carry; used to exercise state machine in AIZ. */
    private SidekickCarryTrigger alwaysOnTrigger() {
        return new SidekickCarryTrigger() {
            @Override
            public boolean shouldEnterCarry(int zoneId, int actId, PlayerCharacter pc) {
                return true;
            }
            @Override
            public void applyInitialPlacement(AbstractPlayableSprite carrier,
                                              AbstractPlayableSprite cargo) {
                // Intentional no-op; teleport parity is covered by Task 4's applyInitialPlacement test.
            }
            @Override public int   carryDescendOffsetY()            { return Sonic3kConstants.CARRY_DESCEND_OFFSET_Y; }
            @Override public short carryInitXVel()                  { return Sonic3kConstants.CARRY_INIT_TAILS_X_VEL; }
            @Override public int   carryInputInjectMask()           { return Sonic3kConstants.CARRY_INPUT_INJECT_MASK; }
            @Override public int   carryJumpReleaseCooldownFrames() { return Sonic3kConstants.CARRY_COOLDOWN_JUMP_RELEASE; }
            @Override public int   carryLatchReleaseCooldownFrames(){ return Sonic3kConstants.CARRY_COOLDOWN_LATCH_RELEASE; }
            @Override public short carryReleaseJumpYVel()           { return Sonic3kConstants.CARRY_RELEASE_JUMP_Y_VEL; }
            @Override public short carryReleaseJumpXVel()           { return Sonic3kConstants.CARRY_RELEASE_JUMP_X_VEL; }
        };
    }

    /**
     * Resets the fixture's sidekick controller to INIT with the stub trigger installed,
     * and returns (sonic, tails) for convenience.
     */
    private AbstractPlayableSprite[] prepareCarry() {
        AbstractPlayableSprite sonic = fixture.sprite();
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        // Position both high above any AIZ1 terrain so the post-parentage
        // collision probe (ROM Tails_Carry_Sonic:27330) finds no ground and
        // the carry tick exercises pure state-machine logic.  AIZ1's spawn
        // ground is near Y=1052; Y=580/600 is well clear.
        sonic.setCentreY((short) 600);
        tails.setCentreY((short) 580);
        sonic.setAir(true);
        tails.setAir(true);
        controller.setCarryTrigger(alwaysOnTrigger());
        controller.setInitialState(SidekickCpuController.State.INIT);
        return new AbstractPlayableSprite[] { sonic, tails };
    }

    // --- init transition --------------------------------------------------

    @Test
    void initWithTriggerTransitionsToCarryInitThenCarryingAcrossTwoFrames() {
        AbstractPlayableSprite[] pair = prepareCarry();
        AbstractPlayableSprite sonic = pair[0];

        // First tick: ROM loc_13A10 (sonic3k.asm:26414) sets
        // Tails_CPU_routine=$C and rts. Engine mirrors by entering
        // CARRY_INIT and returning without executing the 0x0C body
        // (which writes x_vel=$100).
        controller.update(1);
        assertEquals(SidekickCpuController.State.CARRY_INIT, controller.getState(),
                "Frame 1 state must be CARRY_INIT (ROM Tails_CPU_routine=$C just set, body not yet run)");
        assertEquals((short) 0x0000, sonic.getXSpeed(),
                "Frame 1 x_speed unchanged — ROM 0x0C body has not fired yet");

        // Second tick: ROM loc_13FC2 (the 0x0C body, sonic3k.asm:26903)
        // sets x_vel=$100 and falls through (no rts) to loc_13FFA (the
        // 0x0E body). Engine mirrors by transitioning CARRY_INIT ->
        // CARRYING with the x_speed write.
        controller.update(2);
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState(),
                "Frame 2 state must be CARRYING (0x0C body ran; fell through to 0x0E)");
        assertTrue(sonic.isObjectControlled(),
                "Sonic must be object-controlled while carried");
        assertEquals((short) 0x0100, sonic.getXSpeed(),
                "Frame 2 x_speed must match Tails's carry x_vel (loc_13FC2 write)");
        assertTrue(sonic.getAir(), "Sonic.air must be true while carried");
    }

    // --- per-frame parentage ---------------------------------------------

    @Test
    void carryingCopiesPostMovementTailsVelocityToSonicEachFrame() {
        AbstractPlayableSprite[] pair = prepareCarry();
        AbstractPlayableSprite sonic = pair[0];
        AbstractPlayableSprite tails = pair[1];
        controller.update(1);  // INIT -> CARRY_INIT
        controller.update(2);  // CARRY_INIT -> CARRYING (with x_speed write)
        controller.finishCarryAfterCarrierMovement();

        for (int i = 0; i < 10; i++) {
            controller.update(3 + i);
            short postMovementXSpeed = (short) (0x0100 + (i + 1) * 0x18);
            short postMovementYSpeed = (short) ((i + 1) * 0x08);
            tails.setXSpeed(postMovementXSpeed);
            tails.setYSpeed(postMovementYSpeed);
            controller.finishCarryAfterCarrierMovement();
            assertEquals(postMovementXSpeed, sonic.getXSpeed(),
                    "Sonic.x_speed must copy Tails's post-movement x_vel on frame " + (i + 3));
            assertEquals(postMovementYSpeed, sonic.getYSpeed(),
                    "Sonic.y_speed must copy Tails's post-movement y_vel on frame " + (i + 3));
            assertEquals(SidekickCpuController.State.CARRYING, controller.getState());
        }
    }

    // --- release path A: ground contact ---------------------------------

    @Test
    void groundReleasesCarry() {
        AbstractPlayableSprite[] pair = prepareCarry();
        AbstractPlayableSprite sonic = pair[0];
        controller.update(1);  // INIT -> CARRY_INIT
        controller.update(2);  // CARRY_INIT -> CARRYING
        assertEquals(SidekickCpuController.State.CARRYING, controller.getState());

        sonic.setAir(false);  // simulate landing
        controller.update(3);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertFalse(sonic.isObjectControlled());
        assertEquals(0, controller.getReleaseCooldownForTest(),
                "Ground release has no cooldown");
    }

    // --- release path B: A/B/C press ------------------------------------

    @Test
    void jumpPressReleasesCarryWithJumpVelocity() {
        AbstractPlayableSprite[] pair = prepareCarry();
        AbstractPlayableSprite sonic = pair[0];
        controller.update(1);  // INIT -> CARRY_INIT
        controller.update(2);  // CARRY_INIT -> CARRYING

        // Simulate rising-edge jump press: previous frame false, this frame true.
        sonic.setJumpInputPressed(false);
        sonic.setJumpInputPressed(true);
        controller.update(3);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertEquals((short) -0x0380, sonic.getYSpeed(),
                "Jump release imparts -0x380 y_vel");
        assertEquals(0x12, controller.getReleaseCooldownForTest(),
                "Jump release cooldown is 0x12 (~18 frames)");
    }

    // --- release path C: latch mismatch ---------------------------------

    @Test
    void externalXSpeedChangeReleasesCarryWithLatchCooldown() {
        AbstractPlayableSprite[] pair = prepareCarry();
        AbstractPlayableSprite sonic = pair[0];
        controller.update(1);  // INIT -> CARRY_INIT
        controller.update(2);  // CARRY_INIT -> CARRYING with latchX = 0x100
        sonic.setXSpeed((short) 0x0500);  // external bumper-style write

        controller.update(3);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertEquals(0x3C, controller.getReleaseCooldownForTest(),
                "Latch-mismatch release cooldown is 0x3C (~60 frames)");
    }

    // --- cooldown countdown ---------------------------------------------

    @Test
    void cooldownDecrementsEveryFrame() {
        AbstractPlayableSprite[] pair = prepareCarry();
        AbstractPlayableSprite sonic = pair[0];
        controller.update(1);  // INIT -> CARRY_INIT
        controller.update(2);  // CARRY_INIT -> CARRYING
        sonic.setJumpInputPressed(false);
        sonic.setJumpInputPressed(true);
        controller.update(3);
        int cooldownStart = controller.getReleaseCooldownForTest();
        assertEquals(0x12, cooldownStart);

        sonic.setJumpInputPressed(false);
        controller.update(4);
        controller.update(5);
        controller.update(6);

        assertEquals(cooldownStart - 3, controller.getReleaseCooldownForTest(),
                "Cooldown must decrement 1 per frame");
    }

    // --- input injection ------------------------------------------------

    @Test
    void carryInjectsSyntheticRightEvery32Frames() {
        prepareCarry();
        controller.update(1);  // INIT -> CARRY_INIT
        controller.update(2);  // CARRY_INIT -> CARRYING
        boolean sawInjection = false;
        for (int i = 3; i < 67; i++) {
            controller.update(i);
            if (controller.getInputRight()) {
                sawInjection = true;
                break;
            }
        }
        assertTrue(sawInjection, "Right-press injection must fire at least once in 64 frames");
    }
}
