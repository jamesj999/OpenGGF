package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Lava Bubble - Spawned when HTZ boss lava ball hits the ground.
 * ROM Reference: s2.asm:64016-64060 (Obj20_LavaBubble transformation)
 * <p>
 * The lava bubble stays in place where the lava ball hit, animates with a
 * bubbling effect, and damages the player on contact. Despawns after ~120 frames.
 * <p>
 * Uses collision flags 0x98 (enemy projectile with size).
 */
public class LavaBubbleObjectInstance extends AbstractObjectInstance {

    // Animation constants
    private static final int ANIM_DELAY = 8;  // Frames between animation changes
    private static final int FRAME_COUNT = 2; // Total animation frames (cycles frames 14-15)

    // Lifetime constant (ROM: approximately 2 seconds)
    private static final int LIFETIME = 120;

    // Collision constants (ROM: move.b #$98,collision_flags(a1))
    private static final int COLLISION_FLAGS = 0x98;

    private final LevelManager levelManager;

    private int x;
    private int y;
    private int animFrame;
    private int animTimer;
    private int lifetime;

    /**
     * Creates a lava bubble at the specified position.
     *
     * @param x X position where lava ball hit ground
     * @param y Y position (ground level)
     */
    public LavaBubbleObjectInstance(int x, int y) {
        super(new ObjectSpawn(x, y, 0x20, 0, 0, false, 0), "Lava Bubble");
        this.levelManager = LevelManager.getInstance();
        this.x = x;
        this.y = y;
        this.animFrame = 0;
        this.animTimer = ANIM_DELAY;
        this.lifetime = LIFETIME;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Update animation (bubbling effect)
        animTimer--;
        if (animTimer <= 0) {
            animTimer = ANIM_DELAY;
            animFrame = (animFrame + 1) % FRAME_COUNT;
        }

        // Check lifetime
        lifetime--;
        if (lifetime <= 0) {
            setDestroyed(true);
        }
    }

    /**
     * Returns collision flags for touch response.
     * ROM: move.b #$98,collision_flags(a1) - enemy projectile collision
     */
    public int getCollisionFlags() {
        if (isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        ObjectRenderManager renderManager = levelManager != null
                ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.HTZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Lava bubble uses same frames as lava ball (frames 14-15)
        // ROM: Obj20 transformation uses Obj52 art tiles for visual consistency
        // These are the large lava ball frames ($E, $F) that look like bubbling lava
        int frame = 14 + animFrame;

        renderer.drawFrameIndex(frame, x, y, false, false);
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
        return 4;  // Same priority as boss and lava balls
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), 0, spawn.respawnTracked(), spawn.rawYWord());
    }
}
