package com.openggf.sprites.animation;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Resolves the mapping frame index for a sprite on a given tick.
 */
public interface SpriteAnimationProfile {
    default Integer resolveAnimationId(AbstractPlayableSprite sprite, int frameCounter, int scriptCount) {
        return null;
    }

    int resolveFrame(AbstractPlayableSprite sprite, int frameCounter, int frameCount);
}
