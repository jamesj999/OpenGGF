package com.openggf.data;

import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;

/**
 * Optional interface for games that can provide player sprite art from ROM.
 */
public interface PlayerSpriteArtProvider {
    SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException;

    /**
     * Loads the character palette for the given character code.
     * Returns null if the provider does not support character palettes.
     *
     * @param characterCode character identifier ("sonic", "tails", "knuckles")
     * @return the character palette, or null if unavailable
     */
    default Palette loadCharacterPalette(String characterCode) {
        return null;
    }
}
