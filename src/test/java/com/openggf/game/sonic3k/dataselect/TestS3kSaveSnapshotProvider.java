package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kSaveSnapshotProvider {

    @Test
    void capture_includesTeamAndStartLocation() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.NEW_SLOT_START,
                new RuntimeSaveContext(null, ctx));
        assertEquals("sonic", payload.get("mainCharacter"));
        assertEquals(0, payload.get("zone"));
        assertEquals(0, payload.get("act"));
    }

    @Test
    void capture_includesSidekicks() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.NEW_SLOT_START,
                new RuntimeSaveContext(null, ctx));
        assertEquals(List.of("tails"), payload.get("sidekicks"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void capture_nullRuntime_defaultsLivesAndEmeralds() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 2,
                new SelectedTeam("knuckles", List.of()), 3, 1);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.NEW_SLOT_START,
                new RuntimeSaveContext(null, ctx));
        assertEquals("knuckles", payload.get("mainCharacter"));
        assertEquals(3, payload.get("zone"));
        assertEquals(1, payload.get("act"));
        assertEquals(3, payload.get("lives"));
        assertEquals(List.of(), payload.get("chaosEmeralds"));
        assertEquals(List.of(), payload.get("superEmeralds"));
        assertEquals(false, payload.get("clear"));
    }

    @Test
    void capture_runtimeUsesLiveZoneActAndGameState() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        com.openggf.game.GameRuntime runtime = mock(com.openggf.game.GameRuntime.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        when(runtime.getLevelManager()).thenReturn(levelManager);
        when(runtime.getGameState()).thenReturn(gameState);
        when(levelManager.getCurrentZone()).thenReturn(4);
        when(levelManager.getCurrentAct()).thenReturn(1);
        when(gameState.getLives()).thenReturn(7);
        when(gameState.getContinues()).thenReturn(5);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of(0, 2, 4, 6));
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of(2));

        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.PROGRESSION_SAVE,
                new RuntimeSaveContext(runtime, ctx));

        assertEquals(4, payload.get("zone"));
        assertEquals(1, payload.get("act"));
        assertEquals(7, payload.get("lives"));
        assertEquals(5, payload.get("continues"));
        assertEquals(List.of(0, 2, 4, 6), payload.get("chaosEmeralds"));
        assertEquals(List.of(2), payload.get("superEmeralds"));
    }

    @Test
    void capture_clearSaveIncludesProgressCodeAndClearState() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        ctx.markClear();
        com.openggf.game.GameRuntime runtime = mock(com.openggf.game.GameRuntime.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        when(runtime.getLevelManager()).thenReturn(levelManager);
        when(runtime.getGameState()).thenReturn(gameState);
        when(levelManager.getCurrentZone()).thenReturn(0x0C);
        when(levelManager.getCurrentAct()).thenReturn(0);
        when(gameState.getLives()).thenReturn(7);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of(0, 1, 2, 3, 4, 5, 6));
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of(0, 1, 2, 3, 4, 5, 6));

        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.PROGRESSION_SAVE,
                new RuntimeSaveContext(runtime, ctx));

        assertEquals(true, payload.get("clear"));
        assertEquals(14, payload.get("progressCode"));
        assertEquals(2, payload.get("clearState"));
    }

    @Test
    void capture_existingSlotLoadWithoutRuntime_rejected() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.capture(SaveReason.EXISTING_SLOT_LOAD, new RuntimeSaveContext(null, ctx)));

        assertTrue(ex.getMessage().contains("runtime"));
    }

    @Test
    void capture_specialStageSave_requiresRuntimeEmeraldState() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        com.openggf.game.GameRuntime runtime = mock(com.openggf.game.GameRuntime.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        when(runtime.getLevelManager()).thenReturn(levelManager);
        when(runtime.getGameState()).thenReturn(gameState);
        when(levelManager.getCurrentZone()).thenReturn(7);
        when(levelManager.getCurrentAct()).thenReturn(0);
        when(gameState.getLives()).thenReturn(5);
        when(gameState.getContinues()).thenReturn(2);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of(0, 1, 2, 3, 4, 5, 6));
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of(1, 4));

        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.SPECIAL_STAGE_SAVE,
                new RuntimeSaveContext(runtime, ctx));

        assertEquals(7, payload.get("zone"));
        assertEquals(0, payload.get("act"));
        assertEquals(5, payload.get("lives"));
        assertEquals(2, payload.get("continues"));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), payload.get("chaosEmeralds"));
        assertEquals(List.of(1, 4), payload.get("superEmeralds"));
    }
}
