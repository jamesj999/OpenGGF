package com.openggf.game.session;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

/**
 * Resolves the active gameplay team independently from persistent config.
 * <p>
 * Donated Data Select launches keep the chosen team in the current
 * {@link com.openggf.game.save.SaveSessionContext}; gameplay systems should
 * prefer that session-owned team over {@link SonicConfiguration#MAIN_CHARACTER_CODE},
 * which remains the user's default preference for non-session startup.
 */
public final class ActiveGameplayTeamResolver {

    private ActiveGameplayTeamResolver() {
    }

    /**
     * Returns the active main character for the current gameplay/editor world session.
     * Falls back to the configured default when no session-owned team is active.
     */
    public static String resolveMainCharacterCode(SonicConfigurationService configService) {
        WorldSession worldSession = SessionManager.getCurrentWorldSession();
        if (worldSession != null
                && worldSession.getSaveSessionContext() != null
                && worldSession.getSaveSessionContext().selectedTeam() != null) {
            return worldSession.getSaveSessionContext().selectedTeam().mainCharacter();
        }

        String configured = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (configured == null || configured.isBlank()) {
            return "sonic";
        }
        return configured;
    }
}
