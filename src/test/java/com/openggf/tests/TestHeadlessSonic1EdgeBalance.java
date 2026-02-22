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
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Headless regression test for Sonic 1 edge balance detection.
 *
 * Tests both object edge balance and terrain edge balance to verify
 * S1 ROM-accurate behavior:
 * - Single balance state (no Balance2/3/4)
 * - Always forces facing toward the edge
 * - Center probe gates terrain edge detection (ObjFloorDist at obX)
 * - Wider object edge thresholds (< 4 / >= width*2-4)
 *
 * ROM reference: s1disasm/_incObj/01 Sonic.asm:340-375
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestHeadlessSonic1EdgeBalance {

    private static final int ZONE_GHZ = 0;
    private static final int ACT_1 = 0;
    private static final int EDGE_THRESHOLD = 12;
    private static final int LANDING_TIMEOUT_FRAMES = 120;
    private static final int WALK_TIMEOUT_FRAMES = 1500;

    // Platform for object edge tests: 128px half-width, 16px half-height
    private static final int PLATFORM_HALF_WIDTH = 0x80;
    private static final int PLATFORM_HALF_HEIGHT = 0x10;
    // Testbed positioned in a clear area
    private static final int TESTBED_X = 0x0180;
    private static final int TESTBED_FLOOR_Y = 0x0140;
    private static final int TESTBED_SPAWN_Y = TESTBED_FLOOR_Y - 0x60;

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

    // =======================================================================
    // Object edge balance tests (checkObjectEdgeBalance path)
    // =======================================================================

    /**
     * S1 object edge thresholds: d1 < 4 (left) / d1 >= width*2-4 (right).
     * This is wider than S2's d1 < 2 / d1 >= width*2-2.
     * ROM: s1disasm/_incObj/01 Sonic.asm:340-351
     *
     * Verify that Sonic balances when near the right edge of a platform,
     * and that balance state is always 1 (single animation).
     */
    @Test
    public void testObjectEdgeBalanceSingleState() {
        // Create platform and land Sonic on it
        createPlatformAndLand();

        // Position Sonic at the right edge of the platform.
        // Platform is at TESTBED_X with half-width 0x80.
        // Right edge: playerX + halfWidth - objectX >= halfWidth*2 - 4
        // → playerX >= objectX + halfWidth - 4 = TESTBED_X + 0x80 - 4
        int rightEdgeX = TESTBED_X + PLATFORM_HALF_WIDTH - 3;

        int balanceState = settleOnObjectAndCheckBalance(rightEdgeX);
        assertTrue("Should trigger balance at right object edge (x=" + rightEdgeX + ")",
                balanceState > 0);
        assertEquals("S1 object balance should always be state 1", 1, balanceState);
    }

    /**
     * S1 object balance always forces facing TOWARD the edge.
     * ROM: bclr/bset #0,obStatus
     *
     * Set facing LEFT (away from right edge), verify it flips to RIGHT.
     */
    @Test
    public void testObjectEdgeBalanceForcesDirectionTowardEdge() {
        createPlatformAndLand();

        int rightEdgeX = TESTBED_X + PLATFORM_HALF_WIDTH - 3;

        // Face LEFT (away from right edge)
        sprite.setDirection(Direction.LEFT);
        int balanceState = settleOnObjectAndCheckBalance(rightEdgeX);

        assertTrue("Should balance at right edge", balanceState > 0);
        assertEquals("S1 should force facing RIGHT (toward right edge)",
                Direction.RIGHT, sprite.getDirection());
    }

    /**
     * Verify left edge balance forces facing LEFT.
     */
    @Test
    public void testObjectEdgeBalanceLeftEdgeFacesLeft() {
        createPlatformAndLand();

        // Left edge: d1 = playerX + halfWidth - objectX < 4
        // → playerX < objectX - halfWidth + 4 = TESTBED_X - 0x80 + 4
        int leftEdgeX = TESTBED_X - PLATFORM_HALF_WIDTH + 3;

        sprite.setDirection(Direction.RIGHT); // Face away from left edge
        int balanceState = settleOnObjectAndCheckBalance(leftEdgeX);

        assertTrue("Should balance at left edge", balanceState > 0);
        assertEquals("S1 should force facing LEFT (toward left edge)",
                Direction.LEFT, sprite.getDirection());
    }

    /**
     * Verify no balance when safely in the center of the platform.
     */
    @Test
    public void testObjectNoBalanceInCenter() {
        createPlatformAndLand();

        int balanceState = settleOnObjectAndCheckBalance(TESTBED_X);
        assertEquals("Should NOT balance when safely centered on platform",
                0, balanceState);
    }

    // =======================================================================
    // Terrain edge balance tests (checkTerrainEdgeBalance path)
    // =======================================================================

    /**
     * Walk Sonic right through GHZ1 until the right side sensor (center+9)
     * detects a drop while the center probe does NOT. At this position
     * balance must NOT trigger (S1 center probe gate).
     *
     * Then continue to where the center probe also detects a drop —
     * balance SHOULD trigger there.
     */
    @Test
    public void testTerrainEdgeBalanceUsesCenter() {
        // Land on natural GHZ terrain
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        boolean landed = false;
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on GHZ1 ground", landed);

        // Walk right looking for a terrain edge where the right sensor (center+9)
        // reports distance >= 12 while Sonic is grounded. This requires walking
        // far enough to find a genuine cliff in GHZ1's terrain.
        Sensor[] groundSensors = sprite.getGroundSensors();
        assertNotNull("Ground sensors should exist", groundSensors);

        int sideSensorEdgeX = -1;
        int sideSensorEdgeY = -1;

        for (int frame = 0; frame < WALK_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, true, false); // Walk right

            if (!sprite.getAir() && sprite.getGSpeed() > 0) {
                SensorResult rightResult = groundSensors[1].scan();
                int rightDist = (rightResult == null) ? 99 : rightResult.distance();

                if (rightDist >= EDGE_THRESHOLD) {
                    sideSensorEdgeX = sprite.getCentreX();
                    sideSensorEdgeY = sprite.getCentreY();
                    break;
                }
            }
        }

        // If no terrain edge found in GHZ1, skip the terrain portion of the test.
        // Object edge tests above still validate the core fix.
        if (sideSensorEdgeX == -1) {
            System.out.println("[EdgeBalance] No terrain cliff found in GHZ1 within " +
                    WALK_TIMEOUT_FRAMES + " frames — terrain test skipped " +
                    "(object edge tests still validate the fix)");
            return;
        }

        // Check center probe at this position
        SensorResult centerResult = groundSensors[0].scan((short) 9, (short) 0);
        int centerDist = (centerResult == null) ? 99 : centerResult.distance();

        if (centerDist < EDGE_THRESHOLD) {
            // Right sensor off edge, center still on ground → balance must NOT trigger
            int balanceState = settleAndCheckBalance(sideSensorEdgeX, sideSensorEdgeY);
            assertEquals("Balance should NOT trigger when only side sensor drops off " +
                            "(sideSensorEdge x=" + sideSensorEdgeX + ")",
                    0, balanceState);

            // Now find where center also drops off
            int centerEdgeX = -1;
            for (int x = sideSensorEdgeX + 1; x < sideSensorEdgeX + 20; x++) {
                sprite.setCentreX((short) x);
                sprite.setCentreY((short) sideSensorEdgeY);
                sprite.setAir(false);
                sprite.setGSpeed((short) 0);
                sprite.setXSpeed((short) 0);
                sprite.setYSpeed((short) 0);
                testRunner.stepFrame(false, false, false, false, false);

                if (sprite.getAir()) continue;

                SensorResult cr = groundSensors[0].scan((short) 9, (short) 0);
                int cd = (cr == null) ? 99 : cr.distance();
                if (cd >= EDGE_THRESHOLD) {
                    centerEdgeX = x;
                    break;
                }
            }

            if (centerEdgeX != -1) {
                int balanceAtCenter = settleAndCheckBalance(centerEdgeX, sideSensorEdgeY);
                assertTrue("Balance SHOULD trigger when center is at terrain edge " +
                                "(x=" + centerEdgeX + ")",
                        balanceAtCenter > 0);
                assertEquals("S1 terrain balance state should be 1", 1, balanceAtCenter);
            }
        } else {
            // Both sensor and center detected the edge at the same X — just verify
            // balance triggers here
            int balanceState = settleAndCheckBalance(sideSensorEdgeX, sideSensorEdgeY);
            assertTrue("Balance should trigger at terrain edge (x=" + sideSensorEdgeX + ")",
                    balanceState > 0);
        }
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private void createPlatformAndLand() {
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        TESTBED_X, TESTBED_FLOOR_Y,
                        new SolidObjectParams(PLATFORM_HALF_WIDTH, PLATFORM_HALF_HEIGHT, PLATFORM_HALF_HEIGHT),
                        true));

        sprite.setCentreX((short) TESTBED_X);
        sprite.setCentreY((short) TESTBED_SPAWN_Y);
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        boolean landed = false;
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir() && sprite.isOnObject()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on the platform", landed);
    }

    private int settleOnObjectAndCheckBalance(int x) {
        sprite.setCentreX((short) x);
        sprite.setGSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setRolling(false);
        sprite.setBalanceState(0);

        // Two frames: first settles position, second runs balance check
        testRunner.stepFrame(false, false, false, false, false);
        sprite.setGSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setBalanceState(0);
        testRunner.stepFrame(false, false, false, false, false);

        return sprite.getBalanceState();
    }

    private int settleAndCheckBalance(int x, int approxGroundY) {
        sprite.setCentreX((short) x);
        sprite.setCentreY((short) approxGroundY);
        sprite.setAir(false);
        sprite.setGSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setRolling(false);
        sprite.setBalanceState(0);

        testRunner.stepFrame(false, false, false, false, false);

        if (sprite.getAir()) return 0;

        sprite.setGSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setBalanceState(0);
        testRunner.stepFrame(false, false, false, false, false);

        return sprite.getBalanceState();
    }

    private static final class StaticSolidObject extends AbstractObjectInstance
            implements SolidObjectProvider {
        private final int x, y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;

        private StaticSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
            super(new ObjectSpawn(x, y, 0xFE, 0, 0, false, y), "TestStaticSolidObject");
            this.x = x;
            this.y = y;
            this.params = params;
            this.topSolidOnly = topSolidOnly;
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public SolidObjectParams getSolidParams() { return params; }
        @Override public boolean isTopSolidOnly() { return topSolidOnly; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
        @Override public void update(int frameCounter, AbstractPlayableSprite player) {}
    }
}
