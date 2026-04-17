package com.openggf.game.sonic1;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

class TestSonic1LivesHudDonation {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        RuntimeManager.destroyCurrent();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @Test
    void overrideLivesArtFromDonor_prefersSelectedTeamOverConfig() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        SessionManager.openGameplaySession(mock(GameModule.class),
                SaveSessionContext.noSave("s1", new SelectedTeam("knuckles", List.of()), 0, 0));

        Sonic1ObjectArtProvider provider = spy(new Sonic1ObjectArtProvider());
        Pattern[] donorPatterns = { new Pattern() };
        doReturn(donorPatterns).when(provider).loadS3kKnucklesLivesPatterns();

        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(true);
            Method method = Sonic1ObjectArtProvider.class.getDeclaredMethod("overrideLivesArtFromDonor");
            method.setAccessible(true);
            method.invoke(provider);
        }

        assertSame(donorPatterns, provider.getHudLivesPatterns());
    }

    @Test
    void remapPaletteIndices_mapsDonorIndicesToHostSlots() throws Exception {
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        Pattern tile = new Pattern();
        tile.setPixel(0, 0, (byte) 1);
        tile.setPixel(1, 0, (byte) 2);
        tile.setPixel(2, 0, (byte) 3);
        tile.setPixel(3, 0, (byte) 4);
        tile.setPixel(4, 0, (byte) 5);
        tile.setPixel(5, 0, (byte) 10);
        tile.setPixel(6, 0, (byte) 11);
        tile.setPixel(7, 0, (byte) 12);
        tile.setPixel(0, 1, (byte) 13);
        tile.setPixel(1, 1, (byte) 14);
        tile.setPixel(2, 1, (byte) 15);

        Method method = Sonic1ObjectArtProvider.class.getDeclaredMethod("remapPaletteIndices", Pattern[].class);
        method.setAccessible(true);
        method.invoke(provider, (Object) new Pattern[] { tile });

        assertEquals(6, tile.getPixel(0, 0));
        assertEquals(12, tile.getPixel(1, 0));
        assertEquals(13, tile.getPixel(2, 0));
        assertEquals(14, tile.getPixel(3, 0));
        assertEquals(15, tile.getPixel(4, 0));
        assertEquals(10, tile.getPixel(5, 0));
        assertEquals(11, tile.getPixel(6, 0));
        assertEquals(6, tile.getPixel(7, 0));
        assertEquals(7, tile.getPixel(0, 1));
        assertEquals(1, tile.getPixel(1, 1));
        assertEquals(1, tile.getPixel(2, 1));
    }

}
