package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Headless regression for AIZ1 loop traversal via spindash.
 *
 * <p>Spawns Sonic at X=8561 Y=1093 with spindash-speed gSpeed (0x800)
 * rolling right and asserts he passes X=9029 within 180 game frames.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAiz1SpindashLoopTraversal {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;

    private static final short START_X = (short) 8561;
    private static final short START_Y = (short) 1093;
    private static final int PASS_X = 9029;
    private static final int TIMEOUT_FRAMES = 180;
    /** gSpeed matching a fully-charged spindash release (0x800). */
    private static final short SPINDASH_GSPEED = 0x800;

    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic sprite;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sprite = (Sonic) fixture.sprite();
        AizHollowTreeObjectInstance.resetTreeRevealCounter();
        GameServices.level().getObjectManager().reset(0);
    }

    @Test
    public void aiz1SpindashLoop_traversesLoopWithin180Frames() {
        teleportToStart();

        // Verify ground attachment succeeded
        assertTrue("Sonic should be grounded after teleport. air=" + sprite.getAir()
                        + " y=" + sprite.getY() + " gSpd=" + sprite.getGSpeed(),
                !sprite.getAir());

        for (int frame = 0; frame < TIMEOUT_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);

            if (sprite.getX() >= PASS_X) {
                return; // pass
            }
        }

        assertTrue("Expected Sonic to pass X=" + PASS_X + " within " + TIMEOUT_FRAMES
                        + " frames after spindash through AIZ1 loop."
                        + " " + describeState(TIMEOUT_FRAMES),
                sprite.getX() >= PASS_X);
    }

    private void teleportToStart() {
        sprite.setX(START_X);
        sprite.setY(START_Y);
        sprite.setXSpeed(SPINDASH_GSPEED);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed(SPINDASH_GSPEED);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);
        sprite.setAir(false);
        sprite.setRolling(true);
        sprite.setJumping(false);
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setObjectMappingFrameControl(false);
        sprite.setForcedAnimationId(-1);

        Camera camera = fixture.camera();
        camera.updatePosition(true);
        sprite.updateSensors(sprite.getX(), sprite.getY());

        // Snap to ground with max threshold to handle the slope distance
        GameServices.collision().resolveGroundAttachment(sprite, 14, () -> false);
        // Force grounded in case the snap distance was borderline
        sprite.setAir(false);

        GameServices.level().getObjectManager().reset(camera.getX());
    }

    private String describeState(int frame) {
        return "frame=" + frame
                + " x=" + sprite.getX()
                + " y=" + sprite.getY()
                + " xSpeed=" + sprite.getXSpeed()
                + " ySpeed=" + sprite.getYSpeed()
                + " gSpeed=" + sprite.getGSpeed()
                + " angle=0x" + Integer.toHexString(sprite.getAngle() & 0xFF)
                + " groundMode=" + sprite.getGroundMode()
                + " air=" + sprite.getAir()
                + " rolling=" + sprite.getRolling();
    }
}
