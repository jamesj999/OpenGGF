package com.openggf.game.sonic3k.events;

/**
 * Deterministic AIZ fire-wall render state emitted by {@link Sonic3kAIZEvents}.
 *
 * @param coverHeightPx Screen-space wall height in pixels (from bottom of the screen)
 * @param sourceWorldX Source world X used to sample the wall strip
 * @param sourceWorldY Source world Y used to sample the wall strip
 */
public record FireWallRenderState(
        int coverHeightPx,
        int sourceWorldX,
        int sourceWorldY) {
}
