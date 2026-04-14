package com.openggf.game.sonic1.dataselect;

import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.HostSlotPreview;
import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestS1DataSelectProfile {

    @BeforeAll
    static void configureEngineServices() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void resetRuntime() {
        RuntimeManager.destroyCurrent();
    }

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

    @Test
    void clearRestartDestinations_coverS1MainPath() {
        S1DataSelectProfile profile = new S1DataSelectProfile();
        List<DataSelectDestination> destinations = profile.clearRestartDestinations(Map.of(
                "zone", Sonic1ZoneConstants.ZONE_FZ,
                "act", 0,
                "mainCharacter", "sonic",
                "sidekicks", List.of("tails"),
                "emeraldCount", 6,
                "clear", true
        ));

        assertEquals(new DataSelectDestination(Sonic1ZoneConstants.ZONE_GHZ, 0), destinations.getFirst());
        assertEquals(new DataSelectDestination(Sonic1ZoneConstants.ZONE_FZ, 0), destinations.getLast());
        assertTrue(destinations.contains(new DataSelectDestination(Sonic1ZoneConstants.ZONE_SBZ, 0)));
    }

    @Test
    void resolveSlotPreview_returnsTextOnlyWithZoneLabel() {
        S1DataSelectProfile profile = new S1DataSelectProfile();

        HostSlotPreview ghzPreview = profile.resolveSlotPreview(Map.of("zone", 0));
        HostSlotPreview mzPreview = profile.resolveSlotPreview(Map.of("zone", 1));
        HostSlotPreview fzPreview = profile.resolveSlotPreview(Map.of("zone", 6));

        assertNotNull(ghzPreview);
        assertEquals(HostSlotPreview.HostSlotPreviewType.TEXT_ONLY, ghzPreview.type());
        assertEquals("GHZ", ghzPreview.zoneLabelText());
        assertEquals("MZ", mzPreview.zoneLabelText());
        assertEquals("FZ", fzPreview.zoneLabelText());
    }

    @Test
    void resolveSlotPreview_returnsNullForEmptyPayload() {
        S1DataSelectProfile profile = new S1DataSelectProfile();
        assertNull(profile.resolveSlotPreview(null));
        assertNull(profile.resolveSlotPreview(Map.of()));
    }

    @Test
    void module_exposesHostProfileSeparatelyFromPresentationProvider() {
        Sonic1GameModule module = new Sonic1GameModule();

        DataSelectHostProfile hostProfile = module.getDataSelectHostProfile();
        DataSelectPresentationProvider provider = module.getDataSelectPresentationProvider();

        assertNotNull(hostProfile);
        assertEquals("s1", hostProfile.gameCode());
        assertInstanceOf(S3kDataSelectManager.class, provider.delegate(),
                "S1 donated Data Select should use the S3K presentation manager");
    }
}
