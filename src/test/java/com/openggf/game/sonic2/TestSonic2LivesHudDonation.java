package com.openggf.game.sonic2;

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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

class TestSonic2LivesHudDonation {

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
                SaveSessionContext.noSave("s2", new SelectedTeam("knuckles", List.of()), 0, 0));

        Sonic2ObjectArtProvider provider = spy(new Sonic2ObjectArtProvider());
        Pattern[] donorPatterns = { new Pattern() };
        doReturn(donorPatterns).when(provider).loadS3kKnucklesLivesPatterns();

        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(true);
            Method method = Sonic2ObjectArtProvider.class.getDeclaredMethod("overrideLivesArtFromDonor");
            method.setAccessible(true);
            method.invoke(provider);
        }

        assertSame(donorPatterns, provider.getHudLivesPatterns());
        assertTrue(provider.usesIconPaletteForLivesName());
    }
}
