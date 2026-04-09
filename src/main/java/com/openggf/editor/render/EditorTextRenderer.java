package com.openggf.editor.render;

import com.openggf.debug.DebugColor;
import com.openggf.debug.FontSize;
import com.openggf.debug.GlyphBatchRenderer;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EditorTextRenderer {
    public record TextCommand(String text, int x, int y, DebugColor color, FontSize fontSize) {}

    private static final int DEFAULT_LINE_HEIGHT = 10;
    private static final DebugColor DEFAULT_COLOR = DebugColor.WHITE;
    private static final FontSize DEFAULT_FONT_SIZE = FontSize.SMALL;

    private final GlyphBatchRenderer glyphBatch;
    private boolean initializationAttempted;

    public EditorTextRenderer() {
        this(new GlyphBatchRenderer());
    }

    public EditorTextRenderer(GlyphBatchRenderer glyphBatch) {
        this.glyphBatch = Objects.requireNonNull(glyphBatch, "glyphBatch");
    }

    public void renderLines(List<String> lines, int x, int y) {
        renderTextCommands(buildTextCommands(lines, x, y));
    }

    protected List<TextCommand> buildTextCommands(List<String> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<TextCommand> commands = new ArrayList<>();
        int lineY = y;
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                commands.add(new TextCommand(line, x, lineY, DEFAULT_COLOR, DEFAULT_FONT_SIZE));
            }
            lineY += DEFAULT_LINE_HEIGHT;
        }
        return commands;
    }

    protected void renderTextCommands(List<TextCommand> commands) {
        if (commands.isEmpty() || !ensureInitialized()) {
            return;
        }

        glyphBatch.begin();
        for (TextCommand command : commands) {
            glyphBatch.drawTextOutlined(command.text(), command.x(), command.y(),
                    command.color(), command.fontSize());
        }
        glyphBatch.end();
    }

    private boolean ensureInitialized() {
        if (glyphBatch.isInitialized()) {
            return true;
        }
        if (initializationAttempted) {
            return false;
        }

        initializationAttempted = true;
        try {
            glyphBatch.init(new Font(Font.MONOSPACED, Font.PLAIN, 8));
        } catch (RuntimeException | Error ignored) {
            return false;
        }
        return glyphBatch.isInitialized();
    }
}
