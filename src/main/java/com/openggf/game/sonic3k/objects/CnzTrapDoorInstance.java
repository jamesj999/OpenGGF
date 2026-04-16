package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ trap door stub for {@code Obj_CNZTrapDoor}.
 *
 * <p>ROM anchor: the platform uses {@code Map_CNZTrapDoor} and
 * {@code ArtTile_CNZMisc+$9F}. Task 1 only claims the slot and sheet.
 */
public final class CnzTrapDoorInstance extends AbstractObjectInstance {
    public CnzTrapDoorInstance(ObjectSpawn spawn) {
        super(spawn, "CNZTrapDoor");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
