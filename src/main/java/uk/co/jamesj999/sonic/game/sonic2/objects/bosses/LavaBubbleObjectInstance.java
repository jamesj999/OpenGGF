package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
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
public class LavaBubbleObjectInstance extends AbstractObjectInstance implements TouchResponseProvider {

    // Animation constants
    private static final int ANIM_DELAY = 8;  // Frames between animation changes
    private static final int FRAME_COUNT = 2; // Total animation frames (uses Obj20 fireball frames)

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
        super(new ObjectSpawn(x, y, Sonic2ObjectIds.LAVA_BUBBLE, 0, 0, false, 0), "Lava Bubble");
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
    public int getCollisionProperty() {
        return 0;
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SOL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // ROM: Obj20 uses ArtNem_HtzFireball1 after transformation.
        // SOL sheet frames 3 and 4 are the two fireball frames using the same art source.
        int frame = 3 + animFrame;

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
