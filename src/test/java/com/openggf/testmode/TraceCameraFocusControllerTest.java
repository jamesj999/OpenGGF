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

    @Test
    void unpauseRestoresCameraToSavedPosition() {
        when(comparator.currentVisualFrame()).thenReturn(null);
        TraceCameraFocusController controller = newController();

        paused.set(true);
        when(camera.getX()).thenReturn((short) 1234);
        when(camera.getY()).thenReturn((short) 567);
        controller.tick(input);  // pause-edge enter, snapshots 1234/567

        paused.set(false);
        controller.tick(input);  // pause-edge exit, must restore

        org.mockito.Mockito.verify(camera).setX((short) 1234);
        org.mockito.Mockito.verify(camera).setY((short) 567);
    }

    @Test
    void labelClearedAfterUnpause() {
        when(comparator.currentVisualFrame()).thenReturn(null);
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        assertEquals("Default", controller.currentLabel());
        paused.set(false);
        controller.tick(input);
        assertNull(controller.currentLabel());
    }

    @Test
    void rightArrowAdvancesFocusAndAppliesCameraCentredOnTarget() {
        mainSprite.set(spriteAt(1000, 500));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(1000, 500, null));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // Available: DEFAULT, MAIN_ENGINE.

        when(input.isKeyPressed(262)).thenReturn(true);  // RIGHT
        controller.tick(input);
        when(input.isKeyPressed(262)).thenReturn(false);

        assertEquals(FocusMode.MAIN_ENGINE, controller.activeMode());
        // Centred: camX = 1000 - 320/2 = 840; camY = 500 - 224/2 = 388.
        org.mockito.Mockito.verify(camera).setX((short) 840);
        org.mockito.Mockito.verify(camera).setY((short) 388);
    }

    @Test
    void leftArrowFromDefaultWrapsToLastAvailable() {
        mainSprite.set(spriteAt(1000, 500));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(2000, 600, null));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // Available: DEFAULT, MAIN_ENGINE, MAIN_TRACE (3 items).

        when(input.isKeyPressed(263)).thenReturn(true);  // LEFT
        controller.tick(input);
        when(input.isKeyPressed(263)).thenReturn(false);

        assertEquals(FocusMode.MAIN_TRACE, controller.activeMode());
        // MAIN_TRACE centres on (2000, 600): camX = 2000-160 = 1840, camY = 600-112 = 488.
        org.mockito.Mockito.verify(camera).setX((short) 1840);
        org.mockito.Mockito.verify(camera).setY((short) 488);
    }

    @Test
    void cycleClampsToCameraBounds() {
        mainSprite.set(spriteAt(50, 50));  // near top-left of level
        when(comparator.currentVisualFrame()).thenReturn(frameWith(50, 50, null));
        when(camera.getMinX()).thenReturn((short) 0);
        when(camera.getMaxX()).thenReturn((short) 10000);
        when(camera.getMinY()).thenReturn((short) 0);
        when(camera.getMaxY()).thenReturn((short) 10000);
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);

        when(input.isKeyPressed(262)).thenReturn(true);  // RIGHT to MAIN_ENGINE
        controller.tick(input);

        // 50 - 160 = -110, clamped to 0.
        org.mockito.Mockito.verify(camera).setX((short) 0);
        org.mockito.Mockito.verify(camera).setY((short) 0);
    }

    @Test
    void frameStepRestoresCameraBeforeStepAndReappliesFocusAfter() {
        mainSprite.set(spriteAt(1000, 500));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(1000, 500, null));
        TraceCameraFocusController controller = newController();

        when(camera.getX()).thenReturn((short) 100);
        when(camera.getY()).thenReturn((short) 200);
        paused.set(true);
        controller.tick(input);  // pause-edge enter

        when(input.isKeyPressed(262)).thenReturn(true);
        controller.tick(input);  // RIGHT -> MAIN_ENGINE
        when(input.isKeyPressed(262)).thenReturn(false);
        org.mockito.Mockito.clearInvocations(camera);

        // Frame-step pressed: paused stays true, controller should restore savedCam,
        // remember the focus, and let the step happen.
        when(input.isKeyPressed(70)).thenReturn(true);  // FRAME_STEP_KEY
        controller.tick(input);
        when(input.isKeyPressed(70)).thenReturn(false);

        org.mockito.Mockito.verify(camera).setX((short) 100);
        org.mockito.Mockito.verify(camera).setY((short) 200);
        org.mockito.Mockito.clearInvocations(camera);

        // Next tick after step: re-apply MAIN_ENGINE focus.
        controller.tick(input);
        org.mockito.Mockito.verify(camera).setX((short) 840);
        org.mockito.Mockito.verify(camera).setY((short) 388);
        assertEquals(FocusMode.MAIN_ENGINE, controller.activeMode());
    }

    @Test
    void frameStepFallsBackToDefaultWhenPreviousFocusGone() {
        mainSprite.set(spriteAt(1000, 500));
        sidekick.set(spriteAt(950, 500));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(1000, 500, null));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        // Available: DEFAULT, SIDEKICK_ENGINE, MAIN_ENGINE.

        // Move to SIDEKICK_ENGINE.
        when(input.isKeyPressed(262)).thenReturn(true);
        controller.tick(input);
        when(input.isKeyPressed(262)).thenReturn(false);
        assertEquals(FocusMode.SIDEKICK_ENGINE, controller.activeMode());

        // Frame-step: sidekick despawns mid-step.
        when(input.isKeyPressed(70)).thenReturn(true);
        controller.tick(input);
        when(input.isKeyPressed(70)).thenReturn(false);
        sidekick.set(null);

        // Next tick rebuilds available list; SIDEKICK_ENGINE is gone -> fall back to DEFAULT.
        controller.tick(input);
        assertEquals(FocusMode.DEFAULT, controller.activeMode());
    }

    @Test
    void controllerOnlyMutatesSetXAndSetYOnCamera() {
        mainSprite.set(spriteAt(1000, 500));
        when(comparator.currentVisualFrame()).thenReturn(frameWith(2000, 600, null));
        TraceCameraFocusController controller = newController();
        paused.set(true);
        controller.tick(input);
        when(input.isKeyPressed(262)).thenReturn(true);
        controller.tick(input);
        when(input.isKeyPressed(262)).thenReturn(false);
        controller.tick(input);
        paused.set(false);
        controller.tick(input);

        // updatePosition must NEVER be called.
        org.mockito.Mockito.verify(camera, org.mockito.Mockito.never()).updatePosition();
        org.mockito.Mockito.verify(camera, org.mockito.Mockito.never()).updatePosition(
                org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verify(camera, org.mockito.Mockito.never()).setShakeOffsets(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
