package com.openggf.game.sonic1.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSnapshotProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class S1SaveSnapshotProvider implements SaveSnapshotProvider {
    @Override
    public Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context) {
        if (reason != SaveReason.NEW_SLOT_START && context.runtime() == null) {
            throw new IllegalStateException("Save reason " + reason + " requires a live runtime");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        var save = context.saveSessionContext();
        int zone = context.runtime() == null ? save.startZone()
                : context.runtime().getLevelManager().getCurrentZone();
        int act = context.runtime() == null ? save.startAct()
                : context.runtime().getLevelManager().getCurrentAct();
        int lives = context.runtime() == null ? 3 : context.runtime().getGameState().getLives();
        List<Integer> chaosEmeralds = context.runtime() == null ? List.of()
                : context.runtime().getGameState().getCollectedChaosEmeraldIndices();
        boolean clear = save.isClear();
        payload.put("zone", zone);
        payload.put("act", act);
        payload.put("mainCharacter", save.selectedTeam().mainCharacter());
        payload.put("sidekicks", save.selectedTeam().sidekicks());
        payload.put("lives", lives);
        payload.put("chaosEmeralds", chaosEmeralds);
        payload.put("clear", clear);
        payload.put("progressCode", zone + 1);
        payload.put("clearState", clear ? 1 : 0);
        return payload;
    }
}
