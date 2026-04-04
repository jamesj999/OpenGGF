package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.LevelState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Immutable snapshot of player/camera state saved when entering a special
 * stage via a big ring (ROM: Save_Level_Data2 -> Saved2_* variables).
 * Restored on return so the player resumes at the ring location with
 * correct collision path and camera boundaries.
 */
public record BigRingReturnState(
        int playerX,
        int playerY,
        int cameraX,
        int cameraY,
        int rings,
        byte topSolidBit,
        byte lrbSolidBit,
        int cameraMaxY
) {
    /**
     * Restores all saved state onto the player, camera, and game state.
     * Mirrors ROM Load_Starpost_Settings2 (s3.asm:22082-22087).
     */
    public void restoreToPlayer(AbstractPlayableSprite player, Camera camera, LevelState levelState) {
        player.setCentreX((short) playerX);
        player.setCentreY((short) playerY);
        camera.setX((short) cameraX);
        camera.setY((short) cameraY);
        camera.updatePosition(true);
        if (levelState != null) {
            levelState.setRings(rings);
        }
        player.setTopSolidBit(topSolidBit);
        player.setLrbSolidBit(lrbSolidBit);
        camera.setMaxY((short) cameraMaxY);
    }
}
