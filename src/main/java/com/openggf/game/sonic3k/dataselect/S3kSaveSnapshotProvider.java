package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSnapshotProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures S3K game state into a map suitable for save file serialization.
 * When the runtime is available, reads live game state (lives, emeralds);
 * when null (e.g., fresh slot start), uses defaults (3 lives, 0 emeralds).
 */
public final class S3kSaveSnapshotProvider implements SaveSnapshotProvider {

    @Override
    public Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        var save = context.saveSessionContext();
        boolean requiresRuntime = switch (reason) {
            case EXISTING_SLOT_LOAD, CLEAR_RESTART_COMMIT, SPECIAL_STAGE_SAVE,
                 PROGRESSION_SAVE, LIVES_CONTINUES_SAVE -> true;
            case NEW_SLOT_START -> false;
        };
        if (requiresRuntime && context.runtime() == null) {
            throw new IllegalStateException("Save reason " + reason + " requires a live runtime");
        }
        int zone = context.runtime() == null ? save.startZone()
                : context.runtime().getLevelManager().getCurrentZone();
        int act = context.runtime() == null ? save.startAct()
                : context.runtime().getLevelManager().getCurrentAct();
        payload.put("zone", zone);
        payload.put("act", act);
        payload.put("mainCharacter", save.selectedTeam().mainCharacter());
        payload.put("sidekicks", save.selectedTeam().sidekicks());
        int lives = context.runtime() == null ? 3
                : context.runtime().getGameState().getLives();
        int continues = context.runtime() == null ? 0
                : context.runtime().getGameState().getContinues();
        List<Integer> chaosEmeralds = context.runtime() == null ? List.of()
                : context.runtime().getGameState().getCollectedChaosEmeraldIndices();
        List<Integer> superEmeralds = context.runtime() == null ? List.of()
                : context.runtime().getGameState().getCollectedSuperEmeraldIndices();
        boolean clear = save.isClear();
        payload.put("lives", lives);
        payload.put("continues", continues);
        payload.put("chaosEmeralds", chaosEmeralds);
        payload.put("superEmeralds", superEmeralds);
        payload.put("clear", clear);
        payload.put("progressCode",
                S3kSaveProgressions.progressCodeForState(zone, act, save.selectedTeam(), clear, superEmeralds));
        payload.put("clearState", clear ? (S3kSaveProgressions.hasAllSuperEmeralds(superEmeralds) ? 2 : 1) : 0);
        return payload;
    }
}
