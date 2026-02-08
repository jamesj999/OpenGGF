package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;

/**
 * Resolves S3K bootstrap behavior for a zone/act using runtime configuration.
 */
public final class Sonic3kBootstrapResolver {
    private Sonic3kBootstrapResolver() {
    }

    public static Sonic3kLoadBootstrap resolve(int zone, int act) {
        if (zone != 0 || act != 0) {
            return Sonic3kLoadBootstrap.NONE;
        }

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        boolean skipAiz1Intro = config.getBoolean(SonicConfiguration.S3K_SKIP_AIZ1_INTRO);
        String mainCharacter = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        boolean nonSonicMain = mainCharacter != null
                && !mainCharacter.isBlank()
                && !"sonic".equalsIgnoreCase(mainCharacter.trim());

        // AIZ1 intro only applies to Sonic in the original flow.
        // Non-Sonic starts already bypass intro setup, so use gameplay bootstrap there too.
        if (skipAiz1Intro || nonSonicMain) {
            return new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.AIZ1_GAMEPLAY_AFTER_INTRO);
        }

        return Sonic3kLoadBootstrap.NONE;
    }
}
