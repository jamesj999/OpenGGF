package com.openggf.debug.playback;

import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime controller for in-engine BizHawk playback debugging.
 */
public final class PlaybackDebugManager {
    private static final Logger LOGGER = Logger.getLogger(PlaybackDebugManager.class.getName());
    private static final PlaybackDebugManager INSTANCE = new PlaybackDebugManager();
    private static final int DEFAULT_JUMP_FRAMES = 60;
    private static final int PERIODIC_LOG_INTERVAL_FRAMES = 60;

    private final Bk2MovieLoader movieLoader = new Bk2MovieLoader();

    private Bk2Movie movie;
    private PlaybackTimelineController timeline;
    private boolean enabled;
    private String statusMessage = "Playback disabled";
    private int lastAppliedMask;
    private boolean lastAppliedStart;
    private int previousActionMask;
    private boolean currentForcedJumpPress;
    private GameMode lastObservedMode = GameMode.LEVEL;
    private int firstActiveFrame = -1;
    private int periodicLogCounter;

    private PlaybackDebugManager() {
    }

    private SonicConfigurationService configService() {
        return com.openggf.game.RuntimeManager.getEngineServices().configuration();
    }

    public static PlaybackDebugManager getInstance() {
        return INSTANCE;
    }

    public synchronized void handleInput(InputHandler input) {
        if (input == null) {
            return;
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_TOGGLE_KEY))) {
            enabled = !enabled;
            if (enabled) {
                if (movie == null) {
                    loadFromConfig();
                }
                if (movie != null) {
                    setStatus("Playback enabled", true);
                }
            } else {
                if (timeline != null) {
                    timeline.setPlaying(false);
                }
                setStatus("Playback disabled", true);
            }
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_LOAD_KEY))) {
            loadFromConfig();
        }

        if (!enabled || movie == null || timeline == null) {
            return;
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_PLAY_PAUSE_KEY))) {
            timeline.togglePlaying();
            periodicLogCounter = 0;
            setStatus(timeline.isPlaying() ? "Playback running" : "Playback paused", true);
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_STEP_BACK_KEY))) {
            timeline.stepBackward();
            setStatus("Stepped movie frame backward", true);
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_STEP_FORWARD_KEY))) {
            timeline.stepForward();
            setStatus("Stepped movie frame forward", true);
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_JUMP_BACK_KEY))) {
            timeline.jumpBackward(DEFAULT_JUMP_FRAMES);
            setStatus("Jumped movie backward by " + DEFAULT_JUMP_FRAMES + " frames", true);
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_JUMP_FORWARD_KEY))) {
            timeline.jumpForward(DEFAULT_JUMP_FRAMES);
            setStatus("Jumped movie forward by " + DEFAULT_JUMP_FRAMES + " frames", true);
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_FAST_RATE_KEY))) {
            timeline.cycleRate();
            setStatus("Playback rate set to " + timeline.getRate() + "x", true);
        }

        if (input.isKeyPressedWithoutModifiers(configService().getInt(SonicConfiguration.PLAYBACK_RESET_TO_START_KEY))) {
            resetToConfiguredOffset();
            setStatus("Reset movie cursor to start offset", true);
        }
    }

    public synchronized boolean isDriving(GameMode mode) {
        return enabled && movie != null && timeline != null && mode == GameMode.LEVEL;
    }

    public synchronized int getCurrentForcedInputMask() {
        if (movie == null || timeline == null) {
            lastAppliedMask = 0;
            lastAppliedStart = false;
            currentForcedJumpPress = false;
            return 0;
        }
        if (!timeline.isPlaying()) {
            lastAppliedMask = 0;
            lastAppliedStart = false;
            currentForcedJumpPress = false;
            return 0;
        }
        Bk2FrameInput frame = movie.getFrame(timeline.getCursorFrame());
        lastAppliedMask = frame.p1InputMask();
        lastAppliedStart = frame.p1StartPressed();

        int actionMask = frame.p1ActionMask();
        int pressed = (actionMask ^ previousActionMask) & actionMask;
        currentForcedJumpPress = pressed != 0;
        previousActionMask = actionMask;

        return lastAppliedMask;
    }

    public synchronized boolean isCurrentForcedJumpPress() {
        return currentForcedJumpPress;
    }

    public synchronized void onLevelFrameAdvanced() {
        if (!enabled || movie == null || timeline == null) {
            return;
        }
        timeline.advanceIfPlaying();
        if (timeline.isPlaying()) {
            periodicLogCounter++;
            if (periodicLogCounter >= PERIODIC_LOG_INTERVAL_FRAMES) {
                periodicLogCounter = 0;
                logStatus("tick");
            }
        } else {
            periodicLogCounter = 0;
        }
    }

    public synchronized void clearLastAppliedState() {
        lastAppliedMask = 0;
        lastAppliedStart = false;
        currentForcedJumpPress = false;
        previousActionMask = 0;
    }


    public synchronized List<String> buildOverlayLines(GameMode mode) {
        if (mode != null) {
            lastObservedMode = mode;
        }
        return buildOverlayLines();
    }

    public synchronized List<String> buildOverlayLines() {
        if (!enabled && movie == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(8);
        String state;
        if (!enabled) {
            state = "OFF";
        } else if (timeline != null && timeline.isPlaying()) {
            state = "PLAY";
        } else {
            state = "PAUSE";
        }
        lines.add("== PLAYBACK ==");
        lines.add("State: " + state + "  Mode: " + lastObservedMode.name());

        if (movie == null || timeline == null) {
            lines.add("Movie: <none>");
            lines.add(statusMessage);
            return lines;
        }

        String fileName = movie.getSourcePath().getFileName() != null
                ? movie.getSourcePath().getFileName().toString()
                : movie.getSourcePath().toString();
        lines.add("Movie: " + fileName);
        lines.add("Frame: " + timeline.getCursorFrame() + "/" + (movie.getFrameCount() - 1)
                + "  Rate: " + timeline.getRate() + "x");
        if (firstActiveFrame >= 0) {
            lines.add("First Active: " + firstActiveFrame);
        }
        lines.add("Input: " + formatInput(lastAppliedMask, lastAppliedStart));
        lines.add(statusMessage);
        return lines;
    }

    public synchronized boolean hasLoadedMovie() {
        return movie != null;
    }

    public synchronized boolean isHudVisible() {
        return enabled || movie != null;
    }

    public synchronized void setObservedMode(GameMode mode) {
        if (mode != null) {
            lastObservedMode = mode;
        }
    }

    private void loadFromConfig() {
        String configuredPath = configService().getString(SonicConfiguration.PLAYBACK_MOVIE_PATH);
        if (configuredPath == null || configuredPath.isBlank()) {
            setStatus("Playback movie path is blank", true);
            return;
        }
        Path moviePath = resolveAgainstWorkingDir(configuredPath);
        try {
            Bk2Movie loaded = movieLoader.load(moviePath);
            this.movie = loaded;
            this.timeline = new PlaybackTimelineController(loaded.getFrameCount());
            this.firstActiveFrame = findFirstActiveFrame(loaded);
            resetToConfiguredOffset();
            setStatus("Loaded movie (" + loaded.getFrameCount() + " frames)", true);
        } catch (IOException e) {
            setStatus("Failed to load BK2: " + e.getMessage(), true);
            LOGGER.log(Level.WARNING, "Failed to load BK2 movie: " + moviePath, e);
        }
    }

    private void resetToConfiguredOffset() {
        if (timeline == null) {
            return;
        }
        timeline.resetTo(getConfiguredStartOffset());
        periodicLogCounter = 0;
        clearLastAppliedState();
    }

    /**
     * Converts the user-configured BizHawk frame number to a 0-based internal index.
     * BizHawk frame numbers correspond to 1-based line numbers in the Input Log file.
     */
    private int getConfiguredStartOffset() {
        int bk2Frame = configService().getInt(SonicConfiguration.PLAYBACK_START_OFFSET_FRAME);
        if (movie != null) {
            return Math.max(0, movie.bk2FrameToIndex(bk2Frame));
        }
        return Math.max(0, bk2Frame);
    }

    private static Path resolveAgainstWorkingDir(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.isBlank()) {
            return path.normalize();
        }
        return Path.of(userDir).resolve(path).normalize();
    }

    private static String formatInput(int mask, boolean start) {
        StringBuilder sb = new StringBuilder(8);
        sb.append((mask & AbstractPlayableSprite.INPUT_UP) != 0 ? 'U' : '.');
        sb.append((mask & AbstractPlayableSprite.INPUT_DOWN) != 0 ? 'D' : '.');
        sb.append((mask & AbstractPlayableSprite.INPUT_LEFT) != 0 ? 'L' : '.');
        sb.append((mask & AbstractPlayableSprite.INPUT_RIGHT) != 0 ? 'R' : '.');
        sb.append((mask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? 'J' : '.');
        sb.append(start ? 'S' : '.');
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private static int findFirstActiveFrame(Bk2Movie movie) {
        for (Bk2FrameInput frame : movie.getFrames()) {
            if (frame.p1InputMask() != 0 || frame.p1StartPressed()) {
                return frame.frameIndex();
            }
        }
        return -1;
    }

    private void setStatus(String message, boolean logNow) {
        statusMessage = message;
        if (logNow) {
            logStatus("status");
        }
    }

    private void logStatus(String reason) {
        if (movie == null || timeline == null) {
            LOGGER.info("[Playback][" + reason + "] " + statusMessage);
            return;
        }
        Bk2FrameInput frame = movie.getFrame(timeline.getCursorFrame());
        String summary = String.format(
                "[Playback][%s] state=%s mode=%s frame=%d/%d rate=%dx input=%s firstActive=%d msg=%s",
                reason,
                timeline.isPlaying() ? "PLAY" : "PAUSE",
                lastObservedMode.name(),
                timeline.getCursorFrame(),
                movie.getFrameCount() - 1,
                timeline.getRate(),
                formatInput(frame.p1InputMask(), frame.p1StartPressed()),
                firstActiveFrame,
                statusMessage);
        LOGGER.info(summary);
    }
}
