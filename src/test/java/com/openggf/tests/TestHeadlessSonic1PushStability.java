package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sonic 1 push stability test: verifies that Sonic does not jitter when
 * pushing against a static solid object. The original ROM does not exhibit
 * 1px oscillation; any jitter indicates a subpixel accumulation bug in the
 * solid contact resolution path.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestHeadlessSonic1PushStability {

    private static final int ZONE_GHZ = 0;
    private static final int ACT_1 = 0;
    private static final int TESTBED_X = 0x0180;
    private static final int TESTBED_FLOOR_Y = 0x0140;
    private static final int TESTBED_SPAWN_Y = TESTBED_FLOOR_Y - 0x60;
    private static final int LANDING_TIMEOUT_FRAMES = 120;
    private static final int CONTACT_TIMEOUT_FRAMES = 90;
    private static final int CONTACT_WARMUP_FRAMES = 10;
    private static final int STABILITY_FRAMES = 60;
    private static final int FLOOR_HALF_WIDTH = 0x90;
    private static final int FLOOR_HALF_HEIGHT = 0x10;
    private static final int WALL_HALF_WIDTH = 0x18;
    private static final int WALL_HALF_HEIGHT = 0x18;
    private static final int OBJECT_GAP = 4;
    private static final int PUSH_START_OFFSET = 0x30;

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 0, (short) 0);

        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(ZONE_GHZ, ACT_1);
        GroundSensor.setLevelManager(LevelManager.getInstance());
        camera.updatePosition(true);

        testRunner = new HeadlessTestRunner(sprite);
    }

    @Test
    public void testNoJitterWhenPushingStaticObjectToRight() {
        assertNoPushJitter(true);
    }

    @Test
    public void testNoJitterWhenPushingStaticObjectToLeft() {
        assertNoPushJitter(false);
    }

    private void assertNoPushJitter(boolean pushRight) {
        // Create a floor platform for Sonic to stand on
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        TESTBED_X,
                        TESTBED_FLOOR_Y,
                        new SolidObjectParams(FLOOR_HALF_WIDTH, FLOOR_HALF_HEIGHT, FLOOR_HALF_HEIGHT),
                        true));

        sprite.setCentreX((short) (TESTBED_X + (pushRight ? -PUSH_START_OFFSET : PUSH_START_OFFSET)));
        sprite.setCentreY((short) TESTBED_SPAWN_Y);
        sprite.setAir(true);

        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Wait for Sonic to land on the floor
        boolean landed = false;
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on the static floor testbed", landed);

        // Place the wall object next to Sonic
        int objectX = sprite.getCentreX()
                + (pushRight ? WALL_HALF_WIDTH + OBJECT_GAP : -(WALL_HALF_WIDTH + OBJECT_GAP));
        int objectY = sprite.getCentreY();

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        objectX,
                        objectY,
                        new SolidObjectParams(WALL_HALF_WIDTH, WALL_HALF_HEIGHT, WALL_HALF_HEIGHT),
                        false));

        // Walk toward the wall until pushing contact
        boolean pressingLeft = !pushRight;
        boolean contactReached = false;
        for (int frame = 0; frame < CONTACT_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, pressingLeft, pushRight, false);
            if (sprite.getPushing()) {
                contactReached = true;
                break;
            }
        }
        assertTrue("Sonic should reach side-pushing contact (" + directionName(pushRight) + ")", contactReached);

        // Warmup: let subpixels stabilise
        for (int frame = 0; frame < CONTACT_WARMUP_FRAMES; frame++) {
            testRunner.stepFrame(false, false, pressingLeft, pushRight, false);
        }

        // Stability window: position must not oscillate
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int transitionCount = 0;
        Integer previousX = null;
        for (int frame = 0; frame < STABILITY_FRAMES; frame++) {
            testRunner.stepFrame(false, false, pressingLeft, pushRight, false);
            int x = sprite.getX();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            if (previousX != null && previousX != x) {
                transitionCount++;
            }
            previousX = x;
            assertFalse("Sonic should stay grounded while pushing (" + directionName(pushRight) + ")", sprite.getAir());
        }

        assertEquals("Sonic X position should stay stable while pushing static object (" + directionName(pushRight)
                        + "), minX=" + minX + ", maxX=" + maxX + ", transitions=" + transitionCount,
                minX, maxX);
        assertEquals("Sonic X should not oscillate while pushing static object (" + directionName(pushRight) + ")",
                0, transitionCount);
    }

    private static String directionName(boolean pushRight) {
        return pushRight ? "toward right-side object" : "toward left-side object";
    }

    private static final class StaticSolidObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final int x;
        private final int y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;

        private StaticSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
            super(new ObjectSpawn(x, y, 0xFE, 0, 0, false, y), "TestStaticSolidObject");
            this.x = x;
            this.y = y;
            this.params = params;
            this.topSolidOnly = topSolidOnly;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public boolean isTopSolidOnly() {
            return topSolidOnly;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for headless collision tests.
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            // Static object.
        }
    }
}
