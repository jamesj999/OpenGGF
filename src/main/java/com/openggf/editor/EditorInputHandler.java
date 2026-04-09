package com.openggf.editor;

import com.openggf.control.InputHandler;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;

public final class EditorInputHandler {
    public enum Action {
        DESCEND,
        ASCEND,
        CYCLE_FOCUS_REGION,
        APPLY_PRIMARY_ACTION,
        PERFORM_EYEDROP,
        UNDO,
        REDO
    }

    private static final int WORLD_MOVE_SPEED = 3;

    private final LevelEditorController controller;

    public EditorInputHandler(LevelEditorController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public void update(InputHandler inputHandler) {
        Objects.requireNonNull(inputHandler, "inputHandler");
        int dx = 0;
        int dy = 0;
        if (inputHandler.isKeyDown(GLFW_KEY_LEFT)) {
            dx -= 1;
        }
        if (inputHandler.isKeyDown(GLFW_KEY_RIGHT)) {
            dx += 1;
        }
        if (inputHandler.isKeyDown(GLFW_KEY_UP)) {
            dy -= 1;
        }
        if (inputHandler.isKeyDown(GLFW_KEY_DOWN)) {
            dy += 1;
        }
        if (dx != 0 || dy != 0) {
            if (controller.depth() == EditorHierarchyDepth.WORLD) {
                controller.moveWorldCursor(dx * WORLD_MOVE_SPEED, dy * WORLD_MOVE_SPEED);
            } else {
                controller.moveActiveSelection(dx, dy);
            }
        }
        boolean shiftDown = inputHandler.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                || inputHandler.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
        if (inputHandler.isKeyPressed(GLFW_KEY_TAB) && !shiftDown) {
            handleAction(Action.CYCLE_FOCUS_REGION);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_SPACE)) {
            handleAction(Action.APPLY_PRIMARY_ACTION);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_E)) {
            handleAction(Action.PERFORM_EYEDROP);
        }
        boolean controlDown = inputHandler.isKeyDown(GLFW_KEY_LEFT_CONTROL)
                || inputHandler.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_Z)) {
            handleAction(Action.UNDO);
        }
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_Y)) {
            handleAction(Action.REDO);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_ENTER)) {
            handleAction(Action.DESCEND);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_ESCAPE)) {
            handleAction(Action.ASCEND);
        }
    }

    public void handleAction(Action action) {
        Objects.requireNonNull(action, "action");
        switch (action) {
            case DESCEND -> controller.descend();
            case ASCEND -> controller.ascend();
            case CYCLE_FOCUS_REGION -> controller.cycleFocusRegion();
            case APPLY_PRIMARY_ACTION -> controller.applyPrimaryAction();
            case PERFORM_EYEDROP -> controller.performEyedrop();
            case UNDO -> controller.undo();
            case REDO -> controller.redo();
        }
    }
}
