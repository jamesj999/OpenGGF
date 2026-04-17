package com.openggf.game.sonic3k;

import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kLivesHudPaletteOverride {

    @Test
    void create_buildsLivesFrameWithIconOnPalette0AndNameOnPalette1() {
        Pattern[] text = { new Pattern(), new Pattern(), new Pattern(), new Pattern() };
        Pattern[] lives = new Pattern[12];
        Arrays.setAll(lives, i -> new Pattern());

        HudStaticArt art = Sonic3kHudStaticArtFactory.create(text, lives);

        assertNotNull(art);
        assertTrue(art.livesFrame().pieces().stream().anyMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.livesFrame().pieces().stream().anyMatch(piece -> piece.paletteIndex() == 1));
    }
}
