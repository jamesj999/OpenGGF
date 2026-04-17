package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x44 - CNZ Trap Door ({@code Obj_CNZTrapDoor}).
 * <p>
 * ROM behavior:
 * <ul>
 *   <li>{@code Map_CNZTrapDoor} with {@code ArtTile_CNZMisc+$9F}</li>
 *   <li>Uses {@code SolidObjectTop} every frame while closed/opening</li>
 *   <li>Checks both players against the 64x32 trigger box in {@code sub_30D8C}</li>
 *   <li>Plays {@code sfx_TrapDoor} when the trigger box is entered</li>
 *   <li>Animates via {@code Ani_CNZTrapDoor} and returns to the closed frame</li>
 * </ul>
 *
 * <p>Coordinates use the ROM x_pos/y_pos center semantics directly. The engine-side
 * object position therefore stays centered on the spawn point; no top-left conversion
 * is applied here.
 */
public final class CnzTrapDoorInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int PRIORITY = 0x80;

    // ROM: move.w #$20,d1 / move.w #9,d3 / jsr SolidObjectTop
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(0x20, 9, 9);

    // ROM: sub_30D8C trigger window is effectively [-31, +32] on both axes.
    private static final int TRIGGER_MIN_DELTA = -0x1F;
    private static final int TRIGGER_MAX_DELTA = 0x20;

    private static final int FRAME_CLOSED = 0;
    private static final int FRAME_OPENING = 1;
    private static final int FRAME_OPEN = 2;

    // ROM: Ani_CNZTrapDoor uses 5-frame timing on the open sequence.
    private static final int OPEN_STEP_FRAMES = 5;

    private int renderFrame = FRAME_CLOSED;
    private int state = FRAME_CLOSED;
    private int stateTimer = 0;

    public CnzTrapDoorInstance(ObjectSpawn spawn) {
        super(spawn, "CNZTrapDoor");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite
                ? (AbstractPlayableSprite) playerEntity
                : null;

        if (state == FRAME_CLOSED && shouldTrigger(player)) {
            startOpening();
        }

        advanceAnimation();
    }

    private boolean shouldTrigger(AbstractPlayableSprite player) {
        if (player == null || player.isObjectControlled()) {
            return false;
        }

        // ROM trigger box from sub_30D8C:
        //   x: objX - playerX + $20, unsigned compare against $40
        //   y: objY - playerY + $20, unsigned compare against $20
        // That maps to deltas of [-31, +32] on both axes.
        int dx = player.getCentreX() - spawn.x();
        int dy = player.getCentreY() - spawn.y();
        return dx >= TRIGGER_MIN_DELTA && dx <= TRIGGER_MAX_DELTA
                && dy >= TRIGGER_MIN_DELTA && dy <= TRIGGER_MAX_DELTA;
    }

    private void startOpening() {
        state = FRAME_OPENING;
        renderFrame = FRAME_OPENING;
        stateTimer = OPEN_STEP_FRAMES;

        try {
            services().playSfx(Sonic3kSfx.TRAP_DOOR.id);
        } catch (Exception ignored) {
            // Audio is unavailable in some test setups.
        }
    }

    private void advanceAnimation() {
        if (state == FRAME_CLOSED) {
            renderFrame = FRAME_CLOSED;
            return;
        }

        if (stateTimer > 0) {
            stateTimer--;
            return;
        }

        if (state == FRAME_OPENING) {
            state = FRAME_OPEN;
            renderFrame = FRAME_OPEN;
            stateTimer = OPEN_STEP_FRAMES;
            return;
        }

        state = FRAME_CLOSED;
        renderFrame = FRAME_CLOSED;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return state != FRAME_OPEN;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contact.standing() || contact.touchTop()) {
            // ROM still runs SolidObjectTop every frame, so standing contact is enough
            // to trip the door even if the player is not inside the trigger box yet.
            if (state == FRAME_CLOSED) {
                startOpening();
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.CNZ_TRAP_DOOR);
        if (renderer != null && renderer.isReady()) {
            boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
            boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
            renderer.drawFrameIndex(renderFrame, spawn.x(), spawn.y(), hFlip, vFlip);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    int getRenderFrameForTest() {
        return renderFrame;
    }

    boolean isOpenForTest() {
        return state != FRAME_CLOSED;
    }
}
