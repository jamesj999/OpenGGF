package com.openggf.game.sonic1;

import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

public final class Sonic1HudStaticArtFactory {
    private Sonic1HudStaticArtFactory() {
    }

    public static HudStaticArt create(Pattern[] textPatterns, Pattern[] livesPatterns) {
        Pattern[] safeTextPatterns = textPatterns != null ? textPatterns : new Pattern[0];
        Pattern[] safeLivesPatterns = livesPatterns != null ? livesPatterns : new Pattern[0];
        Pattern[] combined = combine(safeTextPatterns, safeLivesPatterns);
        int livesBase = safeTextPatterns.length;
        return new HudStaticArt(
                combined,
                scoreFrame(),
                debugScoreFrame(),
                timeFrame(),
                timeFrame(),
                ringsFrame(),
                ringsFrame(),
                nativeLivesFrame(livesBase));
    }

    private static Pattern[] combine(Pattern[] textPatterns, Pattern[] livesPatterns) {
        Pattern[] combined = new Pattern[textPatterns.length + livesPatterns.length];
        System.arraycopy(textPatterns, 0, combined, 0, textPatterns.length);
        System.arraycopy(livesPatterns, 0, combined, textPatterns.length, livesPatterns.length);
        return combined;
    }

    private static SpriteMappingFrame scoreFrame() {
        return textRow(0, 0, 1, 2, 3, 11);
    }

    private static SpriteMappingFrame debugScoreFrame() {
        return textRow(0, 0, 1, 2, 3);
    }

    private static SpriteMappingFrame timeFrame() {
        return textRow(0, 8, 5, 10, 11);
    }

    private static SpriteMappingFrame ringsFrame() {
        return textRow(0, 3, 5, 6, 7, 0);
    }

    private static SpriteMappingFrame textRow(int palette, int... pairIndices) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        for (int i = 0; i < pairIndices.length; i++) {
            pieces.add(new SpriteMappingPiece(i * 8, 0, 1, 2, pairIndices[i] * 2, false, false, palette));
        }
        return new SpriteMappingFrame(pieces);
    }

    private static SpriteMappingFrame nativeLivesFrame(int livesBase) {
        return new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 2, 2, livesBase, false, false, 0),
                new SpriteMappingPiece(16, 0, 1, 1, livesBase + 4, false, false, 0),
                new SpriteMappingPiece(24, 0, 1, 1, livesBase + 6, false, false, 0),
                new SpriteMappingPiece(32, 0, 1, 1, livesBase + 9, false, false, 0),
                new SpriteMappingPiece(40, 0, 1, 1, livesBase + 8, false, false, 0),
                new SpriteMappingPiece(48, 0, 1, 1, livesBase + 10, false, false, 0),
                new SpriteMappingPiece(56, 0, 1, 1, livesBase + 11, false, false, 0),
                new SpriteMappingPiece(16, 8, 1, 1, livesBase + 5, false, false, 0),
                new SpriteMappingPiece(24, 8, 1, 1, livesBase + 7, false, false, 0)));
    }
}
