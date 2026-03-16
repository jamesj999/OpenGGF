package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
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
        // Stub: art loading is handled in Chunk 6.
        // Render a small placeholder line for the post.
        float r = 0.6f, g = 0.6f, b = 0.3f;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, currentX, currentY - 4, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, currentX, currentY + 12, 0, 0));
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
