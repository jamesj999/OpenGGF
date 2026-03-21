package com.openggf.game.sonic2.objects.badniks;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.audio.Sonic2Sfx;

import com.openggf.debug.DebugColor;

import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic2.objects.PointsObjectInstance;

/**
 * Abstract base class for all Badnik enemies.
 * Provides common collision handling, destruction behavior, and helper methods
 * for AI.
 */
public abstract class AbstractBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    private static final DestructionConfig S2_DESTRUCTION_CONFIG = new DestructionConfig(
            Sonic2Sfx.EXPLOSION.id,
            true,   // spawnAnimal
            false,  // useRespawnTracking
            (spawn, lm, pts) -> new PointsObjectInstance(spawn, lm, pts)
    );

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
     * Returns the destruction configuration for this badnik.
     * S2 badniks use the default; S1 badniks can override to return S1 config.
     */
    protected DestructionConfig getDestructionConfig() {
        return S2_DESTRUCTION_CONFIG;
    }

    /**
     * Handles Badnik destruction: spawn explosion, animal, points, award score.
     */
    protected void destroyBadnik(AbstractPlayableSprite player) {
        destroyed = true;
        setDestroyed(true);
        DestructionEffects.destroyBadnik(currentX, currentY, spawn, player, levelManager,
                getDestructionConfig());
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
        ctx.drawWorldLabel(currentX, currentY, -2, cachedDebugLabel, DebugColor.YELLOW);
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

