package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.SpringHelper;
import com.openggf.level.objects.BoxObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Springboard / Lever Spring (Object 0x40).
 * A red diving-board style springboard found in CPZ, ARZ, and MCZ.
 * <p>
 * When the player stands on the "high" side (the raised end), the platform
 * compresses and launches them upward with position-dependent velocity.
 * Standing closer to the high end gives a stronger launch.
 * <p>
 * Based on Sonic 2 disassembly s2.asm lines 51757-51971.
 * <p>
 * Launch sequence (ROM):
 * 1. SlopedSolid sets p1_standing_bit on initial contact
 * 2. loc_2641E checks standing bit + high side -> starts COMPRESSED anim
 * 3. Standing bit persists via fast-path (X range check only, no side/standing re-eval)
 * 4. When anim==1 AND mapping_frame==0, player is launched
 * <p>
 * Engine implementation:
 * The ROM uses a standing bit that persists without re-evaluating standing vs side
 * contact. Our SolidContacts re-resolves contact type each frame, which can produce
 * SIDE contacts on sloped surfaces near edges. To match ROM behavior, the launch
 * sequence is driven from update() using the riding state (equivalent to the ROM's
 * standing bit) rather than relying on onSolidContact() receiving STANDING every frame.
 */
public class SpringboardObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    // Animation IDs (from Ani_obj40)
    private static final int ANIM_IDLE = 0;       // byte_265EC: delay=0xF, frames=[0], LOOP
    private static final int ANIM_COMPRESSED = 1; // byte_265EF: delay=3, frames=[1,0], SWITCH to 0

    /**
     * Diagonal slope data (idle state - frame 0).
     * From s2.asm Obj40_SlopeData_DiagUp (40 bytes).
     */
    private static final byte[] SLOPE_DIAG_UP = {
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x09,
            0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x14, 0x15, 0x15, 0x16,
            0x17, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
            0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18
    };

    /**
     * Straight slope data (compressed state - frame 1).
     * From s2.asm Obj40_SlopeData_Straight (40 bytes).
     */
    private static final byte[] SLOPE_STRAIGHT = {
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x09,
            0x0A, 0x0B, 0x0C, 0x0C, 0x0C, 0x0C, 0x0D, 0x0D,
            0x0D, 0x0D, 0x0D, 0x0D, 0x0E, 0x0E, 0x0F, 0x0F,
            0x10, 0x10, 0x10, 0x10, 0x0F, 0x0F, 0x0E, 0x0E,
            0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D
    };

    /**
     * Position-to-boost lookup table (72 bytes).
     * From s2.asm byte_26550.
     * Maps relative X position to a boost value (0-4).
     */
    private static final byte[] VELOCITY_BOOST_TABLE = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3, 3, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    // Collision dimensions from ROM (Obj40_Main)
    // d1 = $27 (39) half-width, d2 = 8 height
    private static final int COLLISION_HALF_WIDTH = 0x27;
    private static final int COLLISION_HEIGHT = 8;

    // Position threshold for launch trigger (0x10 pixels from center)
    // ROM: loc_2641E checks player.x vs springboard.x ± 0x10
    private static final int POSITION_THRESHOLD = 0x10;

    private final ObjectAnimationState animationState;
    private int mappingFrame;

    /**
     * Tracks whether a launch sequence is in progress for a player.
     * In the ROM, this is implicit via the p1_standing_bit in the object's status
     * register, which persists across frames as long as the player stays within
     * the object's X range and is not airborne. The ROM's fast-path in
     * SlopedSolid_SingleCharacter only checks X range (not side vs standing),
     * so the standing bit is never cleared by edge-proximity contact type changes.
     * <p>
     * Our SolidContacts re-evaluates contact type each frame, which can produce
     * SIDE contacts on the sloped surface near edges at high speeds. This flag
     * allows the launch sequence to continue in update() independently.
     */
    private boolean launchSequenceActive;
    private AbstractPlayableSprite launchPlayer;

    public SpringboardObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, COLLISION_HALF_WIDTH, COLLISION_HEIGHT, 1.0f, 0.85f, 0.1f, false);

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getAnimations(Sonic2ObjectArtKeys.ANIM_SPRINGBOARD) : null,
                ANIM_IDLE,
                0);
        this.mappingFrame = 0;
    }

    /**
     * Called when player has solid contact with the springboard.
     * This initiates the launch sequence when the player first stands on the high side.
     * Once initiated, the sequence is continued by update() using the riding state
     * (ROM standing bit equivalent), not by subsequent onSolidContact calls.
     * <p>
     * ROM: loc_2641E is called when p1_standing_bit is set in object's status.
     */
    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || !contact.standing()) {
            return;
        }

        // Don't process if player is already springing (post-launch).
        // ROM's SlopedSolid_SingleCharacter does NOT check airborne before setting
        // the standing bit — it only checks X range. The getAir() guard is omitted
        // to match ROM behavior: at high speed, terrain micro-gaps can set air=true
        // for single frames while SolidContacts still resolves STANDING contact.
        if (player.getSpringing()) {
            return;
        }

        // If launch sequence is already in progress, update() handles continuation
        if (launchSequenceActive && launchPlayer == player) {
            return;
        }

        // ROM: loc_2641E - Check if player is on the "high" side
        if (!isPlayerOnHighSide(player)) {
            return;
        }

        // ROM: loc_26446 - Start the compress animation and begin launch sequence
        if (animationState.getAnimId() != ANIM_COMPRESSED) {
            animationState.setAnimId(ANIM_COMPRESSED);
            launchSequenceActive = true;
            launchPlayer = player;
        }
    }

    /**
     * Checks if the player is on the "high" side of the springboard.
     * ROM: loc_2641E (unflipped) and loc_26436 (flipped)
     *
     * NOTE: ROM uses x_pos(a1) which is player CENTER X, not top-left.
     * Must use getCentreX() to match ROM behavior.
     */
    private boolean isPlayerOnHighSide(AbstractPlayableSprite player) {
        int playerX = player.getCentreX();
        int springboardX = spawn.x();

        if (isFlippedHorizontal()) {
            // ROM: loc_26436 - d0 = springboard.x + 0x10, bhs if d0 >= player.x
            // Flipped: high side is on the left, player.x <= springboard.x + 0x10
            return playerX <= springboardX + POSITION_THRESHOLD;
        } else {
            // ROM: loc_2641E - d0 = springboard.x - 0x10, blo if d0 < player.x
            // Unflipped: high side is on the right, player.x > springboard.x - 0x10
            return playerX > springboardX - POSITION_THRESHOLD;
        }
    }

    /**
     * Applies launch velocity and sets player state.
     * ROM: loc_2645E through loc_26546
     */
    private void applyLaunch(AbstractPlayableSprite player) {
        boolean flipped = isFlippedHorizontal();

        // ROM: loc_2645E - Calculate relative X position for boost lookup
        // d0 = player.x - (springboard.x - 0x1C) = player.x - springboard.x + 0x1C
        // NOTE: ROM uses x_pos(a1) which is player CENTER X
        int dx = player.getCentreX() - spawn.x() + 0x1C;

        if (flipped) {
            // ROM bug preserved: uses 0x27 instead of 0x38 (2*0x1C)
            dx = ~dx + 0x27;
        }

        // ROM: loc_2647A - Clamp negative values to 0
        if (dx < 0) {
            dx = 0;
        }

        // Clamp to table bounds (72 entries)
        if (dx >= VELOCITY_BOOST_TABLE.length) {
            dx = VELOCITY_BOOST_TABLE.length - 1;
        }

        // ROM: loc_26480 - Look up boost value (0-4)
        int boost = VELOCITY_BOOST_TABLE[dx] & 0xFF;

        // ROM: move.w #-$400,y_vel(a1) then sub.b d0,y_vel(a1)
        // sub.b from high byte = y_vel -= boost << 8
        int yVelocity = -0x400 - (boost << 8);
        player.setYSpeed((short) yVelocity);

        // ROM: bset #status.player.x_flip,status(a1) (set facing right)
        // Then if springboard NOT flipped: bclr (clear = face left), neg.b d0
        if (flipped) {
            player.setDirection(Direction.LEFT);
        } else {
            player.setDirection(Direction.RIGHT);
            boost = -boost; // ROM: neg.b d0
        }

        // ROM: loc_264AA - Apply X velocity boost if |x_vel| >= 0x400
        int xVel = player.getXSpeed();
        if (Math.abs(xVel) >= 0x400) {
            // ROM: sub.b d0,x_vel(a1) - subtract from high byte = x_vel -= boost << 8
            player.setXSpeed((short) (xVel - (boost << 8)));
        }

        // ROM: loc_264BC - Set player to airborne state
        player.setAir(true);
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);

        // ROM: move.b #0,spindash_flag(a1) - Clear spindash flag
        player.setSpindash(false);

        // ROM: move.b #AniIDSonAni_Spring,anim(a1) - Set Spring animation first
        player.setAnimationId(Sonic2AnimationIds.SPRING);

        // ROM: Animation override based on subtype bit 0 (twirl flag)
        // If bit 0 set: Override to Walk animation with flip/twirl effect
        int subtype = spawn.subtype();
        if ((subtype & 0x01) != 0) {
            // ROM: loc_264D4-26508 - Twirl animation setup
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.setFlipAngle(1);
            player.setFlipSpeed(8);
            // ROM: bit 1 controls flip count - 3 flips if clear, 1 flip if set
            player.setFlipsRemaining((subtype & 0x02) != 0 ? 1 : 3);

            // ROM: move.w #1,inertia(a1) - Set inertia for twirl
            short inertia = 1;

            // ROM: Negate flip_angle and inertia if player facing left
            if (player.getDirection() == Direction.LEFT) {
                player.setFlipAngle(-player.getFlipAngle());
                inertia = -1;  // ROM: neg.w inertia(a1)
            }
            player.setGSpeed(inertia);
        } else {
            // ROM: No twirl - clear inertia
            player.setGSpeed((short) 0);
        }

        // ROM: Clear on_object status (player no longer standing on springboard)
        // This is handled by the physics engine when we set air=true

        // ROM: loc_2651E-26546 - Set collision layer based on subtype bits 2-3
        SpringHelper.applyCollisionLayerBits(player, subtype);

        // ROM: loc_26546 - Play spring sound
        try {
            if (AudioManager.getInstance() != null) {
                AudioManager.getInstance().playSfx(GameSound.SPRING);
            }
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: d1=$27 (half-width), d2=8 (height)
        return new SolidObjectParams(COLLISION_HALF_WIDTH, COLLISION_HEIGHT, COLLISION_HEIGHT);
    }

    @Override
    public byte[] getSlopeData() {
        // ROM: Obj40_Main selects slope data based on mapping_frame
        // frame 0 = diagonal (idle), frame 1 = straight (compressed)
        return mappingFrame == 0 ? SLOPE_DIAG_UP : SLOPE_STRAIGHT;
    }

    @Override
    public boolean isSlopeFlipped() {
        return isFlippedHorizontal();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ROM: Obj40_Main calls AnimateSprite before collision check
        animationState.update();
        mappingFrame = animationState.getMappingFrame();

        // ROM: After SlopedSolid_SingleCharacter, loc_2641E runs if standing bit set.
        // The standing bit persists via the fast-path (X range only) without re-evaluating
        // standing vs side contact. We use our own launchSequenceActive flag to match this,
        // driving the launch sequence from update() rather than onSolidContact().
        if (launchSequenceActive && launchPlayer != null) {
            processLaunchSequence();
        }
    }

    /**
     * Continues the launch sequence each frame, matching the ROM's loc_2641E behavior.
     * <p>
     * In the ROM, this runs every frame the player's standing bit is set on this object.
     * The standing bit only clears when the player goes airborne or leaves X range.
     * Our SolidContacts may produce SIDE contacts on the sloped surface, but the ROM
     * never re-evaluates standing vs side for an already-standing player.
     */
    private void processLaunchSequence() {
        AbstractPlayableSprite lp = launchPlayer;

        // ROM: SlopedSolid_SingleCharacter fast-path checks:
        // - Player springing -> already launched, clear standing bit
        // - Player outside X range -> clear standing bit
        // Note: ROM does NOT clear standing bit on airborne alone — only X range
        // and springing matter. This allows the launch to complete even if terrain
        // micro-gaps briefly set air=true at high speed.
        if (lp.getSpringing()) {
            clearLaunchSequence();
            return;
        }

        // ROM fast-path X range check (s2.asm:34909-34914):
        // relX = player.x - object.x + halfWidth
        // if relX < 0 or relX >= width: clear standing
        int relX = lp.getCentreX() - spawn.x() + COLLISION_HALF_WIDTH;
        int width = COLLISION_HALF_WIDTH * 2;
        if (relX < 0 || relX >= width) {
            clearLaunchSequence();
            return;
        }

        // ROM: loc_2641E - Check if player is still on the "high" side
        if (!isPlayerOnHighSide(lp)) {
            // Player moved to low side - clear launch but keep standing
            clearLaunchSequence();
            return;
        }

        // ROM: loc_26446 - Check animation state
        int currentAnim = animationState.getAnimId();
        if (currentAnim != ANIM_COMPRESSED) {
            // Animation switched back (shouldn't normally happen during sequence)
            animationState.setAnimId(ANIM_COMPRESSED);
            return;
        }

        // ROM: loc_26456 - anim is 1, check if mapping_frame is 0
        if (mappingFrame == 0) {
            // ROM: loc_2645E - Launch the player!
            applyLaunch(lp);
            clearLaunchSequence();
        }
        // If mapping_frame != 0, wait for animation to cycle to frame 0
    }

    private void clearLaunchSequence() {
        launchSequenceActive = false;
        launchPlayer = null;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SPRINGBOARD);
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }

        boolean hFlip = isFlippedHorizontal();
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
