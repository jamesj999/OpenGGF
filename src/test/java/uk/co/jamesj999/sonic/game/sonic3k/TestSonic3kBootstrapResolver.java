package uk.co.jamesj999.sonic.game.sonic3k;

import org.junit.Test;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;

import static org.junit.Assert.assertEquals;

public class TestSonic3kBootstrapResolver {

    @Test
    public void resolvesAiz1BootstrapWhenFlagEnabled() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, true);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(0, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.AIZ1_GAMEPLAY_AFTER_INTRO, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }

    @Test
    public void resolvesAiz1BootstrapForNonSonicMainCharacter() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(0, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.AIZ1_GAMEPLAY_AFTER_INTRO, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }

    @Test
    public void resolvesNoneOutsideAiz1() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO);
        Object oldChar = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, true);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

            Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(1, 0);
            assertEquals(Sonic3kLoadBootstrap.Mode.NONE, bootstrap.mode());
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, oldSkip);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldChar);
        }
    }
}
