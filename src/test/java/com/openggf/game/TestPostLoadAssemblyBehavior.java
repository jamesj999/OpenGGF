package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic1.Sonic1ConveyorState;
import com.openggf.game.sonic1.Sonic1LevelInitProfile;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.Sonic2LevelInitProfile;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelInitProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end tests for post-load assembly behavior.
 * <p>
 * Tests actual step behavior (checkpoint context round-trip, title card
 * suppression logic, sidekick presence), not just step-list verification.
 * No ROM required.
 */
public class TestPostLoadAssemblyBehavior {

    @Before
    public void resetCamera() {
        RuntimeManager.createGameplay();
        GameServices.camera().resetState();
    }

    @After
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    // ========== Checkpoint Resume: Context Snapshot Round-Trip ==========

    @Test
    public void checkpointSnapshotPreservesAllFields() {
        Camera camera = GameServices.camera();
        camera.setX((short) 1000);
        camera.setY((short) 500);

        CheckpointState state = new CheckpointState();
        state.saveCheckpoint(3, 200, 400, false);

        LevelLoadContext ctx = new LevelLoadContext();
        ctx.snapshotCheckpoint(state);

        assertTrue("Context should report checkpoint active", ctx.hasCheckpoint());
        assertEquals(200, ctx.getCheckpointX());
        assertEquals(400, ctx.getCheckpointY());
        assertEquals(1000, ctx.getCheckpointCameraX());
        assertEquals(500, ctx.getCheckpointCameraY());
        assertEquals(3, ctx.getCheckpointIndex());
    }

    @Test
    public void checkpointRestoreFromSavedRoundTrip() {
        Camera camera = GameServices.camera();
        camera.setX((short) 800);
        camera.setY((short) 350);

        CheckpointState state = new CheckpointState();
        state.saveCheckpoint(5, 600, 900, false);

        // Step 1: Snapshot to context (simulates loadCurrentLevel pre-load)
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.snapshotCheckpoint(state);

        // Step 2: Clear state (simulates InitPlayerAndCheckpoint step)
        state.clear();
        assertFalse("Checkpoint should be inactive after clear", state.isActive());

        // Step 3: Restore from context (simulates RestoreCheckpoint step)
        state.restoreFromSaved(
                ctx.getCheckpointX(), ctx.getCheckpointY(),
                ctx.getCheckpointCameraX(), ctx.getCheckpointCameraY(),
                ctx.getCheckpointIndex());

        assertTrue("Checkpoint should be active after restore", state.isActive());
        assertEquals(600, state.getSavedX());
        assertEquals(900, state.getSavedY());
        assertEquals(800, state.getSavedCameraX());
        assertEquals(350, state.getSavedCameraY());
        assertEquals(5, state.getLastCheckpointIndex());
    }

    @Test
    public void checkpointSnapshotWithWaterState() {
        CheckpointState state = createCheckpoint(2, 100, 200);
        state.saveWaterState(0x300, 4);

        LevelLoadContext ctx = new LevelLoadContext();
        ctx.snapshotCheckpoint(state);

        assertTrue("Context should report water state", ctx.hasWaterState());
        assertEquals(0x300, ctx.getCheckpointWaterLevel());
        assertEquals(4, ctx.getCheckpointWaterRoutine());
    }

    @Test
    public void checkpointSnapshotWithoutWaterState() {
        CheckpointState state = createCheckpoint(1, 100, 200);
        // Don't save water state

        LevelLoadContext ctx = new LevelLoadContext();
        ctx.snapshotCheckpoint(state);

        assertFalse("Context should report no water state", ctx.hasWaterState());
    }

    @Test
    public void checkpointSnapshotNullIsInactive() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.snapshotCheckpoint(null);

