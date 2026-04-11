package com.openggf.game.sonic3k;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.Before;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import static org.junit.Assert.*;

public class TestSonic3kBootstrapResolver {

    @Before
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
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
}
