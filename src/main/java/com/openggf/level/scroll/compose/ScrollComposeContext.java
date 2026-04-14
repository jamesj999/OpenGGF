package com.openggf.level.scroll.compose;

/**
 * Frame-local inputs for scroll composition helpers.
 */
public record ScrollComposeContext(int cameraX,
                                   int cameraY,
                                   int frameCounter,
                                   int actId) {
}
