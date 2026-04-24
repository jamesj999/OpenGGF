package com.openggf.testmode;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.Severity;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;

import java.util.List;

/**
 * Bottom-right HUD painted each frame while a trace session is active.
 * Red ERRORS, orange WARN, grey LAG counters; BK2 input visualiser
 * (A/B/C/U/D/L/R/S); and the last five mismatch entries, severity
 * coloured, with repeat counts.
 */
public final class TraceHudOverlay {

    private final LiveTraceComparator comparator;

    public TraceHudOverlay(LiveTraceComparator comparator) {
        this.comparator = comparator;
    }

    private static final float SCALE = 0.5f;
    private static final int LINE_HEIGHT = 6;
    private static final int SECTION_GAP = 8;

    // Lower-left corner, above the lives counter at y~208. Leaves the
    // top-bar HUD (rings/time/score) and right-side game HUD clear.
    private static final int X = 4;
    private static final int COMPLETE_BANNER_Y = 110;
    private static final int TOP_Y = 120;

    public void render(PixelFontTextRenderer text) {
        int y = TOP_Y;
        text.drawShadowedText(String.format("ERRORS %4d", comparator.errorCount()),
                X, y, DebugColor.RED, SCALE);
        y += LINE_HEIGHT;
        text.drawShadowedText(String.format("WARN   %4d", comparator.warningCount()),
                X, y, DebugColor.ORANGE, SCALE);
        y += LINE_HEIGHT;
        text.drawShadowedText(String.format("LAG    %4d", comparator.laggedFrames()),
                X, y, DebugColor.GRAY, SCALE);
        y += SECTION_GAP;

        int actionMask = comparator.recentActionMask();
        int inputMask = comparator.recentInputMask();
        boolean start = comparator.recentStartPressed();
        StringBuilder active = new StringBuilder();
        active.append(bit(actionMask, 0x01, 'A'));
        active.append(bit(actionMask, 0x02, 'B'));
        active.append(bit(actionMask, 0x04, 'C'));
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_UP, 'U'));
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_DOWN, 'D'));
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_LEFT, 'L'));
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_RIGHT, 'R'));
        active.append(start ? 'S' : '.');
        text.drawShadowedText(active.toString(), X, y, DebugColor.GREEN, SCALE);
        y += SECTION_GAP;

        text.drawShadowedText("Last mismatches:", X, y, DebugColor.LIGHT_GRAY, SCALE);
        y += LINE_HEIGHT;
        List<MismatchEntry> recent = comparator.recentMismatches();
        for (MismatchEntry m : recent) {
            String line = String.format("f %04X %s rom=%s eng=%s Δ%s%s",
                    m.frame(), m.field(), m.romValue(),
                    m.engineValue(), m.delta(),
                    m.repeatCount() > 1 ? (" ×" + m.repeatCount()) : "");
            DebugColor color = m.severity() == Severity.ERROR
                    ? DebugColor.RED : DebugColor.ORANGE;
            text.drawShadowedText(line, X, y, color, SCALE);
            y += LINE_HEIGHT;
        }

        if (comparator.isComplete()) {
            text.drawShadowedText("TRACE COMPLETE", X, COMPLETE_BANNER_Y,
                    DebugColor.YELLOW, SCALE);
        }
    }

    private static char bit(int mask, int flag, char letter) {
        return (mask & flag) != 0 ? letter : '.';
    }
}
