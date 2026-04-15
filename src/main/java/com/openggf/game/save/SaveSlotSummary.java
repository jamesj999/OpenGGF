package com.openggf.game.save;

import java.util.Map;

public record SaveSlotSummary(int slot, SaveSlotState state, Map<String, Object> payload) {

    public static SaveSlotSummary empty(int slot) {
        return new SaveSlotSummary(slot, SaveSlotState.EMPTY, Map.of());
    }
}
