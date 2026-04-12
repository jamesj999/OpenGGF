package com.openggf.game.dataselect;

import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;

import java.util.List;

/**
 * Game-specific profile for the data select screen.
 * Provides slot count, available teams, and fresh-slot summaries.
 */
public interface DataSelectGameProfile {

    /** Returns the game code identifier (e.g. "S3K"). */
    String gameCode();

    /** Returns the number of save slots available. */
    int slotCount();

    /** Returns the list of built-in team selections for this game. */
    List<SelectedTeam> builtInTeams();

    /** Returns a summary representing a fresh (empty) slot at the given index. */
    SaveSlotSummary summarizeFreshSlot(int slot);
}
