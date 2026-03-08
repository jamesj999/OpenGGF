package com.openggf.tests;

import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessLevelTransitionRequest.TransitionType;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingManager;
import com.openggf.camera.Camera;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.*;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for {@link LevelManager#executeActTransition}.
 * <p>
 * Verifies that act transitions rebuild the ObjectManager and RingManager
 * with the new act's spawn data, rather than just clearing runtime state
 * on the old managers (which would leave stale spawn lists).
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestActTransitionHeadless {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 96, (short) 655)
                .build();

        // Ensure camera has a focused sprite — executeActTransition calls
        // camera.updatePosition(true) which requires a focused sprite.
        // resetPerTest() creates a fresh Camera singleton with no focus.
        Camera.getInstance().setFocusedSprite(fixture.sprite());
        Camera.getInstance().setFrozen(false);
    }

    // ========== Manager Rebuild ==========

    @Test
    public void executeActTransitionRebuildsObjectManager() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        ObjectManager beforeOM = lm.getObjectManager();
        assertNotNull("ObjectManager should exist before transition", beforeOM);

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        ObjectManager afterOM = lm.getObjectManager();
        assertNotNull("ObjectManager should exist after transition", afterOM);
        assertNotSame("ObjectManager should be a new instance after transition",
                beforeOM, afterOM);
    }

    @Test
    public void executeActTransitionRebuildsRingManager() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        RingManager beforeRM = lm.getRingManager();
        assertNotNull("RingManager should exist before transition", beforeRM);

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        RingManager afterRM = lm.getRingManager();
        assertNotNull("RingManager should exist after transition", afterRM);
        assertNotSame("RingManager should be a new instance after transition",
                beforeRM, afterRM);
    }

    @Test
    public void executeActTransitionSpawnListIsNotStaleReference() throws Exception {
        LevelManager lm = LevelManager.getInstance();

        // Capture the Act 1 ObjectManager's spawn list reference
        List<ObjectSpawn> act1SpawnRef = lm.getObjectManager().getAllSpawns();

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        // The new manager's spawn list should NOT be the same List instance
        // as the old manager's. Even if the data happens to match (EHZ shares
        // layout), the reference must come from the rebuilt manager.
        List<ObjectSpawn> act2SpawnRef = lm.getObjectManager().getAllSpawns();
        assertNotSame("Spawn list reference should be from new manager, not stale",
                act1SpawnRef, act2SpawnRef);
    }

    @Test
    public void executeActTransitionObjectsMatchLevel() throws Exception {
        LevelManager lm = LevelManager.getInstance();

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        // After transition, ObjectManager's spawns should exactly match
        // the level's object list (proving rebuild used new data)
        List<ObjectSpawn> managerSpawns = lm.getObjectManager().getAllSpawns();
        List<ObjectSpawn> levelSpawns = lm.getCurrentLevel().getObjects();
        assertEquals("ObjectManager spawns should match level objects after transition",
                levelSpawns.size(), managerSpawns.size());
        assertEquals("ObjectManager spawns should be identical to level objects",
                levelSpawns, managerSpawns);
    }

    // ========== Coordinate Offsets ==========

    @Test
    public void executeActTransitionCompletesWithOffsets() throws Exception {
        // Verifies that executeActTransition handles player and camera offsets
        // without error. Exact offset values aren't asserted because
        // restoreCameraBoundsForCurrentLevel + updatePosition(true) snap
        // final positions to level bounds.
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .playerOffset(100, -50)
                .cameraOffset(50, -25)
                .preserveMusic(true)
                .build();

        LevelManager.getInstance().executeActTransition(request);

        assertNotNull("Level should exist after transition with offsets",
                LevelManager.getInstance().getCurrentLevel());
    }

    // ========== Zone/Act State ==========

    @Test
    public void executeActTransitionUpdatesCurrentZoneAndAct() throws Exception {
        LevelManager lm = LevelManager.getInstance();

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        assertEquals("Zone should be EHZ after transition", ZONE_EHZ, lm.getCurrentZone());
        assertEquals("Act should be 2 (index 1) after transition", ACT_2, lm.getCurrentAct());
    }

    // ========== Manager Windowing Uses Live Camera ==========

    @Test
    public void executeActTransitionWindowsManagersAtLiveCameraX() throws Exception {
        LevelManager lm = LevelManager.getInstance();

        // Position the live camera at X=5000, well past the level start.
        // If rebuildManagersForActTransition uses the stale cached camera
        // field (which may hold X=0 after Camera.resetInstance()), the
        // spawn window would be [−128, 640] and no objects near X=5000
        // would be active. The correct behavior uses the live camera so
        // the spawn window is [4872, 5640].
        Camera cam = Camera.getInstance();
        cam.setX((short) 5000);

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        // After transition, active spawns should include objects near
        // the camera (X=5000), not near the level start (X=0).
        Collection<ObjectSpawn> active = lm.getObjectManager().getActiveSpawns();
        boolean hasSpawnNearCamera = active.stream()
                .anyMatch(s -> s.x() >= 4800 && s.x() <= 5700);
        boolean hasSpawnNearStart = active.stream()
                .anyMatch(s -> s.x() < 1000);

        assertTrue("Active spawns should include objects near camera X=5000, " +
                "proving windowing used the live camera (found " + active.size() +
                " active spawns)", hasSpawnNearCamera);
        assertFalse("Active spawns should NOT include objects near level start " +
                "when camera is at X=5000", hasSpawnNearStart);
    }
}
