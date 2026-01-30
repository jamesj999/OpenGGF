package uk.co.jamesj999.sonic.level.objects.boss;

import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

/**
 * Base class for boss child components with common functionality.
 * Child components are full object instances so they can participate in collision and rendering buckets.
 */
public abstract class AbstractBossChild extends AbstractObjectInstance implements BossChildComponent {
    protected final AbstractBossInstance parent;
    protected int currentX;
    protected int currentY;
    protected int priority;
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

    public int getCurrentX() {
        return currentX;
    }

    public int getCurrentY() {
        return currentY;
    }
}
