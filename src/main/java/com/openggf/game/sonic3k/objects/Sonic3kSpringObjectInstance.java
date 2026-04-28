package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.audio.GameSound;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.SpringHelper;
import com.openggf.level.objects.SpringBounceHelper;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.GroundMode;
import com.openggf.physics.Direction;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x07 - Spring (Sonic 3 &amp; Knuckles).
 * <p>
 * ROM reference: sonic3k.asm Obj_Spring (lines 47500-48400).
 * Nearly identical to S2 Obj41 with these S3K-specific differences:
 * <ul>
 *   <li>Down spring velocity cap: red down springs cap at $D00 (not $1000)</li>
 *   <li>Horizontal approach detection zone (±$28 x, ±$18 y)</li>
 *   <li>Reverse gravity support (DEZ gravity-flip, currently stubbed)</li>
 *   <li>Art loaded from level patterns (ArtTile_SpikesSprings offsets)</li>
 * </ul>
 * <p>
 * Subtype encoding (identical to S2):
 * <ul>
 *   <li>Bits 4-6: Direction (shifted &gt;&gt;3 &amp; 0xE): 0=Up, 2=Horiz, 4=Down, 6=DiagUp, 8=DiagDown</li>
 *   <li>Bit 1: Strength (0=red/-$1000, 1=yellow/-$A00)</li>
 *   <li>Bits 2-3: Plane switch (00=none, 01=path A, 10=path B)</li>
 *   <li>Bit 0: Corkscrew flip flag</li>
 *   <li>Bit 7: Kill opposite velocity</li>
 * </ul>
 */
