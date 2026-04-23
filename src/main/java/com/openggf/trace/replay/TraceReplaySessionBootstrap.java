package com.openggf.trace.replay;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.OscillationManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceObjectSnapshotBinder;
import com.openggf.trace.TraceReplayBootstrap;

/**
 * Headless and live trace replay share the same pre-gameplay setup:
 * configure team, load level, seed vblank counter, pre-advance
 * oscillation, apply pre-trace object snapshots and replay start
 * state. This helper owns that sequence so {@code AbstractTraceReplayTest}
 * and {@code TraceSessionLauncher} stay consistent.
 */
public final class TraceReplaySessionBootstrap {

    private TraceReplaySessionBootstrap() {
    }

    /**
     * Prepare configuration state that must be set before the level is
     * loaded. Call before the caller loads the level.
     */
    public static void prepareConfiguration(TraceData trace, TraceMetadata meta) {
        applyRecordedTeamConfig(meta);
        if (TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace)
                && "s3k".equals(meta.game())) {
            SonicConfigurationService.getInstance()
                    .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        }
    }

    /**
     * Apply pre-gameplay replay state to an already-loaded level. Must be
     * called after the level has been loaded and a player sprite exists
     * on the runtime.
     *
     * @param preTraceOscOverride number of pre-trace oscillation frames to
     *                            pre-advance; pass a negative value to use
     *                            the value from the trace metadata.
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
        applyStartPositionAndGroundSnap(trace, fixture);
        return new BootstrapResult(hydration, replayStart);
    }

    /**
     * Reapply the metadata-recorded start centre coordinates (when the
     * trace policy says to) and run an initial ground-attachment pass so
     * the sprite's Y/angle match the ROM's post-title-card state before
     * frame 0 of the comparison loop. Mirrors
     * {@code HeadlessTestFixture.Builder.build} steps 6 and 11.
     */
    private static void applyStartPositionAndGroundSnap(TraceData trace,
                                                        TraceReplayFixture fixture) {
        AbstractPlayableSprite sprite = fixture.sprite();
        if (sprite == null) {
            return;
        }
        TraceMetadata meta = trace.metadata();
        if (TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
            sprite.setCentreX(meta.startX());
            sprite.setCentreY(meta.startY());
        }
        // Ground snap: 14 subpixel threshold matches the fixture.
        if (GameServices.collision() != null) {
            GameServices.collision().resolveGroundAttachment(sprite, 14, () -> false);
        }
    }

    public record BootstrapResult(
            TraceObjectSnapshotBinder.Result hydration,
            TraceReplayBootstrap.ReplayStartState replayStart) {
    }

    private static void applyRecordedTeamConfig(TraceMetadata meta) {
        if (!meta.hasRecordedTeam()) {
            return;
        }
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        String main = meta.recordedMainCharacter();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                main == null ? "sonic" : main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                String.join(",", meta.recordedSidekicks()));
    }
}
