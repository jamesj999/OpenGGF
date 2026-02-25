package com.openggf.level.objects.boss;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.awt.Color;

/**
 * Base class for boss child components with common functionality.
 * Child components are full object instances so they can participate in collision and rendering buckets.
 */
public abstract class AbstractBossChild extends AbstractObjectInstance implements BossChildComponent {
    protected final AbstractBossInstance parent;
    protected int currentX;
    protected int currentY;
    protected int priority;
    /** Collision flags set by parent via initChildCollisions/removeAllCollision */
    protected int collisionFlags;
    /** X-flip state, set by parent's updateChildFacing() */
    protected boolean flipX;
    private ObjectSpawn dynamicSpawn;
    private int lastUpdatedFrame = -1;

    public AbstractBossChild(AbstractBossInstance parent, String name, int priority, int objectId) {
        super(new ObjectSpawn(parent.getX(), parent.getY(), objectId, 0, 0, false, 0), name);
        this.parent = parent;
        this.priority = priority;
        syncPositionWithParent();
        this.dynamicSpawn = super.getSpawn();
        updateDynamicSpawn();
    }

    protected boolean beginUpdate(int frameCounter) {
        if (lastUpdatedFrame == frameCounter) {
            return false;
        }
        lastUpdatedFrame = frameCounter;
        return true;
    }

    protected void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
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
        return RenderPriority.clamp(priority);
    }

    @Override
    public void syncPositionWithParent() {
        if (parent != null && !parent.isDestroyed()) {
            this.currentX = parent.getX();
            this.currentY = parent.getY();
        }
    }

    protected boolean shouldUpdate(int frameCounter) {
        if (parent != null && parent.getState().lastUpdatedFrame != frameCounter) {
            return false;
        }
        return beginUpdate(frameCounter);
    }

    @Override
    public boolean isDestroyed() {
        if (parent == null || parent.isDestroyed()) {
            setDestroyed(true);
        }
        return super.isDestroyed();
    }

    @Override
    public void setDestroyed(boolean destroyed) {
        super.setDestroyed(destroyed);
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Set collision flags on this child (used by parent boss for init/remove collision).
     * Individual child getCollisionFlags() implementations should check this field.
     */
    public void setCollisionFlags(int flags) {
        this.collisionFlags = flags;
    }

    /**
     * Get the stored collision flags value (set by parent).
     */
    public int getStoredCollisionFlags() {
        return collisionFlags;
    }

    /**
     * Set the x-flip state on this child (used by parent's updateChildFacing).
     * ROM: loc_3E168 copies body's render_flags.x_flip to all children.
     */
    public void setFlipX(boolean flip) {
        this.flipX = flip;
    }

    /**
     * Get the x-flip state of this child.
     */
    public boolean isFlipX() {
        return flipX;
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Blue cross marker at position
        ctx.drawCross(currentX, currentY, 4, 0.3f, 0.3f, 1f);
        // Cyan name label
        ctx.drawWorldLabel(currentX, currentY, -1, name, Color.CYAN);
    }

    public int getCurrentX() {
        return currentX;
    }

    public int getCurrentY() {
        return currentY;
    }

    /**
     * Set the child's position directly.
     * Used by parent bosses with articulated body parts (e.g., DEZ Robot).
     */
    public void setPosition(int x, int y) {
        this.currentX = x;
        this.currentY = y;
    }
}
