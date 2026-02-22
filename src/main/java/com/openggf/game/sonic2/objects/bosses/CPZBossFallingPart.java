package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.objects.BossExplosionObjectInstance;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CPZ Boss Falling Part - Debris during defeat sequence.
 * ROM Reference: s2.asm Obj5D (ROUTINE_FALLING_PARTS = 0x14)
 * Explodes after timer, then falls with gravity.
 */
public class CPZBossFallingPart extends AbstractObjectInstance {

    private static final int GRAVITY = 0x38;
    private static final int FLOOR_Y = 0x580;

    private final LevelManager levelManager;

    private int x;
    private int y;
    private int xVel;
    private int yVel;
    private int yFixed;
    private int renderFlags;
    private int mappingFrame;
    private int timer;
    private int timer2;
    private boolean exploded;

    public CPZBossFallingPart(ObjectSpawn spawn, LevelManager levelManager, int mappingFrame, int xVel) {
        super(spawn, "CPZ Boss Part");
        this.levelManager = levelManager;
        this.x = spawn.x();
        this.y = spawn.y();
        this.xVel = xVel;
        this.yVel = -0x380;
        this.yFixed = y << 16;
        this.renderFlags = spawn.renderFlags();
        this.mappingFrame = mappingFrame;
        this.timer = randomFallTimer();
        this.timer2 = 0;
        this.exploded = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (!exploded) {
            timer--;
            if (timer > 0) {
                return;
            }
            spawnExplosion();
            exploded = true;
            timer2 = 0x1E;
        }

        timer2--;
        if (timer2 >= 0) {
            return;
        }

        applyFallingMove();
        if (yFixed >= (FLOOR_Y << 16)) {
            setDestroyed(true);
        }
    }

    private void applyFallingMove() {
        x += xVel;
        yFixed += (yVel << 8);
        yVel += GRAVITY;
        y = yFixed >> 16;
    }

    private void spawnExplosion() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        BossExplosionObjectInstance explosion = new BossExplosionObjectInstance(x, y, renderManager);
        levelManager.getObjectManager().addDynamicObject(explosion);
    }

    private int randomFallTimer() {
        int random = ThreadLocalRandom.current().nextInt();
        return ((random >>> 16) + 0x1E) & 0x7F;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_PARTS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (mappingFrame < 0) {
            return;
        }

        boolean flipped = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, flipped, false);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return 2;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
