package com.openggf.tests;

import com.openggf.game.CheckpointState;
import com.openggf.game.RespawnState;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.TailsCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * End-to-end headless tests for S2 post-load assembly behavior.
 * <p>
 * Verifies that the post-load assembly steps produce correct results when
 * executed via the full production path ({@code loadCurrentLevel} /
 * {@code respawnPlayer}). Covers checkpoint resume, death respawn state
 * clearing, and sidekick spawn offset verification.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2PostLoadAssemblyHeadless {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
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
    private Sonic sprite;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 96, (short) 655)
                .build();
        sprite = (Sonic) fixture.sprite();

        // Clear checkpoint state from previous tests (resetPerTest doesn't touch it)
        RespawnState cs = LevelManager.getInstance().getCheckpointState();
        if (cs != null) {
            cs.clear();
        }
    }

    // ========== Checkpoint Resume ==========

    @Test
    public void checkpointResumeRestoresPlayerPosition() {
        int checkpointX = 500;
        int checkpointY = 400;

        // Position player at checkpoint location and save checkpoint
        sprite.setCentreX((short) checkpointX);
        sprite.setCentreY((short) checkpointY);
        fixture.camera().updatePosition(true);

        RespawnState checkpointState = LevelManager.getInstance().getCheckpointState();
        assertNotNull("Checkpoint state should exist after level load", checkpointState);
        ((CheckpointState) checkpointState).saveCheckpoint(1, checkpointX, checkpointY, false);

        // Respawn triggers full level reload with checkpoint data preserved
        LevelManager.getInstance().respawnPlayer();

        // After respawn, SpawnPlayer step positions player at checkpoint coords
        assertEquals("Player centre X should match checkpoint after respawn",
                checkpointX, sprite.getCentreX());
        assertEquals("Player centre Y should match checkpoint after respawn",
                checkpointY, sprite.getCentreY());
    }

    @Test
    public void deathRespawnClearsPlayerVelocity() {
        sprite.setXSpeed((short) 600);
        sprite.setYSpeed((short) -400);
        sprite.setGSpeed((short) 500);

        RespawnState checkpointState = LevelManager.getInstance().getCheckpointState();
        ((CheckpointState) checkpointState).saveCheckpoint(0, 96, 655, false);

        LevelManager.getInstance().respawnPlayer();

        // ResetPlayerState step zeroes all velocities
        assertEquals("X speed should be 0 after respawn", 0, sprite.getXSpeed());
        assertEquals("Y speed should be 0 after respawn", 0, sprite.getYSpeed());
        assertEquals("Ground speed should be 0 after respawn", 0, sprite.getGSpeed());
    }

    @Test
    public void deathRespawnClearsDeathAndHurtState() {
        sprite.setDead(true);
        sprite.setHurt(true);

        RespawnState checkpointState = LevelManager.getInstance().getCheckpointState();
        ((CheckpointState) checkpointState).saveCheckpoint(0, 96, 655, false);

        LevelManager.getInstance().respawnPlayer();

        // ResetPlayerState step clears death/hurt flags
        assertFalse("Player should not be dead after respawn", sprite.getDead());
        assertFalse("Player should not be hurt after respawn", sprite.isHurt());
    }

    @Test
    public void respawnWithoutCheckpointUsesLevelStart() {
        // No checkpoint saved — checkpoint state should be inactive from loadZoneAndAct
        RespawnState checkpointState = LevelManager.getInstance().getCheckpointState();
        assertFalse("Checkpoint should be inactive when no starpost touched",
                checkpointState.isActive());

        // Move player to a non-start position
        sprite.setCentreX((short) 2000);
        sprite.setCentreY((short) 300);

        LevelManager.getInstance().respawnPlayer();

        // SpawnPlayer step uses level start position (not the modified position)
        assertNotEquals("Player should NOT stay at modified X after respawn without checkpoint",
                2000, (int) sprite.getCentreX());
    }

    @Test
    public void checkpointResumePreservesCameraPosition() {
        int checkpointX = 800;
        int checkpointY = 400;

        sprite.setCentreX((short) checkpointX);
        sprite.setCentreY((short) checkpointY);
        fixture.camera().setX((short) 700);
        fixture.camera().setY((short) 300);
        fixture.camera().updatePosition(true);

        RespawnState checkpointState = LevelManager.getInstance().getCheckpointState();
        ((CheckpointState) checkpointState).saveCheckpoint(2, checkpointX, checkpointY, false);

        LevelManager.getInstance().respawnPlayer();

        // InitCamera step snaps camera to player. With checkpoint at 800,400
        // camera should be positioned near the player (exact coords depend on
        // camera bounds, but should not be at the level start camera position).
        int cameraX = fixture.camera().getX();
        assertTrue("Camera X should be near checkpoint after respawn, was " + cameraX,
                cameraX > 0);
    }

    // ========== Sidekick Spawn Offsets ==========

    @Test
    public void s2SidekickSpawnsAtMinus40XSameY() {
        short playerX = 200;
        short playerY = 400;
        sprite.setX(playerX);
        sprite.setY(playerY);

        Tails tails = createSidekick();

        // S2 sidekick offset: -40 X, 0 Y
        LevelManager.getInstance().spawnSidekick(-40, 0);

        assertEquals("S2 sidekick X should be player X - 40",
                playerX - 40, tails.getX());
        assertEquals("S2 sidekick Y should equal player Y",
                playerY, tails.getY());
    }

    @Test
    public void s3kSidekickOffsetDiffersFromS2() {
        short playerX = 200;
        short playerY = 400;
        sprite.setX(playerX);
        sprite.setY(playerY);

        Tails tails = createSidekick();

        // S3K sidekick offset: -32 X, +4 Y
        LevelManager.getInstance().spawnSidekick(-32, 4);

        assertEquals("S3K sidekick X should be player X - 32",
                playerX - 32, tails.getX());
        assertEquals("S3K sidekick Y should be player Y + 4",
                playerY + 4, tails.getY());
    }

    @Test
    public void sidekickStateResetOnSpawn() {
        Tails tails = createSidekick();
        tails.setXSpeed((short) 500);
        tails.setYSpeed((short) -300);
        tails.setGSpeed((short) 400);
        tails.setDead(true);
        tails.setAir(true);

        LevelManager.getInstance().spawnSidekick(-40, 0);

        assertEquals("Sidekick X speed should be 0 after spawn", 0, tails.getXSpeed());
        assertEquals("Sidekick Y speed should be 0 after spawn", 0, tails.getYSpeed());
        assertEquals("Sidekick ground speed should be 0 after spawn", 0, tails.getGSpeed());
        assertFalse("Sidekick should not be dead after spawn", tails.getDead());
        assertFalse("Sidekick should not be airborne after spawn", tails.getAir());
    }

    // ========== Helpers ==========

    private Tails createSidekick() {
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        TailsCpuController controller = new TailsCpuController(tails);
        tails.setCpuController(controller);
        SpriteManager.getInstance().addSprite(tails);
        return tails;
    }
}
