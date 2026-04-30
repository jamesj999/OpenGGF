package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.game.PlayableEntity;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
    private static final int FIRE_REFRESH_ZERO_REJECTS = 20;
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(0xA0, 0x10, 0x10);
    private final Map<PlayableEntity, Integer> zeroDistanceRejects = new IdentityHashMap<>(2);

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
    public boolean rejectsZeroDistanceTopSolidLanding(PlayableEntity player) {
        // ROM keeps this helper active through AIZ1BGE_FireRefresh
        // (sonic3k.asm:104690-104714). During that handoff it repeatedly runs
        // SolidObjectTop (104777-104790), rejecting the exact-surface boundary
        // until the fire-refresh window reaches the landing frame. The accepted
        // first landing then uses the standard SolidObjectTop placement
        // (41982-42015): y_pos += d0 + 3, putting Sonic one pixel inside the
        // transition floor before the standing branch carries him on later
        // frames (41642-41679, 41793-41818).
        return zeroDistanceRejects.getOrDefault(player, 0) < FIRE_REFRESH_ZERO_REJECTS;
    }

    @Override
    public void onRejectedZeroDistanceTopSolidLanding(PlayableEntity player) {
        if (player != null) {
            zeroDistanceRejects.merge(player, 1, Integer::sum);
        }
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
