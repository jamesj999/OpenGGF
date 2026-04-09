package com.openggf.editor;

import com.openggf.control.InputHandler;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

public final class EditorInputHandler {
    public enum Action {
        DESCEND,
        ASCEND
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
        }
    }
}
