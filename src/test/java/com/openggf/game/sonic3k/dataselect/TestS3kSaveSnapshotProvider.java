package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSaveSnapshotProvider {

    @Test
    void capture_includesTeamAndStartLocation() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(new RuntimeSaveContext(null, ctx));
        assertEquals("sonic", payload.get("mainCharacter"));
        assertEquals(0, payload.get("zone"));
        assertEquals(0, payload.get("act"));
    }

    @Test
    void capture_includesSidekicks() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(new RuntimeSaveContext(null, ctx));
        assertEquals(List.of("tails"), payload.get("sidekicks"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void capture_nullRuntime_defaultsLivesAndEmeralds() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 2,
                new SelectedTeam("knuckles", List.of()), 3, 1);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(new RuntimeSaveContext(null, ctx));
        assertEquals("knuckles", payload.get("mainCharacter"));
        assertEquals(3, payload.get("zone"));
        assertEquals(1, payload.get("act"));
        assertEquals(3, payload.get("lives"));
        assertEquals(0, payload.get("emeraldCount"));
        assertEquals(false, payload.get("clear"));
    }
}
