package com.openggf.game;

/**
 * Immutable snapshot of lamppost state for credits demo playback.
 * <p>
 * Some credits demos start mid-level at a lamppost rather than the zone's
 * start position. This record carries the saved lamppost data so the game
 * loop can restore player position, camera, and water state without
 * referencing game-specific constants.
 *
 * @param playerX      player centre X
 * @param playerY      player centre Y
 * @param rings        ring count at lamppost
 * @param cameraX      camera X position
 * @param cameraY      camera Y position
 * @param cameraMaxY   camera lower boundary
 * @param waterHeight  water surface height
 * @param waterRoutine water routine index
 */
public record DemoLamppostState(
        int playerX,
        int playerY,
        int rings,
        int cameraX,
        int cameraY,
        int cameraMaxY,
        int waterHeight,
        int waterRoutine
) {
}
