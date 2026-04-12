package com.openggf.game.save;

import java.util.Objects;
import java.util.OptionalInt;

public final class SaveSessionContext {
    private final String gameCode;
    private final Integer activeSlot;
    private final SelectedTeam selectedTeam;
    private final int startZone;
    private final int startAct;

    private SaveSessionContext(String gameCode, Integer activeSlot, SelectedTeam selectedTeam,
                               int startZone, int startAct) {
        this.gameCode = Objects.requireNonNull(gameCode, "gameCode");
        this.activeSlot = activeSlot;
        this.selectedTeam = Objects.requireNonNull(selectedTeam, "selectedTeam");
        this.startZone = startZone;
        this.startAct = startAct;
    }

    public static SaveSessionContext forSlot(String gameCode, int slot, SelectedTeam team,
                                             int zone, int act) {
        return new SaveSessionContext(gameCode, slot, team, zone, act);
    }

    public static SaveSessionContext noSave(String gameCode, SelectedTeam team,
                                            int zone, int act) {
        return new SaveSessionContext(gameCode, null, team, zone, act);
    }

    public OptionalInt activeSlot() {
        return activeSlot == null ? OptionalInt.empty() : OptionalInt.of(activeSlot);
    }

    public SelectedTeam selectedTeam() {
        return selectedTeam;
    }

    public String gameCode() {
        return gameCode;
    }

    public int startZone() {
        return startZone;
    }

    public int startAct() {
        return startAct;
    }
}