public class Sonic3kSpringObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    // Subtype constants (shifted >> 3 & 0xE) - matches ROM Obj_Spring index
    private static final int TYPE_UP = 0;
    private static final int TYPE_HORIZONTAL = 2;
    private static final int TYPE_DOWN = 4;
    private static final int TYPE_DIAGONAL_UP = 6;
    private static final int TYPE_DIAGONAL_DOWN = 8;

    // Diagonal slope data (from sonic3k.asm Obj_Spring_DiagHeightMap)
    private static final byte[] SLOPE_DIAG_UP = {
            0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10,
            0x10, 0x10, 0x10, 0x10, 0x0E, 0x0C, 0x0A, 0x08,
            0x06, 0x04, 0x02, 0x00, (byte) 0xFE, (byte) 0xFC, (byte) 0xFC, (byte) 0xFC,
            (byte) 0xFC, (byte) 0xFC, (byte) 0xFC, (byte) 0xFC
    };
    private static final byte[] SLOPE_DIAG_DOWN = {
            (byte) 0xF4, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0,
            (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0,
            (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0,
            (byte) 0xF2, (byte) 0xF4, (byte) 0xF6, (byte) 0xF8,
            (byte) 0xFA, (byte) 0xFC, (byte) 0xFE, 0x00,
            0x02, 0x04, 0x04, 0x04,
            0x04, 0x04, 0x04, 0x04
    };

    // Per-variant animation IDs (0-based within each variant's 3-frame sheet)
    private static final int ANIM_IDLE = 0;
    private static final int ANIM_TRIGGERED = 1;

    // Horizontal approach detection zone (sonic3k.asm sub_2326C)
    private static final int HORIZ_DETECT_X = 0x28;
    private static final int HORIZ_DETECT_Y = 0x18;

    private int springType;
    private final boolean redSpring;
    private final ObjectAnimationState animationState;
    private int mappingFrame;
    private boolean initialized;

    public Sonic3kSpringObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Spring");
        this.redSpring = (spawn.subtype() & 0x02) == 0;

        // Base type from subtype bits (gravity swap deferred to ensureInitialized)
        this.springType = (spawn.subtype() >> 3) & 0xE;

        this.mappingFrame = 0;
        this.animationState = new ObjectAnimationState(buildAnimationSet(), ANIM_IDLE, 0);
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (player == null) {
            return;
        }

        if (springType == TYPE_DIAGONAL_UP) {
            if (!contact.standing()) {
                return;
            }
            if (!isPlayerOnUpDiagonalSpringLaunchSide(player)) {
                return;
            }
            applyDiagonalSpring(player, true);
            return;
        }

        if (springType == TYPE_DIAGONAL_DOWN) {
            if (!contact.touchBottom()) {
                return;
            }
            applyDiagonalSpring(player, false);
            return;
        }

        if (springType == TYPE_HORIZONTAL) {
            if (!contact.touchSide() || !isPlayerOnHorizontalSpringActiveSide(player)) {
                return;
            }
            applyHorizontalSpring(player);
            return;
        }

        if (springType == TYPE_DOWN) {
            if (!contact.touchBottom()) {
                return;
            }
            applyDownSpring(player);
            return;
        }

        // Default: Up spring
        if (!contact.standing()) {
            return;
        }
        applyUpSpring(player);
    }

    /**
     * ROM: Obj_Spring_Up (sonic3k.asm)
     * - addq.w #8,y_pos(a1)
     * - move.w objoff_30(a0),y_vel(a1) [negative = up]
     * - bset #status.player.in_air
     */
    private void applyUpSpring(AbstractPlayableSprite player) {
        // ROM updates y_pos (centre coordinate) with a word-sized add, so preserve y_sub.
        player.setCentreYPreserveSubpixel((short) (player.getCentreY() + 8));
        player.setYSpeed((short) getStrength());
        player.setAir(true);

        // ROM: sub_22F98 line 47729-47731 - if bit 7 set, clear x velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }

        player.recordMgzTopPlatformSpringHandoff(player.getXSpeed(), player.getYSpeed());
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);
        trigger(player);
    }

    /**
     * ROM: Obj_Spring_Down
     * S3K-specific: red down springs cap at $D00 instead of $1000.
     */
    private void applyDownSpring(AbstractPlayableSprite player) {
        // ROM updates y_pos (centre coordinate) with a word-sized subtract, so preserve y_sub.
        player.setCentreYPreserveSubpixel((short) (player.getCentreY() - 8));

        // ROM negates strength for down springs (positive = down)
        int yVel = -getStrength();
        // S3K-specific: red down spring velocity cap at $D00
        if (yVel == -SpringBounceHelper.STRENGTH_RED) {
            yVel = 0x0D00;
        }
        player.setYSpeed((short) yVel);

        player.setAir(true);

        // ROM: sub_233CA line 48103-48105 - if bit 7 set, clear x velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }

        player.recordMgzTopPlatformSpringHandoff(player.getXSpeed(), player.getYSpeed());
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);
        trigger(player);
    }

    /**
     * ROM: Obj_Spring_Horizontal (sonic3k.asm)
     * - move.w objoff_30(a0),x_vel(a1) [starts negative]
     * - addq.w #8,x_pos(a1) / subi.w #$10 if not flipped
     * - Sets gSpeed = x_vel, control lock 15 frames
     * - Does NOT set airborne
     */
    private void applyHorizontalSpring(AbstractPlayableSprite player) {
        int strength = getStrength(); // starts negative
        boolean flipped = isFlippedHorizontal();

        // ROM updates x_pos (centre coordinate) with word-sized add/sub, so preserve x_sub.
        int newX = player.getCentreX() + 8;
        Direction dir = Direction.RIGHT;

        if (!flipped) {
            newX -= 16;
            strength = -strength; // now positive (right)
        } else {
            dir = Direction.LEFT;
        }

        player.setCentreXPreserveSubpixel((short) newX);
        player.setXSpeed((short) strength);
        player.setDirection(dir);
        // sub_2326C reaches sub_23190 only after Status_InAir is clear.
        // Engine landing handoff can arrive here before normal floor cleanup.
        player.setAir(false);
        player.setAngle((byte) 0);
        player.setGroundMode(GroundMode.GROUND);

        // ROM: Horizontal springs set gSpeed = x_vel, stay grounded
        player.setGSpeed((short) strength);

        // ROM: If bit 7 set, clear Y velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setYSpeed((short) 0);
        }

        player.recordMgzTopPlatformSpringHandoff(player.getXSpeed(), player.getYSpeed());
        // ROM: move.w #$F,$32(a1) - lock player control while grounded.
        player.setMoveLockTimer(SpringBounceHelper.CONTROL_LOCK_FRAMES);

        trigger(player);
    }

    /**
     * ROM: sub_2350A - Diagonal springs apply both X and Y velocity.
     * Also nudges player position by ±6px to separate from the spring surface.
     * ROM: addq.w #6,y_pos(a1) / addq.w #6,x_pos(a1) / [subi.w #12 if not flipped]
     */
    private void applyDiagonalSpring(AbstractPlayableSprite player, boolean up) {
        int strength = getStrength(); // negative base
        boolean flipped = isFlippedHorizontal();

        int newY = player.getCentreY() + (up ? 6 : -6);
        int newX = player.getCentreX() + 6;
        if (!flipped) {
            newX -= 12;
        }
        player.setCentreXPreserveSubpixel((short) newX);
        player.setCentreYPreserveSubpixel((short) newY);

        int xStrength = flipped ? strength : -strength;
        int yStrength = up ? strength : -strength;

        player.setXSpeed((short) xStrength);
        player.setYSpeed((short) yStrength);
        player.setDirection(xStrength < 0 ? Direction.LEFT : Direction.RIGHT);
        player.setAir(true);
        player.setGSpeed((short) 0);
        player.recordMgzTopPlatformSpringHandoff(player.getXSpeed(), player.getYSpeed());
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);

        trigger(player);
    }

    private void trigger(AbstractPlayableSprite player) {
        animationState.setAnimId(ANIM_TRIGGERED);

        int subtype = spawn.subtype();

        // ROM: Animation handling varies by spring type
        if (springType == TYPE_HORIZONTAL) {
            // ROM: Horizontal springs use Walk animation unless rolling
            if (!player.getRolling()) {
                player.setAnimationId(Sonic3kAnimationIds.WALK);
            }
            player.setPushing(false);
        } else if (springType == TYPE_UP || springType == TYPE_DIAGONAL_UP) {
            player.setAnimationId(Sonic3kAnimationIds.SPRING);
        }

        // ROM: If bit 0 set, override to Walk animation with flip/twirl effect
        if ((subtype & 0x01) != 0) {
            player.setAnimationId(Sonic3kAnimationIds.WALK);
            player.setFlipAngle(1);

            if (springType == TYPE_UP || springType == TYPE_DOWN) {
                player.setFlipSpeed(4);
                player.setFlipsRemaining((subtype & 0x02) != 0 ? 0 : 1);
            } else {
                player.setFlipSpeed(8);
                player.setFlipsRemaining((subtype & 0x02) != 0 ? 1 : 3);
            }

            short inertia = 1;
            if (player.getDirection() == Direction.LEFT) {
                player.setFlipAngle(-player.getFlipAngle());
                inertia = -1;
            }

            if (springType != TYPE_HORIZONTAL) {
                player.setGSpeed(inertia);
            }
        }

        // ROM: Set collision layer based on subtype bits 2-3
        SpringHelper.applyCollisionLayerBits(player, subtype);

        try {
            services().playSfx(GameSound.SPRING);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        // ROM: Reverse_gravity_flag swaps UP<->DOWN during init (sonic3k.asm:47622-47627)
        if (services().gameState() != null && services().gameState().isReverseGravityActive()) {
            if (springType == TYPE_UP) {
                springType = TYPE_DOWN;
            } else if (springType == TYPE_DOWN) {
                springType = TYPE_UP;
            }
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        ensureInitialized();
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // ROM sub_2326C (sonic3k.asm:47957) — proactive horizontal-spring zone.
        // The whole routine is gated on `cmpi.b #3,anim(a0) / beq.w locret_23324`
        // (sonic3k.asm:47958-47959); within that gate, Player_1 (line 47973) and
        // Player_2 (line 47999) are checked independently.  Engine equivalent:
        // iterate the leader and every active sidekick so Tails-as-CPU and
        // Knuckles-as-CPU get the same proactive trigger Tails-as-Player_2 has
        // in ROM.  Without this, an air→ground transition that lands the
        // sidekick onto a horizontal spring while she's outside the side-push
        // collision box (CNZ trace F3649: spring at (0x1D37,0x08B0), Tails at
        // (0x1D21,0x08B0) — 3 px past the box's left edge) leaves the spring
        // unfired because the engine's per-player solid-contact path also has
        // no overlap to resolve.  The proactive zone (±$28 X, ±$18 Y) is the
        // ROM's safety net for exactly this geometry.
        if (springType == TYPE_HORIZONTAL && animationState.getAnimId() == ANIM_IDLE) {
            if (player != null) {
                checkHorizontalApproach(player);
            }
            // ROM sub_2326C falls through from the Player_1 block to the
            // Player_2 block (sonic3k.asm:47998→47999) regardless of whether
            // Player_1 fired the spring, so the second-player check must run
            // unconditionally inside the outer animation gate.
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                    checkHorizontalApproach(sidekick);
                }
            }
        }

        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    /**
     * ROM: sub_2326C - Proactive horizontal spring trigger zone.
     * Checks if a grounded player is approaching from the spring's trigger side
     * within ±$28 X and ±$18 Y. This allows horizontal springs to trigger even
     * without solid push contact when the player runs toward them.
     */
    private void checkHorizontalApproach(AbstractPlayableSprite player) {
        boolean landingHandoff = isHorizontalSpringLandingHandoff(player);
        if (player.getAir() && !landingHandoff) {
            return;
        }

        boolean flipped = isFlippedHorizontal();
        int dx = player.getCentreX() - spawn.x();
        int dy = player.getCentreY() - spawn.y();

        // Check Y range: ±$18
        if (dy < -HORIZ_DETECT_Y || dy > HORIZ_DETECT_Y) {
            return;
        }

        // Check X range: spring face side only
        if (flipped) {
            // Flipped spring faces left: player must be to the left (negative dx)
            if (dx < -HORIZ_DETECT_X || dx > 0) {
                return;
            }
            // Player must be moving left (negative gSpeed)
            if (horizontalApproachSpeed(player, landingHandoff) >= 0) {
                return;
            }
        } else {
            // Unflipped spring faces right: player must be to the right (positive dx)
            if (dx > HORIZ_DETECT_X || dx < 0) {
                return;
            }
            // Player must be moving right (positive gSpeed)
            if (horizontalApproachSpeed(player, landingHandoff) <= 0) {
                return;
            }
        }

        if (landingHandoff) {
            player.setAir(false);
            player.setYSpeed((short) 0);
        }
        applyHorizontalSpring(player);
    }

    private boolean isHorizontalSpringLandingHandoff(AbstractPlayableSprite player) {
        if (!player.getAir()) {
            return false;
        }
        // ROM runs Player_2 before the spring object, so an air->ground landing
        // onto the spring line can reach sub_2326C with Status_InAir already
        // clear. Engine ordering can leave the sidekick airborne until the next
        // tick; accept only the frame that has reached the spring's Y line.
        return player.getYSpeed() > 0 && (player.getCentreY() & 0xFFFF) >= (spawn.y() & 0xFFFF);
    }

    private int horizontalApproachSpeed(AbstractPlayableSprite player, boolean landingHandoff) {
        int gSpeed = player.getGSpeed();
        if (!landingHandoff || gSpeed != 0) {
            return gSpeed;
        }
        return player.getXSpeed();
    }

    /**
     * ROM: Obj_Spring_Strengths: dc.w -$1000, -$A00
     */
    private int getStrength() {
        return SpringBounceHelper.strength(redSpring);
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }

    private boolean isPlayerOnHorizontalSpringActiveSide(AbstractPlayableSprite player) {
        int objectX = spawn.x() & 0xFFFF;
        int playerX = player.getCentreX() & 0xFFFF;
        boolean flipped = isFlippedHorizontal();
        return flipped ? objectX >= playerX : objectX < playerX;
    }

    private boolean isPlayerOnUpDiagonalSpringLaunchSide(AbstractPlayableSprite player) {
        int playerX = player.getCentreX() & 0xFFFF;
        boolean flipped = isFlippedHorizontal();
        if (flipped) {
            // ROM sub_234E6: trigger only when x_pos(a0)+4 >= x_pos(a1).
            int lipX = (spawn.x() + 4) & 0xFFFF;
            return Integer.compareUnsigned(lipX, playerX) >= 0;
        }
        // ROM sub_234E6: trigger only when x_pos(a0)-4 < x_pos(a1).
        int lipX = (spawn.x() - 4) & 0xFFFF;
        return Integer.compareUnsigned(lipX, playerX) < 0;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return true;
    }

    /**
     * ROM collision params:
     * Up/Down: D1=$1B (27), D2=8, D3=$10 (16)
     * Horizontal: D1=$13 (19), D2=$E (14), D3=$F (15)
     * Diagonal: D1=$1B (27), D2=$10 (16)
     */
    @Override
    public SolidObjectParams getSolidParams() {
        if (springType == TYPE_HORIZONTAL) {
            return new SolidObjectParams(19, 14, 15);
        }
        if (springType == TYPE_DIAGONAL_UP || springType == TYPE_DIAGONAL_DOWN) {
            return new SolidObjectParams(27, 16, 16);
        }
        if (springType == TYPE_DOWN) {
            return new SolidObjectParams(27, 8, 9);
        }
        return new SolidObjectParams(27, 8, 16);
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        return springType == TYPE_HORIZONTAL;
    }

    /**
     * ROM divergence: every {@code Obj_Spring} variant routes through
     * {@code SolidObjectFull2_1P} (sonic3k.asm:47664/47673/47692/47701/
     * 47779/47798/47829/47848/48036/48045/48064/48074), and that helper's
     * non-standing branch falls through directly to {@code SolidObject_cont}
     * (sonic3k.asm:41067) without the {@code render_flags} bit-7 gate at
     * {@code loc_1DF88} (sonic3k.asm:41390-41392).  S2 mirrors this: every
     * spring variant uses {@code SolidObject_Always_SingleCharacter}
     * (s2.asm:33709/33718/33784/33802) which jumps straight to
     * {@code SolidObject_cont} without the {@code SolidObject_OnScreenTest}
     * gate at s2.asm:35140-35145.  Off-screen springs therefore still
     * resolve push and side contact in the ROM (AIZ trace replay F2919's
     * horizontal spring at (0x1F39, 0x04A0) sits ~0xAA px below the camera
     * viewport at the trigger frame).
     */
    @Override
    public boolean bypassesOffscreenSolidGate() {
        return true;
    }

    @Override
    public byte[] getSlopeData() {
        if (springType == TYPE_DIAGONAL_UP) {
            return SLOPE_DIAG_UP;
        }
        if (springType == TYPE_DIAGONAL_DOWN) {
            return SLOPE_DIAG_DOWN;
        }
        return null;
    }

    @Override
    public boolean isSlopeFlipped() {
        return isFlippedHorizontal();
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        String artKey = resolveArtKey();
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = isFlippedHorizontal();
        boolean vFlip = springType == TYPE_DOWN || springType == TYPE_DIAGONAL_DOWN
                || (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    private String resolveArtKey() {
        boolean yellow = !redSpring;
        if (springType == TYPE_HORIZONTAL) {
            return yellow ? Sonic3kObjectArtKeys.SPRING_HORIZONTAL_YELLOW
                    : Sonic3kObjectArtKeys.SPRING_HORIZONTAL;
        }
        if (springType == TYPE_DIAGONAL_UP || springType == TYPE_DIAGONAL_DOWN) {
            return yellow ? Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW
                    : Sonic3kObjectArtKeys.SPRING_DIAGONAL;
        }
        return yellow ? Sonic3kObjectArtKeys.SPRING_VERTICAL_YELLOW
                : Sonic3kObjectArtKeys.SPRING_VERTICAL;
    }

    /**
     * Builds a standalone animation set for this spring variant.
     * Each variant has 2 animations (idle and triggered) with 0-based frame indices.
     * <p>
     * From Anim - Spring.asm:
     * Idle: {$F, 0, $FF} → delay=$F, frame 0, loop
     * Triggered: {0, 1, 0, 0, 2,2,2,2,2,2, $FD, 0} → delay=0, 10 frames, switch back to idle
     */
    private static SpriteAnimationSet buildAnimationSet() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        // Anim 0 (idle): delay=$F, single frame 0, loop
        set.addScript(ANIM_IDLE, new SpriteAnimationScript(
                0x0F, List.of(0), SpriteAnimationEndAction.LOOP, 0));
        // Anim 1 (triggered): delay=0, frames {1, 0, 0, 2,2,2,2,2,2}, switch to anim 0
        set.addScript(ANIM_TRIGGERED, new SpriteAnimationScript(
                0, List.of(1, 0, 0, 2, 2, 2, 2, 2, 2), SpriteAnimationEndAction.SWITCH, ANIM_IDLE));
        return set;
    }
}
