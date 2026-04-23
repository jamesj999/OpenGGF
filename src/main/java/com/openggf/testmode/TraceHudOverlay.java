package com.openggf.testmode;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.Severity;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;
import com.openggf.trace.replay.TraceReplayFixture;

import java.util.List;

/**
 * Bottom-right HUD painted each frame while a trace session is active.
 * Red ERRORS, orange WARN, grey LAG counters; BK2 input visualiser
 * (A/B/C/U/D/L/R/S); and the last five mismatch entries, severity
 * coloured, with repeat counts.
 */
public final class TraceHudOverlay {

    private final LiveTraceComparator comparator;
    private final TraceReplayFixture fixture;

    public TraceHudOverlay(LiveTraceComparator comparator, TraceReplayFixture fixture) {
        this.comparator = comparator;
        this.fixture = fixture;
    }

    public void render(PixelFontTextRenderer text) {
        int x = 180;
        int y = 130;
        text.drawShadowedText(String.format("ERRORS %4d", comparator.errorCount()),
                x, y, DebugColor.RED);
        y += 11;
        text.drawShadowedText(String.format("WARN   %4d", comparator.warningCount()),
                x, y, DebugColor.ORANGE);
        y += 11;
        text.drawShadowedText(String.format("LAG    %4d", comparator.laggedFrames()),
                x, y, DebugColor.GRAY);
        y += 14;

        int actionMask = comparator.recentActionMask();
        int inputMask = comparator.recentInputMask();
        boolean start = comparator.recentStartPressed();
        text.drawShadowedText("A B C U D L R S", x, y, DebugColor.WHITE);
        y += 11;

        StringBuilder active = new StringBuilder();
        active.append(bit(actionMask, 0x01, 'A')).append(' ');
        active.append(bit(actionMask, 0x02, 'B')).append(' ');
        active.append(bit(actionMask, 0x04, 'C')).append(' ');
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_UP, 'U')).append(' ');
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_DOWN, 'D')).append(' ');
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_LEFT, 'L')).append(' ');
        active.append(bit(inputMask, AbstractPlayableSprite.INPUT_RIGHT, 'R')).append(' ');
        active.append(start ? 'S' : '.');
        text.drawShadowedText(active.toString(), x, y, DebugColor.GREEN);
        y += 11;

        text.drawShadowedText("Last mismatches:", x, y, DebugColor.LIGHT_GRAY);
        y += 11;
        List<MismatchEntry> recent = comparator.recentMismatches();
        for (MismatchEntry m : recent) {
            String line = String.format("f %04X %s rom=%s eng=%s Δ%s%s",
                    m.frame(), m.field(), m.romValue(),
                    m.engineValue(), m.delta(),
                    m.repeatCount() > 1 ? (" ×" + m.repeatCount()) : "");
            DebugColor color = m.severity() == Severity.ERROR
                    ? DebugColor.RED : DebugColor.ORANGE;
            text.drawShadowedText(line, x, y, color);
            y += 10;
        }

        if (comparator.isComplete()) {
            text.drawShadowedText("TRACE COMPLETE", x, 120, DebugColor.YELLOW);
        }

        // Reference fixture to keep it live for future per-sprite HUD detail.
        if (fixture == null) {
            // Unreachable with a properly-constructed launcher.
            return;
        }
    }

    private static char bit(int mask, int flag, char letter) {
        return (mask & flag) != 0 ? letter : '.';
    }
}
