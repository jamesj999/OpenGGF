package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.level.scroll.AbstractZoneScrollHandler;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;

/**
 * ROM-shaped Slot Machine bonus-stage screen/background handler.
 *
 * <p>This ports the state owned by Slots_ScreenInit/Event and
 * Slots_BackgroundInit/Event into the engine scroll handler rather than
 * synthesizing a new wobble model in the runtime.
 */
public final class SwScrlSlots extends AbstractZoneScrollHandler {
    private static final int VISIBLE_LINES = 224;
    private static final int SCREEN_ORIGIN = 0x400;
    private static final int SCREEN_STEP_LIMIT = 0x20;
    private static final int BG_SCROLL_STEP = 0x400;
    private static final int BG_SCROLL_LIMIT = 0x8000;
    private static final int BG_DEFORM_SENTINEL = 0x7FFF;
    private static final int[] BG_DEFORM_SEGMENTS = {
            0x20, 0x20, 0x20, 0x20, 0x30, 0x20, 0x10,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x30, 0x20, 0x10, BG_DEFORM_SENTINEL
    };

    private static final BandConfig[] BAND_CONFIGS = {
            new BandConfig(0x48, 0x0000C000, 0x0008, true),
            new BandConfig(0x48, 0x0000C000, 0x0009, true),
            new BandConfig(0x50, 0x0000A000, 0x000B, true),
            new BandConfig(0x40, 0x0000E000, 0x0004, false),
            new BandConfig(0x40, 0x00010000, 0x0003, false),
            new BandConfig(0x48, 0x0000C000, 0x000A, true),
            new BandConfig(0x58, 0x00008000, 0x0007, true),
            new BandConfig(0x40, 0x0000E000, 0x0005, false)
    };

    private final short[] perLineVScroll = new short[VISIBLE_LINES];
    private final BandState[] bandStates = createBandStates();
    private final int[] bandScrollValues = new int[BAND_CONFIGS.length];
    private final int[] expandedBandScrollValues = new int[BG_DEFORM_SEGMENTS.length];

