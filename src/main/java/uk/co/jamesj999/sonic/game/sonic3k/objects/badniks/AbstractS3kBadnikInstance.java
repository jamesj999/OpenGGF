package uk.co.jamesj999.sonic.game.sonic3k.objects.badniks;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSfx;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Shared S3K badnik behavior: touch response, defeat handling, dynamic
 * position/collision, and sprite rendering.
 */
abstract class AbstractS3kBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    protected final LevelManager levelManager;
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

    protected AbstractS3kBadnikInstance(ObjectSpawn spawn, String name, LevelManager levelManager,
            String rendererKey, int collisionSizeIndex, int priorityBucket) {
        super(spawn, name);
        this.levelManager = levelManager;
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
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        defeat(player);
    }

    protected final void defeat(AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }
        destroyed = true;
        setDestroyed(true);

        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        if (objectManager != null) {
            objectManager.removeFromActiveSpawns(spawn);
        }

        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (objectManager != null && renderManager != null) {
            objectManager.addDynamicObject(
                    new ExplosionObjectInstance(0x27, getBodyAnchorX(), getBodyAnchorY(), renderManager));
        }

        if (player != null) {
            int pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        AudioManager.getInstance().playSfx(Sonic3kSfx.BREAK.id);
    }

    protected final void spawnProjectile(S3kBadnikProjectileInstance projectile) {
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
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
        return new ObjectSpawn(
                bodyX,
                bodyY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
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
        if (destroyed || levelManager == null) {
            return;
        }
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
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
