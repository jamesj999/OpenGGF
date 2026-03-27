package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
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
 * Sonic 3&K Special Stage Entry Ring (Obj_SSEntryRing, object ID 0x85).
 * <p>
 * The giant gold ring that warps the player into a Special Stage when touched.
 * Each ring has a subtype (0-31) used as a bit index into the 32-bit
 * {@code Collected_special_ring_array} bitfield. If the ring's bit is already
 * set, it is deleted immediately on spawn.
 * <p>
 * Animation (AniRaw_SSEntryRing):
 * <ul>
 *   <li>Formation (anim 0): delay=4, frames 0,0,1,2,3,4,5,6,7, loop</li>
 *   <li>Idle (anim 1): delay=6, frames 10,9,8,11, loop</li>
 * </ul>
 * <p>
 * On touch (SSEntryRing_Main collision):
 * <ol>
 *   <li>Play sfx_BigRing</li>
 *   <li>Branch based on emerald state:
 *     <ul>
 *       <li>Chaos emeralds &lt; 7: enter Special Stage (full flash sequence)</li>
 *       <li>All emeralds collected: award 50 rings, ring vanishes immediately</li>
 *       <li>TODO: Hidden Palace route (subtype bit 7, or S3+7chaos+7super)</li>
 *     </ul>
 *   </li>
 *   <li>For Special Stage path: lock player (hidden + object controlled),
 *       freeze camera, spawn {@link Sonic3kSSEntryFlashObjectInstance} which
 *       handles the flash animation, wait, sfx_EnterSS, and transition</li>
 * </ol>
 * <p>
 * Art: ArtUnc_SSEntryRing (9984 bytes), Map_SSEntryRing (12 frames),
 * DPLC_SSEntryRing (12 frames). art_tile = make_art_tile(ArtTile_Explosion,1,0).
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm Obj_SSEntryRing (lines 128211-128530)
 */
