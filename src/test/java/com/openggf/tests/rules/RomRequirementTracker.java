package com.openggf.tests.rules;

import org.junit.Assert;
import org.junit.Assume;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Centralized strict/optional ROM gating for tests.
 *
 * <p>Set {@code -Dqa.requiredRoms=SONIC_1,SONIC_2,...} to turn missing ROMs
 * from skips into failures for the listed games.</p>
 */
public final class RomRequirementTracker {
    public static final String REQUIRED_ROMS_PROPERTY = "qa.requiredRoms";

    private static final Object LOCK = new Object();
    private static final Map<SonicGame, Integer> missingRomSkipCounts = new EnumMap<>(SonicGame.class);

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(RomRequirementTracker::printMissingRomSkipSummary, "rom-skip-summary"));
    }

    private RomRequirementTracker() {
    }

    /**
     * Returns true if the given game is required in strict mode.
     */
    public static boolean isRequired(SonicGame game) {
        return parseRequiredRoms(System.getProperty(REQUIRED_ROMS_PROPERTY)).contains(game);
    }

    /**
     * Enforces ROM availability for a test that depends on ROM data.
     * Missing ROMs fail in strict mode and skip otherwise.
     */
    public static void requireRomOrSkip(SonicGame game, boolean available, String missingMessage) {
        if (available) {
            return;
        }
        handleMissingDependency(game, missingMessage, true, "ROM");
    }

    /**
     * Enforces non-ROM dependency availability under the same strict-mode gate.
     * Missing dependencies fail in strict mode and skip otherwise.
     */
    public static void requireDependencyOrSkip(SonicGame game, boolean available, String missingMessage) {
        if (available) {
            return;
        }
        handleMissingDependency(game, missingMessage, false, "dependency");
    }

    private static void handleMissingDependency(SonicGame game,
                                                String missingMessage,
                                                boolean trackRomSkip,
                                                String dependencyLabel) {
        if (isRequired(game)) {
            String configured = String.valueOf(System.getProperty(REQUIRED_ROMS_PROPERTY));
            Assert.fail("Missing required " + game.name() + " " + dependencyLabel
                    + " (" + REQUIRED_ROMS_PROPERTY + "=" + configured + "): " + missingMessage);
        }

        if (trackRomSkip) {
            recordMissingRomSkip(game);
        }
        Assume.assumeTrue(game.name() + " " + dependencyLabel + " not available: " + missingMessage, false);
    }

    static EnumSet<SonicGame> parseRequiredRoms(String rawPropertyValue) {
        EnumSet<SonicGame> required = EnumSet.noneOf(SonicGame.class);
        if (rawPropertyValue == null || rawPropertyValue.trim().isEmpty()) {
            return required;
        }

        for (String token : rawPropertyValue.split(",")) {
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                required.add(SonicGame.valueOf(normalized));
            } catch (IllegalArgumentException ignored) {
                // Unknown entries are ignored to keep parsing tolerant.
            }
        }
        return required;
    }

    private static void recordMissingRomSkip(SonicGame game) {
        synchronized (LOCK) {
            Integer count = missingRomSkipCounts.get(game);
            missingRomSkipCounts.put(game, count == null ? 1 : count + 1);
        }
    }

    private static void printMissingRomSkipSummary() {
        Map<SonicGame, Integer> snapshot = missingRomSkipCountsSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (SonicGame game : SonicGame.values()) {
            Integer count = snapshot.get(game);
            if (count != null) {
                joiner.add(game.name() + "=" + count);
            }
        }

        System.out.println("[ROM-SKIP-SUMMARY] " + joiner);
    }

    static Map<SonicGame, Integer> missingRomSkipCountsSnapshot() {
        synchronized (LOCK) {
            if (missingRomSkipCounts.isEmpty()) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new EnumMap<>(missingRomSkipCounts));
        }
    }

    static void resetForTests() {
        synchronized (LOCK) {
            missingRomSkipCounts.clear();
        }
    }
}
