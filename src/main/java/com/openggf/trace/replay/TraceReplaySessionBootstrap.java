package com.openggf.trace.replay;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceObjectSnapshotBinder;
import com.openggf.trace.TraceReplayBootstrap;

import java.util.logging.Logger;

/**
 * Headless and live trace replay share the same pre-gameplay setup.
 * This helper owns that sequence so {@code AbstractTraceReplayTest}
 * and {@code TraceSessionLauncher} stay consistent. Steps, in order:
 * <ol>
 *   <li>{@link #prepareConfiguration}: set recorded team + S3K intro
 *       skip flag on the configuration service. Must run before the
 *       caller loads the level.</li>
 *   <li>{@link #applyBootstrap}: seed the vblank counter, pre-advance
 *       oscillation, apply pre-trace object snapshots, apply the
 *       replay start state, then (when the trace policy calls for it)
 *       reapply the metadata start centre coordinates and run an
 *       initial ground-attachment pass — so both headless fixture and
 *       live launcher end up with the same post-title-card sprite state
 *       before frame 0 of the comparison loop.</li>
 * </ol>
 */
public final class TraceReplaySessionBootstrap {

    private static final Logger LOGGER =
            Logger.getLogger(TraceReplaySessionBootstrap.class.getName());

    private TraceReplaySessionBootstrap() {
    }

