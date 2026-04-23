package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.RuntimeManager;
import com.openggf.game.TitleCardProvider;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.testmode.TraceHudOverlay;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import java.util.logging.Logger;

/**
 * Drives a Trace Test Mode playback session. The picker calls
 * {@link #launch(TraceEntry)}; the launcher then asynchronously:
 * <ol>
 *   <li>asks {@link GameLoop#launchGameByEntry} to run the same
 *       master-title exit path as a user selecting the game,</li>
 *   <li>when the runtime is ready (callback from step 1), loads the
 *       trace's zone/act, starts programmatic BK2 playback, applies
 *       {@link TraceReplaySessionBootstrap}, and attaches a
 *       {@link LiveTraceComparator} + HUD overlay.</li>
 * </ol>
 *
 * <p>The session ends either on BK2 exhaustion (after a 1-second hold
 * on {@code TRACE COMPLETE}) or on Esc, both converging on a single
 * fade-to-black → {@link #teardown()} → picker path.
 */
public final class TraceSessionLauncher {

    private static final Logger LOGGER =
            Logger.getLogger(TraceSessionLauncher.class.getName());

    /** Hold (in seconds at 60 Hz) on TRACE COMPLETE before auto-fade. */
    private static final double COMPLETION_HOLD_SECONDS = 1.0;

    private static TraceSessionLauncher activeSession;

    private final TraceEntry entry;
    private final TraceData trace;
    private final Bk2Movie movie;
    /**
     * Snapshot of the user's gameplay-altering config taken before
     * {@link TraceReplaySessionBootstrap#prepareConfiguration} ran.
     * Restored in {@link #teardown()} so the picker returns to the
     * user's own team / cross-game / S3K_SKIP_INTROS preferences.
     */
    private final TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot;
    private LiveTraceComparator comparator;
    private TraceHudOverlay overlay;
    private TraceReplayFixture fixture;

    private boolean completionArmed;
    private int completionHoldFrames;
    private boolean fadeStarted;

    private TraceSessionLauncher(TraceEntry entry, TraceData trace, Bk2Movie movie,
                                 TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        this.entry = entry;
        this.trace = trace;
        this.movie = movie;
        this.configSnapshot = configSnapshot;
    }

    public static TraceSessionLauncher active() {
        return activeSession;
    }

