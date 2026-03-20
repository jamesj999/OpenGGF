package com.openggf.sprites.playable;

/**
 * Strategy for per-character respawn behavior during the APPROACHING state.
 * Implementations define how a sidekick visually re-enters the game after despawning.
 */
public interface SidekickRespawnStrategy {
    /**
     * Called each frame while in APPROACHING state.
     * @return true when respawn is complete and sidekick should transition to NORMAL
     */
    boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                              int frameCounter);

    /**
     * Position and initialize the sidekick when transitioning from SPAWNING to APPROACHING.
     * @return true if approach started, false if conditions not met (stay in SPAWNING)
     */
    boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader);
}
