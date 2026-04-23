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
    private LiveTraceComparator comparator;
    private TraceHudOverlay overlay;
    private TraceReplayFixture fixture;

    private boolean completionArmed;
    private int completionHoldFrames;
    private boolean fadeStarted;

    private TraceSessionLauncher(TraceEntry entry, TraceData trace, Bk2Movie movie) {
        this.entry = entry;
        this.trace = trace;
        this.movie = movie;
    }

    public static TraceSessionLauncher active() {
        return activeSession;
    }

    public static void launch(TraceEntry entry) {
        try {
            TraceData trace = TraceData.load(entry.dir());
            Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());
            TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
            TraceSessionLauncher session = new TraceSessionLauncher(entry, trace, movie);
            Engine.currentGameLoop().launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishLaunchAfterGameBootstrap);
        } catch (Exception e) {
            LOGGER.severe("Failed to launch trace " + entry.dir() + ": " + e.getMessage());
        }
    }

    private void finishLaunchAfterGameBootstrap() {
        try {
            GameServices.level().loadZoneAndAct(entry.zone(), entry.act());
            GameLoop loop = Engine.currentGameLoop();
            loop.setGameMode(GameMode.LEVEL);

            PlaybackDebugManager playback = PlaybackDebugManager.getInstance();
            int startIndex = TraceReplayBootstrap
                    .recordingStartFrameForTraceReplay(trace);
            playback.startSession(movie, startIndex);

            this.fixture = new LiveFixture(playback, loop);
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

            int initialCursor = boot.replayStart().startingTraceIndex();
            this.comparator = new LiveTraceComparator(
                    trace,
                    ToleranceConfig.DEFAULT,
                    initialCursor,
                    loop::getMainPlayableSprite);
            this.overlay = new TraceHudOverlay(comparator, fixture);
            playback.setFrameObserver(comparator);
            activeSession = this;
        } catch (Exception e) {
            PlaybackDebugManager.getInstance().endSession();
            LOGGER.severe("Failed to finish trace launch for "
                    + entry.dir() + ": " + e.getMessage());
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
        PlaybackDebugManager.getInstance().endSession();
        GameLoop loop = Engine.currentGameLoop();
        if (loop != null) {
            loop.returnToMasterTitle();
        }
        activeSession = null;
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
