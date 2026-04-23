package com.openggf.testmode;

import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.trace.catalog.TraceEntry;

import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

/**
 * Trace picker screen shown when TEST_MODE_ENABLED is true. Owned by
 * MasterTitleScreen; it substitutes this screen's update/render for the
 * normal game-selection ACTIVE behaviour.
 */
public final class TestModeTracePicker {

    public enum Result { NONE, LAUNCH, BACK }

    private final List<TraceEntry> entries;
    private final PixelFont font;
    private int cursor;
    private Result pendingResult = Result.NONE;

    public TestModeTracePicker(List<TraceEntry> entries, PixelFont font) {
        this.entries = entries;
        this.font = font;
    }

    public void update(InputHandler input) {
        if (entries.isEmpty()) {
            if (input.isKeyPressedWithoutModifiers(GLFW_KEY_ESCAPE)) {
                pendingResult = Result.BACK;
            }
            return;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_DOWN)) {
            cursor = Math.min(entries.size() - 1, cursor + 1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_UP)) {
            cursor = Math.max(0, cursor - 1);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_HOME)) {
            cursor = 0;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_END)) {
            cursor = entries.size() - 1;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_DOWN)) {
            cursor = nextGroupStart(cursor);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_PAGE_UP)) {
            cursor = prevGroupStart(cursor);
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_ENTER)) {
            pendingResult = Result.LAUNCH;
        }
        if (input.isKeyPressedWithoutModifiers(GLFW_KEY_ESCAPE)) {
            pendingResult = Result.BACK;
        }
    }

    public void render() {
        font.drawTextCentered("TRACE TEST MODE", 320, 12, 1f, 1f, 1f, 1f);
        int y = 32;
        String lastGame = null;
        for (int i = 0; i < entries.size(); i++) {
            TraceEntry e = entries.get(i);
            if (!e.gameId().equals(lastGame)) {
                if (lastGame != null) y += 6;
                font.drawText(gameHeading(e.gameId()), 20, y, 1f, 1f, 0.6f, 1f);
                y += 14;
                lastGame = e.gameId();
            }
            boolean selected = (i == cursor);
            float brightness = selected ? 1.0f : 0.7f;
            String prefix = selected ? ">" : " ";
            String line = prefix + " " + e.dir().getFileName();
            font.drawText(line, 24, y, brightness, brightness, brightness, 1f);
            y += 11;
        }
        if (cursor < entries.size()) {
            renderInfoPanel(entries.get(cursor));
        }
    }

    private void renderInfoPanel(TraceEntry e) {
        int y = 180;
        font.drawText("SELECTED: " + e.gameId() + "/" + e.dir().getFileName(),
                8, y, 1f, 1f, 1f, 1f);
        y += 12;
        font.drawText(String.format("Zone: %02X  Act: %d   Frames: %d   BK2 offset: %d",
                        e.zone(), e.act(), e.frameCount(), e.bk2StartOffset()),
                8, y, 0.9f, 0.9f, 0.9f, 1f);
        y += 11;
        font.drawText("Team: " + formatTeam(e) + "   Pre-osc: " + e.preTraceOscFrames(),
                8, y, 0.9f, 0.9f, 0.9f, 1f);
        y += 11;
        font.drawText("BK2: " + e.bk2Path().getFileName(),
                8, y, 0.7f, 0.7f, 0.7f, 1f);
    }

    private static String gameHeading(String gameId) {
        return switch (gameId) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3&K";
            default -> gameId.toUpperCase();
        };
    }

    private static String formatTeam(TraceEntry e) {
        StringBuilder sb = new StringBuilder(e.team().mainCharacter());
        for (String sk : e.team().sidekicks()) {
            sb.append('+').append(sk);
        }
        return sb.toString();
    }

    private int nextGroupStart(int from) {
        String current = entries.get(from).gameId();
        for (int i = from + 1; i < entries.size(); i++) {
            if (!entries.get(i).gameId().equals(current)) {
                return i;
            }
        }
        return from;
    }

    private int prevGroupStart(int from) {
        String current = entries.get(from).gameId();
        int found = -1;
        for (int i = 0; i < from; i++) {
            if (!entries.get(i).gameId().equals(current) && found == -1) {
                found = i;
            }
        }
        return found >= 0 ? found : 0;
    }

    public Result consumeResult() {
        Result r = pendingResult;
        pendingResult = Result.NONE;
        return r;
    }

    public TraceEntry selectedEntry() {
        return cursor < entries.size() ? entries.get(cursor) : null;
    }
}
