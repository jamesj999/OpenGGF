package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.dataselect.DataSelectGameProfile;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * S3K-specific data select game profile.
 * Provides 8 save slots, 3 built-in team selections (Sonic, Sonic+Tails, Knuckles),
 * and custom team parsing from semicolon-separated strings.
 */
public final class S3kDataSelectProfile implements DataSelectGameProfile {

    @Override
    public String gameCode() {
        return "s3k";
    }

    @Override
    public int slotCount() {
        return 8;
    }

    @Override
    public List<SelectedTeam> builtInTeams() {
        return List.of(
                new SelectedTeam("sonic", List.of()),
                new SelectedTeam("sonic", List.of("tails")),
                new SelectedTeam("knuckles", List.of())
        );
    }

    @Override
    public SaveSlotSummary summarizeFreshSlot(int slot) {
        return SaveSlotSummary.empty(slot);
    }

    /**
     * Parses extra team combinations from a semicolon-separated string.
     * Each team is a comma-separated list where the first element is the main character
     * and the rest are sidekicks.
     * <p>
     * Example: {@code "sonic,knuckles;knuckles,tails"} produces two teams:
     * Sonic+Knuckles and Knuckles+Tails.
     *
     * @param raw the raw team string, or null/blank for no extra teams
     * @return the parsed list of extra teams
     */
    public List<SelectedTeam> parseExtraTeams(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<SelectedTeam> result = new ArrayList<>();
        for (String teamRaw : raw.split(";")) {
            List<String> parts = Arrays.stream(teamRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!parts.isEmpty()) {
                result.add(new SelectedTeam(parts.get(0), parts.subList(1, parts.size())));
            }
        }
        return result;
    }
}
