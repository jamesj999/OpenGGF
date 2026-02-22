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

import static org.junit.Assert.*;

/**
 * Regression tests for S1 solid-object collision fixes:
 * <ul>
 *   <li>halfHeight always uses airHalfHeight (d2), matching ROM behavior</li>
 *   <li>Landing width uses obActWid, not collision halfWidth</li>
 * </ul>
 * <p>
 * Uses MzBrick-sized params: halfWidth=0x1B, airHH=0x10, groundHH=0x11,
 * activeWidth=0x10.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestHeadlessSonic1ObjectCollision {

    private static final int ZONE_GHZ = 0;
    private static final int ACT_1 = 0;

    // MzBrick solid params from disassembly
    private static final int HALF_WIDTH = 0x1B;       // collision half-width (27px)
    private static final int AIR_HALF_HEIGHT = 0x10;   // d2 = 16px
    private static final int GROUND_HALF_HEIGHT = 0x11; // d3 = 17px (unused by ROM for overlap test)
    private static final int ACTIVE_WIDTH = 0x10;       // obActWid = 16px (landing width)

    private static final int TESTBED_X = 0x0180;
    private static final int TESTBED_FLOOR_Y = 0x0140;
    private static final int TESTBED_SPAWN_Y = TESTBED_FLOOR_Y - 0x60;
    private static final int FLOOR_HALF_WIDTH = 0x90;
    private static final int FLOOR_HALF_HEIGHT = 0x10;
    private static final int LANDING_TIMEOUT_FRAMES = 120;
    private static final int CONTACT_TIMEOUT_FRAMES = 90;
    private static final int STABILITY_FRAMES = 60;

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

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

    /**
     * Walking into a MzBrick-sized solid from the side should block Sonic.
     * Before the fix, groundHH (0x11) was used when grounded, making the
     * collision zone 1px taller and causing false side-collision detection.
     */
    @Test
    public void testSideCollisionUsesAirHalfHeight() {
        // Floor platform
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        TESTBED_X, TESTBED_FLOOR_Y,
                        new SolidObjectParams(FLOOR_HALF_WIDTH, FLOOR_HALF_HEIGHT, FLOOR_HALF_HEIGHT),
                        true, FLOOR_HALF_WIDTH));

        // MzBrick-like object to the right
        int objectX = TESTBED_X + 0x50;
        int objectY = TESTBED_FLOOR_Y - FLOOR_HALF_HEIGHT - AIR_HALF_HEIGHT;

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        objectX, objectY,
                        new SolidObjectParams(HALF_WIDTH, AIR_HALF_HEIGHT, GROUND_HALF_HEIGHT),
                        false, ACTIVE_WIDTH));

        // Spawn Sonic to the left of the object, on the floor
        sprite.setCentreX((short) (objectX - HALF_WIDTH - 0x30));
        sprite.setCentreY((short) TESTBED_SPAWN_Y);
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Land on floor
        boolean landed = false;
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on floor", landed);

        // Walk right into the object
        boolean contactReached = false;
        for (int frame = 0; frame < CONTACT_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, true, false);
            if (sprite.getPushing()) {
                contactReached = true;
                break;
            }
        }
        assertTrue("Sonic should reach side-pushing contact with MzBrick-sized object", contactReached);

        // Verify stability — position should not oscillate
        int stableX = sprite.getX();
        for (int frame = 0; frame < STABILITY_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, true, false);
            assertEquals("Sonic X should stay stable while pushing (frame " + frame + ")",
                    stableX, sprite.getX());
            assertFalse("Sonic should stay grounded while pushing", sprite.getAir());
        }
    }

    /**
     * Sonic should NOT land on a MzBrick-sized solid when outside the active
     * width (0x10) but within the collision halfWidth (0x1B).
     * Before the fix, the full collision width was used for landing checks.
     */
    @Test
    public void testCannotLandOutsideActiveWidth() {
        // Place the solid object
        int objectX = TESTBED_X;
        int objectY = TESTBED_FLOOR_Y;

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        objectX, objectY,
                        new SolidObjectParams(HALF_WIDTH, AIR_HALF_HEIGHT, GROUND_HALF_HEIGHT),
                        false, ACTIVE_WIDTH));

        // Spawn Sonic just outside active width but inside collision width:
        // ACTIVE_WIDTH = 0x10 (16), HALF_WIDTH = 0x1B (27)
        // Position at offset 0x15 (21) from center — outside active, inside collision
        int spawnOffset = ACTIVE_WIDTH + 5; // 21px from object center
        sprite.setCentreX((short) (objectX + spawnOffset));
        sprite.setCentreY((short) (objectY - AIR_HALF_HEIGHT - 0x40));
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Let Sonic fall — should NOT land on the object
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Sonic should have fallen through (still in air or landed on terrain below)
        // The key check is that Sonic's Y position is below the object top
        int sonicBottom = sprite.getCentreY();
        assertTrue("Sonic should fall past the object when outside active width",
                sonicBottom > objectY);
    }

    /**
     * Sonic should land on the object when within the active width.
     */
    @Test
    public void testCanLandWithinActiveWidth() {
        // Floor far below to catch Sonic if landing fails
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        TESTBED_X, TESTBED_FLOOR_Y + 0x80,
                        new SolidObjectParams(FLOOR_HALF_WIDTH, FLOOR_HALF_HEIGHT, FLOOR_HALF_HEIGHT),
                        true, FLOOR_HALF_WIDTH));

        // Place the solid object
        int objectX = TESTBED_X;
        int objectY = TESTBED_FLOOR_Y;

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        objectX, objectY,
                        new SolidObjectParams(HALF_WIDTH, AIR_HALF_HEIGHT, GROUND_HALF_HEIGHT),
                        false, ACTIVE_WIDTH));

        // Spawn Sonic centered on the object
        sprite.setCentreX((short) objectX);
        sprite.setCentreY((short) (objectY - AIR_HALF_HEIGHT - 0x40));
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Let Sonic fall — should land on the object
        boolean landed = false;
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                // Verify landed near the object top, not on the distant floor
                int sonicY = sprite.getCentreY();
                if (sonicY < objectY + AIR_HALF_HEIGHT) {
                    landed = true;
                    break;
                }
            }
        }
        assertTrue("Sonic should land on the object when within active width", landed);
    }

    /**
     * Static solid object for headless testing, with configurable landing width.
     */
    private static final class StaticSolidObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final int x;
        private final int y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;
        private final int landingHalfWidth;

        private StaticSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly,
                int landingHalfWidth) {
            super(new ObjectSpawn(x, y, 0xFE, 0, 0, false, y), "TestStaticSolidObject");
            this.x = x;
            this.y = y;
            this.params = params;
            this.topSolidOnly = topSolidOnly;
            this.landingHalfWidth = landingHalfWidth;
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
        public int getTopLandingHalfWidth(AbstractPlayableSprite player, int collisionHalfWidth) {
            return landingHalfWidth;
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
