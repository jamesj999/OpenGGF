package uk.co.jamesj999.sonic.sprites.managers;

import org.junit.Test;
import uk.co.jamesj999.sonic.Control.InputHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

public class TestSpriteManagerDebugMovementModifiers {

    @Test
    public void speedUpModifierUsesEitherShiftKey() {
        InputHandler leftShift = new InputHandler();
        leftShift.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        assertTrue(SpriteManager.isDebugSpeedUpModifierDown(leftShift));

        InputHandler rightShift = new InputHandler();
        rightShift.handleKeyEvent(GLFW_KEY_RIGHT_SHIFT, GLFW_PRESS);
        assertTrue(SpriteManager.isDebugSpeedUpModifierDown(rightShift));
    }

    @Test
    public void slowDownModifierUsesEitherControlKey() {
        InputHandler leftCtrl = new InputHandler();
        leftCtrl.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        assertTrue(SpriteManager.isDebugSlowDownModifierDown(leftCtrl));

        InputHandler rightCtrl = new InputHandler();
        rightCtrl.handleKeyEvent(GLFW_KEY_RIGHT_CONTROL, GLFW_PRESS);
        assertTrue(SpriteManager.isDebugSlowDownModifierDown(rightCtrl));
    }

    @Test
    public void speedUpModifierIsFalseWithoutShift() {
        InputHandler handler = new InputHandler();
        assertFalse(SpriteManager.isDebugSpeedUpModifierDown(handler));
    }

    @Test
    public void slowDownModifierIsFalseWithoutControl() {
        InputHandler handler = new InputHandler();
        assertFalse(SpriteManager.isDebugSlowDownModifierDown(handler));
    }
}
