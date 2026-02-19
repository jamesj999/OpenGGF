package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
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
 * AIZ Miniboss (0x90) - Flame projectile.
 * ROM: Obj_AIZMiniboss_Flame, collision_flags=0x8B (HURT + size 0x0B),
 * shield_reaction bit 4 set (deflectable by shields).
 * Spawned at barrel position + offset. Uses AIZ_MINIBOSS_FLAME art key
 * with 5 animation frames. Animates through frames then self-destructs.
 */
public class AizMinibossFlameChild extends AbstractObjectInstance
        implements TouchResponseProvider {

    // ROM: collision_flags = $8B (HURT category 0x80 | size index 0x0B)
    private static final int COLLISION_FLAGS = 0x8B;
    // ROM: shield_reaction bit 4 set (deflectable by shield)
    private static final int SHIELD_REACTION = 1 << 3;
    private int worldX;
    private int worldY;
    private int animFrame;
    private int animTimer;
    private int lifeTimer;
    private final int staggerDelay;

    public AizMinibossFlameChild(int x, int y, int index) {
        super(new ObjectSpawn(x, y, 0x90, 0, 0, false, 0), "AIZMinibossFlame");
        this.worldX = x;
        this.worldY = y;
        this.animFrame = 0;
        this.animTimer = 0;
        this.lifeTimer = 0;
        this.staggerDelay = index * 4; // stagger spawn timing
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (lifeTimer < staggerDelay) {
            lifeTimer++;
            return; // stagger delay
        }

        lifeTimer++;
        worldY += 1; // slight downward drift

        // Animate through 5 frames
        animTimer++;
        if (animTimer >= 4) {
            animTimer = 0;
            animFrame++;
            if (animFrame >= 5) {
                setDestroyed(true);
                return;
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (lifeTimer < staggerDelay) {
            return;
        }
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS_FLAME);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(animFrame, worldX, worldY, false, false);
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        if (lifeTimer < staggerDelay || isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION;
    }

    @Override
    public boolean onShieldDeflect(AbstractPlayableSprite player) {
        setDestroyed(true);
        return true;
    }
}
