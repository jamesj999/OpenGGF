package com.openggf.sprites.art;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.List;

/**
 * Game-agnostic sprite art bundle (tile art + mappings + DPLCs).
 */
public record SpriteArtSet(
        Pattern[] artTiles,
        List<SpriteMappingFrame> mappingFrames,
        List<SpriteDplcFrame> dplcFrames,
        int paletteIndex,
        int basePatternIndex,
        int frameDelay,
        int bankSize,
        SpriteAnimationProfile animationProfile,
        SpriteAnimationSet animationSet
) {
    /** Empty sentinel — returned by default {@code GameModule.loadTailsTailArt()}. */
    public static final SpriteArtSet EMPTY = new SpriteArtSet(
            new Pattern[0], List.of(), List.of(), 0, 0, 0, 0, null, null);

    /**
     * Returns {@code true} if this art set contains no tile data.
     * Useful for checking the {@link #EMPTY} sentinel.
     */
    public boolean isEmpty() {
        return artTiles() == null || artTiles().length == 0;
    }
}
