package com.openggf.game.sonic1.dataselect;

import com.openggf.game.save.SelectedTeam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS1DataSelectProfile {

    @Test
    void s1UsesEightSlots() {
        assertEquals(8, new S1DataSelectProfile().slotCount());
    }

    @Test
    void gameCode_returnsS1() {
        assertEquals("s1", new S1DataSelectProfile().gameCode());
    }

    @Test
    void builtInTeams_containsExpectedCharacters() {
        S1DataSelectProfile profile = new S1DataSelectProfile();
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
        S1DataSelectProfile profile = new S1DataSelectProfile();
        var summary = profile.summarizeFreshSlot(3);
        assertEquals(3, summary.slot());
        assertTrue(summary.payload().isEmpty());
    }
}