        assertFalse("Null state should yield inactive context", ctx.hasCheckpoint());
        assertEquals(0, ctx.getCheckpointX());
        assertEquals(0, ctx.getCheckpointY());
        assertEquals(-1, ctx.getCheckpointIndex());
    }

    @Test
    public void checkpointSnapshotInactiveStateIsInactive() {
        CheckpointState state = new CheckpointState();
        assertFalse("Fresh CheckpointState should be inactive", state.isActive());

        LevelLoadContext ctx = new LevelLoadContext();
        ctx.snapshotCheckpoint(state);

        assertFalse("Inactive state should yield inactive context", ctx.hasCheckpoint());
    }

    // ========== Death Respawn: Context Configuration ==========

    @Test
    public void deathRespawnContextSuppressesTitleCard() {
        // respawnPlayer() calls loadCurrentLevel(false), setting showTitleCard=false
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setShowTitleCard(false);
        ctx.setIncludePostLoadAssembly(true);

        assertFalse("Death respawn context should suppress title card",
                ctx.isShowTitleCard());
        assertTrue("Death respawn context should include post-load assembly",
                ctx.isIncludePostLoadAssembly());
    }

    @Test
    public void freshStartContextShowsTitleCard() {
        // loadCurrentLevel() defaults to showTitleCard=true
        LevelLoadContext ctx = new LevelLoadContext();

        assertTrue("Fresh context should default to showing title card",
                ctx.isShowTitleCard());
    }

    @Test
    public void deathRespawnContextPreservesCheckpointData() {
        CheckpointState state = createCheckpoint(5, 300, 600);

        // Replicate what loadCurrentLevel(false) does:
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setShowTitleCard(false);
        ctx.setIncludePostLoadAssembly(true);
        ctx.snapshotCheckpoint(state);

        assertFalse("Title card should be suppressed on respawn", ctx.isShowTitleCard());
        assertTrue("Checkpoint data should survive into respawn context", ctx.hasCheckpoint());
        assertEquals(300, ctx.getCheckpointX());
        assertEquals(600, ctx.getCheckpointY());
    }

    // ========== S3K Title Card Suppression on Checkpoint Resume ==========

    @Test
    public void s3kTitleCardStepExecutesAsNoOpWhenCheckpointActive() {
        CheckpointState state = createCheckpoint(1, 100, 200);

        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        ctx.snapshotCheckpoint(state);
        assertTrue(ctx.hasCheckpoint());

        Sonic3kLevelInitProfile profile = newS3kProfile();
        InitStep titleCardStep = findStep(profile.levelLoadSteps(ctx), "RequestTitleCard");
        assertNotNull("S3K profile should include RequestTitleCard step", titleCardStep);

        // Execute the step. With checkpoint active, S3K's guard skips the
        // LevelManager.requestTitleCardIfNeeded() call entirely, making this a
        // no-op. If the guard were missing, this would NPE because LevelManager
        // is not initialized.
        titleCardStep.execute();
    }

    @Test
    public void s3kTitleCardStepDocumentsCheckpointGuard() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic3kLevelInitProfile profile = newS3kProfile();
        InitStep step = findStep(profile.levelLoadSteps(ctx), "RequestTitleCard");

        assertTrue("S3K title card step should document checkpoint suppression",
                step.romRoutine().contains("skipped on checkpoint"));
    }

    @Test
    public void s2TitleCardStepHasNoCheckpointGuard() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic2LevelInitProfile profile = newS2Profile();
        InitStep step = findStep(profile.levelLoadSteps(ctx), "RequestTitleCard");

        assertFalse("S2 title card step should NOT suppress on checkpoint",
                step.romRoutine().contains("skipped on checkpoint"));
    }

    @Test
    public void s1TitleCardStepHasNoCheckpointGuard() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic1LevelInitProfile profile = newS1Profile();
        InitStep step = findStep(profile.levelLoadSteps(ctx), "RequestTitleCard");

        assertFalse("S1 title card step should NOT suppress on checkpoint",
                step.romRoutine().contains("skipped on checkpoint"));
    }

    // ========== Sidekick Spawn Step Presence ==========

    @Test
    public void s1ProfileOmitsSpawnSidekickStep() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic1LevelInitProfile profile = newS1Profile();
        InitStep sidekickStep = findStep(profile.levelLoadSteps(ctx), "SpawnSidekick");

        assertNull("S1 should NOT include SpawnSidekick (no Tails in Sonic 1)",
                sidekickStep);
    }

    @Test
    public void s1ProfileHas6PostLoadSteps() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic1LevelInitProfile profile = newS1Profile();
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        // 13 resource steps + 6 post-load steps (no SpawnSidekick) = 19
        assertEquals("S1 should have 19 steps (13 resource + 6 post-load)", 19, steps.size());
    }

    @Test
    public void s2ProfileIncludesSpawnSidekickStep() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic2LevelInitProfile profile = newS2Profile();
        InitStep step = findStep(profile.levelLoadSteps(ctx), "SpawnSidekick");

        assertNotNull("S2 should include SpawnSidekick step", step);
    }

    @Test
    public void s3kProfileIncludesSpawnSidekickStep() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic3kLevelInitProfile profile = newS3kProfile();
        InitStep step = findStep(profile.levelLoadSteps(ctx), "SpawnSidekick");

        assertNotNull("S3K should include SpawnSidekick step", step);
    }

    @Test
    public void s3kSidekickStepDocumentsDifferentOffset() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);

        Sonic3kLevelInitProfile profile = newS3kProfile();
        InitStep step = findStep(profile.levelLoadSteps(ctx), "SpawnSidekick");

        // S3K uses -$20 (32px) X offset and +4 Y offset (differs from S2's -40, 0)
        assertTrue("S3K sidekick step should document the $20 X offset",
                step.romRoutine().contains("$20"));
        assertTrue("S3K sidekick step should document the +4 Y offset",
                step.romRoutine().contains("+4"));
    }

    // ========== Helpers ==========

    private CheckpointState createCheckpoint(int index, int x, int y) {
        Camera camera = GameServices.camera();
        CheckpointState state = new CheckpointState();
        state.saveCheckpoint(index, x, y, false);
        return state;
    }

    private static Sonic1LevelInitProfile newS1Profile() {
        return new Sonic1LevelInitProfile(
                new Sonic1LevelEventManager(),
                new Sonic1SwitchManager(),
                new Sonic1ConveyorState());
    }

    private static Sonic2LevelInitProfile newS2Profile() {
        return new Sonic2LevelInitProfile(new Sonic2LevelEventManager());
    }

    private static Sonic3kLevelInitProfile newS3kProfile() {
        return new Sonic3kLevelInitProfile(new Sonic3kLevelEventManager());
    }

    private static InitStep findStep(List<InitStep> steps, String name) {
        return steps.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
