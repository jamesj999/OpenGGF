package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1Level;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.ChunkDesc;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.SolidTile;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.physics.SensorResult;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.GroundMode;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import static org.junit.Assert.assertFalse;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1GhzSlopeTopDiagnostic {

    private static final int ZONE_GHZ = 0;
    private static final int ACT_1 = 0;
    private static final int TARGET_X = 1900;
    private static final int TARGET_Y = 857;

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner runner;

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

        runner = new HeadlessTestRunner(sprite);
    }

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
            runner.stepFrame(false, false, false, false, false);
        }

        System.out.println("=== GHZ slope-top diagnostic at approx (1900,857) ===");
        System.out.printf("Start settled: x=%d y=%d cx=%d cy=%d air=%b angle=0x%02X mode=%s%n",
                sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode());

        Sensor[] groundSensors = sprite.getGroundSensors();
        for (int frame = 0; frame < 120; frame++) {
            runner.stepFrame(false, false, false, false, false);

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
            runner.stepFrame(false, false, false, false, false);
        }

        // Simulate entering this flat-top tile from a descending slope.
        sprite.setAir(false);
        sprite.setAngle((byte) 0xFC);
        sprite.setGroundMode(GroundMode.GROUND);

        System.out.println("=== GHZ slope-top diagnostic with incoming angle 0xFC ===");
        for (int frame = 0; frame < 20; frame++) {
            runner.stepFrame(false, false, false, false, false);
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
            runner.stepFrame(false, false, false, false, false);
        }

        System.out.println("=== GHZ approach diagnostic (left -> right) ===");
        System.out.printf("Start settled: x=%d y=%d cx=%d cy=%d air=%b angle=0x%02X mode=%s%n",
                sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode());

        GroundMode lastMode = sprite.getGroundMode();
        int lastAngle = sprite.getAngle() & 0xFF;
        for (int frame = 0; frame < 260; frame++) {
            boolean holdRight = frame < 180;
            runner.stepFrame(false, false, false, holdRight, false);

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
            resetAtTarget(startOffset);
            System.out.printf("startOffset=%+d settled: x=%d y=%d cx=%d cy=%d air=%b angle=0x%02X mode=%s%n",
                    startOffset, sprite.getX(), sprite.getY(), sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getAir(), sprite.getAngle() & 0xFF, sprite.getGroundMode());

            runNudgeSequence("RIGHT", false, true);
            resetAtTarget(startOffset);
            runNudgeSequence("LEFT ", true, false);
        }
    }

    private void runNudgeSequence(String label, boolean left, boolean right) {
        Sensor[] groundSensors = sprite.getGroundSensors();
        for (int frame = 0; frame < 8; frame++) {
            runner.stepFrame(false, false, left, right, false);

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

    private void resetAtTarget(int xOffset) {
        sprite.setX((short) (TARGET_X + xOffset));
        sprite.setY((short) TARGET_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);

        for (int i = 0; i < 20; i++) {
            runner.stepFrame(false, false, false, false, false);
        }
    }

    @Test
    public void regressionSlopeTopNudgeShouldStayGroundedAt1900() {
        resetAtTarget(0);
        runConstrainedNudge("RIGHT", false, true);

        resetAtTarget(0);
        runConstrainedNudge("LEFT", true, false);
    }

    @Test
    public void regressionSlopeTopX1900MicroMovementShouldNotEnterWallMode() {
        for (int startX = 1899; startX <= 1912; startX++) {
            resetAtTopLeft(startX, TARGET_Y);
            for (int frame = 0; frame < 24; frame++) {
                runner.stepFrame(false, false, false, true, false);
                assertNoWallOrAirNearTop(frame, startX, "R");
            }

            resetAtTopLeft(startX, TARGET_Y);
            for (int frame = 0; frame < 24; frame++) {
                runner.stepFrame(false, false, true, false, false);
                assertNoWallOrAirNearTop(frame, startX, "L");
            }
        }
    }

    private void runConstrainedNudge(String label, boolean left, boolean right) {
        for (int frame = 0; frame < 6; frame++) {
            runner.stepFrame(false, false, left, right, false);
            boolean inWindow = Math.abs(sprite.getX() - TARGET_X) <= 2;
            if (inWindow) {
                assertFalse(label + " frame " + frame + " entered air at X=" + sprite.getX(),
                        sprite.getAir());
                assertFalse(label + " frame " + frame + " entered RIGHTWALL at X=" + sprite.getX(),
                        sprite.getGroundMode() == GroundMode.RIGHTWALL);
                assertFalse(label + " frame " + frame + " entered LEFTWALL at X=" + sprite.getX(),
                        sprite.getGroundMode() == GroundMode.LEFTWALL);
            }
        }
    }

    private void resetAtTopLeft(int x, int y) {
        sprite.setX((short) x);
        sprite.setY((short) y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);
        for (int i = 0; i < 20; i++) {
            runner.stepFrame(false, false, false, false, false);
        }
    }

    private void assertNoWallOrAirNearTop(int frame, int startX, String dir) {
        // Keep checks local to the top-of-slope reproduction area near X=1900.
        int x = sprite.getX();
        int y = sprite.getY();
        if (x < 1898 || x > 1902) {
            return;
        }
        // Ignore unrelated off-ledge falls outside the crest contact band.
        if (y > 860) {
            return;
        }

        assertFalse("entered air near X=1900 startX=" + startX + " dir=" + dir + " frame=" + frame
                        + " x=" + x + " y=" + y + " cx=" + sprite.getCentreX(),
                sprite.getAir());
        assertFalse("entered RIGHTWALL near X=1900 startX=" + startX + " dir=" + dir + " frame=" + frame
                        + " x=" + x + " y=" + y + " cx=" + sprite.getCentreX(),
                sprite.getGroundMode() == GroundMode.RIGHTWALL);
        assertFalse("entered LEFTWALL near X=1900 startX=" + startX + " dir=" + dir + " frame=" + frame
                        + " x=" + x + " y=" + y + " cx=" + sprite.getCentreX(),
                sprite.getGroundMode() == GroundMode.LEFTWALL);
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
            runner.stepFrame(false, false, false, false, false);
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
            runner.stepFrame(false, false, true, false, false);
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
                runner.stepFrame(false, false, false, false, false);
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
            resetAtTopLeft(startX, TARGET_Y);
            for (int frame = 0; frame < 24; frame++) {
                runner.stepFrame(false, false, true, false, false); // hold left
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
        resetAtTopLeft(1903, TARGET_Y);
        sprite.setTopSolidBit((byte) 0x0D);
        sprite.setLrbSolidBit((byte) 0x0E);
        Sonic1Level level = (Sonic1Level) LevelManager.getInstance().getCurrentLevel();
        int aX0 = sprite.getCentreX() - sprite.getXRadius();
        int bX0 = sprite.getCentreX() + sprite.getXRadius();
        int footY0 = sprite.getCentreY() + sprite.getYRadius();
        printVerticalScanState("A0", aX0, footY0);
        printVerticalScanState("B0", bX0, footY0);
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

        runner.stepFrame(false, false, true, false, false);
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
        printVerticalScanState("A1", aX1, footY1);
        printVerticalScanState("B1", bX1, footY1);
    }

    @Test
    public void diagnostic1903SingleLeftStepWithTop0D() {
        System.out.println("=== diagnostic1903SingleLeftStepWithTop0D ===");
        resetAtTopLeft(1903, TARGET_Y);
        sprite.setTopSolidBit((byte) 0x0D);
        sprite.setLrbSolidBit((byte) 0x0E);
        diagnostic1903SingleLeftStep();
    }

    private void printVerticalScanState(String label, int x, int y) {
        LevelManager lm = LevelManager.getInstance();
        byte topBit = sprite.getTopSolidBit();
        System.out.printf("  %s sensor at (%d,%d) topBit=0x%02X%n", label, x, y, topBit & 0xFF);
        printTileState(label + " cur ", lm, x, y, topBit);
        printTileState(label + " next", lm, x, y + 16, topBit);
        printTileState(label + " prev", lm, x, y - 16, topBit);
    }

    private void printTileState(String label, LevelManager lm, int x, int y, byte solidityBit) {
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
}
