package com.openggf.testmode;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.testmode.TraceCameraFocusController.FocusMode;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.live.LiveTraceComparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceCameraFocusControllerTest {

    private LiveTraceComparator comparator;
    private SonicConfigurationService config;
    private InputHandler input;
    private Camera camera;
    private AtomicBoolean paused;
    private AtomicReference<AbstractPlayableSprite> mainSprite;
    private AtomicReference<AbstractPlayableSprite> sidekick;

    @BeforeEach
    void setup() {
        comparator = mock(LiveTraceComparator.class);
        config = mock(SonicConfigurationService.class);
        input = mock(InputHandler.class);
        camera = mock(Camera.class);
        paused = new AtomicBoolean(false);
        mainSprite = new AtomicReference<>(null);
        sidekick = new AtomicReference<>(null);

        when(config.getInt(SonicConfiguration.LEFT)).thenReturn(263);   // GLFW_KEY_LEFT
        when(config.getInt(SonicConfiguration.RIGHT)).thenReturn(262);  // GLFW_KEY_RIGHT
        when(config.getInt(SonicConfiguration.FRAME_STEP_KEY)).thenReturn(70); // some key
        when(camera.getX()).thenReturn((short) 100);
        when(camera.getY()).thenReturn((short) 200);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.getMinX()).thenReturn((short) 0);
        when(camera.getMaxX()).thenReturn((short) 10000);
        when(camera.getMinY()).thenReturn((short) 0);
        when(camera.getMaxY()).thenReturn((short) 10000);
    }

    private TraceCameraFocusController newController() {
        return new TraceCameraFocusController(
                comparator, mainSprite::get, sidekick::get,
                () -> camera, config, paused::get);
    }

    private static AbstractPlayableSprite spriteAt(int centreX, int centreY) {
        AbstractPlayableSprite s = mock(AbstractPlayableSprite.class);
        when(s.getCentreX()).thenReturn((short) centreX);
        when(s.getCentreY()).thenReturn((short) centreY);
        return s;
    }

    private static TraceFrame frameWith(int mainX, int mainY, TraceCharacterState sidekick) {
        return new TraceFrame(0, 0, (short) mainX, (short) mainY,
                (short) 0, (short) 0, (short) 0, (byte) 0, false, false, 0,
                0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, sidekick);
    }

    @Test
    void availableListIsDefaultOnlyWhenNoMainSpriteOrSidekick() {
        when(comparator.currentVisualFrame()).thenReturn(null);
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        assertEquals(1, controller.availableSize());
        assertEquals(FocusMode.DEFAULT, controller.activeMode());
    }

    @Test
    void availableListIncludesMainEngineWhenSpriteAlive() {
        mainSprite.set(spriteAt(500, 300));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(500, 300, null));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // Main engine added; trace position matches engine, so MAIN_TRACE skipped.
        assertEquals(2, controller.availableSize());
    }

    @Test
    void availableListIncludesMainTraceWhenPositionsDiffer() {
        mainSprite.set(spriteAt(500, 300));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(600, 300, null));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        assertEquals(3, controller.availableSize());
    }

    @Test
    void availableListIncludesSidekickEngineAndTraceWhenBothPresentAndDiffer() {
        mainSprite.set(spriteAt(500, 300));
        sidekick.set(spriteAt(450, 320));
        TraceCharacterState sk = new TraceCharacterState(true,
                (short) 470, (short) 320, (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0, 0, 0, -1, -1, -1);
        when(comparator.currentVisualFrame()).thenReturn(frameWith(500, 300, sk));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // DEFAULT, SIDEKICK_ENGINE, SIDEKICK_TRACE, MAIN_ENGINE; MAIN_TRACE skipped (matches).
        assertEquals(4, controller.availableSize());
    }

    @Test
    void availableListSkipsSidekickTraceWhenSidekickAbsentInTrace() {
        sidekick.set(spriteAt(450, 320));
        TraceCharacterState sk = TraceCharacterState.absent();
        when(comparator.currentVisualFrame()).thenReturn(frameWith(500, 300, sk));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // DEFAULT + SIDEKICK_ENGINE only.
        assertEquals(2, controller.availableSize());
    }

    @Test
    void availableListSkipsSidekickWhenEngineHasNoSidekick() {
        TraceCharacterState sk = new TraceCharacterState(true,
                (short) 470, (short) 320, (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0, 0, 0, -1, -1, -1);
        when(comparator.currentVisualFrame()).thenReturn(frameWith(500, 300, sk));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // DEFAULT only — no SIDEKICK_TRACE without SIDEKICK_ENGINE.
        assertEquals(1, controller.availableSize());
    }

    @Test
    void labelIsNullWhenNotPaused() {
        TraceCameraFocusController controller = newController();
        assertNull(controller.currentLabel());
    }
}
