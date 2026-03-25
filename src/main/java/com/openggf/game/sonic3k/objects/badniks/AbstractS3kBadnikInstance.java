package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Shared S3K badnik behavior: touch response, defeat handling, dynamic
 * position/collision, and sprite rendering.
 */
abstract class AbstractS3kBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    private final String rendererKey;
    private final int collisionSizeIndex;
    private final int priorityBucket;

    protected int currentX;
    protected int currentY;
    protected int xVelocity;
    protected int yVelocity;
    protected int xSubpixel;
    protected int ySubpixel;
    protected int mappingFrame;
    protected boolean facingLeft;
    protected boolean destroyed;

    protected AbstractS3kBadnikInstance(ObjectSpawn spawn, String name,
            String rendererKey, int collisionSizeIndex, int priorityBucket) {
        super(spawn, name);
        this.rendererKey = rendererKey;
        this.collisionSizeIndex = collisionSizeIndex;
        this.priorityBucket = priorityBucket;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        // S3K render_flags bit 0 mirrors horizontally. Clear = face left, set = face right.
        this.facingLeft = (spawn.renderFlags() & 0x01) == 0;
    }

    @Override
    public int getCollisionFlags() {
        return collisionSizeIndex & 0x3F;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        defeat(player);
    }

    /** S3K destruction config: no animal, no points popup, no respawn tracking, S3K break SFX. */
    private static final DestructionConfig S3K_DESTRUCTION_CONFIG = new DestructionConfig(
            Sonic3kSfx.BREAK.id,
            false,  // spawnAnimal
            false,  // useRespawnTracking (S3K always removeFromActiveSpawns)
            null    // no points popup
    );

    protected final void defeat(AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }
        destroyed = true;
        setDestroyed(true);

        DestructionEffects.destroyBadnik(
                getBodyAnchorX(), getBodyAnchorY(), spawn, player, services(),
                S3K_DESTRUCTION_CONFIG);
    }

    protected final void spawnProjectile(S3kBadnikProjectileInstance projectile) {
        ObjectManager objectManager = services() != null ? services().objectManager() : null;
        if (objectManager != null) {
            objectManager.addDynamicObject(projectile);
        }
    }

    protected final void moveWithVelocity() {
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += yVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;
    }

    @Override
    public ObjectSpawn getSpawn() {
        int bodyX = getBodyAnchorX();
        int bodyY = getBodyAnchorY();
        return buildSpawnAt(bodyX, bodyY);
    }

    @Override
    public int getX() {
        return getBodyAnchorX();
    }

    @Override
    public int getY() {
        return getBodyAnchorY();
    }

    @Override
    public int getPriorityBucket() {
        return priorityBucket;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(rendererKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getRenderAnchorX(), getRenderAnchorY(), !facingLeft, false);
    }

    /**
     * Body anchor used by touch collision and all gameplay interactions.
     * Subclasses can override when the logical center differs from raw {@code currentX/currentY}.
     */
    protected int getBodyAnchorX() {
        return currentX;
    }

    protected int getBodyAnchorY() {
        return currentY;
    }

    /**
     * Render anchor for sprite drawing. Defaults to body anchor.
     * Subclasses can override for visual-only offsets.
     */
    protected int getRenderAnchorX() {
        return getBodyAnchorX();
    }

    protected int getRenderAnchorY() {
        return getBodyAnchorY();
    }
}
