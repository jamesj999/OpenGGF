package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ cylinder stub for {@code Obj_CNZCylinder}.
 *
 * <p>ROM anchor: the cylinder uses {@code Map_CNZCylinder} and
 * {@code ArtTile_CNZMisc+$3D}. Task 1 only claims the slot and sheet, but it
 * still renders frame 0 so the object remains visible while transport logic is pending.
 */
public final class CnzCylinderInstance extends AbstractCnzTraversalVisibleStubInstance {
    public CnzCylinderInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCylinder", Sonic3kObjectArtKeys.CNZ_CYLINDER);
    }
}
