package com.openggf.game.sonic2.dataselect;

import com.openggf.game.save.SelectedTeam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS2DataSelectProfile {

    @Test
    void s2UsesEightSlots() {
        assertEquals(8, new S2DataSelectProfile().slotCount());
    }

    @Test
    void gameCode_returnsS2() {
        assertEquals("s2", new S2DataSelectProfile().gameCode());
    }

    @Test
    void builtInTeams_containsExpectedCharacters() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        List<SelectedTeam> teams = profile.builtInTeams();
        assertEquals(3, teams.size());
        assertEquals("sonic", teams.get(0).mainCharacter());
        assertTrue(teams.get(0).sidekicks().isEmpty());
        assertEquals("sonic", teams.get(1).mainCharacter());
        assertEquals(List.of("tails"), teams.get(1).sidekicks());
        assertEquals("knuckles", teams.get(2).mainCharacter());
        assertTrue(teams.get(2).sidekicks().isEmpty());
    }

    @Test
    void summarizeFreshSlot_returnsEmpty() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        var summary = profile.summarizeFreshSlot(3);
        assertEquals(3, summary.slot());
        assertTrue(summary.payload().isEmpty());
    }
}
