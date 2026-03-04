package com.openggf.debug;

import com.openggf.graphics.GLCommand;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Context object for per-object debug rendering.
 * Objects populate this with geometry (GL lines) and text labels during the
 * geometry phase. LevelManager renders geometry immediately; text entries
 * are stored on DebugOverlayManager for DebugRenderer to pick up.
 */
public class DebugRenderContext {

    private final List<GLCommand> geometryCommands = new ArrayList<>();
    private final List<DebugTextEntry> textEntries = new ArrayList<>();

    // Shared StringBuilder for callers to build labels without per-frame allocation
    private final StringBuilder labelBuilder = new StringBuilder(64);

    /**
     * Clears all accumulated commands and text entries for reuse next frame.
     */
    public void clear() {
        geometryCommands.clear();
        textEntries.clear();
    }

    /**
     * Returns a shared StringBuilder, reset to empty. Callers can append label
     * text and pass the result to {@link #drawWorldLabel}. Avoids per-call
     * StringBuilder allocation. The returned builder is only valid until the
     * next call to this method.
     */
    public StringBuilder getLabelBuilder() {
        labelBuilder.setLength(0);
        return labelBuilder;
    }

    /**
     * Appends a 2-digit uppercase hex value (00-FF) to the given builder.
     */
    public static void appendHex2(StringBuilder sb, int value) {
        int hi = (value >> 4) & 0xF;
        int lo = value & 0xF;
        sb.append((char) (hi < 10 ? '0' + hi : 'A' + hi - 10));
        sb.append((char) (lo < 10 ? '0' + lo : 'A' + lo - 10));
    }

    /**
     * Appends a float with 2 decimal places without String.format.
     * Handles negative values. Range limited to reasonable debug values.
     */
    public static void appendFixed2(StringBuilder sb, float value) {
        if (value < 0) { sb.append('-'); value = -value; }
        long scaled = Math.round(value * 100.0);
        sb.append(scaled / 100).append('.');
        long frac = scaled % 100;
        if (frac < 10) sb.append('0');
        sb.append(frac);
    }

    /**
     * Appends a float with 1 decimal place without String.format.
     */
    public static void appendFixed1(StringBuilder sb, float value) {
        if (value < 0) { sb.append('-'); value = -value; }
        long scaled = Math.round(value * 10.0);
        sb.append(scaled / 10).append('.').append(scaled % 10);
    }

    /**
     * A text label positioned in world coordinates.
     */
    public record DebugTextEntry(int worldX, int worldY, int lineOffset, String text, Color color) {}

    // ---- Geometry helpers (world coordinates) ----

    /**
     * Draw a line segment between two world-coordinate points.
     */
    public void drawLine(int x1, int y1, int x2, int y2, float r, float g, float b) {
        geometryCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I,
                -1, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                r, g, b, x1, y1, 0, 0));
        geometryCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I,
                -1, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                r, g, b, x2, y2, 0, 0));
    }

    /**
     * Draw an axis-aligned rectangle outline centered at (cx, cy).
     */
    public void drawRect(int cx, int cy, int halfW, int halfH, float r, float g, float b) {
        int left = cx - halfW;
        int right = cx + halfW;
        int top = cy - halfH;
        int bottom = cy + halfH;
        drawLine(left, top, right, top, r, g, b);
        drawLine(right, top, right, bottom, r, g, b);
        drawLine(right, bottom, left, bottom, r, g, b);
        drawLine(left, bottom, left, top, r, g, b);
    }

    /**
     * Draw a small cross marker at (x, y).
     */
    public void drawCross(int x, int y, int size, float r, float g, float b) {
        drawLine(x - size, y, x + size, y, r, g, b);
        drawLine(x, y - size, x, y + size, r, g, b);
    }

    /**
     * Draw a line with a small arrowhead at the endpoint.
     */
    public void drawArrow(int x1, int y1, int x2, int y2, float r, float g, float b) {
        drawLine(x1, y1, x2, y2, r, g, b);
        // Arrowhead: two short lines from the endpoint
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 2) {
            return;
        }
        float nx = dx / len;
        float ny = dy / len;
        int headSize = 3;
        int ax = x2 - (int) (nx * headSize + ny * headSize);
        int ay = y2 - (int) (ny * headSize - nx * headSize);
        int bx = x2 - (int) (nx * headSize - ny * headSize);
        int by = y2 - (int) (ny * headSize + nx * headSize);
        drawLine(x2, y2, ax, ay, r, g, b);
        drawLine(x2, y2, bx, by, r, g, b);
    }

    /**
     * Add a text label at the given world position.
     *
     * @param worldX     world X coordinate
     * @param worldY     world Y coordinate
     * @param lineOffset vertical line offset (0 = at position, 1 = one line below, -1 = one line above)
     * @param text       label text
     * @param color      text color
     */
    public void drawWorldLabel(int worldX, int worldY, int lineOffset, String text, Color color) {
        textEntries.add(new DebugTextEntry(worldX, worldY, lineOffset, text, color));
    }

    // ---- Accessors ----

    public boolean hasGeometry() {
        return !geometryCommands.isEmpty();
    }

    public boolean hasText() {
        return !textEntries.isEmpty();
    }

    public List<GLCommand> getGeometryCommands() {
        return geometryCommands;
    }

    public List<DebugTextEntry> getTextEntries() {
        return textEntries;
    }
}
