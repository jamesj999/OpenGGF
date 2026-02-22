package com.openggf.game.sonic2.objects.badniks;

import com.openggf.audio.AudioManager;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.GameServices;

import java.awt.Color;

import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic2.objects.ExplosionObjectInstance;
import com.openggf.game.sonic2.objects.PointsObjectInstance;

/**
 * Abstract base class for all Badnik enemies.
 * Provides common collision handling, destruction behavior, and helper methods
 * for AI.
 */
public abstract class AbstractBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    protected int currentX;
    protected int currentY;
    protected int xVelocity;
    protected int yVelocity;
    protected int animTimer;
    protected int animFrame;
    protected boolean facingLeft;
    protected boolean destroyed;

    protected final LevelManager levelManager;

    protected AbstractBadnikInstance(ObjectSpawn spawn, LevelManager levelManager, String name) {
        super(spawn, name);
        this.levelManager = levelManager;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xVelocity = 0;
        this.yVelocity = 0;
        this.animTimer = 0;
        this.animFrame = 0;
        this.facingLeft = false;
        this.destroyed = false;
    }

    @Override
    public final void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }
        updateMovement(frameCounter, player);
        updateAnimation(frameCounter);
    }

    /**
     * Subclasses implement their specific movement and AI logic.
     */
    protected abstract void updateMovement(int frameCounter, AbstractPlayableSprite player);

    /**
     * Subclasses can override to implement custom animation logic.
     * Default implementation is a simple frame timer.
     */
    protected void updateAnimation(int frameCounter) {
        // Default: no animation. Subclasses override.
    }

    /**
     * Returns the collision size index for touch response.
     */
    protected abstract int getCollisionSizeIndex();

    @Override
    public int getCollisionFlags() {
        // Category 0x00 = ENEMY, plus size index
        return 0x00 | (getCollisionSizeIndex() & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (destroyed) {
            return;
        }
        destroyBadnik(player);
    }

    /**
     * Handles Badnik destruction: spawn explosion, animal, points, award score.
     */
    protected void destroyBadnik(AbstractPlayableSprite player) {
        destroyed = true;
        setDestroyed(true);

        // Remove from active spawns to prevent immediate respawn
        // (badniks will still respawn when camera leaves and returns - ROM accurate)
        var objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            objectManager.removeFromActiveSpawns(spawn);
        }

        // Spawn explosion
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, currentX, currentY,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);

        // Spawn animal
        AnimalObjectInstance animal = new AnimalObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x28, 0, 0, false, 0), levelManager);
        levelManager.getObjectManager().addDynamicObject(animal);

        // Calculate points based on chain
        int pointsValue = 100;
        if (player != null) {
            pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        // Spawn points
        PointsObjectInstance points = new PointsObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x29, 0, 0, false, 0), levelManager, pointsValue);
        levelManager.getObjectManager().addDynamicObject(points);

        // Play explosion SFX
        AudioManager.getInstance().playSfx(Sonic2Sfx.EXPLOSION.id);

        // Remove self
        // Remove self (handled by update loop via destroyed flag)
    }

    /**
     * Returns a dynamic spawn with the current position for collision detection.
     * This is critical because ObjectManager touch responses use getSpawn() position.
     */
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
    public boolean isHighPriority() {
        return super.isHighPriority();
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    // Cached debug label to avoid per-frame String allocation
    private String cachedDebugLabel;
    private int cachedDebugAnimFrame = -1;
    private boolean cachedDebugFacingLeft;

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Yellow hitbox rectangle (default 16x16 half-size)
        ctx.drawRect(currentX, currentY, 16, 16, 1f, 1f, 0f);

        // Cyan velocity arrow if moving
        if (xVelocity != 0 || yVelocity != 0) {
            int endX = currentX + (xVelocity >> 5);
            int endY = currentY + (yVelocity >> 5);
            ctx.drawArrow(currentX, currentY, endX, endY, 0f, 1f, 1f);
        }

        // Yellow text label: name + frame + facing (cached until state changes)
        if (animFrame != cachedDebugAnimFrame || facingLeft != cachedDebugFacingLeft) {
            cachedDebugLabel = name + " f" + animFrame + " " + (facingLeft ? "L" : "R");
            cachedDebugAnimFrame = animFrame;
            cachedDebugFacingLeft = facingLeft;
        }
        ctx.drawWorldLabel(currentX, currentY, -2, cachedDebugLabel, Color.YELLOW);
    }

    /**
     * Helper: Check if player is to the left of this Badnik.
     */
    protected boolean isPlayerLeft(AbstractPlayableSprite player) {
        if (player == null) {
            return facingLeft;
        }
        return player.getCentreX() < currentX;
    }

    /**
     * Helper: Simple oscillation for vertical movement.
     */
    protected int oscillateVertical(int baseY, int amplitude, int period, int frameCounter) {
        double angle = (frameCounter % period) * (2.0 * Math.PI / period);
        return baseY + (int) (amplitude * Math.sin(angle));
    }
}

