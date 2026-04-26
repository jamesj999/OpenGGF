package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Temporary AIZ1-to-AIZ2 fire transition floor.
 *
 * <p>ROM: {@code Obj_AIZTransitionFloor} at sonic3k.asm:104777. Spawned by
 * {@code AIZ1BGE_FireTransition} at x=$2FB0/y=$3A0 after the AIZ2 art queues,
 * then calls {@code SolidObjectTop} with d1=$A0, d2=$10, d3=$10.
 */
public final class AizTransitionFloorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider {
    private static final int X = 0x2FB0;
    private static final int Y = 0x03A0;
    private static final int OBJECT_ID = 0x00;
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(0xA0, 0x10, 0x10);

    public AizTransitionFloorObjectInstance() {
        super(new ObjectSpawn(X, Y, OBJECT_ID, 0, 0, false, 0), "AIZTransitionFloor");
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // ROM deletes the helper once Current_act is non-zero.
        if (services().currentAct() != 0) {
            setDestroyed(true);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean usesCollisionHalfWidthForTopLanding() {
        return true;
    }

    @Override
    public boolean providesPreMovementGroundAttachmentSupport() {
        return true;
    }

    @Override
    public boolean isSkipSolidContactThisFrame() {
        // Obj_AIZTransitionFloor sets status bit 7 before calling SolidObjectTop,
        // so it is solid on its first execution frame.
        return false;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible ROM helper object.
    }
}
