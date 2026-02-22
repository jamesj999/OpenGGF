package com.openggf.tests;

import org.junit.Test;
import com.openggf.Control.InputHandler;

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
}
