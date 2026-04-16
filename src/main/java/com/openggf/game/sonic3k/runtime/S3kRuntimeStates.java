package com.openggf.game.sonic3k.runtime;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.zone.ZoneRuntimeRegistry;

import java.util.Optional;

public final class S3kRuntimeStates {
    private S3kRuntimeStates() {
    }

    public static Optional<AizZoneRuntimeState> currentAiz(ZoneRuntimeRegistry registry) {
        return registry.currentAs(AizZoneRuntimeState.class);
    }

    public static Optional<HczZoneRuntimeState> currentHcz(ZoneRuntimeRegistry registry) {
        return registry.currentAs(HczZoneRuntimeState.class);
    }

    public static Optional<CnzZoneRuntimeState> currentCnz(ZoneRuntimeRegistry registry) {
        return registry.currentAs(CnzZoneRuntimeState.class);
    }

    public static PlayerCharacter resolvePlayerCharacter(ZoneRuntimeRegistry registry,
                                                         SonicConfigurationService config) {
        if (registry != null) {
            var current = registry.current();
            if (current instanceof S3kZoneRuntimeState s3kState) {
                return s3kState.playerCharacter();
            }
        }

        String mainChar = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if ("knuckles".equalsIgnoreCase(mainChar)) {
            return PlayerCharacter.KNUCKLES;
        }
        if ("tails".equalsIgnoreCase(mainChar)) {
            return PlayerCharacter.TAILS_ALONE;
        }

        String sidekick = config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        if (sidekick == null || sidekick.isBlank()) {
            return PlayerCharacter.SONIC_ALONE;
        }
        return PlayerCharacter.SONIC_AND_TAILS;
    }
}
