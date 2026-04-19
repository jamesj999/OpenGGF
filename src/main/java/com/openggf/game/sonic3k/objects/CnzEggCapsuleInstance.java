package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Thin CNZ-local wrapper for the standard ground egg capsule.
 *
 * <p>ROM anchor: {@code Obj_EggCapsule} as used by {@code Obj_CNZEndBoss}.
 *
 * <p>CNZ must not reuse {@link Aiz2EndEggCapsuleInstance} because that class
 * models the camera-relative floating route-8 capsule from AIZ2. It also
 * should not inherit the HCZ-specific geyser follow-up from
 * {@code HczEndBossEggCapsuleInstance}. Task 8 therefore keeps CNZ on a thin
 * local wrapper whose responsibility is only to exist at a fixed world
 * position, render the shared capsule art, and provide the explicit object
 * spawned by the CNZ end-boss defeat handoff.
 *
 * <p>The full button/open/results choreography remains deferred. Task 8 only
 * needs the fixed-position capsule handoff, so this class intentionally stops
 * before claiming complete egg-capsule parity.
 */
public final class CnzEggCapsuleInstance extends AbstractObjectInstance {
    private static final int BUTTON_Y_OFFSET = -0x24;

    private final int centreX;
    private final int centreY;

    public CnzEggCapsuleInstance(ObjectSpawn spawn) {
        super(spawn, "CNZEggCapsule");
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    @Override
    public int getX() {
        return centreX;
    }

    @Override
    public int getY() {
        return centreY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Task 8 owns only the spawn/handoff seam. The full open sequence is deferred.
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer == null) {
            return;
        }

        /**
         * Shared S3K ground capsule layout: body frame 0 with the button drawn
         * on top at {@code y - $24}. Those frame numbers match the existing
         * ground-capsule implementations and keep the CNZ wrapper visually
         * aligned without inheriting HCZ's later geyser logic.
         */
        renderer.drawFrameIndex(0, centreX, centreY, false, false);
        renderer.drawFrameIndex(5, centreX, centreY + BUTTON_Y_OFFSET, false, false);
    }
}
