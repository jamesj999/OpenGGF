package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ Balloon stub for {@code Obj_CNZBalloon}.
 *
 * <p>Task 1 only claims the slot, object name, and ROM-backed art sheet
 * registration. The actual lift/ride behavior remains for a later task.
 */
public final class CnzBalloonInstance extends AbstractObjectInstance {
    public CnzBalloonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZBalloon");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
