package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ rising platform stub for {@code Obj_CNZRisingPlatform}.
 *
 * <p>ROM anchor: the platform uses {@code Map_CNZRisingPlatform} and
 * {@code ArtTile_CNZMisc+$6D}. Task 1 only claims the slot and sheet.
 */
public final class CnzRisingPlatformInstance extends AbstractObjectInstance {
    public CnzRisingPlatformInstance(ObjectSpawn spawn) {
        super(spawn, "CNZRisingPlatform");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 1: object slot scaffolding only.
    }
}
