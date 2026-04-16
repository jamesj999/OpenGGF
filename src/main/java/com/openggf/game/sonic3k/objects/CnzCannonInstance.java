package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ cannon stub for {@code Obj_CNZCannon}.
 *
 * <p>ROM anchor: the cannon uses {@code Map_CNZCannon} and
 * {@code ArtTile_CNZMisc+$23}. Task 1 only claims the slot and sheet, but it
 * still renders frame 0 to avoid regressing from placeholders to invisibility.
 */
public final class CnzCannonInstance extends AbstractCnzTraversalVisibleStubInstance {
    private static final int ROM_IDLE_FRAME = 9;

    public CnzCannonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCannon", Sonic3kObjectArtKeys.CNZ_CANNON);
    }

    @Override
    protected int initialFrameIndex() {
        // ROM init: move.b #9,mapping_frame(a0)
        return ROM_IDLE_FRAME;
    }
}
