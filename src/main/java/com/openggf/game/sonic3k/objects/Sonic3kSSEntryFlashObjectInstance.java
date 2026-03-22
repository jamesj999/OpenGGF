package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 3&K Special Stage Entry Flash (Obj_SSEntryFlash).
 * <p>
 * Spawned by {@link Sonic3kSSEntryRingObjectInstance} when the player touches
 * the big ring. Plays a flash animation at the ring's position, marks the
 * parent ring for deletion mid-animation, waits 32 frames, then triggers
 * the special stage transition.
 * <p>
 * Animation (AniRaw_SSEntryFlash):
 * delay=0, frames: 0, 0, 1, 2, 3(hflip), 3, 2, 1, 0, then $F4 (call finished routine).
 * Total: 9 animation frames at 1 game frame each.
 * <p>
 * At anim_frame index 3 (when mapping_frame changes for the 4th time):
 * marks parent ring for deletion (ROM: bset #5,$38(a1)).
 * <p>
 * After animation: wait 32 frames ($20), then play sfx_EnterSS and
 * trigger fade-to-white special stage entry.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm lines 128330-128423
 */
public class Sonic3kSSEntryFlashObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSSEntryFlashObjectInstance.class.getName());

    // AniRaw_SSEntryFlash: dc.b 0, 0, 0, 1, 2, 3|$40, 3, 2, 1, 0, $F4
    // Delay=0 (1 game frame per anim frame), bit 6 ($40) = h-flip toggle on frame 3.
    // Animate_RawAdjustFlipX uses bchg (toggle), so once $40 fires at index 4,
    // render_flags bit 0 stays set for all subsequent frames.
    private static final int[] ANIM_FRAMES = {0, 0, 1, 2, 3, 3, 2, 1, 0};
    private static final boolean[] ANIM_HFLIP = {false, false, false, false, true, true, true, true, true};

    // At this anim_frame index, mark parent ring for deletion
    // ROM: cmpi.b #3,anim_frame(a0) — checks the 0-based frame counter
    private static final int RING_DELETE_ANIM_INDEX = 3;

    // Wait 32 frames after animation before triggering special stage
    // ROM: move.w #$20,$2E(a0)
    private static final int POST_ANIM_WAIT = 0x20;

    private enum State { ANIMATING, WAITING, DONE }

    private final Sonic3kSSEntryRingObjectInstance parentRing;

    private State state = State.ANIMATING;
    private int animIndex = 0;
    private int waitTimer = 0;
    private boolean ringDeleteTriggered = false;

    /**
     * @param parentRing  the parent ring object to mark for deletion
     * @param x           center X position
     * @param y           center Y position
     */
    public Sonic3kSSEntryFlashObjectInstance(
            Sonic3kSSEntryRingObjectInstance parentRing,
            int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSEntryFlash");
        this.parentRing = parentRing;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case ANIMATING -> updateAnimation();
            case WAITING -> updateWait();
            case DONE -> { }
        }
    }

    private void updateAnimation() {
        // Check for ring deletion trigger (ROM: at anim_frame == 3)
        if (animIndex == RING_DELETE_ANIM_INDEX && !ringDeleteTriggered) {
            ringDeleteTriggered = true;
            if (parentRing != null) {
                parentRing.markForDeletion();
            }
        }

        animIndex++;
        if (animIndex >= ANIM_FRAMES.length) {
            // Animation complete → enter wait state
            // ROM: SSEntryFlash_Finished → Obj_Wait with $20 frames
            state = State.WAITING;
            waitTimer = POST_ANIM_WAIT;
        }
    }

    private void updateWait() {
        waitTimer--;
        if (waitTimer <= 0) {
            // ROM: SSEntryFlash_GoSS — plays sfx_EnterSS then enters special stage.
            // The SFX is played by GameLoop.enterSpecialStage() via
            // Sonic3kSpecialStageProvider.getTransitionSfxId() (sfx_EnterSS = $AF),
            // so we just trigger the request here.
            state = State.DONE;
            GameServices.level().requestSpecialStageEntry();
            LOGGER.fine("SSEntryFlash: triggering special stage entry");
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (state == State.DONE || state == State.WAITING) {
            return;
        }
        if (animIndex < 0 || animIndex >= ANIM_FRAMES.length) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.SS_ENTRY_FLASH);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int mappingFrame = ANIM_FRAMES[animIndex];
        // ROM: h-flip is internal to flash via bchg toggle, not affected by approach direction
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), ANIM_HFLIP[animIndex], false);
    }

    @Override
    public int getPriorityBucket() {
        // ROM: priority = $280 (bucket 5), same as ring
        return RenderPriority.clamp(5);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return true;
    }
}
