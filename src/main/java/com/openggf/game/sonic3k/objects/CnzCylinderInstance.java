package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ cylinder stub for {@code Obj_CNZCylinder}.
 *
 * <p>ROM anchor: the cylinder uses {@code Map_CNZCylinder} and
 * {@code ArtTile_CNZMisc+$3D}. Task 1 only claims the slot and sheet.
 */
public final class CnzCylinderInstance extends AbstractObjectInstance {
    public CnzCylinderInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCylinder");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