    /**
     * Clears the per-zone subsystem state the headless fixture zaps
     * via {@code TestEnvironment.resetPerTest()}: sprites, collision,
     * camera, fade, game state, timers, water, parallax, cross-game
     * features, debug overlay, and the game's {@code perTestLeadStep}
     * (e.g. S1 event/switch/conveyor reset).
     *
     * <p>Call this BEFORE {@code LevelManager.loadZoneAndAct} when
     * starting a live trace replay. Without it, state left behind by
     * {@code Engine.initializeGame()} (title screen, default level,
     * residual object state) leaks into the replay — one symptom is
     * subpixel drift from frame 0 that first becomes pixel-visible at
     * the first ROM-accurate collision or enemy destruction.
     */
    public static void resetLevelSubsystemsForReplay() {
        LevelInitProfile profile = GameModuleRegistry.getCurrent().getLevelInitProfile();
        for (InitStep step : profile.perTestResetSteps()) {
            try {
                step.execute();
            } catch (RuntimeException e) {
                LOGGER.warning("Trace-replay reset step '" + step.name()
                        + "' threw " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }
    }

    /**
     * Prepare configuration state that must be set before the level is
     * loaded. Call before the caller loads the level.
     *
     * <p>Isolates trace playback from any gameplay-altering settings
     * the user may have configured for their own game (team,
     * cross-game donation, skip-intros). Live callers should snapshot
     * the affected keys via {@link #snapshotGameplayConfig()} before
     * calling this, and restore them via
     * {@link #restoreGameplayConfig(ConfigSnapshot)} when the trace
     * session tears down.
     */
    public static void prepareConfiguration(TraceData trace, TraceMetadata meta) {
        SonicConfigurationService config = SonicConfigurationService.getInstance();

        // Team: the recorded trace dictates the team. If metadata
        // didn't record one (legacy), force Sonic-solo — the trace
        // can't expect anything else.
        String main = meta.recordedMainCharacter();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                main == null || main.isBlank() ? "sonic" : main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                String.join(",", meta.recordedSidekicks()));

        // Cross-game donation wasn't recorded; always force it off so
        // trace physics/visuals match the base ROM.
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);

        if (TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace)
                && "s3k".equals(meta.game())) {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        }
    }

    /**
     * Captured view of the gameplay-altering configuration keys that
     * {@link #prepareConfiguration} rewrites. Pass back to
     * {@link #restoreGameplayConfig} when tearing down a trace
     * session so the user's own config is preserved across launches.
     */
    public record ConfigSnapshot(
            Object mainCharacterCode,
            Object sidekickCharacterCode,
            Object crossGameFeaturesEnabled,
            Object s3kSkipIntros) {
    }

    public static ConfigSnapshot snapshotGameplayConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        return new ConfigSnapshot(
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE),
                config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE),
                config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED),
                config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS));
    }

    public static void restoreGameplayConfig(ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        restore(config, SonicConfiguration.MAIN_CHARACTER_CODE, snapshot.mainCharacterCode());
        restore(config, SonicConfiguration.SIDEKICK_CHARACTER_CODE, snapshot.sidekickCharacterCode());
        restore(config, SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, snapshot.crossGameFeaturesEnabled());
        restore(config, SonicConfiguration.S3K_SKIP_INTROS, snapshot.s3kSkipIntros());
    }

    private static void restore(SonicConfigurationService config,
                                SonicConfiguration key,
                                Object value) {
        if (value == null) {
            return;
        }
        config.setConfigValue(key, value);
    }

    /**
     * Apply pre-gameplay replay state to an already-loaded level. Must
     * be called after the level has been loaded and a player sprite
     * exists on the runtime.
     *
     * <p>Performs, in order:
     * <ol>
     *   <li>{@code ObjectManager.initVblaCounter} — seed the VBlank
     *       counter to match the trace's initial value.</li>
     *   <li>Oscillation pre-advance — {@link OscillationManager#update}
     *       run {@code preTraceOsc} times so the OscillateNumDo phase
     *       matches the ROM at trace frame 0.</li>
     *   <li>{@link TraceReplayBootstrap#applyPreTraceState} — object
     *       snapshot + player history hydration.</li>
     *   <li>{@link TraceReplayBootstrap#applyReplayStartStateForTraceReplay}
     *       — primary-sprite state for seeded traces.</li>
     * </ol>
     *
     * <p>The metadata start-position reapply + initial ground snap that
     * mirrors {@code HeadlessTestFixture.Builder.build} steps 6 and 11
     * is exposed separately as
     * {@link #applyStartPositionAndGroundSnap} so callers can invoke it
     * BEFORE this method (matching the test fixture order, which sets
     * the start position and snaps to ground before hydrating recorded
     * player history).
     *
     * @param preTraceOscOverride number of pre-trace oscillation frames
     *                            to pre-advance; pass a negative value
     *                            to use the value from the trace metadata.
     */
    public static BootstrapResult applyBootstrap(TraceData trace,
                                                 TraceReplayFixture fixture,
                                                 int preTraceOscOverride) {
        TraceMetadata meta = trace.metadata();
        ObjectManager om = GameServices.level().getObjectManager();
        if (om != null
                && TraceReplayBootstrap.shouldUseTraceStartBootstrapForTraceReplay(trace)) {
            om.initVblaCounter(
                    TraceReplayBootstrap.initialVblankCounterForTraceReplay(trace) - 1);
        }

        int preTraceOsc = preTraceOscOverride >= 0
                ? preTraceOscOverride
                : meta.preTraceOscillationFrames();
        if (TraceReplayBootstrap.shouldUseTraceStartBootstrapForTraceReplay(trace)
                && preTraceOsc > 0) {
            for (int i = 0; i < preTraceOsc; i++) {
                OscillationManager.update(-(preTraceOsc - i));
            }
        }

        TraceObjectSnapshotBinder.Result hydration =
                TraceReplayBootstrap.applyPreTraceState(trace, fixture);
        TraceReplayBootstrap.ReplayStartState replayStart =
                TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, fixture);
        return new BootstrapResult(hydration, replayStart);
    }

    /**
     * Reapply the metadata-recorded start centre coordinates and run
     * an initial ground-attachment pass so the sprite's Y/angle match
     * the ROM's post-title-card state. Mirrors
     * {@code HeadlessTestFixture.Builder.build} steps 6 and 11 so
     * headless and live paths end up with identical post-load sprite
     * state.
     *
     * <p>Call this BEFORE {@link #applyBootstrap}. The fixture runs
     * these steps at build time (before any trace-data bootstrap);
     * running them afterwards would clobber subpixel state that
     * {@code applyReplayStartState} had written for seeded traces.
     *
     * <p>Gated on
     * {@link TraceReplayBootstrap#shouldApplyMetadataStartPositionForTraceReplay}
     * (i.e. {@code replaySeedTraceIndex == 0 && !legacyS3kAizIntro}).
     * Seeded-frame traces and legacy-AIZ traces are short-circuited.
     */
    public static void applyStartPositionAndGroundSnap(TraceData trace,
                                                       TraceReplayFixture fixture) {
        if (!TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
            return;
        }
        AbstractPlayableSprite sprite = fixture.sprite();
        if (sprite == null) {
            return;
        }
        TraceMetadata meta = trace.metadata();
        sprite.setCentreX(meta.startX());
        sprite.setCentreY(meta.startY());
        // Ground snap: 14 subpixel threshold matches the fixture.
        if (GameServices.collision() != null) {
            GameServices.collision().resolveGroundAttachment(sprite, 14, () -> false);
        }
    }

    public record BootstrapResult(
            TraceObjectSnapshotBinder.Result hydration,
            TraceReplayBootstrap.ReplayStartState replayStart) {
    }
}
