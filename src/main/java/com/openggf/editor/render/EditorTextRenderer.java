package com.openggf.editor.render;

import com.openggf.debug.DebugColor;
import com.openggf.debug.FontSize;
import com.openggf.debug.GlyphBatchRenderer;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EditorTextRenderer {
    public record TextCommand(String text, int x, int y, int lineHeight, DebugColor color, FontSize fontSize) {}

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
        List<TextCommand> commands = buildTextCommands(lines, x, y);
        if (!commands.isEmpty()) {
            com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics().registerCommand(buildTextBatchCommand(commands));
        }
    }

    protected List<TextCommand> buildTextCommands(List<String> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<TextCommand> commands = new ArrayList<>();
        int lineY = y;
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                commands.add(new TextCommand(line, x, lineY, DEFAULT_LINE_HEIGHT, DEFAULT_COLOR, DEFAULT_FONT_SIZE));
            }
            lineY += DEFAULT_LINE_HEIGHT;
        }
        return commands;
    }

    protected GLCommandable buildTextBatchCommand(List<TextCommand> commands) {
        return new TextBatchCommand(List.copyOf(commands));
    }

    protected int topLeftToGlyphY(int viewportHeight, int y, int lineHeight) {
        return viewportHeight - y - lineHeight;
    }

    private void renderTextCommands(List<TextCommand> commands, int viewportWidth, int viewportHeight) {
        if (commands.isEmpty() || !ensureInitialized()) {
            return;
        }

        glyphBatch.updateViewport(viewportWidth, viewportHeight);
        glyphBatch.begin();
        for (TextCommand command : commands) {
            glyphBatch.drawTextOutlined(command.text(), command.x(),
                    topLeftToGlyphY(viewportHeight, command.y(), command.lineHeight()),
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

    private final class TextBatchCommand implements GLCommandable {
        private final List<TextCommand> commands;

        private TextBatchCommand(List<TextCommand> commands) {
            this.commands = commands;
        }

        @Override
        public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            renderTextCommands(commands, cameraWidth, cameraHeight);
        }
    }
}
