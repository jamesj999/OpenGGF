package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ Act 2 water-helper wrapper for the cork floor trigger.
 *
 * <p>ROM anchor: {@code Obj_CNZWaterLevelCorkFloor}.
 *
 * <p>The disassembly spawns a real {@code Obj_CorkFloor}, waits for it to go
 * away, then performs a one-shot write of {@code $0958} into
 * {@code Target_water_level}. The same routine also sets the helper latch that
 * the button object checks later. Task 7 keeps just that one-shot side effect
 * and the latch handoff, because the generic cork-floor rendering and break
 * logic already lives elsewhere in the engine.
 */
public final class CnzWaterLevelCorkFloorInstance extends AbstractObjectInstance {
    /**
     * ROM: {@code move.w #$958,(Target_water_level).w}.
     */
    private static final int FIRST_WATER_TARGET_Y = 0x0958;

    private boolean floorReleasedForTest;

    public CnzWaterLevelCorkFloorInstance(ObjectSpawn spawn) {
        super(spawn, "CNZWaterLevelCorkFloor");
    }

    /**
     * Test seam that simulates the child cork floor having completed its own
     * destruction path.
     */
    public void forceFloorReleasedForTest() {
        floorReleasedForTest = true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!floorReleasedForTest) {
            return;
        }
        floorReleasedForTest = false;

        /**
         * ROM: the helper arms the later button path at the same time it raises
         * the first water target. Keeping both writes in the CNZ bridge avoids a
         * hidden object-local dependency between the two helper objects.
         */
        S3kCnzEventWriteSupport.setWaterButtonArmed(services(), true);
        S3kCnzEventWriteSupport.setWaterTargetY(services(), FIRST_WATER_TARGET_Y);
        setDestroyed(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No bespoke rendering needed for the Task 7 trigger wrapper.
    }
}
