package com.openggf.data;

import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;

/**
 * Optional interface for games that can provide player sprite art from ROM.
 */
public interface PlayerSpriteArtProvider {
    SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException;
}
