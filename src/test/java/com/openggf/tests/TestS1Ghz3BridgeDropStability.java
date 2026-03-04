package com.openggf.tests;

import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for bridge drop stability in GHZ3.
 *
 * When Sonic drops onto the last bridge in GHZ3 from above, he should land
 * and settle into a stable ground-contact state. A bug in the slope data
 * baseline caused a mismatch between the landing and riding Y positions,
 * making Sonic oscillate between ground and air mode every frame.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestS1Ghz3BridgeDropStability {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_GHZ = 0;
    private static final int ACT_3 = 2;

    // Start position: above the last GHZ3 bridge, Sonic will drop onto it
    private static final short START_X = 9628;
    private static final short START_Y = 888;

    // Frames to allow Sonic to fall and land
    private static final int SETTLE_FRAMES = 30;
    // Frames to observe stability after landing
    private static final int STABILITY_FRAMES = 30;

    private static SharedLevel sharedLevel;
    private HeadlessTestFixture fixture;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_1, ZONE_GHZ, ACT_3);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition(START_X, START_Y)
                .build();

        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.reset(fixture.camera().getX());
        }
    }

    @Test
    public void sonicSettlesStablyOnBridgeAfterDrop() {
        // Let Sonic fall onto the bridge
        for (int i = 0; i < SETTLE_FRAMES; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        // Sonic should now be on the bridge in ground mode
        boolean landed = !fixture.sprite().getAir() && fixture.sprite().isOnObject();
        assertTrue("Sonic should have landed on bridge after " + SETTLE_FRAMES
                + " frames, air=" + fixture.sprite().getAir()
                + " onObject=" + fixture.sprite().isOnObject()
                + " y=" + fixture.sprite().getCentreY(), landed);

        // Track air/ground oscillation over the stability window
        int airFrames = 0;
        int groundFrames = 0;
        int transitions = 0;
        boolean wasAir = fixture.sprite().getAir();

        for (int frame = 0; frame < STABILITY_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);

            boolean isAir = fixture.sprite().getAir();
            if (isAir) {
                airFrames++;
            } else {
                groundFrames++;
            }
            if (isAir != wasAir) {
                transitions++;
            }
            wasAir = isAir;
        }

        // Sonic should remain stably on the bridge with no air/ground oscillation.
        // A transition count > 1 indicates the landing/riding positions are mismatched.
        assertFalse("Sonic oscillated between air/ground " + transitions + " times over "
                        + STABILITY_FRAMES + " frames (airFrames=" + airFrames
                        + " groundFrames=" + groundFrames
                        + ") — landing and riding Y positions are mismatched",
                transitions > 1);

        // Sonic should be in ground contact, not airborne
        assertFalse("Sonic should not be airborne while standing on bridge",
                fixture.sprite().getAir());
    }
}
