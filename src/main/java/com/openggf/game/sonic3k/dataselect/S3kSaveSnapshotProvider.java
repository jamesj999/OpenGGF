package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveSnapshotProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures S3K game state into a map suitable for save file serialization.
 * When the runtime is available, reads live game state (lives, emeralds);
 * when null (e.g., fresh slot start), uses defaults (3 lives, 0 emeralds).
 */
public final class S3kSaveSnapshotProvider implements SaveSnapshotProvider {

    @Override
    public Map<String, Object> capture(RuntimeSaveContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        var save = context.saveSessionContext();
        payload.put("zone", save.startZone());
        payload.put("act", save.startAct());
        payload.put("mainCharacter", save.selectedTeam().mainCharacter());
        payload.put("sidekicks", save.selectedTeam().sidekicks());
        int lives = context.runtime() == null ? 3
                : context.runtime().getGameState().getLives();
        int emeraldCount = context.runtime() == null ? 0
                : context.runtime().getGameState().getEmeraldCount();
        payload.put("lives", lives);
        payload.put("emeraldCount", emeraldCount);
        payload.put("clear", false);
        return payload;
    }
}
