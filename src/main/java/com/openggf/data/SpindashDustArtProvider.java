package com.openggf.data;

import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;

/**
 * Optional interface for games that can provide spindash dust art from ROM.
 */
public interface SpindashDustArtProvider {
    SpriteArtSet loadSpindashDustArt(String characterCode) throws IOException;
}