    private S3kSlotBonusStageRuntime activeRuntime;
    private boolean screenInitialized;
    private boolean backgroundInitialized;
    private int foregroundOriginX;
    private int foregroundOriginY;
    private int backgroundVelocity;
    private int backgroundCameraY;
    private int lastForegroundOriginX;
    private int lastForegroundOriginY;
    private int lastBackgroundOriginX;
    private int lastBackgroundOriginY;

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        applyScrollState(horizScrollBuf, cameraX, cameraY, activeSlotRuntime());
    }

    void updateForTest(S3kSlotBonusStageRuntime runtime, int cameraX, int cameraY, int frameCounter) {
        applyScrollState(null, cameraX, cameraY, runtime);
    }

    void updateForTest(S3kSlotBonusStageRuntime runtime, int[] horizScrollBuf, int cameraX, int cameraY,
                       int frameCounter) {
        applyScrollState(horizScrollBuf, cameraX, cameraY, runtime);
    }

    private void applyScrollState(int[] horizScrollBuf, int cameraX, int cameraY, S3kSlotBonusStageRuntime runtime) {
        resetScrollTracking();
        ensureRuntimeState(runtime, cameraX, cameraY);

        S3kSlotBonusStageRuntime.SlotVisualState visualState = runtime != null
                ? runtime.slotVisualState()
                : new S3kSlotBonusStageRuntime.SlotVisualState(0x10, 0x2D, 0x40, false);

        int targetX = (SCREEN_ORIGIN - visualState.eventsBgX()) + cameraX;
        int targetY = (SCREEN_ORIGIN - visualState.eventsBgY()) + cameraY;
        if (!screenInitialized) {
            foregroundOriginX = targetX;
            foregroundOriginY = targetY;
            screenInitialized = true;
        } else {
            foregroundOriginX = stepToward(foregroundOriginX, targetX, SCREEN_STEP_LIMIT);
            foregroundOriginY = stepToward(foregroundOriginY, targetY, SCREEN_STEP_LIMIT);
        }

        tickBackground(visualState.scalarIndex1());

        lastForegroundOriginX = foregroundOriginX;
        lastForegroundOriginY = foregroundOriginY;
        lastBackgroundOriginX = foregroundOriginX + bandScrollValues[0];
        lastBackgroundOriginY = backgroundCameraY & 0xFF;
        vscrollFactorBG = (short) (backgroundCameraY & 0xFF);
        Arrays.fill(perLineVScroll, (short) 0);

        if (horizScrollBuf != null) {
            fillPackedScrollBuffer(horizScrollBuf);
        }
    }

    @Override
    public short[] getPerLineVScrollBG() {
        return perLineVScroll;
    }

    int lastForegroundOriginXForTest() {
        return lastForegroundOriginX;
    }

    int lastForegroundOriginYForTest() {
        return lastForegroundOriginY;
    }

    int lastBackgroundOriginXForTest() {
        return lastBackgroundOriginX;
    }

    int lastBackgroundOriginYForTest() {
        return lastBackgroundOriginY;
    }

    int backgroundVelocityForTest() {
        return backgroundVelocity;
    }

    @Override
    public short getVscrollFactorFG() {
        return (short) foregroundOriginY;
    }

    private void ensureRuntimeState(S3kSlotBonusStageRuntime runtime, int cameraX, int cameraY) {
        if (runtime == activeRuntime) {
            return;
        }
        activeRuntime = runtime;
        screenInitialized = false;
        backgroundInitialized = false;
        foregroundOriginX = cameraX;
        foregroundOriginY = cameraY;
        backgroundVelocity = 0;
        backgroundCameraY = 0;
        Arrays.fill(bandScrollValues, 0);
        Arrays.fill(expandedBandScrollValues, 0);
        for (BandState bandState : bandStates) {
            bandState.reset();
        }
    }

    private void tickBackground(int scalarIndex1) {
        if (!backgroundInitialized) {
            backgroundInitialized = true;
        }

        int step = scalarIndex1 < 0 ? -BG_SCROLL_STEP : BG_SCROLL_STEP;
        backgroundVelocity = clampSignedLong(backgroundVelocity + step, BG_SCROLL_LIMIT);
        backgroundCameraY = (backgroundCameraY + backgroundVelocity) & 0xFF;

        boolean backgroundEventLatched = false;
        for (int i = 0; i < BAND_CONFIGS.length; i++) {
            backgroundEventLatched = tickBand(bandStates[i], BAND_CONFIGS[i], backgroundEventLatched);
            // The ROM stores fractional/phase state in the low word and feeds it
            // into VDP h-scroll table semantics directly. The engine's packed
            // per-line scroll buffer expects stable pixel offsets; using the raw
            // accumulator word here causes wrap-sized jumps and runaway scrolling.
            // Use the stepped base scroll component until the handler is ported
            // all the way down to VDP-equivalent table semantics.
            bandScrollValues[i] = toSignedWord(bandStates[i].baseScroll);
        }
        expandBandScrollTable();
    }

    private void fillPackedScrollBuffer(int[] horizScrollBuf) {
        short fgScroll = negWord(foregroundOriginX);
        int segmentIndex = 0;
        int segmentOffset = backgroundCameraY & 0xFFFF;
        while (segmentIndex < BG_DEFORM_SEGMENTS.length - 1 && segmentOffset >= BG_DEFORM_SEGMENTS[segmentIndex]) {
            segmentOffset -= BG_DEFORM_SEGMENTS[segmentIndex];
            segmentIndex++;
        }

        int line = 0;
        while (line < VISIBLE_LINES && segmentIndex < BG_DEFORM_SEGMENTS.length) {
            int segmentLength = BG_DEFORM_SEGMENTS[segmentIndex];
            if (segmentLength == BG_DEFORM_SENTINEL) {
                segmentLength = VISIBLE_LINES - line;
            } else {
                segmentLength -= segmentOffset;
            }
            if (segmentLength <= 0) {
                segmentIndex++;
                segmentOffset = 0;
                continue;
            }

            short bgScroll = negWord(expandedBandScrollValues[segmentIndex]);
            int linesToWrite = Math.min(segmentLength, VISIBLE_LINES - line);
            for (int i = 0; i < linesToWrite; i++) {
                int packed = packScrollWords(fgScroll, bgScroll);
                horizScrollBuf[line++] = packed;
                trackOffsetFromPacked(packed);
            }
            segmentIndex++;
            segmentOffset = 0;
        }
    }

    private void expandBandScrollTable() {
        for (int i = 0; i < expandedBandScrollValues.length; i++) {
            expandedBandScrollValues[i] = bandScrollValues[i & 0x7];
        }
    }

    private boolean tickBand(BandState state, BandConfig config, boolean backgroundEventLatched) {
        long sum = (state.accumulator & 0xFFFF_FFFFL) + (config.deltaLong & 0xFFFF_FFFFL);
        state.accumulator = (int) sum;
        boolean carry = (sum & 0x1_0000_0000L) != 0;
        if (carry) {
            state.accumulator = (state.accumulator & 0xFFFF0000)
                    | (((state.accumulator & 0xFFFF) + config.stepWord) & 0xFFFF);
            state.pendingAdvance = true;
        } else if ((state.accumulator & 0xFFFF) < config.stepWord) {
            state.pendingAdvance = true;
        }

        boolean blockedByFrameLatch = config.usesFrameLatch && backgroundEventLatched;
        if (blockedByFrameLatch) {
            return backgroundEventLatched;
        }

        state.countdown--;
        if (state.countdown < 0) {
            state.countdown = config.resetCountdown;
        } else if (!state.pendingAdvance) {
            return backgroundEventLatched;
        }

        state.pendingAdvance = false;
        if (!config.usesFrameLatch) {
            state.baseScroll = toSignedWord(state.baseScroll + config.stepWord);
        }
        return true;
    }

    private static int clampSignedLong(int value, int limit) {
        if (value > limit) {
            return limit;
        }
        if (value < -limit) {
            return -limit;
        }
        return value;
    }

    private static int stepToward(int current, int target, int maxStep) {
        int delta = target - current;
        if (delta > maxStep) {
            return current + maxStep;
        }
        if (delta < -maxStep) {
            return current - maxStep;
        }
        return target;
    }

    private static int toSignedWord(int value) {
        return (short) value;
    }

    private static BandState[] createBandStates() {
        BandState[] states = new BandState[BAND_CONFIGS.length];
        for (int i = 0; i < states.length; i++) {
            states[i] = new BandState();
        }
        return states;
    }

    private S3kSlotBonusStageRuntime activeSlotRuntime() {
        if (GameModuleRegistry.getCurrent() == null
                || !(GameModuleRegistry.getCurrent().getBonusStageProvider() instanceof Sonic3kBonusStageCoordinator coordinator)) {
            return null;
        }
        return coordinator.activeSlotRuntime();
    }

    private static final class BandState {
        private int accumulator;
        private int countdown;
        private int baseScroll;
        private boolean pendingAdvance;

        private void reset() {
            accumulator = 0;
            countdown = 0;
            baseScroll = 0;
            pendingAdvance = false;
        }
    }

    private record BandConfig(int stepWord, int deltaLong, int resetCountdown, boolean usesFrameLatch) {
    }
}
