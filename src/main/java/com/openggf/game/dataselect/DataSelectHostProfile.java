package com.openggf.game.dataselect;

import com.openggf.game.save.SaveSlotState;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;

import java.util.List;
import java.util.Map;

/**
 * Host-owned data select semantics for a game.
 */
public interface DataSelectHostProfile {
    String gameCode();

    int slotCount();

    List<SelectedTeam> builtInTeams();

    default List<SelectedTeam> parseExtraTeams(String raw) {
        return List.of();
    }

    SaveSlotSummary summarizeFreshSlot(int slot);

    boolean isPayloadValid(Map<String, Object> payload);

    default boolean isSummaryValid(SaveSlotSummary summary) {
        return summary != null
                && (summary.state() == SaveSlotState.EMPTY || isPayloadValid(summary.payload()));
    }

    default List<DataSelectDestination> clearRestartDestinations(Map<String, Object> payload) {
        return List.of();
    }

    default int clearRestartSelectionCount(Map<String, Object> payload) {
        return clearRestartDestinations(payload).size();
    }

    default int defaultClearRestartIndex(Map<String, Object> payload) {
        return 0;
    }
}