public class Sonic3kSSEntryRingObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSSEntryRingObjectInstance.class.getName());

    // Collision extents from center (ROM: SSEntry_Range: dc.w -$18, $30, -$28, $50)
    // Asymmetric box: X [-24, +48], Y [-40, +80] relative to ring center
    private static final int COLLISION_X_MIN = -0x18;  // -24
    private static final int COLLISION_X_MAX =  0x30;  //  48
    private static final int COLLISION_Y_MIN = -0x28;  // -40
    private static final int COLLISION_Y_MAX =  0x50;  //  80

    // ROM: render_flags = 4 (on-screen check), priority = $280 (bucket 5)
    private static final int RENDER_PRIORITY = 5;

    // Formation animation: mapping frames 0-7, delay=4 game frames per anim frame
    // ROM: AniRaw_SSEntryRing anim 0: dc.b 4, 0, 0, 1, 2, 3, 4, 5, 6, 7, $F8, $0C
    // ROM Animate_Raw uses down-counter: delay N means N+1 game frames per anim frame
    private static final int FORMATION_DELAY = 4;
    private static final int[] FORMATION_FRAMES = {0, 0, 1, 2, 3, 4, 5, 6, 7};

    // Idle animation: mapping frames 8-11, delay=6 game frames per anim frame
    // ROM: AniRaw_SSEntryRing anim 1: dc.b 6, $A, 9, 8, $B, $FC
    private static final int IDLE_DELAY = 6;
    private static final int[] IDLE_FRAMES = {10, 9, 8, 11};

    // ROM collision gate: mapping_frame must be >= 8 for collision to be active.
    // Formation frames are 0-7, idle frames are 8-11. This is the definitive guard
    // matching sonic3k.asm line 128261: cmpi.b #8,mapping_frame(a0) / blo.s locret_61708
    private static final int COLLISION_FRAME_THRESHOLD = 8;

    // Ring award when all emeralds already collected
    private static final int RING_REWARD = 50;

    /** Object states matching ROM routine progression. */
    private enum State {
        /**
         * Main state: ring animates (formation then idle) and checks collision.
         * Matches ROM's SSEntryRing_Main (routine 2) which handles BOTH formation
         * and idle via Animate_Raw + mapping_frame >= 8 gate.
         */
        MAIN,
        /** Player touched ring, flash animation playing, awaiting deletion mark. */
        ENTERED,
        /** Ring marked for deletion by flash (bit 5 in ROM). */
        MARKED_DELETE
    }

    /** Subtype is the bit index (0-31) into Collected_special_ring_array. */
    private final int bitIndex;

    private State state;
    private boolean initialized;

    /**
     * Animation down-counter matching ROM's Animate_Raw.
     * Starts at the delay value and decrements each frame.
     * When it underflows below 0, reload from current delay and advance frame.
     * This gives delay+1 game frames per animation frame (ROM-accurate).
     */
    private int animTimer;

    /** Current index into the active animation frame array. */
    private int animIndex;

    /** Current mapping frame to display. */
    private int mappingFrame;

    /** Which animation is active: formation (false) or idle (true). */
    private boolean inIdleAnim;

    public Sonic3kSSEntryRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSEntryRing");
        this.bitIndex = spawn.subtype();

        // Default to MAIN state; ensureInitialized will check collection status
        this.state = State.MAIN;
        this.inIdleAnim = false;
        this.animTimer = 0;
        this.animIndex = 0;
        this.mappingFrame = FORMATION_FRAMES[0];
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        // ROM pre-check: if already collected, delete immediately
        var gameState = services().gameState();
        if (gameState.isSpecialRingCollected(bitIndex)) {
            setDestroyed(true);
            this.state = State.MARKED_DELETE;
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        ensureInitialized();
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case MAIN -> updateMain(player);
            case ENTERED -> { /* Ring continues displaying; flash controls deletion */ }
            case MARKED_DELETE -> {
                // ROM restores palette + reloads ArtKosM_BadnikExplosion here because
                // the ring's DPLC overwrites shared VRAM at ArtTile_Explosion. Our engine
                // uses standalone Pattern[] arrays so no restoration is needed.
                setDestroyed(true);
            }
        }
    }

    /**
     * Combined formation + idle handler matching ROM's SSEntryRing_Main.
     * <p>
     * ROM flow (sonic3k.asm line 128257-128269):
     * <ol>
     *   <li>{@code jsr (Animate_Raw).l} — advance animation</li>
     *   <li>{@code cmpi.b #8,mapping_frame(a0) / blo.s locret} — gate collision on frame number</li>
     *   <li>{@code jsr (Check_PlayerInRange).l} — manual range check</li>
     * </ol>
     * <p>
     * ROM also calls {@code Obj_WaitOffscreen} before this routine, which prevents
     * ALL processing while the ring is off-screen. We replicate this by skipping
     * the entire update when the ring is not visible.
     */
    private void updateMain(AbstractPlayableSprite player) {
        // ROM: Obj_WaitOffscreen — skip all processing while off-screen.
        // This prevents the formation animation from advancing before the player
        // can see the ring, ensuring the full grow-in is always visible.
        if (!isOnScreen(OFFSCREEN_MARGIN)) {
            return;
        }

        // ROM: jsr (Animate_Raw).l — advance animation using down-counter
        advanceAnimation();

        // ROM: tst.w (Debug_placement_mode).w / bne.s locret_61708
        // ROM explicitly disables big ring collision in debug mode.
        if (player != null && player.isDebugMode()) {
            return;
        }

        // ROM: cmpi.b #8,mapping_frame(a0) / blo.s locret_61708
        // If ring hasn't finished forming (mapping_frame < 8), don't allow collision.
        if (mappingFrame < COLLISION_FRAME_THRESHOLD) {
            return;
        }

        // ROM: Check_PlayerInRange — manual range collision
        if (player != null) {
            checkCollision(player);
        }
    }

    /** Margin for on-screen check matching ROM's Obj_WaitOffscreen tolerance. */
    private static final int OFFSCREEN_MARGIN = 128;

    /**
     * Advances animation matching ROM's Animate_Raw down-counter pattern.
     * Timer starts at delay value, decrements each frame. When it underflows
     * below 0, the delay is reloaded and the frame index advances.
     * This gives delay+1 game frames per animation frame (ROM-accurate).
     */
    private void advanceAnimation() {
        animTimer--;
        if (animTimer >= 0) {
            return; // Still counting down — hold current frame
        }

        // Timer underflowed: advance to next frame
        if (!inIdleAnim) {
            // Currently in formation animation
            animIndex++;
            if (animIndex >= FORMATION_FRAMES.length) {
                // Formation complete → transition to idle animation
                // ROM: $F8,$0C command jumps to idle animation data
                inIdleAnim = true;
                animIndex = 0;
                animTimer = IDLE_DELAY;
                mappingFrame = IDLE_FRAMES[0];
                return;
            }
            animTimer = FORMATION_DELAY;
            mappingFrame = FORMATION_FRAMES[animIndex];
        } else {
            // Currently in idle animation (looping)
            animIndex++;
            if (animIndex >= IDLE_FRAMES.length) {
                animIndex = 0; // ROM: $FC = loop to start
            }
            animTimer = IDLE_DELAY;
            mappingFrame = IDLE_FRAMES[animIndex];
        }
    }

    /**
     * Checks collision between the player and this ring.
     * Uses center-to-center distance matching the ROM's SSEntry_Range box.
     */
    private void checkCollision(AbstractPlayableSprite player) {
        // ROM: skip if player dead (routine >= 6)
        if (player.getDead()) {
            return;
        }

        int playerCX = player.getCentreX();
        int playerCY = player.getCentreY();
        int ringX = spawn.x();
        int ringY = spawn.y();

        int dx = playerCX - ringX;
        int dy = playerCY - ringY;

        if (dx >= COLLISION_X_MIN && dx < COLLISION_X_MAX
                && dy >= COLLISION_Y_MIN && dy < COLLISION_Y_MAX) {
            onTouched(player);
        }
    }

    /**
     * Handle ring touch. Branches based on emerald state.
     * <p>
     * ROM branching (SSEntryRing_Main lines 128272-128323):
     * <ul>
     *   <li>Chaos emeralds &lt; 7 → enter Special Stage</li>
     *   <li>SK_alone + 7 chaos → award 50 rings</li>
     *   <li>S3 level + 7 chaos → enter Special Stage (for super emeralds)</li>
     *   <li>SK level + 7 chaos + 7 super → award 50 rings</li>
     *   <li>Subtype bit 7 set → Hidden Palace (TODO)</li>
     * </ul>
     */
    private void onTouched(AbstractPlayableSprite player) {
        LOGGER.fine(() -> String.format(
                "SSEntryRing #%d TOUCHED at (%d,%d) — mappingFrame=%d, inIdleAnim=%b, player(%d,%d)",
                bitIndex, spawn.x(), spawn.y(), mappingFrame, inIdleAnim,
                player.getCentreX(), player.getCentreY()));
        var gameState = services().gameState();

        // Play sfx_BigRing ($B3) — always plays on touch
        services().playSfx(Sonic3kSfx.BIG_RING.id);

        // TODO: Hidden Palace route — subtype bit 7, or S3 completed + 7 chaos + 7 super
        // When implemented: check (bitIndex & 0x80) != 0 or SSEntry_CheckLevel + emerald state
        // and route to HPZ (zone 0x17, act 0x01) instead of special stage.

        if (gameState.hasAllEmeralds()) {
            // Path B: All emeralds collected — award 50 rings, instant delete
            LOGGER.fine("SSEntryRing #" + bitIndex + " — all emeralds, awarding 50 rings");
            gameState.markSpecialRingCollected(bitIndex);
            player.addRings(RING_REWARD);
            setDestroyed(true);
        } else {
            // Path A: Enter Special Stage — full flash sequence
            // ROM: loc_61774 — lock player, spawn flash
            LOGGER.fine("SSEntryRing #" + bitIndex + " — entering Special Stage sequence");
            enterSpecialStageSequence(player);
        }
    }

    /**
     * Initiates the special stage entry sequence.
     * ROM: SSEntryRing_Main lines 128287-128305
     * <ol>
     *   <li>Lock player: object_control=$53, anim=$1C, Player_prev_frame=-1</li>
     *   <li>Freeze camera at current position</li>
     *   <li>Spawn Obj_SSEntryFlash child</li>
     *   <li>Ring enters ENTERED state (continues displaying until flash marks it)</li>
     * </ol>
     */
    private void enterSpecialStageSequence(AbstractPlayableSprite player) {
        state = State.ENTERED;

        // ROM: Save_Level_Data2 — save player position at ring for return from SS.
        // This is separate from checkpoint state (ROM: Saved_ vs Saved2_).
        var camera = services().camera();
        services().saveBigRingReturnPosition(
                player.getCentreX(), player.getCentreY(),
                camera.getX(), camera.getY());

        // Lock player: hidden + object controlled
        // ROM: move.b #$53,object_control(a2) — disables input
        // ROM: move.b #-1,(Player_prev_frame).w — makes player invisible
        player.setHidden(true);
        player.setObjectControlled(true);

        // Freeze camera at player's last position
        camera.setFrozen(true);

        // Spawn flash child object
        // ROM: direction bit is set on the ring (not flash) based on player approach,
        // but has no visual effect since flash uses internal h-flip toggle.
        spawnDynamicObject(new Sonic3kSSEntryFlashObjectInstance(
                this, spawn.x(), spawn.y()));
    }

    /**
     * Called by {@link Sonic3kSSEntryFlashObjectInstance} at anim_frame 3
     * to mark this ring for deletion.
     * ROM: bset #5,$38(a1) — sets deletion flag on parent ring.
     */
    public void markForDeletion() {
        state = State.MARKED_DELETE;
        services().gameState().markSpecialRingCollected(bitIndex);
        LOGGER.fine("SSEntryRing #" + bitIndex + " marked for deletion by flash");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state == State.MARKED_DELETE) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.SS_ENTRY_RING);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
                return;
            }
        }

        // Fallback wireframe if art not loaded
        appendFallbackRing(commands);
    }

    private void appendFallbackRing(List<GLCommand> commands) {
        int cx = spawn.x();
        int cy = spawn.y();
        int hw = 24, hh = 40;
        float r = 1.0f, g = 0.85f, b = 0.2f;
        addLine(commands, cx, cy - hh, cx + hw, cy, r, g, b);
        addLine(commands, cx + hw, cy, cx, cy + hh, r, g, b);
        addLine(commands, cx, cy + hh, cx - hw, cy, r, g, b);
        addLine(commands, cx - hw, cy, cx, cy - hh, r, g, b);
    }

    private void addLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, x2, y2, 0, 0));
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(RENDER_PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        // Ring must persist while flash animation is playing
        return state == State.ENTERED;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return state == State.ENTERED;
    }

    // --- Package-visible accessors for testing ---

    /** Returns the current mapping frame index (for verifying animation state). */
    int getMappingFrame() {
        return mappingFrame;
    }

    /** Returns true if the ring is in formation (mapping_frame < 8, collision disabled). */
    boolean isForming() {
        return state == State.MAIN && mappingFrame < COLLISION_FRAME_THRESHOLD;
    }

    /** Returns true if the ring is in the main state (formation or idle). */
    boolean isMainState() {
        return state == State.MAIN;
    }
}
