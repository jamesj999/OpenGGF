package com.openggf.level.objects;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Centralised badnik destruction sequence shared across S1, S2, and S3K.
 * <p>
 * Each game configures a {@link DestructionConfig} capturing what varies
 * (SFX, animal spawn, respawn tracking, points popup factory) while the
 * core sequence (explosion, score chain, SFX) stays in one place.
 */
public final class DestructionEffects {

    private DestructionEffects() {
        // utility class
    }

    /**
     * Functional interface for game-specific points popup creation.
     * S2 creates {@code PointsObjectInstance}, S1 creates {@code Sonic1PointsObjectInstance},
     * S3K passes {@code null} (no popup).
     */
    @FunctionalInterface
    public interface PointsFactory {
        ObjectInstance create(ObjectSpawn spawn, ObjectServices services, int pointsValue);
    }

    /**
     * Immutable configuration record capturing what varies between games.
     *
     * @param sfxId              explosion SFX ID (game-specific)
     * @param spawnAnimal        whether to spawn an {@link AnimalObjectInstance}
     * @param useRespawnTracking if true, uses {@code markRemembered} for respawn-tracked spawns;
     *                           if false, always uses {@code removeFromActiveSpawns}
     * @param pointsFactory      factory for the floating points popup, or {@code null} to skip
     */
    public record DestructionConfig(
            int sfxId,
            boolean spawnAnimal,
            boolean useRespawnTracking,
            PointsFactory pointsFactory
    ) {
    }

    /**
     * Executes the standard badnik destruction sequence:
     * <ol>
     *   <li>Handle respawn tracking (mark remembered or remove from active spawns)</li>
     *   <li>Spawn explosion</li>
     *   <li>Optionally spawn animal</li>
     *   <li>Calculate and award chain score</li>
     *   <li>Optionally spawn points popup</li>
     *   <li>Play explosion SFX</li>
     * </ol>
     *
     * @param x            current X position of the destroyed badnik
     * @param y            current Y position of the destroyed badnik
     * @param spawn        the badnik's original spawn data
     * @param player       the player who destroyed the badnik (may be null)
     * @param services     injectable services handle
     * @param config       game-specific destruction configuration
     */
    public static void destroyBadnik(int x, int y, ObjectSpawn spawn,
            AbstractPlayableSprite player, ObjectServices services,
            DestructionConfig config) {

        // --- Respawn tracking ---
        var objectManager = services != null ? services.objectManager() : null;
        if (objectManager != null) {
            if (config.useRespawnTracking() && spawn.respawnTracked()) {
                objectManager.markRemembered(spawn);
            } else {
                objectManager.removeFromActiveSpawns(spawn);
            }
        }

        // --- Spawn explosion ---
        ObjectRenderManager renderManager = services != null
                ? services.renderManager() : null;
        if (objectManager != null && renderManager != null) {
            ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                    0x27, x, y, renderManager);
            objectManager.addDynamicObject(explosion);
        }

        // --- Optionally spawn animal ---
        if (config.spawnAnimal() && objectManager != null) {
            AnimalObjectInstance animal = new AnimalObjectInstance(
                    new ObjectSpawn(x, y, 0x28, 0, 0, false, 0), services);
            objectManager.addDynamicObject(animal);
        }

        // --- Calculate and award chain score ---
        int pointsValue = 100;
        if (player != null) {
            pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        // --- Optionally spawn points popup ---
        if (config.pointsFactory() != null && objectManager != null) {
            ObjectInstance points = config.pointsFactory().create(
                    new ObjectSpawn(x, y, 0x29, 0, 0, false, 0),
                    services, pointsValue);
            objectManager.addDynamicObject((AbstractObjectInstance) points);
        }

        // --- Play explosion SFX ---
        if (services != null) {
            services.playSfx(config.sfxId());
        }
    }
}
