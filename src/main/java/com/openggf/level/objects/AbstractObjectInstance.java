package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractObjectInstance implements ObjectInstance {
    private static final Logger LOG = Logger.getLogger(AbstractObjectInstance.class.getName());

    /**
     * Cached camera bounds, updated once per frame by ObjectManager.
     * Avoids repeated Camera.getInstance() calls when checking visibility.
     */
    private static CameraBounds cameraBounds = new CameraBounds(0, 0, 320, 224);

    protected final ObjectSpawn spawn;
    protected final String name;
    private boolean destroyed;

    protected AbstractObjectInstance(ObjectSpawn spawn, String name) {
        this.spawn = spawn;
        this.name = name;
    }

    /**
     * Updates the cached camera bounds in place. Called once per frame by ObjectManager
     * before any object updates run.
     *
     * @param verticalWrapRange Vertical wrap range in pixels (0 = no wrapping).
     *                          When > 0, Y visibility checks use modular arithmetic.
     */
    public static void updateCameraBounds(int left, int top, int right, int bottom, int verticalWrapRange) {
        cameraBounds.update(left, top, right, bottom);
        cameraBounds.setVerticalWrapRange(verticalWrapRange);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return spawn;
    }

    public String getName() {
        return name;
    }

    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Default no-op.
    }

    @Override
    public abstract void appendRenderCommands(List<GLCommand> commands);

    /**
     * Checks if this object is currently visible on screen.
     * ROM: render_flags.on_screen bit is set by MarkObjGone when object
     * is within camera bounds. Used by PlaySoundLocal (s2.asm line 1555)
     * to prevent off-screen objects from playing audio.
     * <p>
     * Uses pre-computed camera bounds (updated once per frame) for efficiency.
     *
     * @return true if the object is within the camera viewport
     */
    protected boolean isOnScreen() {
        return cameraBounds.contains(getX(), getY());
    }

    /**
     * Checks if this object's X is within the camera viewport.
     * Matches ROM's MarkObjGone which only checks X distance for the on_screen flag.
     * Use this for detection checks where Y proximity is handled separately.
     */
    protected boolean isOnScreenX() {
        return cameraBounds.containsX(getX());
    }

    protected boolean isOnScreenX(int margin) {
        return cameraBounds.containsX(getX(), margin);
    }

    /**
     * Checks if this object is within the camera viewport with a margin.
     * Useful for projectiles that should persist slightly off-screen.
     *
     * @param margin pixels of extra space beyond camera bounds
     * @return true if the object is within the extended camera viewport
     */
    protected boolean isOnScreen(int margin) {
        return cameraBounds.contains(getX(), getY(), margin);
    }

    /**
     * Adds a dynamically-created object to the level's object manager.
     * Safe to call in test environments where LevelManager may not be initialized.
     *
     * @param object the object instance to spawn
     */
    protected static void spawnDynamicObject(AbstractObjectInstance object) {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm != null && lm.getObjectManager() != null) {
                lm.getObjectManager().addDynamicObject(object);
            }
        } catch (Exception e) {
            LOG.fine("Could not spawn dynamic object (test env?): " + e.getMessage());
        }
    }

    /**
     * Builds an ObjectSpawn at the given position, preserving all other fields from the
     * original spawn. Use in getSpawn() overrides and dynamic spawn tracking.
     */
    protected ObjectSpawn buildSpawnAt(int x, int y) {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(),
                spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord());
    }

    /**
     * Returns true if any player is currently riding (standing on) this object.
     * Safe to call in test environments where LevelManager/ObjectManager may be null.
     */
    protected boolean isPlayerRiding() {
        LevelManager lm = LevelManager.getInstance();
        if (lm == null) return false;
        var om = lm.getObjectManager();
        return om != null && om.isAnyPlayerRiding(this);
    }

    /**
     * Returns the ObjectRenderManager, or null if not available.
     */
    protected static ObjectRenderManager getRenderManager() {
        LevelManager lm = LevelManager.getInstance();
        return (lm != null) ? lm.getObjectRenderManager() : null;
    }

    /**
     * Returns the ready PatternSpriteRenderer for the given art key, or null
     * if the render manager or renderer is unavailable/not ready.
     */
    protected static PatternSpriteRenderer getRenderer(String artKey) {
        ObjectRenderManager rm = getRenderManager();
        if (rm == null) return null;
        PatternSpriteRenderer renderer = rm.getRenderer(artKey);
        return (renderer != null && renderer.isReady()) ? renderer : null;
    }
}

