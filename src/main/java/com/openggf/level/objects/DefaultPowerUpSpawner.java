package com.openggf.level.objects;

import com.openggf.game.InstaShieldHandle;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.PowerUpSpawner;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic1.objects.Sonic1SplashObjectInstance;
import com.openggf.game.sonic3k.objects.BubbleShieldObjectInstance;
import com.openggf.game.sonic3k.objects.FireShieldObjectInstance;
import com.openggf.game.sonic3k.objects.InstaShieldObjectInstance;
import com.openggf.game.sonic3k.objects.LightningShieldObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kInvincibilityStarsObjectInstance;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.WaterSystem;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.logging.Logger;

/**
 * Default implementation of {@link PowerUpSpawner} that creates concrete
 * power-up objects and registers them with the {@link ObjectManager}.
 * <p>
 * <b>Intentional bridge class:</b> This class imports game-specific types
 * (S1 splash, S3K elemental shields) from the game-agnostic layer. This is
 * an accepted layering violation because extracting a per-game shield factory
 * would add indirection with no practical benefit — the shield types are
 * stable and the spawner logic is shared across all games.
 */
public class DefaultPowerUpSpawner implements PowerUpSpawner {

    private static final Logger LOGGER = Logger.getLogger(DefaultPowerUpSpawner.class.getName());

    private final ObjectManager objectManager;
    private final ObjectServices services;

    public DefaultPowerUpSpawner(ObjectManager objectManager) {
        this.objectManager = objectManager;
        this.services = objectManager != null ? objectManager.services() : null;
    }

    @Override
    public PowerUpObject spawnShield(PlayableEntity player, ShieldType type) {
        ShieldObjectInstance shield;
        if (player instanceof AbstractPlayableSprite aps) {
            shield = switch (type) {
                case FIRE -> new FireShieldObjectInstance(aps);
                case LIGHTNING -> new LightningShieldObjectInstance(aps);
                case BUBBLE -> new BubbleShieldObjectInstance(aps);
                default -> new ShieldObjectInstance(player);
            };
        } else {
            // Non-elemental fallback when player is not AbstractPlayableSprite
            shield = new ShieldObjectInstance(player);
        }
        objectManager.addDynamicObject(shield);
        return shield;
    }

    @Override
    public PowerUpObject spawnInvincibilityStars(PlayableEntity player) {
        AbstractObjectInstance stars;
        if (GameModuleRegistry.getCurrent() instanceof Sonic3kGameModule) {
            stars = new Sonic3kInvincibilityStarsObjectInstance(player);
        } else {
            stars = new InvincibilityStarsObjectInstance(player);
        }
        objectManager.addDynamicObject(stars);
        return (PowerUpObject) stars;
    }

    @Override
    public InstaShieldHandle createInstaShield(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite aps)) {
            LOGGER.warning("createInstaShield called with non-AbstractPlayableSprite");
            return null;
        }
        return new InstaShieldObjectInstance(aps);
    }

    @Override
    public void registerObject(PowerUpObject obj) {
        if (obj instanceof ObjectInstance oi) {
            if (oi instanceof AbstractObjectInstance aoi) {
                // registerObject() is used for persistent visuals that survive an
                // ObjectManager rebuild. Their old slot belongs to the previous
                // manager and must be dropped before the new manager allocates one.
                aoi.setSlotIndex(-1);
            }
            objectManager.addDynamicObject(oi);
        }
    }

    @Override
    public void spawnSplash(PlayableEntity player) {
        if (objectManager == null) {
            return;
        }

        if (services == null) {
            return;
        }

        var level = services.currentLevel();
        if (level == null) {
            return;
        }

        // Get water level from WaterSystem
        // Use getVisualWaterLevelY so splash appears at the oscillating water surface (CPZ2)
        WaterSystem waterSystem = services != null ? services.waterSystem() : null;
        if (waterSystem == null) {
            return;
        }
        int waterY = waterSystem.getVisualWaterLevelY(level.getZoneIndex(), services.currentAct());

        // S2/S3K: use dust/splash renderer from SpindashDustController
        if (player instanceof AbstractPlayableSprite aps) {
            SpindashDustController dustController = aps.getSpindashDustController();
            if (dustController != null && dustController.getRenderer() != null) {
                PlayerSpriteRenderer renderer = dustController.getRenderer();
                boolean facingLeft = player.getDirection() == Direction.LEFT;
                var splash = new SplashObjectInstance(
                        player.getCentreX(), waterY, renderer, facingLeft);
                objectManager.addDynamicObject(splash);
                return;
            }
        }

        // S1: use LZ splash art from ObjectRenderManager (Object 0x08)
        var s1Splash = new Sonic1SplashObjectInstance(
                player.getCentreX(), waterY);
        objectManager.addDynamicObject(s1Splash);
    }
}
