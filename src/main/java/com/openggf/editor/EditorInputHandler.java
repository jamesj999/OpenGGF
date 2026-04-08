package com.openggf.editor;

import com.openggf.control.InputHandler;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public final class EditorInputHandler {
    public enum Action {
        DESCEND,
        ASCEND,
        APPLY,
        EYEDROP,
        SWITCH_REGION
    }

    private final LevelEditorController controller;

    public EditorInputHandler(LevelEditorController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public void update(InputHandler inputHandler) {
        Objects.requireNonNull(inputHandler, "inputHandler");
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
            default -> throw new UnsupportedOperationException("Action " + action + " is not implemented yet");
        }
    }
}
