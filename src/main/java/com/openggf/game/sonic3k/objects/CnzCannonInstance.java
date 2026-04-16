package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ cannon stub for {@code Obj_CNZCannon}.
 *
 * <p>ROM anchor: the cannon uses {@code Map_CNZCannon} and
 * {@code ArtTile_CNZMisc+$23}. Task 1 only claims the slot and sheet.
 */
public final class CnzCannonInstance extends AbstractObjectInstance {
    public CnzCannonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCannon");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
