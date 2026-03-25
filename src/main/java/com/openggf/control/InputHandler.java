package com.openggf.control;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles keyboard input from GLFW.
 * Key codes are GLFW key codes (GLFW_KEY_*).
 */
public class InputHandler {
	// GLFW key codes can range from 0 to GLFW_KEY_LAST (348)
	private static final int MAX_KEYS = 512;
	boolean[] keys = new boolean[MAX_KEYS];
	boolean[] previousKeys = new boolean[MAX_KEYS];

	/**
	 * Creates a new InputHandler.
	 * Key events should be delivered via handleKeyEvent() from GLFW callback.
	 */
	public InputHandler() {
	}

	/**
	 * Handle a key event from GLFW.
	 *
	 * @param key    The GLFW key code
	 * @param action GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT
	 */
	public void handleKeyEvent(int key, int action) {
		if (key >= 0 && key < MAX_KEYS) {
			if (action == GLFW_PRESS || action == GLFW_REPEAT) {
				keys[key] = true;
			} else if (action == GLFW_RELEASE) {
				keys[key] = false;
			}
		}
	}

	/**
	 * Checks whether a specific key is down.
	 *
	 * @param keyCode The GLFW key code to check
	 * @return Whether the key is pressed or not
	 */
	public boolean isKeyDown(int keyCode) {
		if (keyCode >= 0 && keyCode < MAX_KEYS) {
			return keys[keyCode];
		}
		return false;
	}

	/**
	 * Checks whether a specific key was just pressed this frame.
	 *
	 * @param keyCode The GLFW key code to check
	 * @return Whether the key was just pressed
	 */
	public boolean isKeyPressed(int keyCode) {
		if (keyCode >= 0 && keyCode < MAX_KEYS) {
			return keys[keyCode] && !previousKeys[keyCode];
		}
		return false;
	}

	/**
	 * Updates the input handler state. Should be called at the end of the game loop.
	 */
	public void update() {
		System.arraycopy(keys, 0, previousKeys, 0, MAX_KEYS);
	}

	// GLFW to AWT key code conversion for backwards compatibility
	// This allows existing code that uses AWT KeyEvent codes to still work
	// during the migration period.

