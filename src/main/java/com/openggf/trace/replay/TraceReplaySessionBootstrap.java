package com.openggf.trace.replay;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceObjectSnapshotBinder;
import com.openggf.trace.TraceReplayBootstrap;

import java.util.List;
import java.util.logging.Logger;

/**
 * Headless and live trace replay share the same pre-gameplay setup.
 * This helper owns that sequence so {@code AbstractTraceReplayTest}
 * and {@code TraceSessionLauncher} stay consistent. Steps, in order:
 * <ol>
 *   <li>{@link #prepareConfiguration}: set recorded team + S3K intro
 *       skip flag on the configuration service. Must run before the
 *       caller loads the level.</li>
 *   <li>{@link #applyBootstrap}: derive any allowed timing prelude from
 *       trace-visible execution timing, advance native timing-only state
 *       where policy allows, and choose the replay comparison cursor.
 *       It must not copy recorded object, player, sidekick, RNG, camera,
 *       or vblank state into the engine.</li>
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
     * residual object state) leaks into the replay â€” one symptom is
     * subpixel drift from frame 0 that first becomes pixel-visible at
     * the first ROM-accurate collision or enemy destruction.
     */
    public static void resetLevelSubsystemsForReplay() {
        LevelInitProfile profile = GameServices.module().getLevelInitProfile();
        for (InitStep step : profile.perTestResetSteps()) {
            try {
                step.execute();
            } catch (RuntimeException e) {
                LOGGER.warning("Trace-replay reset step '" + step.name()
                        + "' threw " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }
        // Reset the GameRng seed so the replay starts from the same
        // pristine state the headless test fixture does. Between
        // Engine.initializeGame() and the trace callback, the master
        // title screen and any configured startup mode may advance
        // the PRNG; a single divergent Random() call later rewrites
        // badnik behaviour (e.g. animal selection on kill, Batbrain
        // eyelid flicker) and causes subpixel drift that surfaces at
        // the first enemy destruction.
        if (GameServices.runtimeOrNull() != null) {
            GameRng rng = GameServices.rngOrNull();
            if (rng != null) {
                rng.setSeed(0L);
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
        SonicConfigurationService config = GameServices.configuration();

        // Team: the recorded trace dictates the team. If metadata
        // didn't record one (legacy), force Sonic-solo â€” the trace
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
        SonicConfigurationService config = GameServices.configuration();
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
        SonicConfigurationService config = GameServices.configuration();
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
     * Apply pre-gameplay replay policy to an already-loaded level. Must
     * be called after the level has been loaded and a player sprite
     * exists on the runtime.
     *
     * <p>Performs, in order:
     * <ol>
     *   <li>Oscillation pre-advance derived from trace-visible gameplay
     *       timing, or from an explicit diagnostic override.</li>
     *   <li>Sidekick-only prelude ticks for title-card timing, when the
     *       trace policy can derive them from normal execution order.</li>
     *   <li>{@link TraceReplayBootstrap#applyPreTraceState} - currently
     *       a comparison-only compatibility hook that reports zero
     *       applied snapshots.</li>
     *   <li>{@link TraceReplayBootstrap#applyReplayStartStateForTraceReplay}
     *       - deterministic warmup/cursor selection without trace-state
     *       hydration.</li>
     * </ol>
     *
     * <p>The metadata start-position reapply + initial ground snap that
     * mirrors {@code HeadlessTestFixture.Builder.build} steps 6 and 11
     * is exposed separately as
     * {@link #applyStartPositionAndGroundSnap} so callers can invoke it
     * BEFORE this method (matching the test fixture order, which sets
     * the start position and snaps to ground before replay bootstrap
     * policy runs).
     *
     * @param preTraceOscOverride number of pre-trace oscillation frames
     *                            to pre-advance; pass a negative value
     *                            to derive timing through trace replay
     *                            policy.
     */
    public static BootstrapResult applyBootstrap(TraceData trace,
                                                 TraceReplayFixture fixture,
                                                 int preTraceOscOverride) {
        int preTraceOsc = TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(
                trace, preTraceOscOverride);
        for (int i = 0; i < preTraceOsc; i++) {
            OscillationManager.update(-(preTraceOsc - i));
        }
        int sidekickPreludeFrames =
                TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace);
        int objectPreludeFrames =
                TraceReplayBootstrap.levelObjectTitleCardPreludeFramesForTraceReplay(trace);
        if (objectPreludeFrames > 0
                && fixture.runtime() != null
                && fixture.runtime().getLevelManager() != null
                && fixture.runtime().getLevelManager().getObjectManager() != null) {
            var levelManager = fixture.runtime().getLevelManager();
            var objectManager = levelManager.getObjectManager();
            var camera = GameServices.cameraOrNull();
            int cameraX = camera != null ? camera.getX() : 0;
            for (int i = 0; i < objectPreludeFrames; i++) {
                objectManager.update(cameraX, null, List.of(), -(objectPreludeFrames - i), false);
            }
        }
        if (sidekickPreludeFrames > 0
                && fixture.runtime() != null
                && fixture.runtime().getSpriteManager() != null
                && fixture.runtime().getLevelManager() != null) {
            fixture.runtime().getSpriteManager().warmUpCpuSidekicksOnly(
                    sidekickPreludeFrames,
                    fixture.runtime().getLevelManager());
        }
        TraceObjectSnapshotBinder.Result hydration =
                TraceReplayBootstrap.applyPreTraceState(trace, fixture);
        TraceReplayBootstrap.ReplayStartState replayStart =
                TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, fixture);
        return new BootstrapResult(hydration, replayStart);
    }

    /**
     * Live trace visualisation starts at trace frame 0 and must not consume
     * visible trace prefix frames before the first rendered frame. Headless
     * replay may warm through legacy prefixes to align comparison, but doing
     * that in the live launcher makes full-intro traces appear to skip ahead.
     */
    public static BootstrapResult applyLiveBootstrap(TraceData trace,
                                                     TraceReplayFixture fixture,
                                                     int preTraceOscOverride) {
        int preTraceOsc = TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(
                trace, preTraceOscOverride);
        for (int i = 0; i < preTraceOsc; i++) {
            OscillationManager.update(-(preTraceOsc - i));
        }
        TraceObjectSnapshotBinder.Result hydration =
                TraceReplayBootstrap.applyPreTraceState(trace, fixture);
        return new BootstrapResult(hydration, TraceReplayBootstrap.ReplayStartState.DEFAULT);
    }

    /**
     * Align replay-local gameplay counters once before the comparison loop.
     * This is bootstrap state equivalent to loading the BK2 save-state point;
     * it is not per-frame trace hydration. The value comes from the trace row
     * immediately before the first driven row so native per-frame increments
     * keep both counters aligned afterward.
     */
    public static void alignFrameCountersForReplayStart(TraceFrame previousDriveFrame,
                                                        TraceFrame firstDriveFrame) {
        if (previousDriveFrame != null && previousDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.spritesOrNull() != null) {
            GameServices.spritesOrNull().setFrameCounter(previousDriveFrame.gameplayFrameCounter());
        }
        if (firstDriveFrame != null && firstDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.levelOrNull() != null) {
            GameServices.levelOrNull().setFrameCounter(firstDriveFrame.gameplayFrameCounter());
        }
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
     * these steps at build time before replay bootstrap policy runs;
     * running them afterwards would perturb the native state selected
     * by {@code applyReplayStartState}.
     *
     * <p>Gated on
     * {@link TraceReplayBootstrap#shouldApplyMetadataStartPositionForTraceReplay}
     * (i.e. {@code replaySeedTraceIndex == 0 && !legacyS3kAizIntro}).
     * Legacy-AIZ traces are short-circuited because their prefix is
     * consumed by deterministic warmup.
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

        // Mirror HeadlessTestFixture.Builder.build steps 6-11 exactly:
        // set the metadata centre coords, re-anchor sidekicks, wire
        // GroundSensor's level-manager override, re-run the camera +
        // level-events init so they pick up the new player position
        // (loadZoneAndAct ran them against the ROM default), then
        // snap to ground. Without the re-inits the camera and event
        // handlers keep the default-start-derived bounds from the
        // initial load, which drifts physics at the first collision.
        sprite.setCentreX(meta.startX());
        sprite.setCentreY(meta.startY());
        var level = GameServices.levelOrNull();
        if (level != null) {
            GameplayTeamBootstrap.repositionRegisteredSidekicks(
                    GameServices.module(),
                    level);
            GroundSensor.setLevelManager(level);
            level.initCameraForLevel();
            level.initLevelEventsForLevel();
        }
        // Ground snap: 14 subpixel threshold matches the fixture.
        var collision = GameServices.collisionOrNull();
        if (collision != null) {
            collision.resolveGroundAttachment(sprite, 14, () -> false);
        }
    }

    public record BootstrapResult(
            TraceObjectSnapshotBinder.Result hydration,
            TraceReplayBootstrap.ReplayStartState replayStart) {
    }
}
