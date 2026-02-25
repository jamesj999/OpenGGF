package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Diagnostic tests for ARZ1 loop traversal.
 *
 * <p>In the live game, Sonic gets stuck near X~2600 when running through
 * the loop section. These tests investigate whether the collision path
 * (primary vs secondary) is incorrectly switched by a plane switcher
 * during loop traversal.
 *
 * <p>Key findings from investigation:
 * <ul>
 *   <li>Primary path (0x0C/0x0D): NO wall at X=2595 (chunk 204 has primaryCollisionIdx=0)</li>
 *   <li>Secondary path (0x0E/0x0F): WALL at X=2595 (chunk 204 has altCollisionIdx=251, fully solid)</li>
 *   <li>Plane switcher at (2576, 576, subtype=0x0A) switches to SECONDARY when
 *       playerX crosses 2576 while playerY is in [448, 704) — this Y range is reached
 *       during loop traversal when Sonic curves upward</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestArzRunRight {
    @Rule public RequiresRomRule romRule = new RequiresRomRule();
    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    // Spring-loop start position (same as TestArzSpringLoop)
    private static final short START_X = 2468;
    private static final short START_Y = 841;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        SonicConfigurationService cs = SonicConfigurationService.getInstance();
        sprite = new Sonic(cs.getString(SonicConfiguration.MAIN_CHARACTER_CODE), START_X, START_Y);
        SpriteManager.getInstance().addSprite(sprite);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);
        LevelManager.getInstance().loadZoneAndAct(2, 0);
        GroundSensor.setLevelManager(LevelManager.getInstance());
        sprite.setX(START_X);
        sprite.setY(START_Y);
        camera.updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(camera.getX());
        testRunner = new HeadlessTestRunner(sprite);
    }

    @Test
    public void dumpPlaneSwitchers() {
        var level = LevelManager.getInstance().getCurrentLevel();
        System.out.println("=== ALL PLANE SWITCHERS IN ARZ1 ===");
        for (var obj : level.getObjects()) {
            if (obj.objectId() == 0x03) {
                int sub = obj.subtype();
                int sizeIdx = sub & 0x03;
                int[] spans = {0x20, 0x40, 0x80, 0x100};
                boolean horiz = (sub & 0x04) != 0;
                int pathSide1 = (sub & 0x08) != 0 ? 1 : 0;
                int pathSide0 = (sub & 0x10) != 0 ? 1 : 0;
                boolean groundedOnly = (sub & 0x80) != 0;
                String type = horiz ? "Y-based(horizontal)" : "X-based(vertical)";
                int half = spans[sizeIdx];

                String spanDesc;
                if (horiz) {
                    // horizontal: side based on Y, span on X axis
                    spanDesc = String.format("X∈[%d,%d) side=Y≷%d",
                            obj.x() - half, obj.x() + half, obj.y());
                } else {
                    // vertical: side based on X, span on Y axis
                    spanDesc = String.format("Y∈[%d,%d) side=X≷%d",
                            obj.y() - half, obj.y() + half, obj.x());
                }

                System.out.printf("PS: X=%d Y=%d sub=0x%02X flags=0x%02X | %s half=%d %s " +
                                "left/above→path%d right/below→path%d%s%n",
                        obj.x(), obj.y(), sub, obj.renderFlags(),
                        type, half, spanDesc,
                        pathSide0, pathSide1,
                        groundedOnly ? " [grounded-only]" : "");
            }
        }
    }

    @Test
    public void dumpCollisionDataAtWall() {
        LevelManager lm = LevelManager.getInstance();
        System.out.println("Chunk solidity at wall location (X=2580-2610, Y=440-850):");
        for (int y = 440; y <= 850; y += 10) {
            for (int x = 2560; x <= 2610; x += 8) {
                var desc = lm.getChunkDescAt((byte) 0, x, y);
                if (desc != null) {
                    boolean pTop = desc.isSolidityBitSet(0x0C);
                    boolean pLrb = desc.isSolidityBitSet(0x0D);
                    boolean sTop = desc.isSolidityBitSet(0x0E);
                    boolean sLrb = desc.isSolidityBitSet(0x0F);
                    if (pTop || pLrb || sTop || sLrb) {
                        System.out.printf("  (%d,%d) chunk=%d | P:%b/%b S:%b/%b%n",
                                x, y, desc.getChunkIndex(), pTop, pLrb, sTop, sLrb);
                    }
                }
            }
        }
    }

    /**
     * Test running right from (2468, 841) on PRIMARY path (default).
     * This should succeed - primary path has no wall at X=2595.
     */
    @Test
    public void testRunRightOnPrimaryPath() {
        // Verify starting on primary
        assertEquals("Should start on primary top", 0x0C, sprite.getTopSolidBit());
        assertEquals("Should start on primary lrb", 0x0D, sprite.getLrbSolidBit());

        for (int f = 0; f < 400; f++) {
            Camera.getInstance().updateBoundaryEasing();
            testRunner.stepFrame(false, false, false, true, false); // hold right

            if (sprite.getX() > 2700) {
                break;
            }
        }
        assertTrue("Sonic should pass X=2600 on primary path. Actual X=" + sprite.getX(),
                sprite.getX() > 2600);
    }

    /**
     * Simulate spring-loop with RIGHT held after spring bounce.
     * This more closely matches actual gameplay where the user holds right.
     */
    @Test
    public void debugSpringLoopHoldingRight() {
        Camera cam = Camera.getInstance();

        // Phase 1: Run left to hit the spring
        for (int f = 0; f < 600; f++) {
            cam.updateBoundaryEasing();
            testRunner.stepFrame(false, false, true, false, false);
            if (sprite.getGSpeed() > 0x200) {
                System.out.printf("SPRING at f%d: X=%d Y=%d G=%d%n",
                        f, sprite.getX(), sprite.getY(), sprite.getGSpeed());
                break;
            }
        }

        // Phase 2: Hold RIGHT after spring bounce
        byte prevTop = sprite.getTopSolidBit();
        byte prevLrb = sprite.getLrbSolidBit();
        int prevX = sprite.getX();
        short prevG = sprite.getGSpeed();

        for (int f = 0; f < 600; f++) {
            cam.updateBoundaryEasing();
            testRunner.stepFrame(false, false, false, true, false); // hold RIGHT

            int x = sprite.getX();
            int y = sprite.getY();
            short g = sprite.getGSpeed();
            byte top = sprite.getTopSolidBit();
            byte lrb = sprite.getLrbSolidBit();
            int dx = x - prevX;

            if (top != prevTop || lrb != prevLrb) {
                System.out.printf("*** PATH CHANGE f%d: X=%d Y=%d 0x%02X/0x%02X -> 0x%02X/0x%02X%n",
                        f, x, y, prevTop, prevLrb, top, lrb);
                prevTop = top;
                prevLrb = lrb;
            }

            boolean anomaly = (g == 0 && prevG != 0 && !sprite.getAir()) || (dx < -3);
            boolean nearTarget = (x >= 2550 && x <= 2650);
            if (f < 20 || f % 30 == 0 || nearTarget || anomaly) {
                System.out.printf("f%d: X=%d Y=%d G=%d Air=%b Ang=0x%02X Mode=%s path=0x%02X/0x%02X dx=%d%n",
                        f, x, y, g, sprite.getAir(), sprite.getAngle() & 0xFF,
                        sprite.getGroundMode(), top, lrb, dx);
            }

            if (g == 0 && prevG != 0 && !sprite.getAir() && f > 5) {
                System.out.printf("*** STUCK at f%d: X=%d Y=%d path=0x%02X/0x%02X%n",
                        f, x, y, top, lrb);
                break;
            }

            if (x > 3200) {
                System.out.printf("*** PASSED 3200 at f%d%n", f);
                break;
            }

            prevX = x;
            prevG = g;
        }

        System.out.printf("FINAL: X=%d Y=%d G=%d path=0x%02X/0x%02X%n",
                sprite.getX(), sprite.getY(), sprite.getGSpeed(),
                sprite.getTopSolidBit(), sprite.getLrbSolidBit());
    }
}