	/**
	 * Convert GLFW key code to AWT KeyEvent key code.
	 * This is for backwards compatibility with existing code.
	 */
	public static int glfwToAwt(int glfwKey) {
		return switch (glfwKey) {
			case GLFW_KEY_A -> java.awt.event.KeyEvent.VK_A;
			case GLFW_KEY_B -> java.awt.event.KeyEvent.VK_B;
			case GLFW_KEY_C -> java.awt.event.KeyEvent.VK_C;
			case GLFW_KEY_D -> java.awt.event.KeyEvent.VK_D;
			case GLFW_KEY_E -> java.awt.event.KeyEvent.VK_E;
			case GLFW_KEY_F -> java.awt.event.KeyEvent.VK_F;
			case GLFW_KEY_G -> java.awt.event.KeyEvent.VK_G;
			case GLFW_KEY_H -> java.awt.event.KeyEvent.VK_H;
			case GLFW_KEY_I -> java.awt.event.KeyEvent.VK_I;
			case GLFW_KEY_J -> java.awt.event.KeyEvent.VK_J;
			case GLFW_KEY_K -> java.awt.event.KeyEvent.VK_K;
			case GLFW_KEY_L -> java.awt.event.KeyEvent.VK_L;
			case GLFW_KEY_M -> java.awt.event.KeyEvent.VK_M;
			case GLFW_KEY_N -> java.awt.event.KeyEvent.VK_N;
			case GLFW_KEY_O -> java.awt.event.KeyEvent.VK_O;
			case GLFW_KEY_P -> java.awt.event.KeyEvent.VK_P;
			case GLFW_KEY_Q -> java.awt.event.KeyEvent.VK_Q;
			case GLFW_KEY_R -> java.awt.event.KeyEvent.VK_R;
			case GLFW_KEY_S -> java.awt.event.KeyEvent.VK_S;
			case GLFW_KEY_T -> java.awt.event.KeyEvent.VK_T;
			case GLFW_KEY_U -> java.awt.event.KeyEvent.VK_U;
			case GLFW_KEY_V -> java.awt.event.KeyEvent.VK_V;
			case GLFW_KEY_W -> java.awt.event.KeyEvent.VK_W;
			case GLFW_KEY_X -> java.awt.event.KeyEvent.VK_X;
			case GLFW_KEY_Y -> java.awt.event.KeyEvent.VK_Y;
			case GLFW_KEY_Z -> java.awt.event.KeyEvent.VK_Z;
			case GLFW_KEY_0 -> java.awt.event.KeyEvent.VK_0;
			case GLFW_KEY_1 -> java.awt.event.KeyEvent.VK_1;
			case GLFW_KEY_2 -> java.awt.event.KeyEvent.VK_2;
			case GLFW_KEY_3 -> java.awt.event.KeyEvent.VK_3;
			case GLFW_KEY_4 -> java.awt.event.KeyEvent.VK_4;
			case GLFW_KEY_5 -> java.awt.event.KeyEvent.VK_5;
			case GLFW_KEY_6 -> java.awt.event.KeyEvent.VK_6;
			case GLFW_KEY_7 -> java.awt.event.KeyEvent.VK_7;
			case GLFW_KEY_8 -> java.awt.event.KeyEvent.VK_8;
			case GLFW_KEY_9 -> java.awt.event.KeyEvent.VK_9;
			case GLFW_KEY_SPACE -> java.awt.event.KeyEvent.VK_SPACE;
			case GLFW_KEY_ENTER -> java.awt.event.KeyEvent.VK_ENTER;
			case GLFW_KEY_ESCAPE -> java.awt.event.KeyEvent.VK_ESCAPE;
			case GLFW_KEY_TAB -> java.awt.event.KeyEvent.VK_TAB;
			case GLFW_KEY_BACKSPACE -> java.awt.event.KeyEvent.VK_BACK_SPACE;
			case GLFW_KEY_INSERT -> java.awt.event.KeyEvent.VK_INSERT;
			case GLFW_KEY_DELETE -> java.awt.event.KeyEvent.VK_DELETE;
			case GLFW_KEY_RIGHT -> java.awt.event.KeyEvent.VK_RIGHT;
			case GLFW_KEY_LEFT -> java.awt.event.KeyEvent.VK_LEFT;
			case GLFW_KEY_DOWN -> java.awt.event.KeyEvent.VK_DOWN;
			case GLFW_KEY_UP -> java.awt.event.KeyEvent.VK_UP;
			case GLFW_KEY_PAGE_UP -> java.awt.event.KeyEvent.VK_PAGE_UP;
			case GLFW_KEY_PAGE_DOWN -> java.awt.event.KeyEvent.VK_PAGE_DOWN;
			case GLFW_KEY_HOME -> java.awt.event.KeyEvent.VK_HOME;
			case GLFW_KEY_END -> java.awt.event.KeyEvent.VK_END;
			case GLFW_KEY_F1 -> java.awt.event.KeyEvent.VK_F1;
			case GLFW_KEY_F2 -> java.awt.event.KeyEvent.VK_F2;
			case GLFW_KEY_F3 -> java.awt.event.KeyEvent.VK_F3;
			case GLFW_KEY_F4 -> java.awt.event.KeyEvent.VK_F4;
			case GLFW_KEY_F5 -> java.awt.event.KeyEvent.VK_F5;
			case GLFW_KEY_F6 -> java.awt.event.KeyEvent.VK_F6;
			case GLFW_KEY_F7 -> java.awt.event.KeyEvent.VK_F7;
			case GLFW_KEY_F8 -> java.awt.event.KeyEvent.VK_F8;
			case GLFW_KEY_F9 -> java.awt.event.KeyEvent.VK_F9;
			case GLFW_KEY_F10 -> java.awt.event.KeyEvent.VK_F10;
			case GLFW_KEY_F11 -> java.awt.event.KeyEvent.VK_F11;
			case GLFW_KEY_F12 -> java.awt.event.KeyEvent.VK_F12;
			case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> java.awt.event.KeyEvent.VK_SHIFT;
			case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> java.awt.event.KeyEvent.VK_CONTROL;
			case GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> java.awt.event.KeyEvent.VK_ALT;
			default -> glfwKey;
		};
	}