    public static void launch(TraceEntry entry) {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            LOGGER.severe("Cannot launch trace " + entry.dir()
                    + ": Engine is not initialised");
            return;
        }
        // prepareConfiguration must run BEFORE launchGameByEntry
        // because the master-title exit handler calls
        // GameplayTeamBootstrap.registerActiveTeam, which reads
        // MAIN_CHARACTER_CODE / SIDEKICK_CHARACTER_CODE to build the
        // sprites for this session. If we deferred the write until
        // after the handler, the session would use the pre-trace team.
        //
        // Pre-flight the fade check via GameLoop so we don't mutate
        // config and then fail at launchGameByEntry with a
        // fade-active throw. GameServices.fade() isn't usable here —
        // no GameRuntime exists at master-title time — so go through
        // GameLoop which resolves the graphics-backed fade manager.
        if (!loop.canLaunchGameNow()) {
            LOGGER.severe("Cannot launch trace " + entry.dir()
                    + ": a master-title fade is already in flight");
            return;
        }
        // Snapshot the user's gameplay config BEFORE prepareConfiguration
        // mutates it, so teardown can restore the team / cross-game /
        // S3K_SKIP_INTROS preferences the user actually had.
        TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot =
                TraceReplaySessionBootstrap.snapshotGameplayConfig();
        boolean configMutated = false;
        try {
            TraceData trace = TraceData.load(entry.dir());
            Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());
            TraceSessionLauncher session = new TraceSessionLauncher(
                    entry, trace, movie, configSnapshot);
            TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
            configMutated = true;
            loop.launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishLaunchAfterGameBootstrap);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to launch trace " + entry.dir(), e);
            // If we already mutated config before launchGameByEntry
            // threw, restore the user's settings so the picker
            // resumes with their preferences intact.
            if (configMutated) {
                TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
            }
        }
    }

    private void finishLaunchAfterGameBootstrap() {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            LOGGER.severe("Trace launch callback fired after engine teardown; "
                    + "aborting for " + entry.dir());
            return;
        }
        PlaybackDebugManager playback = PlaybackDebugManager.getInstance();
        try {
            // prepareConfiguration already ran inside launch() before
            // launchGameByEntry fired — the master-title exit handler
            // needs the recorded team config in place when it calls
            // GameplayTeamBootstrap.registerActiveTeam.
            GameServices.level().loadZoneAndAct(entry.zone(), entry.act());
            loop.setGameMode(GameMode.LEVEL);

            // Swallow every title-card request the level load raised —
            // both the main `consumeTitleCardRequest` (fired by
            // requestTitleCardIfNeeded during profile load steps; if
            // left armed, GameLoop.stepInternal flips the mode to
            // TITLE_CARD on the next step, freezing the sprite and
            // desyncing the BK2 cursor from the comparator) and the
            // in-level variant. Headless trace tests bypass both via
            // the headless graphics mode; we do it explicitly.
            //
            // Relies on TitleCardProvider.reset() being a simple state
            // wipe that can't throw — true for all three game modules
            // today (S1/S2/S3K TitleCardManager.reset are field resets).
            GameServices.level().consumeTitleCardRequest();
            GameServices.level().consumeInLevelTitleCardRequest();
            TitleCardProvider titleCardProvider =
                    GameServices.module() != null
                            ? GameServices.module().getTitleCardProvider()
                            : null;
            if (titleCardProvider != null && titleCardProvider.isOverlayActive()) {
                titleCardProvider.reset();
            }

            int startIndex = TraceReplayBootstrap
                    .recordingStartFrameForTraceReplay(trace);
            playback.startSession(movie, startIndex);

            this.fixture = new LiveFixture(playback, loop);
            // Reapply metadata start centre + initial ground snap
            // BEFORE applyBootstrap so the order matches the headless
            // fixture (which does these in Builder.build() — i.e.
            // before any trace-data bootstrap runs). Running them
            // after applyBootstrap would clobber subpixel state the
            // helper's hydration steps wrote for seeded traces.
            TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

            int initialCursor = boot.replayStart().startingTraceIndex();
            this.comparator = new LiveTraceComparator(
                    trace,
                    ToleranceConfig.DEFAULT,
                    initialCursor,
                    loop::getMainPlayableSprite);
            this.overlay = new TraceHudOverlay(comparator);
            playback.setFrameObserver(comparator);
            activeSession = this;
        } catch (Exception e) {
            // Partial bootstrap: detach playback, restore the user's
            // gameplay config (we already mutated it in launch()), and
            // route back to the picker so the engine doesn't end up
            // orphaned in LEVEL mode with no session.
            playback.endSession();
            activeSession = null;
            TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to finish trace launch for " + entry.dir(), e);
            // loop is guaranteed non-null here — we early-returned at the
            // top of the method when it was null.
            loop.returnToMasterTitle();
        }
    }

    /** Called from {@link GameLoop} each LEVEL tick while active. */
    public void tick() {
        if (comparator == null || fadeStarted) {
            return;
        }
        if (comparator.isComplete() && !completionArmed) {
            completionArmed = true;
            completionHoldFrames = (int) Math.round(COMPLETION_HOLD_SECONDS * 60.0);
        }
        if (completionArmed) {
            if (completionHoldFrames > 0) {
                completionHoldFrames--;
            } else {
                startFadeOut();
            }
        }
    }

    /** Called when Esc is pressed during a LEVEL tick. */
    public void requestEarlyExit() {
        if (comparator == null || fadeStarted) {
            return;
        }
        startFadeOut();
    }

    public void render(PixelFontTextRenderer textRenderer) {
        if (overlay != null) {
            overlay.render(textRenderer);
        }
    }

    private void startFadeOut() {
        fadeStarted = true;
        GameServices.fade().startFadeToBlack(this::teardown);
    }

    private void teardown() {
        // Clear the static session pointer BEFORE kicking off the
        // runtime reset so any callback running during teardown
        // (GameLoop tick, Engine.draw, observer afterFrameAdvanced)
        // sees a clean "no session active" state instead of the
        // half-torn-down launcher.
        activeSession = null;
        PlaybackDebugManager.getInstance().endSession();
        // Restore the user's gameplay-altering config before we
        // rebuild the master title. If the user re-launches the
        // picker immediately, they see their own preferences rather
        // than whatever the trace dictated.
        TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
        GameLoop loop = Engine.currentGameLoop();
        if (loop != null) {
            loop.returnToMasterTitle();
        }
    }

    private static MasterTitleScreen.GameEntry resolveGameEntry(String gameId) {
        return switch (gameId) {
            case "s1" -> MasterTitleScreen.GameEntry.SONIC_1;
            case "s2" -> MasterTitleScreen.GameEntry.SONIC_2;
            case "s3k" -> MasterTitleScreen.GameEntry.SONIC_3K;
            default -> throw new IllegalArgumentException("Unknown game: " + gameId);
        };
    }

    /** Thin live-engine implementation of {@link TraceReplayFixture}. */
    private static final class LiveFixture implements TraceReplayFixture {
        private final PlaybackDebugManager playback;
        private final GameLoop gameLoop;

        private LiveFixture(PlaybackDebugManager playback, GameLoop gameLoop) {
            this.playback = playback;
            this.gameLoop = gameLoop;
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return gameLoop.getMainPlayableSprite();
        }

        @Override
        public GameRuntime runtime() {
            return RuntimeManager.getCurrent();
        }

        @Override
        public int stepFrameFromRecording() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            gameLoop.step();
            return mask;
        }

        @Override
        public int skipFrameFromRecording() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            playback.advanceCurrentFrameWithoutGameplay();
            return mask;
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            for (int i = 0; i < frameCount; i++) {
                playback.advanceCurrentFrameWithoutGameplay();
            }
        }

        private static int toReplayValidationMask(Bk2FrameInput frame) {
            int mask = frame.p1InputMask();
            if (frame.p1ActionMask() != 0) {
                mask |= AbstractPlayableSprite.INPUT_JUMP;
            }
            return mask;
        }
    }
}
