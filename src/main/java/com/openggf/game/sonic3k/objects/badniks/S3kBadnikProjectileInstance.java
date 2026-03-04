package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Shared projectile object for S3K badniks that use Map_Bloominator / Map_MonkeyDude
 * projectile frames and hurt-category touch flags.
 */
final class S3kBadnikProjectileInstance extends AbstractObjectInstance implements TouchResponseProvider {
    private static final int SHIELD_REACTION_BOUNCE = 1 << 3;
    private static final int DEFLECT_SPEED = 0x800;

    private final String rendererKey;
    private final int mappingFrame;
    private final int collisionSizeIndex;
    private final int priorityBucket;
    private final boolean hFlip;
    private final int gravity;

    private int currentX;
    private int currentY;
    private int xVelocity;
    private int yVelocity;
    private int xSubpixel;
    private int ySubpixel;
    private boolean collisionEnabled = true;

    S3kBadnikProjectileInstance(ObjectSpawn ownerSpawn,
            String rendererKey,
            int mappingFrame,
            int x,
            int y,
            int xVelocity,
            int yVelocity,
            int gravity,
            int collisionSizeIndex,
            int priorityBucket,
            boolean hFlip) {
        super(ownerSpawn, "S3kBadnikProjectile");
        this.rendererKey = rendererKey;
        this.mappingFrame = mappingFrame;
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVelocity;
        this.yVelocity = yVelocity;
        this.gravity = gravity;
        this.collisionSizeIndex = collisionSizeIndex;
        this.priorityBucket = priorityBucket;
        this.hFlip = hFlip;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // MoveSprite / MoveSprite_LightGravity order:
        // use old y_vel for movement this frame, then apply gravity.
        int oldYVel = yVelocity;
        yVelocity += gravity;

        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += oldYVel;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;

        if (!isOnScreen(48)) {
            setDestroyed(true);
        }
    }

    @Override
    public int getCollisionFlags() {
        if (!collisionEnabled) {
            return 0;
        }
        // HURT category + collision size index (ObjDat3 flags: $98).
        return 0x80 | (collisionSizeIndex & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return priorityBucket;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION_BOUNCE;
    }

    @Override
    public boolean onShieldDeflect(AbstractPlayableSprite player) {
        if (player == null) {
            return false;
        }

        int dx = player.getCentreX() - currentX;
        int dy = player.getCentreY() - currentY;
        int angle = TrigLookupTable.calcAngle(saturateToShort(dx), saturateToShort(dy));

        // ROM: Touch_ChkHurt_Bounce_Projectile / ShieldTouchResponse
        // d1 -> x_vel, d0 -> y_vel after GetSineCosine.
        xVelocity = -((TrigLookupTable.cosHex(angle) * DEFLECT_SPEED) >> 8);
        yVelocity = -((TrigLookupTable.sinHex(angle) * DEFLECT_SPEED) >> 8);
        collisionEnabled = false;
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(rendererKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, hFlip, false);
    }

    private static short saturateToShort(int value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) value;
    }
}