	/**
	 * Convert AWT KeyEvent key code to GLFW key code.
	 */
	public static int awtToGlfw(int awtKey) {
		return switch (awtKey) {
			case java.awt.event.KeyEvent.VK_A -> GLFW_KEY_A;
			case java.awt.event.KeyEvent.VK_B -> GLFW_KEY_B;
			case java.awt.event.KeyEvent.VK_C -> GLFW_KEY_C;
			case java.awt.event.KeyEvent.VK_D -> GLFW_KEY_D;
			case java.awt.event.KeyEvent.VK_E -> GLFW_KEY_E;
			case java.awt.event.KeyEvent.VK_F -> GLFW_KEY_F;
			case java.awt.event.KeyEvent.VK_G -> GLFW_KEY_G;
			case java.awt.event.KeyEvent.VK_H -> GLFW_KEY_H;
			case java.awt.event.KeyEvent.VK_I -> GLFW_KEY_I;
			case java.awt.event.KeyEvent.VK_J -> GLFW_KEY_J;
			case java.awt.event.KeyEvent.VK_K -> GLFW_KEY_K;
			case java.awt.event.KeyEvent.VK_L -> GLFW_KEY_L;
			case java.awt.event.KeyEvent.VK_M -> GLFW_KEY_M;
			case java.awt.event.KeyEvent.VK_N -> GLFW_KEY_N;
			case java.awt.event.KeyEvent.VK_O -> GLFW_KEY_O;
			case java.awt.event.KeyEvent.VK_P -> GLFW_KEY_P;
			case java.awt.event.KeyEvent.VK_Q -> GLFW_KEY_Q;
			case java.awt.event.KeyEvent.VK_R -> GLFW_KEY_R;
			case java.awt.event.KeyEvent.VK_S -> GLFW_KEY_S;
			case java.awt.event.KeyEvent.VK_T -> GLFW_KEY_T;
			case java.awt.event.KeyEvent.VK_U -> GLFW_KEY_U;
			case java.awt.event.KeyEvent.VK_V -> GLFW_KEY_V;
			case java.awt.event.KeyEvent.VK_W -> GLFW_KEY_W;
			case java.awt.event.KeyEvent.VK_X -> GLFW_KEY_X;
			case java.awt.event.KeyEvent.VK_Y -> GLFW_KEY_Y;
			case java.awt.event.KeyEvent.VK_Z -> GLFW_KEY_Z;
			case java.awt.event.KeyEvent.VK_0 -> GLFW_KEY_0;
			case java.awt.event.KeyEvent.VK_1 -> GLFW_KEY_1;
			case java.awt.event.KeyEvent.VK_2 -> GLFW_KEY_2;
			case java.awt.event.KeyEvent.VK_3 -> GLFW_KEY_3;
			case java.awt.event.KeyEvent.VK_4 -> GLFW_KEY_4;
			case java.awt.event.KeyEvent.VK_5 -> GLFW_KEY_5;
			case java.awt.event.KeyEvent.VK_6 -> GLFW_KEY_6;
			case java.awt.event.KeyEvent.VK_7 -> GLFW_KEY_7;
			case java.awt.event.KeyEvent.VK_8 -> GLFW_KEY_8;
			case java.awt.event.KeyEvent.VK_9 -> GLFW_KEY_9;
			case java.awt.event.KeyEvent.VK_SPACE -> GLFW_KEY_SPACE;
			case java.awt.event.KeyEvent.VK_ENTER -> GLFW_KEY_ENTER;
			case java.awt.event.KeyEvent.VK_ESCAPE -> GLFW_KEY_ESCAPE;
			case java.awt.event.KeyEvent.VK_TAB -> GLFW_KEY_TAB;
			case java.awt.event.KeyEvent.VK_BACK_SPACE -> GLFW_KEY_BACKSPACE;
			case java.awt.event.KeyEvent.VK_INSERT -> GLFW_KEY_INSERT;
			case java.awt.event.KeyEvent.VK_DELETE -> GLFW_KEY_DELETE;
			case java.awt.event.KeyEvent.VK_RIGHT -> GLFW_KEY_RIGHT;
			case java.awt.event.KeyEvent.VK_LEFT -> GLFW_KEY_LEFT;
			case java.awt.event.KeyEvent.VK_DOWN -> GLFW_KEY_DOWN;
			case java.awt.event.KeyEvent.VK_UP -> GLFW_KEY_UP;
			case java.awt.event.KeyEvent.VK_PAGE_UP -> GLFW_KEY_PAGE_UP;
			case java.awt.event.KeyEvent.VK_PAGE_DOWN -> GLFW_KEY_PAGE_DOWN;
			case java.awt.event.KeyEvent.VK_HOME -> GLFW_KEY_HOME;
			case java.awt.event.KeyEvent.VK_END -> GLFW_KEY_END;
			case java.awt.event.KeyEvent.VK_F1 -> GLFW_KEY_F1;
			case java.awt.event.KeyEvent.VK_F2 -> GLFW_KEY_F2;
			case java.awt.event.KeyEvent.VK_F3 -> GLFW_KEY_F3;
			case java.awt.event.KeyEvent.VK_F4 -> GLFW_KEY_F4;
			case java.awt.event.KeyEvent.VK_F5 -> GLFW_KEY_F5;
			case java.awt.event.KeyEvent.VK_F6 -> GLFW_KEY_F6;
			case java.awt.event.KeyEvent.VK_F7 -> GLFW_KEY_F7;
			case java.awt.event.KeyEvent.VK_F8 -> GLFW_KEY_F8;
			case java.awt.event.KeyEvent.VK_F9 -> GLFW_KEY_F9;
			case java.awt.event.KeyEvent.VK_F10 -> GLFW_KEY_F10;
			case java.awt.event.KeyEvent.VK_F11 -> GLFW_KEY_F11;
			case java.awt.event.KeyEvent.VK_F12 -> GLFW_KEY_F12;
			case java.awt.event.KeyEvent.VK_SHIFT -> GLFW_KEY_LEFT_SHIFT;
			case java.awt.event.KeyEvent.VK_CONTROL -> GLFW_KEY_LEFT_CONTROL;
			case java.awt.event.KeyEvent.VK_ALT -> GLFW_KEY_LEFT_ALT;
			default -> awtKey;
		};
	}
}
