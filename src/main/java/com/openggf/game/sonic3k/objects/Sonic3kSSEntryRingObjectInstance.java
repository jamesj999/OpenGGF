package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
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
    private static final int FORMATION_DELAY = 4;
    private static final int[] FORMATION_FRAMES = {0, 0, 1, 2, 3, 4, 5, 6, 7};

    // Idle animation: mapping frames 8-11, delay=6 game frames per anim frame
    // ROM: AniRaw_SSEntryRing anim 1: dc.b 6, $A, 9, 8, $B, $FC
    private static final int IDLE_DELAY = 6;
    private static final int[] IDLE_FRAMES = {10, 9, 8, 11};

    // Ring award when all emeralds already collected
    private static final int RING_REWARD = 50;

    /** Object states matching ROM routine progression. */
    private enum State {
        /** Formation animation playing, collision disabled. */
        FORMING,
        /** Idle animation, collision active. */
        IDLE,
        /** Player touched ring, flash animation playing, awaiting deletion mark. */
        ENTERED,
        /** Ring marked for deletion by flash (bit 5 in ROM). */
        MARKED_DELETE
    }

    /** Subtype is the bit index (0-31) into Collected_special_ring_array. */
    private final int bitIndex;

    private State state;

    /** Game frame counter for animation timing. */
    private int animTimer;

    /** Current index into the active animation frame array. */
    private int animIndex;

    /** Current mapping frame to display. */
    private int mappingFrame;

    public Sonic3kSSEntryRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSEntryRing");
        this.bitIndex = spawn.subtype();

        // ROM pre-check: if already collected, delete immediately
        GameStateManager gameState = GameServices.gameState();
        if (gameState.isSpecialRingCollected(bitIndex)) {
            setDestroyed(true);
            this.state = State.MARKED_DELETE;
            return;
        }

        this.state = State.FORMING;
        this.animTimer = 0;
        this.animIndex = 0;
        this.mappingFrame = FORMATION_FRAMES[0];
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case FORMING -> updateFormation(player);
            case IDLE -> updateIdle(player);
            case ENTERED -> { /* Ring continues displaying; flash controls deletion */ }
            case MARKED_DELETE -> {
                // ROM restores palette + reloads ArtKosM_BadnikExplosion here because
                // the ring's DPLC overwrites shared VRAM at ArtTile_Explosion. Our engine
                // uses standalone Pattern[] arrays so no restoration is needed.
                setDestroyed(true);
            }
        }
    }

    private void updateFormation(AbstractPlayableSprite player) {
        animTimer++;
        if (animTimer >= FORMATION_DELAY) {
            animTimer = 0;
            animIndex++;
            if (animIndex >= FORMATION_FRAMES.length) {
                state = State.IDLE;
                animIndex = 0;
                animTimer = 0;
                mappingFrame = IDLE_FRAMES[0];
                return;
            }
        }
        mappingFrame = FORMATION_FRAMES[animIndex];
    }

    private void updateIdle(AbstractPlayableSprite player) {
        // Advance idle animation
        animTimer++;
        if (animTimer >= IDLE_DELAY) {
            animTimer = 0;
            animIndex++;
            if (animIndex >= IDLE_FRAMES.length) {
                animIndex = 0;
            }
        }
        mappingFrame = IDLE_FRAMES[animIndex];

        // Check collision with player (ROM: mapping_frame >= 8 check)
        if (player != null) {
            checkCollision(player);
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
        GameStateManager gameState = GameServices.gameState();

        // Play sfx_BigRing ($B3) — always plays on touch
        AudioManager.getInstance().playSfx(Sonic3kSfx.BIG_RING.id);

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

        // Lock player: hidden + object controlled
        // ROM: move.b #$53,object_control(a2) — disables input
        // ROM: move.b #-1,(Player_prev_frame).w — makes player invisible
        player.setHidden(true);
        player.setObjectControlled(true);

        // Freeze camera at player's last position
        Camera.getInstance().setFrozen(true);

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
        GameServices.gameState().markSpecialRingCollected(bitIndex);
        LOGGER.fine("SSEntryRing #" + bitIndex + " marked for deletion by flash");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state == State.MARKED_DELETE) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
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
}
