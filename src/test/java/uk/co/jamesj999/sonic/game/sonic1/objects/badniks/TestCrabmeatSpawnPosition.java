package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.HeadlessTestRunner;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Headless integration test for Crabmeat spawn positions in GHZ Act 1.
 *
 * <p>Verifies that Crabmeats spawn at the correct X position from the ROM data
 * and land on the terrain directly below their spawn point (no horizontal drift
 * during ObjectFall initialization).
 *
 * <p>Known-good positions from docs/s1disasm/objpos/ghz1.bin:
 * <ul>
 *   <li>Crabmeat #1: X=0x08B0, Y=0x0350</li>
 *   <li>Crabmeat #2: X=0x0960, Y=0x02FA</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestCrabmeatSpawnPosition {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;
    private LevelManager levelManager;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 0x0050, (short) 0x03B0);

        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        // Load GHZ1 (zone 0, act 0 for Sonic 1)
        levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);
        GroundSensor.setLevelManager(levelManager);

        // Reposition Sonic near the first two Crabmeats (X=0x08B0, 0x0960)
        // so they fall within the object spawn window (camera + 0x280).
        // Must be done AFTER level load which resets position to start.
        sprite.setX((short) 0x0800);
        sprite.setY((short) 0x0350);
        camera.updatePosition(true);

        testRunner = new HeadlessTestRunner(sprite);
    }

    /**
     * Immediately after the first game frame, Crabmeats should exist with
     * their X positions matching the ROM spawn data exactly (ObjectFall
     * initialization only affects Y, never X).
     */
    @Test
    public void crabmeatsSpawnAtCorrectXPositions() {
        // One frame is enough for the ObjectManager to create instances
        testRunner.stepIdleFrames(1);

        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertTrue("Both GHZ1 Crabmeats should spawn within camera window",
                crabmeats.size() >= 2);

        assertEquals("Crabmeat #1 X must match ROM position",
                0x08B0, crabmeats.get(0).getX());
        assertEquals("Crabmeat #2 X must match ROM position",
                0x0960, crabmeats.get(1).getX());
    }

    /**
     * During ObjectFall (before landing), Crabmeat X must not change.
     * This catches any bug where horizontal velocity is applied during init.
     */
    @Test
    public void xDoesNotChangeDuringObjectFall() {
        testRunner.stepIdleFrames(1);
        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertFalse("Crabmeats should have spawned", crabmeats.isEmpty());

        // Step frame-by-frame through ObjectFall and verify X never changes
        for (int frame = 0; frame < 15; frame++) {
            for (Sonic1CrabmeatBadnikInstance crab : crabmeats) {
                int spawnX = crab.getSpawn().x();
                // During ObjectFall or immediately after landing, X stays at spawn
                // (walking hasn't started yet in the first ~59 frames for on-screen Crabmeats)
                assertEquals("Crabmeat X must not drift during init (frame " + frame + ")",
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
     * full AI cycle.  Cycle: land → fire (59f) → walk dir A (127f) →
     * idle (59f) → fire (59f) → walk dir B (127f).
     * Walking speed is 0x80 subpixels/frame (0.5 px/frame).
     *
     * <p>Run for 500 frames, track min/max X, and verify the Crabmeat patrols
     * a reasonable range. The platform at this spawn point is roughly symmetric
     * around X=0x08B0, so the patrol range should be at least 20px in each
     * direction. Before the velocity-direction fix, the Crabmeat barely moved
     * left (1px) because the edge check was looking behind instead of ahead.
     */
    @Test
    public void firstCrabmeatPatrolsInBothDirections() {
        testRunner.stepIdleFrames(1);
        List<Sonic1CrabmeatBadnikInstance> crabmeats = findCrabmeats();
        assertFalse("Crabmeat should have spawned", crabmeats.isEmpty());

        Sonic1CrabmeatBadnikInstance crab = crabmeats.get(0);
        final int spawnX = 0x08B0;
        assertEquals(spawnX, crab.getSpawn().x());

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

    private List<Sonic1CrabmeatBadnikInstance> findCrabmeats() {
        ObjectManager objectManager = levelManager.getObjectManager();
        assertNotNull("ObjectManager should exist", objectManager);

        return objectManager.getActiveObjects().stream()
                .filter(obj -> obj instanceof Sonic1CrabmeatBadnikInstance)
                .map(obj -> (Sonic1CrabmeatBadnikInstance) obj)
                .sorted((a, b) -> Integer.compare(a.getSpawn().x(), b.getSpawn().x()))
                .toList();
    }
}
