package com.openggf.game.sonic3k.events;

import com.openggf.level.LevelManager;

import java.util.logging.Logger;

/**
 * Deterministic mutation handler for S3K seamless in-place transitions.
 */
public final class S3kSeamlessMutationExecutor {
    private static final Logger LOG = Logger.getLogger(S3kSeamlessMutationExecutor.class.getName());

    public static final String MUTATION_AIZ1_FIRE_TRANSITION_STAGE = "s3k.aiz1.fire_transition_stage";

    private S3kSeamlessMutationExecutor() {
    }

    public static void apply(LevelManager levelManager, String mutationKey) {
        if (mutationKey == null || mutationKey.isBlank() || levelManager == null) {
            return;
        }
        switch (mutationKey) {
            case MUTATION_AIZ1_FIRE_TRANSITION_STAGE -> {
                // Fire transition performs dynamic layout/palette updates while staying in AIZ1.
                // Ensure tilemaps are rebuilt from current runtime data.
                levelManager.invalidateAllTilemaps();
            }
            default -> LOG.warning("Unknown S3K seamless mutation key: " + mutationKey);
        }
    }
}
