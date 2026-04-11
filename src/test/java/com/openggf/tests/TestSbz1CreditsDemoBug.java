package com.openggf.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for S1 SBZ1 credits demo replay.
 *
 * Replays the SBZ1 credits demo input sequence and verifies the engine
 * completes it without crashing or Sonic dying.
 *
 * History: this test originally checked for a bug where Sonic's speeds were
 * all reset to 0 at Y=889. After trace-replay-verified physics corrections
 * (commit 8a894f39b), the demo trajectory changed â€” Sonic no longer reaches
 * Y=889 with the corrected collision model. The test now verifies the demo
 * replays cleanly and Sonic moves from the start position.
 *
 * Demo input sequence (from ROM at 0x5E4C):
 *   Idle 37f, Left 82f, Idle 37f, Left 35f, Idle 231f, Left 104f, Idle 13f, Right 110f
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestSbz1CreditsDemoBug {

    private static final int ZONE_SBZ = 5;
    private static final int ACT_1 = 0;
    private static final short START_X = 0x1570;
    private static final short START_Y = 0x016C;

    /**
     * Demo input pairs: [buttonMask, duration].
     * Button bits: 0x04=Left, 0x08=Right, 0x00=None.
     */
    private static final int[][] DEMO_INPUTS = {
        {0x00, 0x25}, // Idle 37 frames
        {0x04, 0x52}, // Left 82 frames
        {0x00, 0x25}, // Idle 37 frames
        {0x04, 0x23}, // Left 35 frames
        {0x00, 0xE7}, // Idle 231 frames
        {0x04, 0x68}, // Left 104 frames
        {0x00, 0x0D}, // Idle 13 frames
        {0x08, 0x6E}, // Right 110 frames
    };
    private static SharedLevel sharedLevel;

    @BeforeAll
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_1, ZONE_SBZ, ACT_1);
    }

    @AfterAll
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;

    @BeforeEach
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
    }

    @Test
    public void testSonicDoesNotGetStuckInSbz1Tube() {
        AbstractPlayableSprite sprite = fixture.sprite();

        // Set the credits demo start position
        sprite.setCentreX(START_X);
        sprite.setCentreY(START_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);  // Start airborne to settle onto terrain

        fixture.camera().updatePosition(true);

        // Let Sonic settle onto the ground
        for (int i = 0; i < 30; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        int settledX = sprite.getX();
        int settledY = sprite.getY();
        assertFalse(sprite.getAir(), "Sonic should settle onto ground");

        // Replay the demo input sequence
        int totalFrame = 0;
        int minX = settledX;

        for (int[] pair : DEMO_INPUTS) {
            int buttons = pair[0];
            int duration = pair[1];

            boolean left = (buttons & 0x04) != 0;
            boolean right = (buttons & 0x08) != 0;
            boolean up = (buttons & 0x01) != 0;
            boolean down = (buttons & 0x02) != 0;
            boolean jump = (buttons & 0x70) != 0;

            for (int f = 0; f < duration; f++) {
                fixture.stepFrame(up, down, left, right, jump);
                totalFrame++;

                int x = sprite.getX();
                if (x < minX) {
                    minX = x;
                }
            }
        }

        // Verify Sonic moved during the demo (the leftward inputs should move him)
        assertTrue(minX < settledX - 50, "Sonic should have moved left during demo (minX=" + minX
                + " settledX=" + settledX + ")");

        // Verify Sonic didn't die
        assertFalse(sprite.getDead(), "Sonic should not die during SBZ1 demo");
    }
}


