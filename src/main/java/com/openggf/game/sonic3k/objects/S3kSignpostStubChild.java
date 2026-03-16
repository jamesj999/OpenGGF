package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Signpost stub/post child object.
 *
 * <p>Simple child that follows the signpost with a Y offset of +0x18,
 * representing the post/pole beneath the spinning sign face.
 * Self-destroys when the parent signpost is destroyed.
 */
public class S3kSignpostStubChild extends AbstractObjectInstance {

    private static final int Y_OFFSET = 0x18;

    private final S3kSignpostInstance parent;
    private int currentX;
    private int currentY;

    public S3kSignpostStubChild(S3kSignpostInstance parent) {
        super(null, "S3kSignpostStub");
        this.parent = parent;
        syncPosition();
    }

    @Override
    public boolean isPersistent() {
        return true;
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
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (parent == null || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }
        syncPosition();
    }

    private void syncPosition() {
        if (parent != null) {
            currentX = parent.getWorldX();
            currentY = parent.getWorldY() + Y_OFFSET;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getStubRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        // Single frame (frame 0) at stub position
        renderer.drawFrameIndex(0, currentX, currentY, false, false);
    }

    private PatternSpriteRenderer getStubRenderer() {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm != null) {
                ObjectRenderManager orm = lm.getObjectRenderManager();
                if (orm != null) {
                    return orm.getRenderer(Sonic3kObjectArtKeys.SIGNPOST_STUB);
                }
            }
        } catch (Exception ignored) {
            // Render manager unavailable (e.g. headless test)
        }
        return null;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
