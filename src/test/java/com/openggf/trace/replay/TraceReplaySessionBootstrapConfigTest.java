package com.openggf.trace.replay;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the snapshot/restore contract for
 * {@link TraceReplaySessionBootstrap#prepareConfiguration} — the live
 * launcher relies on this to isolate trace playback from the user's
 * own gameplay preferences.
 */
class TraceReplaySessionBootstrapConfigTest {

    private SonicConfigurationService config;
    private Object savedMain;
    private Object savedSidekick;
    private Object savedCrossGame;
    private Object savedSkipIntros;

    @BeforeEach
    void setUp() {
        config = SonicConfigurationService.getInstance();
        savedMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        savedSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        savedCrossGame = config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED);
        savedSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
    }

    @AfterEach
    void tearDown() {
        if (savedMain != null) {
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, savedMain);
        }
        if (savedSidekick != null) {
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, savedSidekick);
        }
        if (savedCrossGame != null) {
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, savedCrossGame);
        }
        if (savedSkipIntros != null) {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, savedSkipIntros);
        }
    }

    @Test
    void prepareConfigurationForcesSoloSonicWhenMetadataHasNoTeam() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);

        TraceData trace = TraceFixtures.trace(
                TraceFixtures.metadata("s1", 0, 0), List.of());
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());

        // Legacy trace without a recorded team → default to sonic-solo.
        assertEquals("sonic",
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("",
                config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        // Cross-game donation is always suppressed — trace physics
        // must match the base ROM.
        assertEquals(false,
                config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
    }

    @Test
    void restoreGameplayConfigRoundtripsUserPreferences() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        TraceReplaySessionBootstrap.ConfigSnapshot snap =
                TraceReplaySessionBootstrap.snapshotGameplayConfig();

        // Simulate a trace launch: prepareConfiguration overwrites.
        TraceData trace = TraceFixtures.trace(
                TraceFixtures.metadata("s3k", 0, 0), List.of());
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());

        assertEquals("sonic",
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals(false,
                config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        // Note: prepareConfiguration only forces S3K_SKIP_INTROS=false
        // when TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay
        // returns true (legacy S3K AIZ intro traces). This stub doesn't
        // qualify, so the user's true stays in place — and is therefore
        // already restored by the roundtrip.

        // Restore should bring back the user's settings.
        TraceReplaySessionBootstrap.restoreGameplayConfig(snap);

        assertEquals("tails",
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("knuckles",
                config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        assertEquals(true,
                config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        assertEquals(true,
                config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS));
    }

    @Test
    void restoreGameplayConfigIsNullSafe() {
        // Should not throw. Null snapshot = no-op.
        TraceReplaySessionBootstrap.restoreGameplayConfig(null);
        assertFalse(config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE) == null);
    }
}
