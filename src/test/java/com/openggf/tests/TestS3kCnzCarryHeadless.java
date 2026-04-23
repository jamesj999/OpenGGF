package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end headless check of the CNZ1 Tails-carry intro.
 *
 * <p>Mirrors the first ~200 frames of the {@code TestS3kCnzTraceReplay}
 * BK2 without running the full trace engine. Success criteria (per design
 * spec §10 row 1, 3 and §8.2, updated for the ROM 0x00 -> 0x0C
 * non-same-frame fix):
 * <ul>
 *   <li>Frame 1: {@code Sonic.x_speed == 0x0000} (ROM {@code loc_13A10}
 *       sets {@code Tails_CPU_routine=$C} and returns; the 0x0C body has
 *       not run yet)</li>
 *   <li>Frame 2: {@code Sonic.x_speed == 0x0100} and object-controlled/
 *       airborne (ROM {@code loc_13FC2} body runs, falling through to
 *       {@code loc_13FFA}; engine {@code CARRY_INIT -> CARRYING})</li>
 *   <li>Frame 20: {@code Sonic.air == 1} (still carried; ROM-parity frame
 *       is 43 per trace row #3, but engine Tails lacks carry-aware lift
 *       and grounds early — see
 *       {@code docs/S3K_KNOWN_DISCREPANCIES.md} section
 *       "Tails Flying-With-Cargo Physics")</li>
 *   <li>By frame ~200: state back to {@code NORMAL}, {@code object_control} cleared</li>
 * </ul>
 *
 * <p>Knuckles-alone coverage lives in {@code TestSonic3kCnzCarryTrigger}
 * (Task 4 unit tests); this class asserts only the Sonic+Tails path.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzCarryHeadless {

    private static final int ZONE_CNZ = 3;
    private static final int ACT_1 = 0;

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        // Intro skip keeps the first frame on CNZ1 gameplay (skips zone-intro
        // title-card frames), so frame 1 is the carry intro's first tick.
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_CNZ, ACT_1);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
        if (oldSkipIntros != null) {
            SonicConfigurationService.getInstance()
                    .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros);
            oldSkipIntros = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
    }

    private SidekickCpuController sidekickController() {
        return GameServices.sprites().getSidekicks().get(0).getCpuController();
    }

    @Test
    void cnz1Frame2SonicXSpeedMatchesRom() {
        AbstractPlayableSprite sonic = fixture.sprite();

        // Frame 1: ROM loc_13A10 (sonic3k.asm:26414) INIT handler sets
        // Tails_CPU_routine=$C and rts. Engine enters CARRY_INIT; the
        // 0x0C body that writes x_vel=$100 has NOT run yet.
        fixture.stepFrame(false, false, false, false, false);
        assertEquals((short) 0x0000, sonic.getXSpeed(),
                "Frame 1 Sonic.x_speed: 0x0C body has not run yet (INIT just set routine=$C)");

        // Frame 2: ROM loc_13FC2 (the 0x0C body, sonic3k.asm:26903)
        // writes x_vel=$100 and falls through (no rts) to loc_13FFA
        // (the 0x0E body). Engine transitions CARRY_INIT -> CARRYING
        // with the x_speed write.
        fixture.stepFrame(false, false, false, false, false);
        assertEquals((short) 0x0100, sonic.getXSpeed(),
                "Frame 2 Sonic.x_speed must match ROM carry velocity (loc_13FC2 write)");
        assertEquals((short) 0, sonic.getYSpeed(),
                "Frame 2 Sonic.y_speed (carry init)");
        assertTrue(sonic.isObjectControlled(),
                "Frame 2: Sonic object-controlled by Tails");
        assertTrue(sonic.getAir(),
                "Frame 2: Sonic airborne (being carried)");
    }

    /**
     * At frame 20, Tails is still airborne in both ROM (until ~106) and
     * our engine (until ~42), so Sonic remains carried. ROM-parity frame
     * is 43 per trace row #3, but engine Tails lacks carry-aware lift
     * and grounds early — see docs/S3K_KNOWN_DISCREPANCIES.md
     * "Tails Flying-With-Cargo Physics".
     */
    @Test
    void cnz1Frame20SonicStillCarried() {
        AbstractPlayableSprite sonic = fixture.sprite();

        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertTrue(sonic.getAir(), "Frame 20: Sonic must still be airborne");
        assertTrue(sonic.isObjectControlled(),
                "Frame 20: Sonic still object-controlled (carry has not released)");
    }

    @Test
    void cnz1CarryReleasesByFrame200() {
        AbstractPlayableSprite sonic = fixture.sprite();
        SidekickCpuController ctrl = sidekickController();

        int releasedAtFrame = -1;
        for (int i = 0; i < 200; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (ctrl.getState() == SidekickCpuController.State.NORMAL) {
                releasedAtFrame = i;
                break;
            }
        }

        assertNotEquals(-1, releasedAtFrame,
                "Carry never transitioned to NORMAL within 200 frames");
        assertTrue(releasedAtFrame > 0,
                "Carry released on frame 0 — carry must engage for at least 1 frame before releasing");
        assertTrue(releasedAtFrame < 200,
                "Carry released outside the 200-frame window at frame " + releasedAtFrame);
        assertFalse(sonic.isObjectControlled(),
                "After release Sonic is no longer object-controlled");
    }
}
