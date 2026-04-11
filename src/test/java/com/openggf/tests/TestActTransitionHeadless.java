package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessLevelTransitionRequest.TransitionType;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingManager;
import com.openggf.camera.Camera;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link LevelManager#executeActTransition}.
 * <p>
 * Verifies that act transitions rebuild the ObjectManager and RingManager
 * with the new act's spawn data, rather than just clearing runtime state
 * on the old managers (which would leave stale spawn lists).
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestActTransitionHeadless {
    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;
    private static SharedLevel sharedLevel;

    @BeforeAll
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterAll
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;

    @BeforeEach
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 96, (short) 655)
                .build();

        // Ensure camera has a focused sprite â€” executeActTransition calls
        // camera.updatePosition(true) which requires a focused sprite.
        // resetPerTest() creates a fresh Camera singleton with no focus.
        GameServices.camera().setFocusedSprite(fixture.sprite());
        GameServices.camera().setFrozen(false);
    }

    // ========== Manager Rebuild ==========

    @Test
    public void executeActTransitionRebuildsObjectManager() throws Exception {
        LevelManager lm = GameServices.level();
        ObjectManager beforeOM = lm.getObjectManager();
        assertNotNull(beforeOM, "ObjectManager should exist before transition");

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        ObjectManager afterOM = lm.getObjectManager();
        assertNotNull(afterOM, "ObjectManager should exist after transition");
        assertNotSame(beforeOM, afterOM, "ObjectManager should be a new instance after transition");
    }

    @Test
    public void executeActTransitionRebuildsRingManager() throws Exception {
        LevelManager lm = GameServices.level();
        RingManager beforeRM = lm.getRingManager();
        assertNotNull(beforeRM, "RingManager should exist before transition");

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        RingManager afterRM = lm.getRingManager();
        assertNotNull(afterRM, "RingManager should exist after transition");
        assertNotSame(beforeRM, afterRM, "RingManager should be a new instance after transition");
    }

    @Test
    public void executeActTransitionSpawnListIsNotStaleReference() throws Exception {
        LevelManager lm = GameServices.level();

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
        assertNotSame(act1SpawnRef, act2SpawnRef, "Spawn list reference should be from new manager, not stale");
    }

    @Test
    public void executeActTransitionObjectsMatchLevel() throws Exception {
        LevelManager lm = GameServices.level();

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
        assertEquals(levelSpawns.size(), managerSpawns.size(), "ObjectManager spawns should match level objects after transition");
        assertEquals(levelSpawns, managerSpawns, "ObjectManager spawns should be identical to level objects");
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

        GameServices.level().executeActTransition(request);

        assertNotNull(GameServices.level().getCurrentLevel(), "Level should exist after transition with offsets");
    }

    // ========== Zone/Act State ==========

    @Test
    public void executeActTransitionUpdatesCurrentZoneAndAct() throws Exception {
        LevelManager lm = GameServices.level();

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();

        lm.executeActTransition(request);

        assertEquals(ZONE_EHZ, lm.getCurrentZone(), "Zone should be EHZ after transition");
        assertEquals(ACT_2, lm.getCurrentAct(), "Act should be 2 (index 1) after transition");
    }

    // ========== Manager Windowing Uses Live Camera ==========

    @Test
    public void executeActTransitionWindowsManagersAtLiveCameraX() throws Exception {
        LevelManager lm = GameServices.level();

        // Position the live camera at X=5000, well past the level start.
        // If rebuildManagersForActTransition uses the stale cached camera
        // field (which may hold X=0 after Camera.resetState()), the
        // spawn window would be [âˆ’128, 640] and no objects near X=5000
        // would be active. The correct behavior uses the live camera so
        // the spawn window is [4872, 5640].
        Camera cam = GameServices.camera();
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

        assertTrue(hasSpawnNearCamera, "Active spawns should include objects near camera X=5000, " +
                "proving windowing used the live camera (found " + active.size() +
                " active spawns)");
        assertFalse(hasSpawnNearStart, "Active spawns should NOT include objects near level start " +
                "when camera is at X=5000");
    }
}



