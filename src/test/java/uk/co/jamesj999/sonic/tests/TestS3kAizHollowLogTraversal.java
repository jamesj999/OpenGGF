package uk.co.jamesj999.sonic.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Map;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizHollowTreeObjectInstance;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.GroundMode;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizHollowLogTraversal {

    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;
    private static final int FPS = 60;
    private static final int TIMEOUT_FRAMES = 7 * FPS;

    // User-provided debug state (top-left position and raw speeds).
    private static final short START_X = (short) 11164;
    private static final short START_Y = (short) 951;
    private static final short START_X_SPEED = (short) 1954;
    private static final short START_Y_SPEED = (short) 803;
    private static final short START_G_SPEED = (short) 2120;
    private static final byte START_ANGLE = 0x10;

    private static final short WAYPOINT_X = (short) 11412;
    private static final short WAYPOINT_Y = (short) 1070;
    private static final int TARGET_EXIT_Y = 808;
    private static final int AIZ_MINIBOSS_TRIGGER_X = 0x2F10;
    private static final int TOP_EXIT_CHECK_MAX_FRAMES = 96;
    private static final int TOP_EXIT_CHECK_MAX_X = AIZ_MINIBOSS_TRIGGER_X - 0x20;

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner runner;
    private Object oldSkipIntros;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        GraphicsManager.getInstance().initHeadless();

        String mainCode = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 100, (short) 624);
        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(ZONE_AIZ, ACT_1);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        runner = new HeadlessTestRunner(sprite);

        // Apply exact debug state from the report.
        applyDebugStartState();
    }

    @After
    public void tearDown() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
    }

    @Test
    public void hollowLogTraversal_regressionScenario_fromDebugCoords() {
        boolean reachedWaypoint = false;
        boolean reachedExitHeight = false;
        int minYAfterWaypoint = Integer.MAX_VALUE;
        int minYOverall = Integer.MAX_VALUE;
        int minYOverallFrame = -1;
        int stuckFramesNear1097 = 0;
        boolean enteredHollowLogRide = false;
        int firstRideFrame = -1;
        int rideFrames = 0;
        int firstRideOffFrame = -1;
        int frame = 0;
        int prevX = sprite.getX();

        // Phase 1: advance while holding Right until exact waypoint is reached.
        for (; frame < TIMEOUT_FRAMES; frame++) {
            runner.stepFrame(false, false, false, true, false);

            int x = sprite.getX();
            int y = sprite.getY();
            if (y < minYOverall) {
                minYOverall = y;
                minYOverallFrame = frame;
            }
            if (!enteredHollowLogRide && sprite.isObjectMappingFrameControl()) {
                enteredHollowLogRide = true;
                firstRideFrame = frame;
            }
            if (sprite.isObjectMappingFrameControl()) {
                rideFrames++;
            } else if (enteredHollowLogRide && firstRideOffFrame < 0) {
                firstRideOffFrame = frame;
            }

            // Frame-granular crossing check: the exact waypoint can occur between two
            // discrete simulation steps (e.g. x 11404 -> 11414 with y=1070).
            boolean crossedWaypointX = prevX <= WAYPOINT_X && x >= WAYPOINT_X;
            if (!reachedWaypoint && y == WAYPOINT_Y && crossedWaypointX) {
                reachedWaypoint = true;
                frame++;
                break;
            }
            prevX = x;
        }

        // Phase 2: continue holding Right and verify hollow log traversal.
        for (; frame < TIMEOUT_FRAMES; frame++) {
            runner.stepFrame(false, false, false, true, false);

            int x = sprite.getX();
            int y = sprite.getY();
            int absGSpeed = Math.abs(sprite.getGSpeed());
            if (y < minYOverall) {
                minYOverall = y;
                minYOverallFrame = frame;
            }
            if (!enteredHollowLogRide && sprite.isObjectMappingFrameControl()) {
                enteredHollowLogRide = true;
                firstRideFrame = frame;
            }
            if (sprite.isObjectMappingFrameControl()) {
                rideFrames++;
            } else if (enteredHollowLogRide && firstRideOffFrame < 0) {
                firstRideOffFrame = frame;
            }
            if (y < minYAfterWaypoint) {
                minYAfterWaypoint = y;
            }

            if (y <= TARGET_EXIT_Y) {
                reachedExitHeight = true;
                break;
            }

            // Explicit fail mode from report: speed collapses and Sonic stalls near Y=1097.
            boolean stuckNearBottomCenter = y >= 1095 && y <= 1099 && absGSpeed <= 0x20;
            if (stuckNearBottomCenter) {
                stuckFramesNear1097++;
                if (stuckFramesNear1097 >= 30) {
                    fail("Hollow log failure: Sonic got stuck near Y=1097 with near-zero speed."
                            + " x=" + x + " y=" + y + " gSpeed=" + sprite.getGSpeed()
                            + " frame=" + frame);
                }
            } else {
                stuckFramesNear1097 = 0;
            }
            prevX = x;
        }

        assertTrue("Expected Sonic to pass waypoint X=11412 Y=1070 within 7 in-game seconds. "
                        + "Final X=" + sprite.getX() + " Y=" + sprite.getY()
                        + " Input=HoldRight"
                        + " MinYOverall=" + minYOverall
                        + " MinYOverallFrame=" + minYOverallFrame
                        + " EnteredRide=" + enteredHollowLogRide
                        + " FirstRideFrame=" + firstRideFrame
                        + " RideFrames=" + rideFrames
                        + " FirstRideOffFrame=" + firstRideOffFrame
                        + " ObjCtrl=" + sprite.isObjectControlled()
                        + " ObjMapCtrl=" + sprite.isObjectMappingFrameControl(),
                reachedWaypoint);
        assertTrue("Expected Sonic to traverse hollow log and reach Y<=808 within 7 in-game seconds."
                        + " MinYAfterWaypoint=" + minYAfterWaypoint
                        + " MinYOverall=" + minYOverall
                        + " MinYOverallFrame=" + minYOverallFrame
                        + " FinalY=" + sprite.getY()
                        + " FinalGSpeed=" + sprite.getGSpeed()
                        + " Input=HoldRight"
                        + " EnteredRide=" + enteredHollowLogRide
                        + " FirstRideFrame=" + firstRideFrame
                        + " RideFrames=" + rideFrames
                        + " FirstRideOffFrame=" + firstRideOffFrame
                        + " ObjCtrl=" + sprite.isObjectControlled()
                        + " ObjMapCtrl=" + sprite.isObjectMappingFrameControl(),
                reachedExitHeight);
    }

    @Test
    public void hollowLogTraversal_appliesTreeRevealChunkSwitch() {
        boolean reachedFirstThreshold = false;
        boolean reachedSecondThreshold = false;
        boolean reachedUpperThreshold = false;
        int firstThresholdFrame = -1;
        for (int frame = 0; frame < TIMEOUT_FRAMES; frame++) {
            runner.stepFrame(false, false, false, true, false);

            int revealCounter = AizHollowTreeObjectInstance.getTreeRevealCounter();
            if (revealCounter >= 0x14) {
                reachedFirstThreshold = true;
                if (firstThresholdFrame < 0) {
                    firstThresholdFrame = frame;
                }
            }
            if (revealCounter >= 0x24) {
                reachedSecondThreshold = true;
            }
            if (revealCounter >= 0x34) {
                reachedUpperThreshold = true;
            }
        }

        assertTrue("Expected Hollow Log to drive Events_fg_4 to at least 0x14 in Act 1. "
                        + "Frame=" + firstThresholdFrame,
                reachedFirstThreshold);
        assertTrue("Expected Hollow Log to progress to at least the second reveal threshold (0x24). "
                        + "FirstThresholdFrame=" + firstThresholdFrame,
                reachedSecondThreshold);
        assertTrue("Expected upper reveal progression (counter >= 0x34) to occur for full top-section reveal.",
                reachedUpperThreshold);
    }

    @Test
    public void hollowLogTraversal_treeRevealCompletesAndClears() {
        boolean revealStarted = false;
        boolean revealClearedAfterStart = false;
        int maxRevealCounter = 0;
        int clearFrame = -1;

        for (int frame = 0; frame < TIMEOUT_FRAMES; frame++) {
            runner.stepFrame(false, false, false, true, false);

            int revealCounter = AizHollowTreeObjectInstance.getTreeRevealCounter();
            if (revealCounter > 0) {
                revealStarted = true;
            }
            if (revealCounter > maxRevealCounter) {
                maxRevealCounter = revealCounter;
            }
            if (revealStarted && revealCounter == 0) {
                revealClearedAfterStart = true;
                clearFrame = frame;
                break;
            }
        }

        assertTrue("Expected Events_fg_4 to start (non-zero) during hollow-log traversal.",
                revealStarted);
        assertTrue("Expected Events_fg_4 to self-clear after reveal progression while holding Right."
                        + " MaxCounter=0x" + Integer.toHexString(maxRevealCounter)
                        + " ClearFrame=" + clearFrame,
                revealClearedAfterStart);
    }

    @Test
    public void hollowLogTraversal_doesNotMutateCollisionMapRows() {
        Map map = LevelManager.getInstance().getCurrentLevel().getMap();
        byte[][] before = snapshotTreeColumns(map);

        for (int frame = 0; frame < TIMEOUT_FRAMES; frame++) {
            runner.stepFrame(false, false, false, true, false);
        }

        byte[][] after = snapshotTreeColumns(map);
        for (int row = 0; row < before.length; row++) {
            if (before[row][0] != after[row][0] || before[row][1] != after[row][1]) {
                fail("Expected tree collision-driving map bytes to remain unchanged; row="
                        + row + " before=(" + (before[row][0] & 0xFF) + "," + (before[row][1] & 0xFF) + ")"
                        + " after=(" + (after[row][0] & 0xFF) + "," + (after[row][1] & 0xFF) + ")");
            }
        }
    }

    @Test
    public void hollowLogTraversal_visualRevealStableAcrossRepeatRuns() {
        LevelManager levelManager = LevelManager.getInstance();

        assertTrue("Expected first traversal to trigger and finish tree reveal.",
                runUntilTreeRevealClears());
        int[] firstPass = snapshotRevealWindow(levelManager);

        reloadAizAct1AndApplyDebugStart();
        assertTrue("Expected second traversal to trigger and finish tree reveal.",
                runUntilTreeRevealClears());
        int[] secondPass = snapshotRevealWindow(levelManager);

        int diffCount = 0;
        int firstDiffIndex = -1;
        for (int i = 0; i < firstPass.length; i++) {
            if (firstPass[i] != secondPass[i]) {
                diffCount++;
                if (firstDiffIndex < 0) {
                    firstDiffIndex = i;
                }
            }
        }

        assertTrue("Expected repeat traversal to produce identical reveal tilemap output."
                        + " diffCount=" + diffCount
                        + " firstDiffIndex=" + firstDiffIndex,
                diffCount == 0);
    }

    @Test
    public void hollowLogTraversal_reentryLandsOnBottomFloor() {
        assertTrue("Expected first pass to complete hollow-log ascent before re-entry check.",
                runUntilExitHeightHoldingRight());

        boolean descendedBackIntoTunnel = false;
        boolean landedAtBottomFloor = false;
        int maxYAfterExit = sprite.getY();
        int landedY = -1;

        // Return into the tunnel and ensure Sonic lands instead of falling through.
        for (int frame = 0; frame < TIMEOUT_FRAMES * 3; frame++) {
            runner.stepFrame(false, false, true, false, false);

            int y = sprite.getY();
            if (y > maxYAfterExit) {
                maxYAfterExit = y;
            }
            if (y >= 1024) {
                descendedBackIntoTunnel = true;
            }
            if (descendedBackIntoTunnel && !sprite.getAir() && y >= 1060 && y <= 1130) {
                landedAtBottomFloor = true;
                landedY = y;
                break;
            }
            if (sprite.getDead() || y > 1500) {
                fail("Hollow tree re-entry failure: Sonic fell through bottom floor."
                        + " y=" + y
                        + " dead=" + sprite.getDead()
                        + " frame=" + frame);
            }
        }

        assertTrue("Expected Sonic to descend back into the hollow-tree tunnel after ascent."
                        + " maxYAfterExit=" + maxYAfterExit,
                descendedBackIntoTunnel);
        assertTrue("Expected Sonic to land on the tunnel bottom floor after re-entry."
                        + " landedY=" + landedY
                        + " maxYAfterExit=" + maxYAfterExit,
                landedAtBottomFloor);
    }

    @Test
    public void hollowLogTraversal_topExitMaintainsForwardTraversal() {
        assertTrue("Expected hollow-log ascent to reach top-exit height before momentum check.",
                runUntilExitHeightHoldingRight());

        int exitX = sprite.getX();
        int exitY = sprite.getY();
        boolean releasedFromRide = !sprite.isObjectMappingFrameControl();
        int releaseFrame = releasedFromRide ? 0 : -1;
        int maxForwardGain = 0;
        int groundedNearTopZeroSpeedFrames = 0;
        int finalCheckedX = exitX;
        int finalCheckedFrame = -1;

        for (int frame = 0; frame < TOP_EXIT_CHECK_MAX_FRAMES; frame++) {
            runner.stepFrame(false, false, false, true, false);

            int x = sprite.getX();
            int y = sprite.getY();
            finalCheckedX = x;
            finalCheckedFrame = frame;
            int forwardGain = x - exitX;
            if (forwardGain > maxForwardGain) {
                maxForwardGain = forwardGain;
            }
            if (!releasedFromRide && !sprite.isObjectMappingFrameControl()) {
                releasedFromRide = true;
                releaseFrame = frame;
            }

            boolean nearTopBand = y >= 760 && y <= 920;
            if (nearTopBand && !sprite.getAir() && Math.abs(sprite.getGSpeed()) <= 0x20) {
                groundedNearTopZeroSpeedFrames++;
                if (groundedNearTopZeroSpeedFrames >= 20) {
                    fail("Top-exit stall regression: Sonic grounded near top with zero momentum."
                            + " frame=" + frame
                            + " x=" + x
                            + " y=" + y
                            + " gSpeed=" + sprite.getGSpeed()
                            + " exitX=" + exitX
                            + " exitY=" + exitY);
                }
            } else {
                groundedNearTopZeroSpeedFrames = 0;
            }

            // Keep this regression scoped to hollow-log exit behavior; beyond this point,
            // AIZ miniboss scripting can legitimately alter camera/movement state.
            if (x >= TOP_EXIT_CHECK_MAX_X) {
                break;
            }
        }

        assertTrue("Expected ride release to occur shortly after top-exit traversal."
                        + " releaseFrame=" + releaseFrame
                        + " exitX=" + exitX
                        + " exitY=" + exitY,
                releasedFromRide);
        assertTrue("Expected continued forward traversal after top exit while holding Right."
                        + " maxForwardGain=" + maxForwardGain
                        + " releaseFrame=" + releaseFrame
                        + " finalCheckedFrame=" + finalCheckedFrame
                        + " finalCheckedX=0x" + Integer.toHexString(finalCheckedX)
                        + " exitX=" + exitX
                        + " exitY=" + exitY,
                maxForwardGain >= 48);
    }

    private static byte[][] snapshotTreeColumns(Map map) {
        byte[][] values = new byte[9][2];
        for (int row = 0; row < values.length; row++) {
            values[row][0] = map.getValue(0, 0x59, row);
            values[row][1] = map.getValue(0, 0x5A, row);
        }
        return values;
    }

    private void applyDebugStartState() {
        Camera camera = Camera.getInstance();
        sprite.setX(START_X);
        sprite.setY(START_Y);
        sprite.setXSpeed(START_X_SPEED);
        sprite.setYSpeed(START_Y_SPEED);
        sprite.setGSpeed(START_G_SPEED);
        sprite.setAngle(START_ANGLE);
        sprite.setGroundMode(GroundMode.GROUND);
        sprite.setAir(false);
        sprite.setRolling(false);
        sprite.setJumping(false);
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setObjectMappingFrameControl(false);
        sprite.setForcedAnimationId(-1);

        camera.updatePosition(true);
        sprite.updateSensors(sprite.getX(), sprite.getY());
    }

    private boolean runUntilTreeRevealClears() {
        boolean revealStarted = false;
        for (int frame = 0; frame < TIMEOUT_FRAMES * 2; frame++) {
            runner.stepFrame(false, false, false, true, false);
            int revealCounter = AizHollowTreeObjectInstance.getTreeRevealCounter();
            if (revealCounter > 0) {
                revealStarted = true;
            }
            if (revealStarted && revealCounter == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean runUntilExitHeightHoldingRight() {
        for (int frame = 0; frame < TIMEOUT_FRAMES * 2; frame++) {
            runner.stepFrame(false, false, false, true, false);
            if (sprite.getY() <= TARGET_EXIT_Y) {
                return true;
            }
        }
        return false;
    }

    private void reloadAizAct1AndApplyDebugStart() {
        try {
            LevelManager levelManager = LevelManager.getInstance();
            levelManager.loadZoneAndAct(ZONE_AIZ, ACT_1);
            GroundSensor.setLevelManager(levelManager);
            Camera.getInstance().updatePosition(true);
            applyDebugStartState();
        } catch (Exception e) {
            throw new AssertionError("Failed to reload AIZ1 for repeat traversal.", e);
        }
    }

    private static int[] snapshotRevealWindow(LevelManager levelManager) {
        final int startX = 0x2C80;
        final int endXExclusive = 0x2D80;
        final int startY = 0x280;
        final int endYExclusive = 0x480;
        final int step = 8;

        int widthTiles = (endXExclusive - startX) / step;
        int heightTiles = (endYExclusive - startY) / step;
        int[] descriptors = new int[widthTiles * heightTiles];
        int index = 0;

        for (int y = startY; y < endYExclusive; y += step) {
            for (int x = startX; x < endXExclusive; x += step) {
                descriptors[index++] = readForegroundDescriptorCompat(levelManager, x, y);
            }
        }
        return descriptors;
    }

    private static int readForegroundDescriptorCompat(LevelManager levelManager, int worldX, int worldY) {
        try {
            Method fromTilemap = LevelManager.class.getMethod(
                    "getForegroundTileDescriptorFromTilemapAtWorld",
                    int.class,
                    int.class);
            return (int) fromTilemap.invoke(levelManager, worldX, worldY);
        } catch (NoSuchMethodException ignored) {
            // Older branches may not expose foreground descriptor APIs used by this regression test.
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed reading foreground tile descriptor via tilemap API.", e);
        }

        try {
            Method resolved = LevelManager.class.getMethod(
                    "getForegroundTileDescriptorAtWorld",
                    int.class,
                    int.class);
            return (int) resolved.invoke(levelManager, worldX, worldY);
        } catch (NoSuchMethodException ignored) {
            // Fall through to map-chunk snapshot fallback.
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed reading foreground tile descriptor via resolved API.", e);
        }

        Map map = levelManager.getCurrentLevel().getMap();
        int chunkX = Math.clamp(worldX >> 7, 0, map.getWidth() - 1);
        int chunkY = Math.clamp(worldY >> 7, 0, map.getHeight() - 1);
        return map.getValue(0, chunkX, chunkY) & 0xFF;
    }
}
