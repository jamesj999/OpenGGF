package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ rising platform stub for {@code Obj_CNZRisingPlatform}.
 *
 * <p>ROM anchor: the platform uses {@code Map_CNZRisingPlatform} and
 * {@code ArtTile_CNZMisc+$6D}. Task 1 only claims the slot and sheet, but it
 * still renders frame 0 so the traversal prop remains visible.
 */
public final class CnzRisingPlatformInstance extends AbstractCnzTraversalVisibleStubInstance {
    public CnzRisingPlatformInstance(ObjectSpawn spawn) {
        super(spawn, "CNZRisingPlatform", Sonic3kObjectArtKeys.CNZ_RISING_PLATFORM);
    }
}
