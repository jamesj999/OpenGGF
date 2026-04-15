package com.openggf.game.sonic3k;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.Sonic3kGameModule;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestSonic3kBootstrapResolver {

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    @Test
    public void resolvesSkipIntroWhenFlagEnabled() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(0, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }

    @Test
    public void resolvesSkipIntroForNonSonicMainCharacter() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(0, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }

    @Test
    public void resolvesNormalOutsideAiz1() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(1, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.NORMAL, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }

    @Test
    public void resolvesIntroWithPositionWhenIntroEnabled() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(0, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.INTRO, bootstrap.mode());
            assertTrue(bootstrap.hasIntroStartPosition());
            assertArrayEquals(new int[]{0x40, 0x420}, bootstrap.introStartPosition());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }

    @Test
    public void resolvesSkipIntroForSessionSelectedKnucklesEvenWhenConfigIsSonic() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            SessionManager.openGameplaySession(new Sonic3kGameModule(),
                    SaveSessionContext.noSave("s3k", new SelectedTeam("knuckles", List.of()), 0, 0));

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(0, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }
}


