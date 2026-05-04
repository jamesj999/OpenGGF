package com.openggf.testmode;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.live.LiveTraceComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Trace Test Mode camera focus controller.
 *
 * <p>While paused during a live trace session, lets the user cycle the camera
 * between up to five focus targets (default, engine/trace sidekick, engine/trace
 * main player) via the P1 LEFT/RIGHT keys, then restores the camera to its
 * pre-pause position on unpause.
 *
 * <p><b>Camera mutation contract:</b> this class must only call
 * {@link Camera#setX(short)} and {@link Camera#setY(short)}. It must not call
 * {@link Camera#updatePosition} or any other camera method, and it must not
 * invoke any sprite/object/manager update path. Calling {@code updatePosition}
 * would clobber the focus value with the focused-sprite-derived position; the
 * pause early-return in {@link com.openggf.GameLoop} keeps every other system
 * frozen, so simply writing {@code x}/{@code y} is sufficient and safe.
 */
public final class TraceCameraFocusController {

    public enum FocusMode {
        DEFAULT("Default"),
        SIDEKICK_ENGINE("Sidekick (Eng)"),
        SIDEKICK_TRACE("Sidekick (Trace)"),
        MAIN_ENGINE("Main (Eng)"),
        MAIN_TRACE("Main (Trace)");

        private final String label;
        FocusMode(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final LiveTraceComparator comparator;
    private final Supplier<AbstractPlayableSprite> mainSpriteSupplier;
    private final Supplier<AbstractPlayableSprite> firstSidekickSupplier;
    private final Supplier<Camera> cameraSupplier;
    private final SonicConfigurationService configService;
    private final Supplier<Boolean> pausedSupplier;

    private final List<FocusMode> available = new ArrayList<>();
    private int activeIndex;
    private short savedCamX;
    private short savedCamY;
    private boolean wasPaused;
    private FocusMode reapplyAfterStep;

    public TraceCameraFocusController(
            LiveTraceComparator comparator,
            Supplier<AbstractPlayableSprite> mainSpriteSupplier,
            Supplier<AbstractPlayableSprite> firstSidekickSupplier,
            Supplier<Camera> cameraSupplier,
            SonicConfigurationService configService,
            Supplier<Boolean> pausedSupplier) {
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.mainSpriteSupplier = Objects.requireNonNull(mainSpriteSupplier, "mainSpriteSupplier");
        this.firstSidekickSupplier = Objects.requireNonNull(firstSidekickSupplier, "firstSidekickSupplier");
        this.cameraSupplier = Objects.requireNonNull(cameraSupplier, "cameraSupplier");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.pausedSupplier = Objects.requireNonNull(pausedSupplier, "pausedSupplier");
    }

    /** @return current focus label for the HUD, or {@code null} if not paused. */
    public String currentLabel() {
        if (!wasPaused || available.isEmpty()) return null;
        return available.get(activeIndex).label();
    }

    /** Test hook: number of focuses currently available (including DEFAULT). */
    int availableSize() { return available.size(); }

    /** Test hook: currently active focus mode while paused. */
    FocusMode activeMode() { return available.isEmpty() ? null : available.get(activeIndex); }

    public void tick(InputHandler inputHandler) {
        // Implemented in later tasks.
    }
}
