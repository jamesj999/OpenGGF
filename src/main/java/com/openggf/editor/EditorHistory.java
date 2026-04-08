package com.openggf.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class EditorHistory {
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();

    public void execute(EditorCommand command) {
        EditorCommand nonNullCommand = Objects.requireNonNull(command, "command");
        nonNullCommand.apply();
        undoStack.push(nonNullCommand);
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }

        EditorCommand command = undoStack.peek();
        command.undo();
        undoStack.pop();
        redoStack.push(command);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }

        EditorCommand command = redoStack.peek();
        command.apply();
        redoStack.pop();
        undoStack.push(command);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
