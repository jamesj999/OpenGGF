package com.openggf.sprites.render;

import com.openggf.game.GameId;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.art.SpriteArtSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlayerSpriteRendererPaletteContext {

    @AfterEach
    void tearDown() {
        RenderContext.reset();
    }

    @Test
    void resolveRenderPaletteIndex_usesRenderContextPaletteBlockWhenPresent() {
        SpriteArtSet artSet = new SpriteArtSet(
                new Pattern[0],
                List.of(new SpriteMappingFrame(List.of())),
                List.of(new SpriteDplcFrame(List.of())),
                2,
                0,
                0,
                0,
                null,
                null);
        PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet);

        RenderContext donorContext = RenderContext.getOrCreateDonor(GameId.S3K);
        renderer.setRenderContext(donorContext);

        assertEquals(donorContext.getEffectivePaletteLine(2), renderer.resolveRenderPaletteIndex(2));
    }

    @Test
    void resolveRenderPaletteIndex_keepsLogicalPaletteWhenNoRenderContext() {
        SpriteArtSet artSet = new SpriteArtSet(
                new Pattern[0],
                List.of(new SpriteMappingFrame(List.of())),
                List.of(new SpriteDplcFrame(List.of())),
                1,
                0,
                0,
                0,
                null,
                null);
        PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet);

        assertEquals(1, renderer.resolveRenderPaletteIndex(1));
    }
}
