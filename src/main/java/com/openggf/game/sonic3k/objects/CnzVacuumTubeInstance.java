package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ vacuum tube stub for {@code Obj_CNZVacuumTube}.
 *
 * <p>ROM anchor: controller-only object flow in {@code Obj_CNZVacuumTube}.
 * This task claims the slot but not a dedicated traversal art sheet.
 */
public final class CnzVacuumTubeInstance extends AbstractObjectInstance {
    public CnzVacuumTubeInstance(ObjectSpawn spawn) {
        super(spawn, "CNZVacuumTube");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
