package com.openggf.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic1.Sonic1Level;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.GroundMode;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Grouped headless tests for Sonic 1 GHZ Act 1.
 *
 * Level data is loaded once via {@code @BeforeClass}; sprite, camera, and game
 * state are reset per test via {@link TestEnvironment#resetPerTest()}.
 *
 * Merged from:
 * <ul>
 *   <li>TestSonic1GhzSlopeTopDiagnostic</li>
 *   <li>TestSonic1GhzTunnel</li>
 *   <li>TestHeadlessSonic1PushStability</li>
 *   <li>TestHeadlessSonic1EdgeBalance</li>
 *   <li>TestHeadlessSonic1ObjectCollision</li>
 *   <li>TestCrabmeatSpawnPosition</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestS1Ghz1Headless {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_GHZ = 0;
    private static final int ACT_1 = 0;
    private static String mainCharCode;

    @BeforeClass
    public static void loadLevel() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        SonicConfigurationService cs = SonicConfigurationService.getInstance();
        mainCharCode = cs.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        // LevelManager.loadCurrentLevel() needs a player sprite in SpriteManager.
        Sonic temp = new Sonic(mainCharCode, (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(temp);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(temp);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(ZONE_GHZ, ACT_1);
        GroundSensor.setLevelManager(LevelManager.getInstance());
    }

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() {
        TestEnvironment.resetPerTest();
        sprite = new Sonic(mainCharCode, (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(sprite);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        // Restore camera bounds from the loaded level -- resetPerTest() zeroes them
        // but the level data is still valid since we skip LevelManager reset.
        Level level = LevelManager.getInstance().getCurrentLevel();
        if (level != null) {
            camera.setMinX((short) level.getMinX());
            camera.setMaxX((short) level.getMaxX());
            camera.setMinY((short) level.getMinY());
            camera.setMaxY((short) level.getMaxY());
        }

        // Reset object manager to clear dynamic objects and respawn state from
        // previous tests. Uses camera X=0 since sprite starts at origin.
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.reset(camera.getX());
        }

        camera.updatePosition(true);
        testRunner = new HeadlessTestRunner(sprite);
    }

    // ========================================================================
    // From TestSonic1GhzSlopeTopDiagnostic
    // ========================================================================

    private static final int TARGET_X = 1900;
    private static final int TARGET_Y = 857;

    @Test
    public void diagnosticSlopeTopWallModeBlip() {
        sprite.setCentreX((short) TARGET_X);
        sprite.setCentreY((short) TARGET_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setTopSolidBit((byte) 0x0D);
        sprite.setLrbSolidBit((byte) 0x0E);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);

        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        System.out.println("=== GHZ slope-top diagnostic at approx (1900,857) ===");
        System.out.printf("Start settled: x=%d y=%d cx=%d cy=%d air=%b angle=0x%02X mode=%s%n",
                sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode());

        Sensor[] groundSensors = sprite.getGroundSensors();
        for (int frame = 0; frame < 120; frame++) {
            testRunner.stepFrame(false, false, false, false, false);

            SensorResult left = groundSensors[0].scan();
            SensorResult right = groundSensors[1].scan();
            int leftD = left != null ? left.distance() : -99;
            int rightD = right != null ? right.distance() : -99;
            int leftA = left != null ? left.angle() & 0xFF : -1;
            int rightA = right != null ? right.angle() & 0xFF : -1;

            System.out.printf("f=%03d x=%d y=%d cx=%d cy=%d g=%d xs=%d ys=%d air=%b angle=0x%02X mode=%s | "
                            + "L(d=%d a=0x%02X) R(d=%d a=0x%02X)%n",
                    frame,
                    sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(),
                    sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                    leftD, leftA, rightD, rightA);
        }
    }

    @Test
    public void diagnosticSlopeTopWithIncomingSlopeAngle() {
        sprite.setCentreX((short) TARGET_X);
        sprite.setCentreY((short) TARGET_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);

        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Simulate entering this flat-top tile from a descending slope.
        sprite.setAir(false);
        sprite.setAngle((byte) 0xFC);
        sprite.setGroundMode(GroundMode.GROUND);

        System.out.println("=== GHZ slope-top diagnostic with incoming angle 0xFC ===");
        for (int frame = 0; frame < 20; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            Sensor[] groundSensors = sprite.getGroundSensors();
            SensorResult left = groundSensors[0].scan();
            SensorResult right = groundSensors[1].scan();
            System.out.printf(
                    "f=%02d angle=0x%02X mode=%s air=%b | L(d=%d a=0x%02X tid=%d) R(d=%d a=0x%02X tid=%d)%n",
                    frame,
                    sprite.getAngle() & 0xFF,
                    sprite.getGroundMode(),
                    sprite.getAir(),
                    left != null ? left.distance() : -99,
                    left != null ? left.angle() & 0xFF : -1,
                    left != null ? left.tileId() : -1,
                    right != null ? right.distance() : -99,
                    right != null ? right.angle() & 0xFF : -1,
                    right != null ? right.tileId() : -1);
        }
    }

    @Test
    public void diagnosticApproachSlopeTopFromLeft() {
        sprite.setX((short) 1750);
        sprite.setY((short) 900);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);

        for (int i = 0; i < 40; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        System.out.println("=== GHZ approach diagnostic (left -> right) ===");
        System.out.printf("Start settled: x=%d y=%d cx=%d cy=%d air=%b angle=0x%02X mode=%s%n",
                sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode());

        GroundMode lastMode = sprite.getGroundMode();
        int lastAngle = sprite.getAngle() & 0xFF;
        for (int frame = 0; frame < 260; frame++) {
            boolean holdRight = frame < 180;
            testRunner.stepFrame(false, false, false, holdRight, false);

            int angle = sprite.getAngle() & 0xFF;
            GroundMode mode = sprite.getGroundMode();
            boolean interesting = mode != lastMode || angle != lastAngle
                    || mode == GroundMode.LEFTWALL || mode == GroundMode.RIGHTWALL;
            if (interesting) {
                Sensor[] groundSensors = sprite.getGroundSensors();
                SensorResult left = groundSensors[0].scan();
                SensorResult right = groundSensors[1].scan();
                int leftD = left != null ? left.distance() : -99;
                int rightD = right != null ? right.distance() : -99;
                int leftA = left != null ? left.angle() & 0xFF : -1;
                int rightA = right != null ? right.angle() & 0xFF : -1;

                System.out.printf(
                        "f=%03d holdR=%b x=%d y=%d cx=%d cy=%d g=%d xs=%d ys=%d air=%b angle=0x%02X mode=%s | "
                                + "L(d=%d a=0x%02X) R(d=%d a=0x%02X)%n",
                        frame, holdRight, sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                        sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(), sprite.getAir(), angle, mode,
                        leftD, leftA, rightD, rightA);
            }

            lastMode = mode;
            lastAngle = angle;
        }
    }

    @Test
    public void diagnosticSlopeTopMicroNudges() {
        System.out.println("=== GHZ slope-top micro-nudges around (1900,857) ===");

        for (int startOffset = -2; startOffset <= 2; startOffset++) {
            slopeResetAtTarget(startOffset);
            System.out.printf("startOffset=%+d settled: x=%d y=%d cx=%d cy=%d air=%b angle=0x%02X mode=%s%n",
                    startOffset, sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode());

            slopeRunNudgeSequence("RIGHT", false, true);
            slopeResetAtTarget(startOffset);
            slopeRunNudgeSequence("LEFT ", true, false);
        }
    }

    private void slopeRunNudgeSequence(String label, boolean left, boolean right) {
        Sensor[] groundSensors = sprite.getGroundSensors();
        for (int frame = 0; frame < 8; frame++) {
            testRunner.stepFrame(false, false, left, right, false);

            SensorResult leftResult = groundSensors[0].scan();
            SensorResult rightResult = groundSensors[1].scan();
            int leftD = leftResult != null ? leftResult.distance() : -99;
            int rightD = rightResult != null ? rightResult.distance() : -99;
            int leftA = leftResult != null ? leftResult.angle() & 0xFF : -1;
            int rightA = rightResult != null ? rightResult.angle() & 0xFF : -1;

            System.out.printf("  %s f=%02d x=%d y=%d cx=%d cy=%d g=%d xs=%d ys=%d air=%b angle=0x%02X mode=%s | "
                            + "L(d=%d a=0x%02X) R(d=%d a=0x%02X)%n",
                    label, frame, sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(),
                    sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                    leftD, leftA, rightD, rightA);
        }
    }

    private void slopeResetAtTarget(int xOffset) {
        sprite.setX((short) (TARGET_X + xOffset));
        sprite.setY((short) TARGET_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);

        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }
    }

    private void slopeResetAtTopLeft(int x, int y) {
        sprite.setX((short) x);
        sprite.setY((short) y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);
        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }
    }

    @Test
    public void diagnosticCentre1896LeftNudge() {
        sprite.setCentreX((short) 1896);
        sprite.setCentreY((short) 857);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);
        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        Sensor[] groundSensors = sprite.getGroundSensors();
        System.out.println("=== diagnosticCentre1896LeftNudge ===");
        System.out.printf("topSolidBit=0x%02X lrbSolidBit=0x%02X xRad=%d yRad=%d%n",
                sprite.getTopSolidBit() & 0xFF,
                sprite.getLrbSolidBit() & 0xFF,
                sprite.getXRadius(),
                sprite.getYRadius());
        System.out.printf("width=%d height=%d%n", sprite.getWidth(), sprite.getHeight());
        for (int frame = 0; frame < 10; frame++) {
            testRunner.stepFrame(false, false, true, false, false);
            SensorResult left = groundSensors[0].scan();
            SensorResult right = groundSensors[1].scan();
            System.out.printf("f=%02d x=%d y=%d cx=%d cy=%d g=%d xs=%d ys=%d air=%b angle=0x%02X mode=%s | "
                            + "L(d=%d a=0x%02X tid=%d) R(d=%d a=0x%02X tid=%d)%n",
                    frame,
                    sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(), sprite.getAir(),
                    sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                    left != null ? left.distance() : -99,
                    left != null ? left.angle() & 0xFF : -1,
                    left != null ? left.tileId() : -1,
                    right != null ? right.distance() : -99,
                    right != null ? right.angle() & 0xFF : -1,
                    right != null ? right.tileId() : -1);
        }
    }

    @Test
    public void diagnosticFindEmbeddedPositionsNear1900() {
        System.out.println("=== diagnosticFindEmbeddedPositionsNear1900 ===");
        Sensor[] groundSensors = sprite.getGroundSensors();
        for (int cx = 1888; cx <= 1912; cx++) {
            sprite.setCentreX((short) cx);
            sprite.setCentreY((short) 857);
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            sprite.setAir(true);
            sprite.setAngle((byte) 0);
            sprite.setGroundMode(GroundMode.GROUND);
            for (int i = 0; i < 20; i++) {
                testRunner.stepFrame(false, false, false, false, false);
            }

            SensorResult left = groundSensors[0].scan();
            SensorResult right = groundSensors[1].scan();
            int leftD = left != null ? left.distance() : -99;
            int rightD = right != null ? right.distance() : -99;
            boolean embedded = leftD <= 0 && rightD <= 0;
            if (embedded || cx == 1900) {
                System.out.printf("cx=%d x=%d embedded=%b angle=0x%02X mode=%s | L(d=%d a=0x%02X tid=%d) R(d=%d a=0x%02X tid=%d)%n",
                        sprite.getCentreX(), sprite.getX(), embedded, sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                        leftD, left != null ? left.angle() & 0xFF : -1, left != null ? left.tileId() : -1,
                        rightD, right != null ? right.angle() & 0xFF : -1, right != null ? right.tileId() : -1);
            }
        }
    }

    @Test
    public void diagnosticFindImageStateNear1902() {
        System.out.println("=== diagnosticFindImageStateNear1902 ===");
        for (int startX = 1888; startX <= 1912; startX++) {
            slopeResetAtTopLeft(startX, TARGET_Y);
            for (int frame = 0; frame < 24; frame++) {
                testRunner.stepFrame(false, false, true, false, false); // hold left
                int x = sprite.getX();
                int angle = sprite.getAngle() & 0xFF;
                if (x >= 1901 && x <= 1903 && sprite.getAir() && angle == 0xCC) {
                    System.out.printf("HIT startX=%d frame=%d x=%d cx=%d y=%d g=%d xs=%d ys=%d mode=%s%n",
                            startX, frame, x, sprite.getCentreX(), sprite.getY(),
                            sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGroundMode());
                }
            }
        }
    }

    @Test
    public void diagnostic1903SingleLeftStep() {
        System.out.println("=== diagnostic1903SingleLeftStep ===");
        slopeResetAtTopLeft(1903, TARGET_Y);
        sprite.setTopSolidBit((byte) 0x0D);
        sprite.setLrbSolidBit((byte) 0x0E);
        Sonic1Level level = (Sonic1Level) LevelManager.getInstance().getCurrentLevel();
        int aX0 = sprite.getCentreX() - sprite.getXRadius();
        int bX0 = sprite.getCentreX() + sprite.getXRadius();
        int footY0 = sprite.getCentreY() + sprite.getYRadius();
        slopePrintVerticalScanState("A0", aX0, footY0);
        slopePrintVerticalScanState("B0", bX0, footY0);
        Sensor[] sensors = sprite.getGroundSensors();
        SensorResult a0 = sensors[0].scan();
        SensorResult b0 = sensors[1].scan();
        int mapX0 = (sprite.getCentreX() & 0xFFFF) / 256;
        int mapY0 = (sprite.getCentreY() & 0xFFFF) / 256;
        int raw0 = level.getRawFgValue(mapX0, mapY0);
        System.out.printf("before: x=%d cx=%d y=%d air=%b angle=0x%02X mode=%s g=%d xs=%d ys=%d | "
                        + "loopLow=%b raw=0x%02X map=(%d,%d) | A(d=%d a=0x%02X tid=%d) B(d=%d a=0x%02X tid=%d)%n",
                sprite.getX(), sprite.getCentreX(), sprite.getY(), sprite.getAir(),
                sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(),
                sprite.isLoopLowPlane(), raw0, mapX0, mapY0,
                a0 != null ? a0.distance() : -99, a0 != null ? a0.angle() & 0xFF : -1,
                a0 != null ? a0.tileId() : -1,
                b0 != null ? b0.distance() : -99, b0 != null ? b0.angle() & 0xFF : -1,
                b0 != null ? b0.tileId() : -1);

        testRunner.stepFrame(false, false, true, false, false);
        SensorResult a1 = sensors[0].scan();
        SensorResult b1 = sensors[1].scan();
        int mapX1 = (sprite.getCentreX() & 0xFFFF) / 256;
        int mapY1 = (sprite.getCentreY() & 0xFFFF) / 256;
        int raw1 = level.getRawFgValue(mapX1, mapY1);
        System.out.printf("after : x=%d cx=%d y=%d air=%b angle=0x%02X mode=%s g=%d xs=%d ys=%d | "
                        + "loopLow=%b raw=0x%02X map=(%d,%d) | A(d=%d a=0x%02X tid=%d) B(d=%d a=0x%02X tid=%d)%n",
                sprite.getX(), sprite.getCentreX(), sprite.getY(), sprite.getAir(),
                sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(),
                sprite.isLoopLowPlane(), raw1, mapX1, mapY1,
                a1 != null ? a1.distance() : -99, a1 != null ? a1.angle() & 0xFF : -1,
                a1 != null ? a1.tileId() : -1,
                b1 != null ? b1.distance() : -99, b1 != null ? b1.angle() & 0xFF : -1,
                b1 != null ? b1.tileId() : -1);
        int aX1 = sprite.getCentreX() - sprite.getXRadius();
        int bX1 = sprite.getCentreX() + sprite.getXRadius();
        int footY1 = sprite.getCentreY() + sprite.getYRadius();
        slopePrintVerticalScanState("A1", aX1, footY1);
        slopePrintVerticalScanState("B1", bX1, footY1);
    }

    @Test
    public void diagnostic1903SingleLeftStepWithTop0D() {
        System.out.println("=== diagnostic1903SingleLeftStepWithTop0D ===");
        slopeResetAtTopLeft(1903, TARGET_Y);
        sprite.setTopSolidBit((byte) 0x0D);
        sprite.setLrbSolidBit((byte) 0x0E);
        diagnostic1903SingleLeftStep();
    }

    private void slopePrintVerticalScanState(String label, int x, int y) {
        LevelManager lm = LevelManager.getInstance();
        byte topBit = sprite.getTopSolidBit();
        System.out.printf("  %s sensor at (%d,%d) topBit=0x%02X%n", label, x, y, topBit & 0xFF);
        slopePrintTileState(label + " cur ", lm, x, y, topBit);
        slopePrintTileState(label + " next", lm, x, y + 16, topBit);
        slopePrintTileState(label + " prev", lm, x, y - 16, topBit);
    }

    private void slopePrintTileState(String label, LevelManager lm, int x, int y, byte solidityBit) {
        ChunkDesc desc = lm.getChunkDescAt((byte) 0, x, y, sprite.isLoopLowPlane());
        if (desc == null) {
            System.out.printf("    %s: null desc at (%d,%d)%n", label, x, y);
            return;
        }
        int word = desc.get();
        boolean bC = desc.isSolidityBitSet(0x0C);
        boolean bD = desc.isSolidityBitSet(0x0D);
        boolean bE = desc.isSolidityBitSet(0x0E);
        boolean bF = desc.isSolidityBitSet(0x0F);
        if (!desc.isSolidityBitSet(solidityBit)) {
            System.out.printf("    %s: non-solid desc=0x%04X tile=(%d,%d) h=%b v=%b bits[C=%b D=%b E=%b F=%b]%n",
                    label, word, x >> 4, y >> 4, desc.getHFlip(), desc.getVFlip(), bC, bD, bE, bF);
            return;
        }
        SolidTile tile = lm.getSolidTileForChunkDesc(desc, solidityBit);
        if (tile == null) {
            System.out.printf("    %s: solid bit set but tile null%n", label);
            return;
        }
        int index = x & 0x0F;
        if (desc.getHFlip()) {
            index = 15 - index;
        }
        int metric = tile.getHeightAt((byte) index);
        if (metric != 0 && metric != 16 && desc.getVFlip()) {
            metric = -metric;
        }
        int angle = tile.getAngle(desc.getHFlip(), desc.getVFlip()) & 0xFF;
        System.out.printf("    %s: desc=0x%04X tile=%d h=%b v=%b idx=%d metric=%d angle=0x%02X bits[C=%b D=%b E=%b F=%b]%n",
                label, word, tile.getIndex(), desc.getHFlip(), desc.getVFlip(), index, metric, angle, bC, bD, bE, bF);
    }

    // ========================================================================
    // From TestSonic1GhzTunnel
    // ========================================================================

    /**
     * Finds the first tunnel tile (GHZ_ROLL1 or GHZ_ROLL2) in the FG layout.
     * Returns {mapX, mapY} or null if not found.
     */
    private int[] findFirstTunnelTile() {
        Sonic1Level s1Level = (Sonic1Level) LevelManager.getInstance().getCurrentLevel();
        // Scan through the FG layout looking for tunnel tiles
        // The layout is indexed by 256x256 block cells
        for (int mapY = 0; mapY < 8; mapY++) {
            for (int mapX = 0; mapX < 64; mapX++) {
                int rawValue = s1Level.getRawFgValue(mapX, mapY);
                if (rawValue == Sonic1Constants.GHZ_ROLL1 || rawValue == Sonic1Constants.GHZ_ROLL2) {
                    return new int[]{mapX, mapY};
                }
            }
        }
        return null;
    }

    /**
     * Diagnostic test: prints the state of Sonic as he approaches and enters
     * the first GHZ S-tube tunnel. Helps identify what goes wrong.
     */
    @Test
    public void testTunnelTraversal() throws Exception {
        Sonic1Level s1Level = (Sonic1Level) LevelManager.getInstance().getCurrentLevel();
        assertTrue("Level should be Sonic1Level", s1Level != null);

        int[] tunnelCell = findFirstTunnelTile();
        assertNotNull("Should find a tunnel tile in GHZ1 layout", tunnelCell);

        int tunnelMapX = tunnelCell[0];
        int tunnelMapY = tunnelCell[1];
        int blockSize = Sonic1Constants.BLOCK_WIDTH_PX; // 256

        // Calculate pixel position of the tunnel block
        int tunnelPixelX = tunnelMapX * blockSize;
        int tunnelPixelY = tunnelMapY * blockSize;

        System.out.println("=== GHZ1 Tunnel Test ===");
        System.out.printf("First tunnel tile at map cell (%d, %d) = pixel (%d, %d)%n",
                tunnelMapX, tunnelMapY, tunnelPixelX, tunnelPixelY);

        // Place Sonic ~128px to the left of the tunnel block, at a Y that should be
        // on the ground surface above the tunnel entrance.
        short startX = (short) (tunnelPixelX - 128);
        short startY = (short) (tunnelPixelY + 80);

        sprite.setX(startX);
        sprite.setY(startY);
        sprite.setAir(true); // Let him fall to find ground
        Camera.getInstance().updatePosition(true);

        System.out.printf("Initial position: X=%d, Y=%d%n", sprite.getX(), sprite.getY());

        // Let Sonic settle onto the ground (fall and land)
        for (int frame = 0; frame < 30; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir() && frame > 5) break;
        }

        System.out.printf("After settling: X=%d, Y=%d, air=%b, angle=0x%02X%n",
                sprite.getX(), sprite.getY(), sprite.getAir(), sprite.getAngle() & 0xFF);

        // If still in air, try a different Y
        if (sprite.getAir()) {
            System.out.println("WARNING: Sonic did not land. Trying different Y...");
            sprite.setX(startX);
            sprite.setY((short) (tunnelPixelY - 32));
            sprite.setAir(true);
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            for (int frame = 0; frame < 30; frame++) {
                testRunner.stepFrame(false, false, false, false, false);
                if (!sprite.getAir() && frame > 5) break;
            }
            System.out.printf("After re-settling: X=%d, Y=%d, air=%b%n",
                    sprite.getX(), sprite.getY(), sprite.getAir());
        }

        assertFalse("Sonic should be on the ground before approaching tunnel", sprite.getAir());

        short groundY = sprite.getY();
        System.out.printf("Ground Y level: %d (centre Y: %d)%n", groundY, sprite.getCentreY());

        // Give Sonic rightward speed to approach the tunnel
        sprite.setGSpeed((short) 0x600); // ~6 pixels/frame
        sprite.setXSpeed((short) 0x600);

        // Now step frames and log every frame as Sonic approaches and enters the tunnel
        System.out.println("\n=== Frame-by-frame log ===");
        System.out.println("Frame | X      | Y      | CentreY | GSpd  | Angle | Air   | Roll  | Push  | Mode    | TunnelTile");

        short prevY = sprite.getY();
        int framesInTunnel = 0;
        int framesAirborne = 0;
        boolean enteredTunnel = false;
        boolean failedInTunnel = false;

        for (int frame = 0; frame < 200; frame++) {
            testRunner.stepFrame(false, false, false, true, false);

            short x = sprite.getX();
            short y = sprite.getY();
            short centreY = (short) sprite.getCentreY();
            short gSpeed = sprite.getGSpeed();
            int angle = sprite.getAngle() & 0xFF;
            boolean air = sprite.getAir();
            boolean rolling = sprite.getRolling();
            boolean pushing = sprite.getPushing();
            String mode = sprite.getGroundMode().name();

            // Check what tile Sonic's centre is on
            int cx = sprite.getCentreX() & 0xFFFF;
            int cy = sprite.getCentreY() & 0xFFFF;
            int cellX = cx / blockSize;
            int cellY = cy / blockSize;
            int rawTile = s1Level.getRawFgValue(cellX, cellY);
            boolean onTunnelTile = (rawTile == Sonic1Constants.GHZ_ROLL1 || rawTile == Sonic1Constants.GHZ_ROLL2);
            String tileStr = onTunnelTile ? String.format("YES(0x%02X)", rawTile) : String.format("no(0x%02X)", rawTile);

            // Calculate Y delta
            short yDelta = (short) (y - prevY);

            System.out.printf("%5d | %6d | %6d | %7d | %5d | 0x%02X  | %-5b | %-5b | %-5b | %-7s | %s (dY=%d)%n",
                    frame, x, y, centreY, gSpeed, angle, air, rolling, pushing, mode, tileStr, yDelta);

            // Log raw sensor results at critical frames around the curve
            if (frame >= 25 && frame <= 50 && !air) {
                Sensor[] groundSensors = sprite.getGroundSensors();
                if (groundSensors != null && groundSensors.length >= 2) {
                    SensorResult sA = groundSensors[0].scan();
                    SensorResult sB = groundSensors[1].scan();
                    System.out.printf("       Sensors: A(d=%d a=0x%02X tid=%d) B(d=%d a=0x%02X tid=%d)%n",
                            sA != null ? sA.distance() : -99, sA != null ? sA.angle() & 0xFF : 0, sA != null ? sA.tileId() : -1,
                            sB != null ? sB.distance() : -99, sB != null ? sB.angle() & 0xFF : 0, sB != null ? sB.tileId() : -1);
                }
            }

            if (onTunnelTile) {
                enteredTunnel = true;
                framesInTunnel++;
            }

            // Detect the failure: Sonic goes airborne or gets stuck (gSpeed near 0) in the tunnel
            if (enteredTunnel && onTunnelTile) {
                if (air) {
                    framesAirborne++;
                    if (framesAirborne > 5) {
                        System.out.println("*** FAILURE: Sonic went airborne in tunnel for 5+ frames ***");
                        failedInTunnel = true;
                        break;
                    }
                }
                if (!air && Math.abs(gSpeed) < 0x80 && pushing) {
                    System.out.println("*** FAILURE: Sonic stuck pushing in tunnel (gSpeed~0, pushing=true) ***");
                    failedInTunnel = true;
                    break;
                }
            }

            // If Sonic has passed well beyond the tunnel, we can stop
            if (enteredTunnel && !onTunnelTile && framesInTunnel > 10 && x > tunnelPixelX + blockSize + 128) {
                System.out.println("Sonic passed through the tunnel successfully.");
                break;
            }

            // If X hasn't moved in 10 frames, Sonic is stuck
            if (frame > 50 && Math.abs(gSpeed) < 0x40 && !air) {
                System.out.println("*** Sonic appears stuck (very low speed) at frame " + frame + " ***");
                break;
            }

            prevY = y;
        }

        if (enteredTunnel) {
            System.out.printf("%nTunnel frames: %d, Airborne in tunnel: %d%n", framesInTunnel, framesAirborne);
        }

        assertFalse("Sonic should traverse the tunnel without getting stuck or going airborne", failedInTunnel);
        assertTrue("Sonic should have entered the tunnel", enteredTunnel);
    }

    // ========================================================================
    // From TestHeadlessSonic1PushStability
    // ========================================================================

    private static final int PUSH_TESTBED_X = 0x0180;
    private static final int PUSH_TESTBED_FLOOR_Y = 0x0140;
    private static final int PUSH_TESTBED_SPAWN_Y = PUSH_TESTBED_FLOOR_Y - 0x60;
    private static final int PUSH_LANDING_TIMEOUT_FRAMES = 120;
    private static final int PUSH_CONTACT_TIMEOUT_FRAMES = 90;
    private static final int PUSH_CONTACT_WARMUP_FRAMES = 10;
    private static final int PUSH_STABILITY_FRAMES = 60;
    private static final int PUSH_FLOOR_HALF_WIDTH = 0x90;
    private static final int PUSH_FLOOR_HALF_HEIGHT = 0x10;
    private static final int PUSH_WALL_HALF_WIDTH = 0x18;
    private static final int PUSH_WALL_HALF_HEIGHT = 0x18;
    private static final int PUSH_OBJECT_GAP = 4;
    private static final int PUSH_START_OFFSET = 0x30;

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
                .addDynamicObject(new PushTestSolidObject(
                        PUSH_TESTBED_X,
                        PUSH_TESTBED_FLOOR_Y,
                        new SolidObjectParams(PUSH_FLOOR_HALF_WIDTH, PUSH_FLOOR_HALF_HEIGHT, PUSH_FLOOR_HALF_HEIGHT),
                        true));

        sprite.setCentreX((short) (PUSH_TESTBED_X + (pushRight ? -PUSH_START_OFFSET : PUSH_START_OFFSET)));
        sprite.setCentreY((short) PUSH_TESTBED_SPAWN_Y);
        sprite.setAir(true);

        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Wait for Sonic to land on the floor
        boolean landed = false;
        for (int frame = 0; frame < PUSH_LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on the static floor testbed", landed);

        // Place the wall object next to Sonic
        int objectX = sprite.getCentreX()
                + (pushRight ? PUSH_WALL_HALF_WIDTH + PUSH_OBJECT_GAP : -(PUSH_WALL_HALF_WIDTH + PUSH_OBJECT_GAP));
        int objectY = sprite.getCentreY();

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new PushTestSolidObject(
                        objectX,
                        objectY,
                        new SolidObjectParams(PUSH_WALL_HALF_WIDTH, PUSH_WALL_HALF_HEIGHT, PUSH_WALL_HALF_HEIGHT),
                        false));

        // Walk toward the wall until pushing contact
        boolean pressingLeft = !pushRight;
        boolean contactReached = false;
        for (int frame = 0; frame < PUSH_CONTACT_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, pressingLeft, pushRight, false);
            if (sprite.getPushing()) {
                contactReached = true;
                break;
            }
        }
        assertTrue("Sonic should reach side-pushing contact (" + pushDirectionName(pushRight) + ")", contactReached);

        // Warmup: let subpixels stabilise
        for (int frame = 0; frame < PUSH_CONTACT_WARMUP_FRAMES; frame++) {
            testRunner.stepFrame(false, false, pressingLeft, pushRight, false);
        }

        // Stability window: position must not oscillate
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int transitionCount = 0;
        Integer previousX = null;
        for (int frame = 0; frame < PUSH_STABILITY_FRAMES; frame++) {
            testRunner.stepFrame(false, false, pressingLeft, pushRight, false);
            int x = sprite.getX();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            if (previousX != null && previousX != x) {
                transitionCount++;
            }
            previousX = x;
            assertFalse("Sonic should stay grounded while pushing (" + pushDirectionName(pushRight) + ")", sprite.getAir());
        }

        assertEquals("Sonic X position should stay stable while pushing static object (" + pushDirectionName(pushRight)
                        + "), minX=" + minX + ", maxX=" + maxX + ", transitions=" + transitionCount,
                minX, maxX);
        assertEquals("Sonic X should not oscillate while pushing static object (" + pushDirectionName(pushRight) + ")",
                0, transitionCount);
    }

    private static String pushDirectionName(boolean pushRight) {
        return pushRight ? "toward right-side object" : "toward left-side object";
    }

    private static final class PushTestSolidObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final int x;
        private final int y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;

        private PushTestSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
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

    // ========================================================================
    // From TestHeadlessSonic1EdgeBalance
    // ========================================================================

    private static final int EDGE_THRESHOLD = 12;
    private static final int EDGE_LANDING_TIMEOUT_FRAMES = 120;
    private static final int EDGE_WALK_TIMEOUT_FRAMES = 1500;

    // Platform for object edge tests: 128px half-width, 16px half-height
    private static final int EDGE_PLATFORM_HALF_WIDTH = 0x80;
    private static final int EDGE_PLATFORM_HALF_HEIGHT = 0x10;
    // Testbed positioned in a clear area
    private static final int EDGE_TESTBED_X = 0x0180;
    private static final int EDGE_TESTBED_FLOOR_Y = 0x0140;
    private static final int EDGE_TESTBED_SPAWN_Y = EDGE_TESTBED_FLOOR_Y - 0x60;

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
        edgeCreatePlatformAndLand();

        // Position Sonic at the right edge of the platform.
        int rightEdgeX = EDGE_TESTBED_X + EDGE_PLATFORM_HALF_WIDTH - 3;

        int balanceState = edgeSettleOnObjectAndCheckBalance(rightEdgeX);
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
        edgeCreatePlatformAndLand();

        int rightEdgeX = EDGE_TESTBED_X + EDGE_PLATFORM_HALF_WIDTH - 3;

        // Face LEFT (away from right edge)
        sprite.setDirection(Direction.LEFT);
        int balanceState = edgeSettleOnObjectAndCheckBalance(rightEdgeX);

        assertTrue("Should balance at right edge", balanceState > 0);
        assertEquals("S1 should force facing RIGHT (toward right edge)",
                Direction.RIGHT, sprite.getDirection());
    }

    /**
     * Verify left edge balance forces facing LEFT.
     */
    @Test
    public void testObjectEdgeBalanceLeftEdgeFacesLeft() {
        edgeCreatePlatformAndLand();

        int leftEdgeX = EDGE_TESTBED_X - EDGE_PLATFORM_HALF_WIDTH + 3;

        sprite.setDirection(Direction.RIGHT); // Face away from left edge
        int balanceState = edgeSettleOnObjectAndCheckBalance(leftEdgeX);

        assertTrue("Should balance at left edge", balanceState > 0);
        assertEquals("S1 should force facing LEFT (toward left edge)",
                Direction.LEFT, sprite.getDirection());
    }

    /**
     * Verify no balance when safely in the center of the platform.
     */
    @Test
    public void testObjectNoBalanceInCenter() {
        edgeCreatePlatformAndLand();

        int balanceState = edgeSettleOnObjectAndCheckBalance(EDGE_TESTBED_X);
        assertEquals("Should NOT balance when safely centered on platform",
                0, balanceState);
    }

    /**
     * Walk Sonic right through GHZ1 until the right side sensor (center+9)
     * detects a drop while the center probe does NOT. At this position
     * balance must NOT trigger (S1 center probe gate).
     *
     * Then continue to where the center probe also detects a drop --
     * balance SHOULD trigger there.
     */
    @Test
    public void testTerrainEdgeBalanceUsesCenter() {
        // Land on natural GHZ terrain
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        boolean landed = false;
        for (int frame = 0; frame < EDGE_LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on GHZ1 ground", landed);

        Sensor[] groundSensors = sprite.getGroundSensors();
        assertNotNull("Ground sensors should exist", groundSensors);

        int sideSensorEdgeX = -1;
        int sideSensorEdgeY = -1;

        for (int frame = 0; frame < EDGE_WALK_TIMEOUT_FRAMES; frame++) {
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

        if (sideSensorEdgeX == -1) {
            System.out.println("[EdgeBalance] No terrain cliff found in GHZ1 within " +
                    EDGE_WALK_TIMEOUT_FRAMES + " frames -- terrain test skipped " +
                    "(object edge tests still validate the fix)");
            return;
        }

        // Check center probe at this position
        SensorResult centerResult = groundSensors[0].scan((short) 9, (short) 0);
        int centerDist = (centerResult == null) ? 99 : centerResult.distance();

        if (centerDist < EDGE_THRESHOLD) {
            int balanceState = edgeSettleAndCheckBalance(sideSensorEdgeX, sideSensorEdgeY);
            assertEquals("Balance should NOT trigger when only side sensor drops off " +
                            "(sideSensorEdge x=" + sideSensorEdgeX + ")",
                    0, balanceState);

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
                int balanceAtCenter = edgeSettleAndCheckBalance(centerEdgeX, sideSensorEdgeY);
                assertTrue("Balance SHOULD trigger when center is at terrain edge " +
                                "(x=" + centerEdgeX + ")",
                        balanceAtCenter > 0);
                assertEquals("S1 terrain balance state should be 1", 1, balanceAtCenter);
            }
        } else {
            int balanceState = edgeSettleAndCheckBalance(sideSensorEdgeX, sideSensorEdgeY);
            assertTrue("Balance should trigger at terrain edge (x=" + sideSensorEdgeX + ")",
                    balanceState > 0);
        }
    }

    private void edgeCreatePlatformAndLand() {
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new EdgeBalanceSolidObject(
                        EDGE_TESTBED_X, EDGE_TESTBED_FLOOR_Y,
                        new SolidObjectParams(EDGE_PLATFORM_HALF_WIDTH, EDGE_PLATFORM_HALF_HEIGHT, EDGE_PLATFORM_HALF_HEIGHT),
                        true));

        sprite.setCentreX((short) EDGE_TESTBED_X);
        sprite.setCentreY((short) EDGE_TESTBED_SPAWN_Y);
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        boolean landed = false;
        for (int frame = 0; frame < EDGE_LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir() && sprite.isOnObject()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on the platform", landed);
    }

    private int edgeSettleOnObjectAndCheckBalance(int x) {
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

    private int edgeSettleAndCheckBalance(int x, int approxGroundY) {
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

    private static final class EdgeBalanceSolidObject extends AbstractObjectInstance
            implements SolidObjectProvider {
        private final int x, y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;

        private EdgeBalanceSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
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

    // ========================================================================
    // From TestHeadlessSonic1ObjectCollision
    // ========================================================================

    // MzBrick solid params from disassembly
    private static final int COLLISION_HALF_WIDTH = 0x1B;       // collision half-width (27px)
    private static final int COLLISION_AIR_HALF_HEIGHT = 0x10;   // d2 = 16px
    private static final int COLLISION_GROUND_HALF_HEIGHT = 0x11; // d3 = 17px (unused by ROM for overlap test)
    private static final int COLLISION_ACTIVE_WIDTH = 0x10;       // obActWid = 16px (landing width)

    private static final int COLLISION_TESTBED_X = 0x0180;
    private static final int COLLISION_TESTBED_FLOOR_Y = 0x0140;
    private static final int COLLISION_TESTBED_SPAWN_Y = COLLISION_TESTBED_FLOOR_Y - 0x60;
    private static final int COLLISION_FLOOR_HALF_WIDTH = 0x90;
    private static final int COLLISION_FLOOR_HALF_HEIGHT = 0x10;
    private static final int COLLISION_LANDING_TIMEOUT_FRAMES = 120;
    private static final int COLLISION_CONTACT_TIMEOUT_FRAMES = 90;
    private static final int COLLISION_STABILITY_FRAMES = 60;

    /**
     * Walking into a MzBrick-sized solid from the side should block Sonic.
     * Before the fix, groundHH (0x11) was used when grounded, making the
     * collision zone 1px taller and causing false side-collision detection.
     */
    @Test
    public void testSideCollisionUsesAirHalfHeight() {
        // Floor platform
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new CollisionTestSolidObject(
                        COLLISION_TESTBED_X, COLLISION_TESTBED_FLOOR_Y,
                        new SolidObjectParams(COLLISION_FLOOR_HALF_WIDTH, COLLISION_FLOOR_HALF_HEIGHT, COLLISION_FLOOR_HALF_HEIGHT),
                        true, COLLISION_FLOOR_HALF_WIDTH));

        // MzBrick-like object to the right
        int objectX = COLLISION_TESTBED_X + 0x50;
        int objectY = COLLISION_TESTBED_FLOOR_Y - COLLISION_FLOOR_HALF_HEIGHT - COLLISION_AIR_HALF_HEIGHT;

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new CollisionTestSolidObject(
                        objectX, objectY,
                        new SolidObjectParams(COLLISION_HALF_WIDTH, COLLISION_AIR_HALF_HEIGHT, COLLISION_GROUND_HALF_HEIGHT),
                        false, COLLISION_ACTIVE_WIDTH));

        // Spawn Sonic to the left of the object, on the floor
        sprite.setCentreX((short) (objectX - COLLISION_HALF_WIDTH - 0x30));
        sprite.setCentreY((short) COLLISION_TESTBED_SPAWN_Y);
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Land on floor
        boolean landed = false;
        for (int frame = 0; frame < COLLISION_LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on floor", landed);

        // Walk right into the object
        boolean contactReached = false;
        for (int frame = 0; frame < COLLISION_CONTACT_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, true, false);
            if (sprite.getPushing()) {
                contactReached = true;
                break;
            }
        }
        assertTrue("Sonic should reach side-pushing contact with MzBrick-sized object", contactReached);

        // Verify stability -- position should not oscillate
        int stableX = sprite.getX();
        for (int frame = 0; frame < COLLISION_STABILITY_FRAMES; frame++) {
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
        int objectX = COLLISION_TESTBED_X;
        int objectY = COLLISION_TESTBED_FLOOR_Y;

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new CollisionTestSolidObject(
                        objectX, objectY,
                        new SolidObjectParams(COLLISION_HALF_WIDTH, COLLISION_AIR_HALF_HEIGHT, COLLISION_GROUND_HALF_HEIGHT),
                        false, COLLISION_ACTIVE_WIDTH));

        // Spawn Sonic just outside active width but inside collision width:
        // ACTIVE_WIDTH = 0x10 (16), HALF_WIDTH = 0x1B (27)
        // Position at offset 0x15 (21) from center -- outside active, inside collision
        int spawnOffset = COLLISION_ACTIVE_WIDTH + 5; // 21px from object center
        sprite.setCentreX((short) (objectX + spawnOffset));
        sprite.setCentreY((short) (objectY - COLLISION_AIR_HALF_HEIGHT - 0x40));
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Let Sonic fall -- should NOT land on the object
        for (int frame = 0; frame < COLLISION_LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Sonic should have fallen through (still in air or landed on terrain below)
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
                .addDynamicObject(new CollisionTestSolidObject(
                        COLLISION_TESTBED_X, COLLISION_TESTBED_FLOOR_Y + 0x80,
                        new SolidObjectParams(COLLISION_FLOOR_HALF_WIDTH, COLLISION_FLOOR_HALF_HEIGHT, COLLISION_FLOOR_HALF_HEIGHT),
                        true, COLLISION_FLOOR_HALF_WIDTH));

        // Place the solid object
        int objectX = COLLISION_TESTBED_X;
        int objectY = COLLISION_TESTBED_FLOOR_Y;

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new CollisionTestSolidObject(
                        objectX, objectY,
                        new SolidObjectParams(COLLISION_HALF_WIDTH, COLLISION_AIR_HALF_HEIGHT, COLLISION_GROUND_HALF_HEIGHT),
                        false, COLLISION_ACTIVE_WIDTH));

        // Spawn Sonic centered on the object
        sprite.setCentreX((short) objectX);
        sprite.setCentreY((short) (objectY - COLLISION_AIR_HALF_HEIGHT - 0x40));
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        // Let Sonic fall -- should land on the object
        boolean landed = false;
        for (int frame = 0; frame < COLLISION_LANDING_TIMEOUT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                // Verify landed near the object top, not on the distant floor
                int sonicY = sprite.getCentreY();
                if (sonicY < objectY + COLLISION_AIR_HALF_HEIGHT) {
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
    private static final class CollisionTestSolidObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final int x;
        private final int y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;
        private final int landingHalfWidth;

        private CollisionTestSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly,
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

    // ========================================================================
    // From TestCrabmeatSpawnPosition
    // ========================================================================

    private List<Sonic1CrabmeatBadnikInstance> findCrabmeats() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        assertNotNull("ObjectManager should exist", objectManager);

        return objectManager.getActiveObjects().stream()
                .filter(obj -> obj instanceof Sonic1CrabmeatBadnikInstance)
                .map(obj -> (Sonic1CrabmeatBadnikInstance) obj)
                .sorted((a, b) -> Integer.compare(a.getSpawn().x(), b.getSpawn().x()))
                .toList();
    }

    private long countCrabmeatsAtSpawnX(int spawnX) {
        return findCrabmeats().stream()
                .filter(crab -> crab.getSpawn().x() == spawnX)
                .count();
    }

    /**
     * Immediately after the first game frame, Crabmeats should exist with
     * their X positions matching the ROM spawn data exactly (ObjectFall
     * initialization only affects Y, never X).
     */
    @Test
    public void crabmeatsSpawnAtCorrectXPositions() {
        // Reposition Sonic near the first two Crabmeats (X=0x08B0, 0x0960)
        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);

        // One frame is enough for the ObjectManager to create instances
        testRunner.stepIdleFrames(1);

        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertTrue("Both GHZ1 Crabmeats should spawn within camera window",
                crabmeats.size() >= 2);

        Assert.assertEquals("Crabmeat #1 X must match ROM position",
                0x08B0, crabmeats.get(0).getX());
        Assert.assertEquals("Crabmeat #2 X must match ROM position",
                0x0960, crabmeats.get(1).getX());
    }

    /**
     * During ObjectFall (before landing), Crabmeat X must not change.
     * This catches any bug where horizontal velocity is applied during init.
     */
    @Test
    public void xDoesNotChangeDuringObjectFall() {
        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);

        testRunner.stepIdleFrames(1);
        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertFalse("Crabmeats should have spawned", crabmeats.isEmpty());

        // Step frame-by-frame through ObjectFall and verify X never changes
        for (int frame = 0; frame < 15; frame++) {
            for (Sonic1CrabmeatBadnikInstance crab : crabmeats) {
                int spawnX = crab.getSpawn().x();
                Assert.assertEquals("Crabmeat X must not drift during init (frame " + frame + ")",
                        spawnX, crab.getX());
            }
            testRunner.stepIdleFrames(1);
        }
    }

    /**
     * After enough frames for ObjectFall to complete, Crabmeats should have
     * landed on solid terrain (Y adjusted from spawn to floor surface).
     */
    @Test
    public void crabmeatsLandOnTerrain() {
        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);

        // 30 frames is more than enough for ObjectFall at gravity 0x38/frame
        testRunner.stepIdleFrames(30);

        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertFalse("Crabmeats should have spawned", crabmeats.isEmpty());

        for (int i = 0; i < crabmeats.size(); i++) {
            Sonic1CrabmeatBadnikInstance crab = crabmeats.get(i);
            int y = crab.getY();
            assertTrue("Crabmeat " + i + " Y (" + y + ") should be > 0",
                    y > 0);
            assertTrue("Crabmeat " + i + " Y (" + y + ") should be < 0x0800 (not fallen off level)",
                    y < 0x0800);
        }
    }

    /**
     * The first Crabmeat (X=0x08B0) should patrol left and right over its
     * full AI cycle.
     */
    @Test
    public void firstCrabmeatPatrolsInBothDirections() {
        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);

        testRunner.stepIdleFrames(1);
        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertFalse("Crabmeat should have spawned", crabmeats.isEmpty());

        Sonic1CrabmeatBadnikInstance crab = crabmeats.get(0);
        final int spawnX = 0x08B0;
        Assert.assertEquals(spawnX, crab.getSpawn().x());

        int minX = spawnX;
        int maxX = spawnX;

        for (int frame = 0; frame < 500; frame++) {
            testRunner.stepIdleFrames(1);
            int x = crab.getX();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }

        int leftDelta = spawnX - minX;
        int rightDelta = maxX - spawnX;
        int totalRange = maxX - minX;

        System.out.println("[Crabmeat walk] spawnX=" + spawnX +
                " minX=" + minX + " maxX=" + maxX +
                " leftDelta=" + leftDelta + " rightDelta=" + rightDelta +
                " totalRange=" + totalRange);

        // The Crabmeat should walk at least 20px left of spawn (ROM first walk is left)
        assertTrue("Crabmeat should walk significantly left of spawn (leftDelta=" +
                        leftDelta + ", expected >= 20)",
                leftDelta >= 20);

        // Total patrol range should be significant (walks left then right back)
        assertTrue("Crabmeat patrol range should be >= 20px (totalRange=" +
                        totalRange + ")",
                totalRange >= 20);
    }

    /**
     * Regression: destroyed S1 badniks must stay gone after their spawn leaves and
     * re-enters the spawn window.
     */
    @Test
    public void destroyedCrabmeatDoesNotRespawnAfterCameraWindowCycle() {
        final int targetSpawnX = 0x08B0;

        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);

        testRunner.stepIdleFrames(1);
        Sonic1CrabmeatBadnikInstance target = findCrabmeats().stream()
                .filter(crab -> crab.getSpawn().x() == targetSpawnX)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Target Crabmeat did not spawn"));

        assertEquals("Target Crabmeat should be present before destruction",
                1, countCrabmeatsAtSpawnX(targetSpawnX));

        // Simulate player destroying the badnik.
        target.onPlayerAttack(sprite, null);
        testRunner.stepIdleFrames(2);
        assertEquals("Destroyed Crabmeat should be removed immediately",
                0, countCrabmeatsAtSpawnX(targetSpawnX));

        // Move camera far enough that the original spawn leaves the placement window.
        sprite.setX((short) 0x1500);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);
        testRunner.stepIdleFrames(4);

        // Return camera so the original spawn is in range again.
        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        Camera.getInstance().updatePosition(true);
        testRunner.stepIdleFrames(6);

        assertEquals("Destroyed Crabmeat must not respawn after window cycle",
                0, countCrabmeatsAtSpawnX(targetSpawnX));
    }

    // ========================================================================
    // Regression: Jump at 557,921 should not land inside terrain above
    // ========================================================================

    /**
     * At GHZ1 position (557, 921), jumping straight up should not cause Sonic
     * to collide with or land on the terrain above. He should arc up, not hit
     * any ceiling, and fall back down to approximately y=921.
     *
     * Bug: The engine incorrectly places Sonic at y≈841 after the jump,
     * standing inside the terrain above.
     */
    /**
     * Regression: jumping at x=682 in GHZ1 lower path should NOT land Sonic
     * inside terrain above (~y=841). Sonic should fall back to roughly the
     * same starting height (~y=925 area).
     *
     * The bug: Sonic jumps, reaches the top-solid underground blocks of the
     * upper path, and incorrectly lands inside them instead of passing through.
     * On real hardware Sonic would not land on these blocks.
     */
    @Test
    public void regressionJumpAt682ShouldNotStickInsideTerrain() {
        final short START_X = 682;
        final short START_Y = 925;
        final int HOLD_JUMP_FRAMES = 7;
        final int POST_JUMP_FRAMES = 21;

        // Place Sonic above the target position and let him settle on ground.
        // Start high enough to find the ground surface at this X.
        sprite.setCentreX(START_X);
        sprite.setCentreY((short) (START_Y - 40));
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);

        // Let Sonic fall and land on the ground.
        for (int i = 0; i < 60; i++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) break;
        }
        assertFalse("Sonic should have landed on ground", sprite.getAir());

        short settledY = sprite.getCentreY();
        System.out.println("[JUMP-REGRESSION] Settled centreY=" + settledY
                + " centreX=" + sprite.getCentreX());

        // Hold jump for 7 frames.
        for (int i = 0; i < HOLD_JUMP_FRAMES; i++) {
            testRunner.stepFrame(false, false, false, false, true);
        }
        assertTrue("Sonic should be airborne after pressing jump", sprite.getAir());

        // Continue for POST_JUMP_FRAMES frames (no input).
        for (int i = 0; i < POST_JUMP_FRAMES; i++) {
            testRunner.stepFrame(false, false, false, false, false);

            int frameNum = HOLD_JUMP_FRAMES + i + 1;
            System.out.printf("[JUMP-REGRESSION] Frame %2d: centreY=%d ySpd=0x%04X air=%b gMode=%s%n",
                    frameNum, sprite.getCentreY(), sprite.getYSpeed() & 0xFFFF,
                    sprite.getAir(), sprite.getGroundMode());
        }

        // Continue stepping until Sonic lands or we time out.
        for (int i = POST_JUMP_FRAMES; i < 80; i++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) break;
        }

        short finalY = sprite.getCentreY();
        System.out.println("[JUMP-REGRESSION] Final centreY=" + finalY
                + " air=" + sprite.getAir()
                + " settled was " + settledY);

        // Sonic must NOT be stuck at ~841 (inside the terrain above).
        // He should be near where he started (within ~30px of settledY) or still airborne
        // and heading back down. Being grounded at y < settledY - 40 means he stuck
        // inside the upper path terrain.
        boolean stuckAbove = !sprite.getAir() && finalY < (settledY - 40);
        assertFalse("Sonic should not be stuck inside terrain above (centreY="
                + finalY + ", expected near " + settledY + ")", stuckAbove);
    }
}
