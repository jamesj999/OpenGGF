package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.DataSelectProvider;
import com.openggf.game.EngineServices;
import com.openggf.game.NoOpDataSelectProvider;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kDataSelectProfile {

    @BeforeAll
    static void configureEngineServices() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void resetRuntime() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void s3kGameModule_exposesDataSelectProvider() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        DataSelectProvider provider = module.getDataSelectProvider();
        assertNotNull(provider);
        assertNotSame(NoOpDataSelectProvider.INSTANCE, provider,
                "S3K should provide a real DataSelectProvider, not the no-op stub");
        assertTrue(provider.getClass().getSimpleName().contains("DataSelect"));
    }

    @Test
    void gameCode_returnsS3k() {
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
        assertEquals("s3k", profile.gameCode());
    }

    @Test
    void slotCount_returnsEight() {
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
        assertEquals(8, profile.slotCount());
    }

    @Test
    void builtInTeams_containsExpectedCharacters() {
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
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
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
        var summary = profile.summarizeFreshSlot(3);
        assertEquals(3, summary.slot());
        assertTrue(summary.payload().isEmpty());
    }

    @Test
    void extraCombos_parseSemicolonSeparatedTeams() {
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
        var teams = profile.parseExtraTeams("sonic,knuckles;knuckles,tails");
        assertEquals(2, teams.size());
        assertEquals("sonic", teams.get(0).mainCharacter());
        assertEquals(List.of("knuckles"), teams.get(0).sidekicks());
        assertEquals("knuckles", teams.get(1).mainCharacter());
        assertEquals(List.of("tails"), teams.get(1).sidekicks());
    }

    @Test
    void extraCombos_nullOrBlank_returnsEmpty() {
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
        assertTrue(profile.parseExtraTeams(null).isEmpty());
        assertTrue(profile.parseExtraTeams("").isEmpty());
        assertTrue(profile.parseExtraTeams("   ").isEmpty());
    }

    @Test
    void extraCombos_singleCharacter_noSidekicks() {
        S3kDataSelectProfile profile = new S3kDataSelectProfile();
        var teams = profile.parseExtraTeams("tails");
        assertEquals(1, teams.size());
        assertEquals("tails", teams.get(0).mainCharacter());
        assertTrue(teams.get(0).sidekicks().isEmpty());
    }
}
