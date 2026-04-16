package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ trap door stub for {@code Obj_CNZTrapDoor}.
 *
 * <p>ROM anchor: the platform uses {@code Map_CNZTrapDoor} and
 * {@code ArtTile_CNZMisc+$9F}. Task 1 only claims the slot and sheet, but it
 * still renders frame 0 so the object remains visible while behavior is stubbed.
 */
public final class CnzTrapDoorInstance extends AbstractCnzTraversalVisibleStubInstance {
    public CnzTrapDoorInstance(ObjectSpawn spawn) {
        super(spawn, "CNZTrapDoor", Sonic3kObjectArtKeys.CNZ_TRAP_DOOR);
    }
}
