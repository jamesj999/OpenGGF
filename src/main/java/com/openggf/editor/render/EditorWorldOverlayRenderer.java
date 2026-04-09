package com.openggf.editor.render;

import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.GraphicsManager;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINES;

public class EditorWorldOverlayRenderer {
    private static final float CURSOR_R = 1.0f;
    private static final float CURSOR_G = 0.92f;
    private static final float CURSOR_B = 0.30f;

    public void render() {
        EditorModeContext editorMode = SessionManager.getCurrentEditorMode();
        if (editorMode == null) {
            return;
        }
        List<GLCommand> commands = new ArrayList<>();
        appendCursorCommands(commands, editorMode.getCursor());
        if (!commands.isEmpty()) {
            GraphicsManager.getInstance().registerCommand(new GLCommandGroup(GL_LINES, commands));
        }
    }

    protected void appendCursorCommands(List<GLCommand> commands, EditorCursorState cursor) {
        int x = cursor.x();
        int y = cursor.y();
        int outer = 16;
        int inner = 6;

        EditorToolbarRenderer.appendRectOutline(commands,
                x - outer, y - outer, x + outer, y + outer,
                CURSOR_R, CURSOR_G, CURSOR_B);
        EditorToolbarRenderer.appendLine(commands, x - outer, y, x - inner, y,
                CURSOR_R, CURSOR_G, CURSOR_B);
        EditorToolbarRenderer.appendLine(commands, x + inner, y, x + outer, y,
                CURSOR_R, CURSOR_G, CURSOR_B);
        EditorToolbarRenderer.appendLine(commands, x, y - outer, x, y - inner,
                CURSOR_R, CURSOR_G, CURSOR_B);
        EditorToolbarRenderer.appendLine(commands, x, y + inner, x, y + outer,
                CURSOR_R, CURSOR_G, CURSOR_B);
    }
}
