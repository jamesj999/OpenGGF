package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ Miniboss (0x90) - Debris fragment.
 * Six instances spawned with specific offsets from camera position.
 * Uses AIZ_MINIBOSS_SMALL art key with 3 frames.
 * Has a wait phase then drifts upward, eventually self-destructs.
 */
public class AizMinibossDebrisChild extends AbstractObjectInstance {
    private int worldX;
    private int worldY;
    private final int mappingFrame;
    private int timer;
    private boolean drifting;
    private int lifeTimer;

    public AizMinibossDebrisChild(int x, int y, int yVel, int mappingFrame) {
        super(new ObjectSpawn(x, y, 0x90, 0, 0, false, 0), "AIZMinibossDebris");
        this.worldX = x;
        this.worldY = y;
        this.mappingFrame = mappingFrame;
        this.timer = yVel; // wait time = velocity value
        this.drifting = false;
        this.lifeTimer = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        lifeTimer++;
        if (!drifting) {
            timer--;
            if (timer <= 0) {
                drifting = true;
            }
        } else {
            worldY -= 1; // drift upward
            if (lifeTimer > 300) {
                setDestroyed(true);
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS_SMALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, worldX, worldY, false, false);
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }
}
