package com.openggf.tests;

import org.junit.Test;
import com.openggf.control.InputHandler;

import static org.lwjgl.glfw.GLFW.*;
import static org.junit.Assert.*;

public class TestInputHandler {
    @Test
    public void testKeyPressRelease() {
        InputHandler handler = new InputHandler();
        // Simulate key press using GLFW key codes
        handler.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS);
        assertTrue(handler.isKeyDown(GLFW_KEY_A));
        // Simulate key release
        handler.handleKeyEvent(GLFW_KEY_A, GLFW_RELEASE);
        assertFalse(handler.isKeyDown(GLFW_KEY_A));
        assertFalse(handler.isKeyDown(999));
    }

    @Test
    public void testModifierHelpersRecognizeHeldModifiers() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_RIGHT_CONTROL, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);

        assertTrue(handler.isShiftDown());
        assertTrue(handler.isControlDown());
        assertTrue(handler.isAltDown());
        assertTrue(handler.isAnyModifierDown());
    }

    @Test
    public void testKeyPressedWithoutModifiersIsSuppressedByModifier() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertTrue(handler.isKeyPressed(GLFW_KEY_B));
        assertFalse(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
    }

    @Test
    public void testKeyPressedWithoutModifiersAllowsPlainKey() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertTrue(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
    }
}
