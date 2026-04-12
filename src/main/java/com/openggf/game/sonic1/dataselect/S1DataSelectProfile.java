package com.openggf.game.sonic1.dataselect;

import com.openggf.game.dataselect.DataSelectGameProfile;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;

import java.util.List;

public final class S1DataSelectProfile implements DataSelectGameProfile {
    @Override public String gameCode() { return "s1"; }
    @Override public int slotCount() { return 8; }
    @Override public List<SelectedTeam> builtInTeams() {
        return List.of(
                new SelectedTeam("sonic", List.of()),
                new SelectedTeam("sonic", List.of("tails")),
                new SelectedTeam("knuckles", List.of())
        );
    }
    @Override public SaveSlotSummary summarizeFreshSlot(int slot) { return SaveSlotSummary.empty(slot); }
}
