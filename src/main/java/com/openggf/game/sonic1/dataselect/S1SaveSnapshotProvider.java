package com.openggf.game.sonic1.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveSnapshotProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public final class S1SaveSnapshotProvider implements SaveSnapshotProvider {
    @Override
    public Map<String, Object> capture(RuntimeSaveContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        var save = context.saveSessionContext();
        payload.put("zone", save.startZone());
        payload.put("act", save.startAct());
        payload.put("mainCharacter", save.selectedTeam().mainCharacter());
        payload.put("sidekicks", save.selectedTeam().sidekicks());
        return payload;
    }
}
