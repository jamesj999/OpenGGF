package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ hover fan stub for {@code Obj_CNZHoverFan}.
 *
 * <p>ROM anchor: the fan uses {@code Map_CNZHoverFan} and
 * {@code ArtTile_CNZMisc+$97}. Task 1 only claims the slot and sheet, but it
 * still renders frame 0 so the fan remains visible while lift behavior is pending.
 */
public final class CnzHoverFanInstance extends AbstractCnzTraversalVisibleStubInstance {
    public CnzHoverFanInstance(ObjectSpawn spawn) {
        super(spawn, "CNZHoverFan", Sonic3kObjectArtKeys.CNZ_HOVER_FAN);
    }

    @Override
    protected int initialFrameIndex() {
        // Negative subtypes route through the horizontal hover-fan variant and
        // seed mapping_frame from subtype bits 4-6. Non-negative subtypes leave
        // the default frame 0 until behavior-driven animation takes over.
        if ((spawn.subtype() & 0x80) == 0) {
            return 0;
        }
        return (spawn.subtype() & 0x70) >> 4;
    }
}
