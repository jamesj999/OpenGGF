package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Object 0xED - Pachinko Item Orb.
 *
 * <p>ROM reference: {@code Obj_PachinkoItemOrb}. The orb animates until touched, then
 * resolves to a reward subtype based on the orb's Y position and frame counter and
 * turns into the shared {@link GumballItemObjectInstance} Pachinko reward object.
 */
public class PachinkoItemOrbObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener {

    private static final int COLLISION_FLAGS = 0x40 | 0x17;
    private static final int[] ANIMATION = {0, 1, 2, 3, 4, 3, 2, 1};
    private static final int[] REWARD_TABLE = {
            1, 3, 1, 3, 8, 3, 8, 5, 1, 3, 6, 4, 1, 7, 6, 5, 8, 6, 4, 3,
            4, 3, 4, 5, 8, 4, 5, 3, 7, 3, 8, 3, 6, 5, 6, 7, 4, 3, 7, 5,
            6, 4, 6, 4, 7, 3, 3, 5, 4, 3, 4, 6, 3, 4, 3, 7, 4, 3, 4, 3,
            4, 3, 4, 3
    };

    private boolean triggered;
    private int spawnDelayFrames = -1;
    private int animationFrameCounter;

    public PachinkoItemOrbObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoItemOrb");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        animationFrameCounter = frameCounter;
        if (spawnDelayFrames >= 0) {
            spawnDelayFrames--;
            if (spawnDelayFrames < 0) {
                spawnReward(frameCounter);
            }
        }
    }

    @Override
    public int getCollisionFlags() {
        return triggered ? 0 : COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return false;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (triggered) {
            return;
        }
        triggered = true;
        spawnDelayFrames = 1;
    }

    private void spawnReward(int frameCounter) {
        playSfx(Sonic3kSfx.BLUE_SPHERE);

        int yNibble = spawn.y() & 0x0F;
        int rewardIndex = ((yNibble << 2) + (frameCounter & 3)) & 0x3F;
        int rewardSubtype = REWARD_TABLE[rewardIndex];

        ObjectSpawn rewardSpawn = new ObjectSpawn(
                spawn.x(), spawn.y(), spawn.objectId() - 2, rewardSubtype,
                spawn.renderFlags(), false, 0, spawn.layoutIndex());
        spawnChild(() -> GumballItemObjectInstance.createPachinkoItem(rewardSpawn));
        setDestroyed(true);
    }

    private void playSfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_ITEM_ORB);
        if (renderer == null || spawnDelayFrames >= 0) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        int frame = ANIMATION[animationFrameCounter & 0x7];
        renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
