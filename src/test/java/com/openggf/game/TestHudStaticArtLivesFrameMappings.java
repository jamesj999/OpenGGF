package com.openggf.game;

import com.openggf.game.sonic1.Sonic1HudStaticArtFactory;
import com.openggf.game.sonic2.Sonic2HudStaticArtFactory;
import com.openggf.game.sonic3k.Sonic3kHudStaticArtFactory;
import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestHudStaticArtLivesFrameMappings {

    @Test
    void sonic1LivesFrameMatchesHudMapGeometry() {
        HudStaticArt art = Sonic1HudStaticArtFactory.create(patterns(12), patterns(12));

        assertLivesFrame(art.livesFrame(), 12, 0);
    }

    @Test
    void sonic2NativeLivesFrameMatchesHudMapGeometry() {
        HudStaticArt art = Sonic2HudStaticArtFactory.create(patterns(12), patterns(12), false);

        assertLivesFrame(art.livesFrame(), 12, 1);
    }

    @Test
    void sonic2DonorLivesFrameMatchesHudMapGeometryUsingIconPalette() {
        HudStaticArt art = Sonic2HudStaticArtFactory.create(patterns(12), patterns(12), true);

        assertLivesFrame(art.livesFrame(), 12, 0);
    }

    @Test
    void sonic3kLivesFrameMatchesHudMapGeometry() {
        HudStaticArt art = Sonic3kHudStaticArtFactory.create(patterns(12), patterns(12));

        assertLivesFrame(art.livesFrame(), 12, 1);
    }

    private static void assertLivesFrame(SpriteMappingFrame frame, int livesBase, int namePalette) {
        assertEquals(List.of(
                new SpriteMappingPiece(0, 0, 2, 2, livesBase, false, false, 0),
                new SpriteMappingPiece(16, 0, 4, 2, livesBase + 4, false, false, namePalette)),
                frame.pieces());
    }

    private static Pattern[] patterns(int count) {
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
        }
        return patterns;
    }
}
