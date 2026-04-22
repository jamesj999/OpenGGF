package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;

/**
 * Shared destruction configuration for all Sonic 2 badniks.
 */
public final class Sonic2BadnikConfig {

    /** Standard S2 badnik destruction: explosion SFX, spawn animal, award points. */
    public static final DestructionConfig DESTRUCTION = new DestructionConfig(
            Sonic2Sfx.EXPLOSION.id,
            AnimalObjectInstance::new,
            false,  // useRespawnTracking
            (spawn, svc, pts) -> new PointsObjectInstance(spawn, svc, pts),
            null
    );

    private Sonic2BadnikConfig() {
        // Utility class
    }
}
