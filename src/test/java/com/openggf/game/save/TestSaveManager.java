package com.openggf.game.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestSaveManager {

    @TempDir
    Path root;

    @Test
    void malformedFile_isRenamedToCorrupt() throws Exception {
        Path slot = root.resolve("s3k").resolve("slot1.json");
        Files.createDirectories(slot.getParent());
        Files.writeString(slot, "{ not-json");
        SaveManager manager = new SaveManager(root);
        SaveSlotSummary summary = manager.readSlotSummary("s3k", 1);
        assertEquals(SaveSlotState.EMPTY, summary.state());
        assertTrue(Files.exists(slot.resolveSibling("slot1.json.corrupt")));
    }

    @Test
    void hashMismatch_warnsButStillLoads() throws Exception {
        SaveManager manager = new SaveManager(root);
        manager.writeSlot("s3k", 1, Map.of("zone", 0, "act", 0));
        Path slot = root.resolve("s3k").resolve("slot1.json");
        Files.writeString(slot, Files.readString(slot).replace("\"hash\":\"", "\"hash\":\"broken"));
        SaveSlotSummary summary = manager.readSlotSummary("s3k", 1);
        assertEquals(SaveSlotState.HASH_WARNING, summary.state());
        assertFalse(summary.payload().isEmpty());
    }

    @Test
    void writeAndRead_roundTrips() throws Exception {
        SaveManager manager = new SaveManager(root);
        Map<String, Object> payload = Map.of("zone", 2, "act", 1, "emeralds", 3);
        manager.writeSlot("s3k", 1, payload);
        SaveSlotSummary summary = manager.readSlotSummary("s3k", 1);
        assertEquals(SaveSlotState.VALID, summary.state());
        assertEquals(1, summary.slot());
        assertEquals(2, summary.payload().get("zone"));
        assertEquals(1, summary.payload().get("act"));
        assertEquals(3, summary.payload().get("emeralds"));
    }

    @Test
    void readMissingSlot_returnsEmpty() throws Exception {
        SaveManager manager = new SaveManager(root);
        SaveSlotSummary summary = manager.readSlotSummary("s3k", 5);
        assertEquals(SaveSlotState.EMPTY, summary.state());
        assertEquals(5, summary.slot());
        assertTrue(summary.payload().isEmpty());
    }

    @Test
    void wrongGame_quarantinesFile() throws Exception {
        SaveManager manager = new SaveManager(root);
        manager.writeSlot("s2", 1, Map.of("zone", 0));
        Path s2File = root.resolve("s2").resolve("slot1.json");
        Path s3kDir = root.resolve("s3k");
        Files.createDirectories(s3kDir);
        Files.copy(s2File, s3kDir.resolve("slot1.json"));
        SaveSlotSummary summary = manager.readSlotSummary("s3k", 1);
        assertEquals(SaveSlotState.EMPTY, summary.state());
        assertTrue(Files.exists(s3kDir.resolve("slot1.json.corrupt")));
    }

    @Test
    void noSaveSession_requestSaveDoesNotWriteFile() throws Exception {
        SaveManager manager = new SaveManager(root);
        SaveSessionContext ctx = SaveSessionContext.noSave("s3k",
                new SelectedTeam("sonic", java.util.List.of()), 0, 0);
        ctx.requestSave(SaveReason.LEVEL_PROGRESS,
                new RuntimeSaveContext(null, ctx),
                runtime -> java.util.Map.of("zone", 0, "act", 1),
                manager);
        assertTrue(Files.notExists(root.resolve("s3k").resolve("slot1.json")));
    }

    @Test
    void multipleSlots_independentReadWrite() throws Exception {
        SaveManager manager = new SaveManager(root);
        manager.writeSlot("s3k", 1, Map.of("zone", 0));
        manager.writeSlot("s3k", 2, Map.of("zone", 3));
        SaveSlotSummary s1 = manager.readSlotSummary("s3k", 1);
        SaveSlotSummary s2 = manager.readSlotSummary("s3k", 2);
        assertEquals(0, s1.payload().get("zone"));
        assertEquals(3, s2.payload().get("zone"));
    }
}
