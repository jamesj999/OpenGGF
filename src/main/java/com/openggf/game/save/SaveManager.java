package com.openggf.game.save;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Logger;

public final class SaveManager {

    private static final Logger LOG = Logger.getLogger(SaveManager.class.getName());

    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper();

    public SaveManager(Path root) {
        this.root = root;
    }

    public void writeSlot(String game, int slot, Map<String, Object> payload) throws IOException {
        Path file = slotPath(game, slot);
        Files.createDirectories(file.getParent());
        String payloadJson = mapper.writeValueAsString(payload);
        SaveEnvelope env = new SaveEnvelope(1, game, slot, payload, sha256(payloadJson));
        mapper.writeValue(file.toFile(), env);
    }

    public SaveSlotSummary readSlotSummary(String game, int slot) throws IOException {
        Path file = slotPath(game, slot);
        if (!Files.exists(file)) {
            return SaveSlotSummary.empty(slot);
        }
        try {
            Map<String, Object> raw = mapper.readValue(file.toFile(), new TypeReference<>() {});
            if (!game.equals(raw.get("game"))) {
                quarantine(file, "wrong game");
                return SaveSlotSummary.empty(slot);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) raw.get("payload");
            if (payload == null) {
                quarantine(file, "missing payload");
                return SaveSlotSummary.empty(slot);
            }
            String actual = sha256(mapper.writeValueAsString(payload));
            String expected = String.valueOf(raw.get("hash"));
            return actual.equals(expected)
                    ? new SaveSlotSummary(slot, SaveSlotState.VALID, payload)
                    : new SaveSlotSummary(slot, SaveSlotState.HASH_WARNING, payload);
        } catch (Exception ex) {
            quarantine(file, ex.getMessage());
            return SaveSlotSummary.empty(slot);
        }
    }

    private void quarantine(Path file, String reason) throws IOException {
        LOG.warning("Quarantining corrupt save " + file + ": " + reason);
        Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"));
    }

    private Path slotPath(String game, int slot) {
        return root.resolve(game).resolve("slot" + slot + ".json");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
