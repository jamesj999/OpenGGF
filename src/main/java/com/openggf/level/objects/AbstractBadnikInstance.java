package com.openggf.level.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.debug.DebugColor;

import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.game.PlayableEntity;

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

    private final DestructionConfig destructionConfig;

    /**
     * Full constructor with explicit DestructionConfig.
     * S2 badniks pass Sonic2BadnikConfig.DESTRUCTION; S1 badniks use the 3-arg
     * constructor and override getDestructionConfig().
     */
    protected AbstractBadnikInstance(ObjectSpawn spawn,
            String name, DestructionConfig destructionConfig) {
        super(spawn, name);
        this.destructionConfig = destructionConfig;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xVelocity = 0;
        this.yVelocity = 0;
        this.animTimer = 0;
        this.animFrame = 0;
        this.facingLeft = false;
    }

    /**
     * Backwards-compatible 3-arg constructor for subclasses that override
     * getDestructionConfig() (e.g. S1 badniks).
     */
    protected AbstractBadnikInstance(ObjectSpawn spawn, String name) {
        this(spawn, name, null);
    }

    @Override
    public final void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }
        updateMovement(frameCounter, player);
        updateAnimation(frameCounter);
        updateDynamicSpawn(currentX, currentY);
    }

    /**
     * Hydrates common badnik state (position, velocity, animation cursor, facing)
     * from a pre-trace SST snapshot. Subclasses override to add per-object fields
     * (timers, sub-states) and MUST call {@code super.hydrateFromRomSnapshot(snapshot)}.
     *
     * <p>Source offsets (Sonic 2 SST, universal header):
     * <ul>
     *   <li>{@code $08/$0C} — x_pos / y_pos (word)</li>
     *   <li>{@code $10/$12} — x_vel / y_vel (signed word, subpixel/frame)</li>
     *   <li>{@code $1A} — mapping_frame (byte)</li>
     *   <li>{@code $1E} — anim_frame_timer (byte)</li>
     *   <li>{@code $22} — status; bit 0 = X-flip (facing left)</li>
     * </ul>
     */
    @Override
    public void hydrateFromRomSnapshot(RomObjectSnapshot snapshot) {
        super.hydrateFromRomSnapshot(snapshot);
        this.currentX = snapshot.xPos();
        this.currentY = snapshot.yPos();
        this.xVelocity = snapshot.xVel();
        this.yVelocity = snapshot.yVel();
        this.animFrame = snapshot.mappingFrame() & 0xFF;
        this.animTimer = snapshot.animFrameTimer() & 0xFF;
        this.facingLeft = (snapshot.status() & 0x01) != 0;
        updateDynamicSpawn(currentX, currentY);
    }

    /**
     * Subclasses implement their specific movement and AI logic.
     */
    protected abstract void updateMovement(int frameCounter, PlayableEntity player);

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
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed()) {
            return;
        }
        destroyBadnik(player);
    }

    /**
     * Returns the destruction configuration for this badnik.
     * Subclasses can override to return game-specific config (e.g. S1 badniks).
     * When using the 4-arg constructor, the injected config is returned.
     */
    protected DestructionConfig getDestructionConfig() {
        return destructionConfig;
    }

    /**
     * Handles Badnik destruction: spawn explosion, animal, points, award score.
     * <p>
     * ROM parity: the explosion inherits this badnik's SST slot. In the ROM,
     * destroying a badnik changes its obID in-place to ExplosionItem (0x27) —
     * the slot is never freed and re-allocated. To prevent the engine from
     * freeing the slot when this badnik is removed, we clear the slot index
     * before marking destroyed. The explosion takes ownership of the slot.
     */
    protected void destroyBadnik(PlayableEntity player) {
        int mySlot = getSlotIndex();
        // Clear our slot index so ObjectManager.freeSlot() won't release it
        // when this instance is removed. The explosion inherits the slot.
        setSlotIndex(-1);
        setDestroyed(true);
        DestructionEffects.destroyBadnik(currentX, currentY, spawn, mySlot,
                player, services(), getDestructionConfig());
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
    protected boolean isPlayerLeft(PlayableEntity player) {
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

    /**
     * Captures badnik movement state (position, velocity, animation cursor, facing)
     * into a {@link PerObjectRewindSnapshot.BadnikRewindExtra} record for rewind snapshots.
     * <p>
     * Subclasses with additional per-frame timers or state machine fields (multi-phase AI)
     * should override this method, call {@code super.captureRewindState()}, and wrap
     * the result with their own extra fields using custom record nesting.
     *
     * @return snapshot with badnik extra populated
     */
    @Override
    public PerObjectRewindSnapshot captureRewindState() {
        PerObjectRewindSnapshot base = super.captureRewindState();
        PerObjectRewindSnapshot.BadnikRewindExtra badnikExtra =
                new PerObjectRewindSnapshot.BadnikRewindExtra(
                        currentX, currentY, xVelocity, yVelocity,
                        animTimer, animFrame, facingLeft);
        // The record constructor with badnikExtra parameter; playerExtra is null for badniks
        return new PerObjectRewindSnapshot(
                base.destroyed(),
                base.destroyedRespawnable(),
                base.hasDynamicSpawn(),
                base.dynamicSpawnX(),
                base.dynamicSpawnY(),
                base.preUpdateX(),
                base.preUpdateY(),
                base.preUpdateValid(),
                base.preUpdateCollisionFlags(),
                base.skipTouchThisFrame(),
                base.solidContactFirstFrame(),
                base.slotIndex(),
                base.respawnStateIndex(),
                badnikExtra,
                null   // playerExtra is not applicable for badniks
        );
    }

    /**
     * Restores badnik movement state from a rewind snapshot.
     * <p>
     * Subclasses with additional state should override, call {@code super.restoreRewindState()},
     * and restore their own extra fields from the snapshot.
     *
     * @param s the snapshot to restore from
     */
    @Override
    public void restoreRewindState(PerObjectRewindSnapshot s) {
        super.restoreRewindState(s);
        if (s.badnikExtra() != null) {
            PerObjectRewindSnapshot.BadnikRewindExtra extra = s.badnikExtra();
            this.currentX = extra.currentX();
            this.currentY = extra.currentY();
            this.xVelocity = extra.xVelocity();
            this.yVelocity = extra.yVelocity();
            this.animTimer = extra.animTimer();
            this.animFrame = extra.animFrame();
            this.facingLeft = extra.facingLeft();
        }
    }
}
