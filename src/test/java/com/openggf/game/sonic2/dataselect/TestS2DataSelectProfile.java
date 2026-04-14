package com.openggf.game.sonic2.dataselect;

import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.HostSlotPreview;
import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestS2DataSelectProfile {

    @BeforeAll
    static void configureEngineServices() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void resetRuntime() {
        RuntimeManager.destroyCurrent();
    }

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

    @Test
    void clearRestartDestinations_coverS2MainPath() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        List<DataSelectDestination> destinations = profile.clearRestartDestinations(Map.of(
                "zone", Sonic2ZoneConstants.ZONE_DEZ,
                "act", 0,
                "mainCharacter", "sonic",
                "sidekicks", List.of("tails"),
                "emeraldCount", 7,
                "clear", true
        ));

        assertEquals(new DataSelectDestination(Sonic2ZoneConstants.ZONE_EHZ, 0), destinations.getFirst());
        assertEquals(new DataSelectDestination(Sonic2ZoneConstants.ZONE_DEZ, 0), destinations.getLast());
        assertTrue(destinations.contains(new DataSelectDestination(Sonic2ZoneConstants.ZONE_WFZ, 0)));
    }

    @Test
    void resolveSlotPreview_returnsTextOnlyWithZoneLabel() {
        S2DataSelectProfile profile = new S2DataSelectProfile();

        HostSlotPreview ehzPreview = profile.resolveSlotPreview(Map.of("zone", 0));
        HostSlotPreview cpzPreview = profile.resolveSlotPreview(Map.of("zone", 1));
        HostSlotPreview dezPreview = profile.resolveSlotPreview(Map.of("zone", 10));

        assertNotNull(ehzPreview);
        assertEquals(HostSlotPreview.HostSlotPreviewType.TEXT_ONLY, ehzPreview.type());
        assertEquals("EHZ", ehzPreview.zoneLabelText());
        assertEquals("CPZ", cpzPreview.zoneLabelText());
        assertEquals("DEZ", dezPreview.zoneLabelText());
    }

    @Test
    void resolveSlotPreview_returnsNullForEmptyPayload() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        assertNull(profile.resolveSlotPreview(null));
        assertNull(profile.resolveSlotPreview(Map.of()));
    }

    @Test
    void module_exposesHostProfileSeparatelyFromPresentationProvider() {
        Sonic2GameModule module = new Sonic2GameModule();

        DataSelectHostProfile hostProfile = module.getDataSelectHostProfile();
        DataSelectPresentationProvider provider = module.getDataSelectPresentationProvider();

        assertNotNull(hostProfile);
        assertEquals("s2", hostProfile.gameCode());
        assertInstanceOf(S3kDataSelectManager.class, provider.delegate(),
                "S2 donated Data Select should use the S3K presentation manager");
    }
}
