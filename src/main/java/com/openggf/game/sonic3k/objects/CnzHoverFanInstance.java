package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ hover fan stub for {@code Obj_CNZHoverFan}.
 *
 * <p>ROM anchor: the fan uses {@code Map_CNZHoverFan} and
 * {@code ArtTile_CNZMisc+$97}. Task 1 only claims the slot and sheet.
 */
public final class CnzHoverFanInstance extends AbstractObjectInstance {
    public CnzHoverFanInstance(ObjectSpawn spawn) {
        super(spawn, "CNZHoverFan");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
