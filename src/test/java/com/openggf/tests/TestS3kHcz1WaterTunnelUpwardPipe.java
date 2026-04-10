package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Headless regression for HCZ Act 1 upward water tunnel pipe.
 *
 * <p>Places Sonic at (12107, 1715) top-left inside the rightmost vertical
 * pipe. The water tunnel handler pulls Sonic upward through the straight
 * section, then the twisting loop captures and guides him through the
 * curved upper path to at least y=1100.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kHcz1WaterTunnelUpwardPipe {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static final short START_X = 12107;
    private static final short START_Y = 1715;
    private static final int TARGET_Y = 1100;
    private static final int MAX_FRAMES = 180;

    private static Object oldSkipIntros, oldMainCharacter, oldSidekickCharacter;
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
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_HCZ, 0);
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
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sprite = (Sonic) fixture.sprite();

        sprite.setX(START_X);
        sprite.setY(START_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        // Do NOT zero gSpeed — ROM tunnel handler doesn't touch ground_vel.
        // The twisting loop's flipped-entry detection needs gSpeed < 0.
        sprite.setGSpeed((short) 0);
        sprite.setAngle((byte) 0);
        sprite.setAir(true);
        sprite.setRolling(false);
        sprite.setJumping(false);
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setForcedAnimationId(-1);

        Camera camera = fixture.camera();
        camera.updatePosition(true);
        GameServices.level().getObjectManager().reset(camera.getX());
    }

    /**
     * Full scenario: tunnel + twisting loop. Sonic must reach y &lt; 1100
     * within 180 frames.
     */
    @Test
    public void hcz1Pipe_sonicReachesTarget() {
        int minY = START_Y;

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);

            int y = sprite.getY();
            if (y < minY) minY = y;

            if (minY < TARGET_Y) {
                return;
            }

            if (frame == 60 && minY > START_Y - 100) {
                fail("Stuck after 60 frames. minY=" + minY
                        + " y=" + y + " cy=" + sprite.getCentreY()
                        + " ySpd=" + sprite.getYSpeed()
                        + " gSpd=" + sprite.getGSpeed()
                        + " objCtrl=" + sprite.isObjectControlled()
                        + " air=" + sprite.getAir());
            }
        }

        fail("Did not reach y=" + TARGET_Y + " in " + MAX_FRAMES
                + " frames. minY=" + minY + " y=" + sprite.getY()
                + " cy=" + sprite.getCentreY()
                + " ySpd=" + sprite.getYSpeed()
                + " gSpd=" + sprite.getGSpeed()
                + " objCtrl=" + sprite.isObjectControlled());
    }
}
