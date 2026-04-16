package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ Balloon stub for {@code Obj_CNZBalloon}.
 *
 * <p>Task 1 only claims the slot, object name, and ROM-backed art sheet
 * registration. The actual lift/ride behavior remains for a later task, but the
 * visible object still renders frame 0 from {@code Map_CNZBalloon} so CNZ does
 * not regress to invisible traversal props.
 */
public final class CnzBalloonInstance extends AbstractCnzTraversalVisibleStubInstance {
    public CnzBalloonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZBalloon", Sonic3kObjectArtKeys.CNZ_BALLOON);
    }

    @Override
    protected int initialFrameIndex() {
        // ROM init sets anim = (subtype * 2) & $E; the matching animation
        // scripts start at mapping frames 0, 5, 10, 15, and 20 respectively.
        int balloonVariant = Math.min(4, spawn.subtype() & 0x0F);
        return balloonVariant * 5;
    }
}
