package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kZoneIds;

/**
 * Resolves S3K bootstrap behavior for a zone/act using runtime configuration.
 *
 * <p>Intro start positions are data-driven: add a new zone entry in
 * {@link #getIntroStartPosition(int, int)} to support its intro sequence.
 */
public final class Sonic3kBootstrapResolver {
    private Sonic3kBootstrapResolver() {
    }

    public static Sonic3kLoadBootstrap resolve(int zone, int act) {
        int[] introPos = getIntroStartPosition(zone, act);
        if (introPos == null) {
            return Sonic3kLoadBootstrap.NORMAL; // Zone has no intro
        }

        // Zone has an intro — check if we should skip it
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        boolean skipIntros = config.getBoolean(SonicConfiguration.S3K_SKIP_INTROS);
        String mainCharacter = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        boolean nonSonicMain = mainCharacter != null
                && !mainCharacter.isBlank()
                && !"sonic".equalsIgnoreCase(mainCharacter.trim());

        if (skipIntros || nonSonicMain) {
            return new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null);
        }

        return new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.INTRO, introPos);
    }

    /** Per-zone intro start positions. Returns null if zone has no intro. */
    private static int[] getIntroStartPosition(int zone, int act) {
        if (zone == Sonic3kZoneIds.ZONE_AIZ && act == 0) return new int[]{0x60, 0x30};
        // Future zones: add entries here
        return null;
    }
}
