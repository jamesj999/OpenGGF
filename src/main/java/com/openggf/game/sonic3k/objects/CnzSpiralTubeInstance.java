package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ spiral tube stub for {@code Obj_CNZSpiralTube}.
 *
 * <p>ROM anchor: controller-only object flow in {@code Obj_CNZSpiralTube}.
 * This task claims the slot but not a dedicated traversal art sheet.
 */
public final class CnzSpiralTubeInstance extends AbstractObjectInstance {
    public CnzSpiralTubeInstance(ObjectSpawn spawn) {
        super(spawn, "CNZSpiralTube");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
