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

    /**
     * Whether this strategy needs the normal physics pipeline to run during APPROACHING.
     * Strategies that manually position the sprite (Tails fly-in, Knuckles glide) return false.
     * Strategies that rely on ground speed and terrain collision (Sonic walk/spindash) return true.
     */
    default boolean requiresPhysics() {
        return false;
    }
}
