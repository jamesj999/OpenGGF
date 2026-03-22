package com.openggf.game.sonic2.objects;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.SpringHelper;
import com.openggf.level.objects.BoxObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.objects.*;

import com.openggf.audio.GameSound;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

public class SpringObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    // Subtype constants (shifted >> 3 & 0xE) - matches ROM Obj41_Index
    private static final int TYPE_UP = 0;
    private static final int TYPE_HORIZONTAL = 2;
    private static final int TYPE_DOWN = 4;
    private static final int TYPE_DIAGONAL_UP = 6;
    private static final int TYPE_DIAGONAL_DOWN = 8;

    // Diagonal slope data
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

    private static final int ANIM_VERTICAL_IDLE = 0;
    private static final int ANIM_VERTICAL_TRIGGER = 1;
    private static final int ANIM_HORIZONTAL_IDLE = 2;
    private static final int ANIM_HORIZONTAL_TRIGGER = 3;
    private static final int ANIM_DIAGONAL_IDLE = 4;
    private static final int ANIM_DIAGONAL_TRIGGER = 5;

    private final boolean redSpring;
    private final ObjectAnimationState animationState;
    private final int idleAnimId;
    private final int triggeredAnimId;
    private int mappingFrame;

    public SpringObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, 8, 8, 1.0f, 0.85f, 0.1f, false);
        // ROM: bit 1 of subtype selects strength (0=red/-$1000, 2=yellow/-$A00)
        this.redSpring = (spawn.subtype() & 0x02) == 0;
        this.idleAnimId = resolveIdleAnimId();
        this.triggeredAnimId = resolveTriggeredAnimId();
        this.mappingFrame = resolveIdleMappingFrame();

        ObjectRenderManager renderManager = GameServices.level().getObjectRenderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getSpringAnimations() : null,
                idleAnimId,
                mappingFrame);
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (player == null) {
            return;
        }

        // ROM behavior: No check for "springing" state before triggering.
        // The ROM only checks pushing/standing flags and side position.
        // Natural collision resolution (pushing player away) prevents
        // infinite re-triggering on the same spring. The move_lock/springing
        // state only locks player INPUT, not object interactions.

        int type = getType();

        if (type == TYPE_DIAGONAL_UP) {
            // ROM: Obj41_DiagonallyUp calls SlopedSolid_SingleCharacter which sets the
            // standing bit when the player lands on the sloped surface. The standing bit
            // persists across frames. Our engine re-evaluates contacts each frame, and
            // the generic side-vs-top comparison can misclassify contacts on the slope
            // as side contacts (absDistX < absDistY). To match ROM behavior, accept any
            // solid contact where the player is grounded — this mirrors the ROM's
            // SlopedSolid_SingleCharacter which only checks !in_air + X range when the
            // standing bit is already set.
            //
            // ROM: loc_18DB4 checks X threshold (springX +/-4 vs playerX) to prevent
            // launch from the flat portion. In the ROM, this works because the player
            // naturally walks past the threshold within a few frames. In our engine
            // with batched solid contacts, the standing contact itself (resolved via
            // resolveSlopedContact) already confirms the player is within the spring's
            // sloped surface area, making the X threshold check redundant. The
            // SolidObjectParams halfWidth (27) bounds the overall contact area, while
            // the slope data constrains the Y surface — together they gate activation
            // more accurately than the fixed 4px X offset.
            if (!contact.standing() && player.getAir()) {
                return;
            }
            applyDiagonalSpring(player, true);
            return;
        }

        if (type == TYPE_DIAGONAL_DOWN) {
            // Same logic as diagonal-up: accept any contact when player is grounded
            if (!contact.touchBottom() && player.getAir()) {
                return;
            }
            applyDiagonalSpring(player, false);
            return;
        }

        if (type == TYPE_HORIZONTAL) {
            // ROM: checks pushing_bit, which maps to our pushing/touchSide
            if (!contact.pushing()) {
                return;
            }
            applyHorizontalSpring(player);
            return;
        }

        if (type == TYPE_DOWN) {
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
     * ROM: Obj41_Up (loc_189CA)
     * - addq.w #8,y_pos(a1)
     * - move.w objoff_30(a0),y_vel(a1) [negative = up]
     * - bset #status.player.in_air
     */
    /**
     * ROM: Obj41_Up (loc_189CA)
     * - addq.w #8,y_pos(a1) -> In ROM, Y increases downward, so this pushes player
     * down
     * - In our engine, Y increases upward, so we SUBTRACT to push down (away from
     * spring face)
     */
    private void applyUpSpring(AbstractPlayableSprite player) {
        // ROM: addq.w #8,y_pos(a1) — push player down 8px (away from spring face)
        // before launching. y_pos is center coordinate.
        player.setCentreY((short) (player.getCentreY() + 8));

        // ROM: y_vel = negative value (negative = up in Y-down coordinate system)
        player.setYSpeed((short) getStrength()); // Negative = up

        player.setAir(true);
        player.setGSpeed((short) 0);
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);
        trigger(player);
    }

    /**
     * ROM: Obj41_Down - same as Up but flipped
     * - subq.w #8,y_pos(a1)
     * - move.w objoff_30(a0),y_vel(a1) then neg.w
     */
    private void applyDownSpring(AbstractPlayableSprite player) {
        // ROM: subq.w #8,y_pos (pushes player up in Y-down coordinate system)
        // Java engine also has Y-down, so we SUBTRACT to push up (away from spring)
        player.setY((short) (player.getY() - 8));

        // ROM negates the strength for down springs (positive = down in Y-down system)
        player.setYSpeed((short) -getStrength()); // Negated = positive = down

        player.setAir(true);
        player.setGSpeed((short) 0);
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);
        trigger(player);
    }

    /**
     * ROM: Obj41_Horizontal (loc_18AEE)
     * - move.w objoff_30(a0),x_vel(a1) [starts negative]
     * - addq.w #8,x_pos(a1)
     * - bset player facing right
     * - btst spring.x_flip
     * - bne skip_adjustment (if flipped, keep +8 and negative velocity)
     * - bclr player facing (now left)
     * - subi.w #$10,x_pos(a1) [net: -8]
     * - neg.w x_vel(a1) [now positive = right]
     */
    private void applyHorizontalSpring(AbstractPlayableSprite player) {
        int strength = getStrength(); // starts negative
        boolean flipped = isFlippedHorizontal();

        // ROM uses x_pos which is center coordinate.
        // Always add 8 first (addq.w #8,x_pos)
        int newCentreX = player.getCentreX() + 8;
        Direction dir = Direction.RIGHT;

        if (!flipped) {
            // Unflipped spring: subtract 16 (net -8), negate velocity
            // ROM: subi.w #$10,x_pos(a1)
            newCentreX -= 16;
            strength = -strength; // now positive (right)
        } else {
            // Flipped spring: keep +8, keep negative velocity (left)
            dir = Direction.LEFT;
        }

        player.setCentreX((short) newCentreX);
        player.setXSpeed((short) strength);
        player.setDirection(dir);

        // ROM: Horizontal springs do NOT set in_air!
        // They set inertia (gSpeed) = x_vel and keep player grounded
        // Line 33810: move.w x_vel(a1),inertia(a1)
        player.setGSpeed((short) strength);

        // ROM Line 33818: bpl.s -> move.w #0,y_vel(a1) (if subtype bit 7 set, clear Y
        // velocity)
        if ((spawn.subtype() & 0x80) != 0) {
            player.setYSpeed((short) 0);
        }

        // ROM: move.w #$F,move_lock(a1) — 15 frames of input lock
        // Horizontal springs use move_lock (not springing state) to prevent
        // player from braking immediately after being launched
        player.setMoveLockTimer(SpringBounceHelper.CONTROL_LOCK_FRAMES);

        trigger(player);
    }

    /**
     * ROM: Diagonal springs apply both X and Y velocity.
     * ROM: s2.asm:34052-34058 — Position offsets before launch:
     *   addq.w #6,y_pos(a1)
     *   addq.w #6,x_pos(a1)
     *   btst #0,status(a0)  ; if spring faces right (not x-flipped)
     *   beq.s +              ; skip if flipped
     *   subi.w #$C,x_pos(a1) ; subtract 12 (net -6)
     */
    private void applyDiagonalSpring(AbstractPlayableSprite player, boolean up) {
        int strength = getStrength(); // negative base
        boolean flipped = isFlippedHorizontal();

        // ROM position offsets before launching
        player.setCentreY((short) (player.getCentreY() + 6));
        int newCentreX = player.getCentreX() + 6;
        if (!flipped) {
            // Unflipped (faces right): subtract 12 from X (net -6)
            newCentreX -= 12;
        }
        player.setCentreX((short) newCentreX);

        int xStrength = flipped ? strength : -strength;
        int yStrength = up ? strength : -strength;

        player.setXSpeed((short) xStrength);
        player.setYSpeed((short) yStrength);
        player.setDirection(xStrength < 0 ? Direction.LEFT : Direction.RIGHT);
        player.setAir(true);
        player.setGSpeed((short) 0);
        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);

        trigger(player);
    }

    private void trigger(AbstractPlayableSprite player) {
        animationState.setAnimId(triggeredAnimId);

        int subtype = spawn.subtype();
        int type = getType();

        // ROM: Animation handling varies by spring type
        // Up/Diagonal-Up: Set Spring animation, then override to Walk if bit 0 set
        // Down/Diagonal-Down: Only set Walk if bit 0 set (no default animation change)
        // Horizontal: Set Walk (unless rolling), then add flip params if bit 0 set

        if (type == TYPE_HORIZONTAL) {
            // ROM: loc_18B11-18B13 - Horizontal springs use Walk animation (unless rolling)
            if (!player.getRolling()) {
                player.setAnimationId(Sonic2AnimationIds.WALK);
            }
            // ROM: loc_18BAA clears pushing flags after horizontal spring triggers
            player.setPushing(false);
        } else if (type == TYPE_UP || type == TYPE_DIAGONAL_UP) {
            // ROM: loc_189CA/loc_18E10 - Up springs set Spring animation first
            player.setAnimationId(Sonic2AnimationIds.SPRING);
        }
        // Down springs (TYPE_DOWN, TYPE_DIAGONAL_DOWN): No default animation change

        // ROM: If bit 0 set, override to Walk animation with flip/twirl effect
        if ((subtype & 0x01) != 0) {
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.setFlipAngle(1);

            if (type == TYPE_UP || type == TYPE_DOWN) {
                // ROM: Up/Down springs use flip_speed=4, flips=0 (or 1 if bit 1 NOT set)
                player.setFlipSpeed(4);
                player.setFlipsRemaining((subtype & 0x02) != 0 ? 0 : 1);
            } else {
                // ROM: Horizontal/Diagonal springs use flip_speed=8, flips=1 (or 3 if bit 1 NOT set)
                player.setFlipSpeed(8);
                player.setFlipsRemaining((subtype & 0x02) != 0 ? 1 : 3);
            }

            // ROM: move.w #1,inertia(a1) - Set inertia for twirl
            short inertia = 1;

            // ROM: Negate flip_angle and inertia if player facing left
            if (player.getDirection() == Direction.LEFT) {
                player.setFlipAngle(-player.getFlipAngle());
                inertia = -1;  // ROM: neg.w inertia(a1)
            }

            // Only set inertia for non-horizontal springs (horizontal already sets gSpeed = x_vel)
            if (type != TYPE_HORIZONTAL) {
                player.setGSpeed(inertia);
            }
        }

        // ROM: loc_18A3E-18A66 - Set collision layer based on subtype bits 2-3
        SpringHelper.applyCollisionLayerBits(player, subtype);

        try {
            if (GameServices.audio() != null) {
                GameServices.audio().playSfx(GameSound.SPRING);
            }
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    /**
     * ROM: getStrength returns NEGATIVE values
     * Obj41_Strengths: dc.w -$1000, -$A00
     * Bit 1 of subtype: 0=red(-$1000), 2=yellow(-$A00)
     */
    private int getStrength() {
        return SpringBounceHelper.strength(redSpring);
    }

    private int getType() {
        // ROM: lsr.w #3,d0 then andi.w #$E,d0
        return (spawn.subtype() >> 3) & 0xE;
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }

    /**
     * ROM behavior: Springs are always solid.
     * The onSolidContact guard (checking player.getSpringing()) prevents
     * re-triggering while allowing the spring to remain solid for collision.
     * This is critical for spring loops where the player must collide with
     * the second spring after hitting the first.
     *
     * Previous implementation made springs non-solid during springing state,
     * which caused players to pass through springs and hit terrain.
     */
    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return true;
    }

    /**
     * ROM collision params vary by type:
     * Up/Down: D1=$1B (27), D2=8, D3=$10 (16)
     * Horizontal: D1=$13 (19), D2=$E (14), D3=$F (15)
     * Diagonal: D1=$1B (27), D2=$10 (16) - taller to catch player running off terrain
     */
    @Override
    public SolidObjectParams getSolidParams() {
        int type = getType();
        if (type == TYPE_HORIZONTAL) {
            // ROM: d2=$E (14), d3=$F (15) — air half-height and ground half-height
            return new SolidObjectParams(19, 14, 15);
        }
        if (type == TYPE_DIAGONAL_UP || type == TYPE_DIAGONAL_DOWN) {
            // ROM: Diagonal springs use d2=$10 (halfHeight=16), taller collision box
            // This is critical for catching the player when running off terrain edges
            return new SolidObjectParams(27, 16, 16);
        }
        // Up, Down springs use standard vertical params
        // ROM: d2=8, d3=$10 (16) — air half-height=8, ground half-height=16
        return new SolidObjectParams(27, 8, 16);
    }

    @Override
    public byte[] getSlopeData() {
        int type = getType();
        if (type == TYPE_DIAGONAL_UP) {
            return SLOPE_DIAG_UP;
        }
        if (type == TYPE_DIAGONAL_DOWN) {
            return SLOPE_DIAG_DOWN;
        }
        return null;
    }

    @Override
    public boolean isSlopeFlipped() {
        return isFlippedHorizontal();
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }
        ObjectRenderManager.SpringVariant variant = resolveVariant();
        // NOTE: Renderer naming is inverted - "RedRenderer" variants are yellow,
        // default are red
        // So we pass !redSpring to get correct visual color
        PatternSpriteRenderer renderer = renderManager.getSpringRenderer(variant, !redSpring);
        boolean hFlip = isFlippedHorizontal();
        boolean vFlip = getType() == TYPE_DOWN || (spawn.renderFlags() & 0x2) != 0;
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    private ObjectRenderManager.SpringVariant resolveVariant() {
        int type = getType();
        if (type == TYPE_HORIZONTAL) {
            return ObjectRenderManager.SpringVariant.HORIZONTAL;
        }
        if (type == TYPE_DIAGONAL_UP || type == TYPE_DIAGONAL_DOWN) {
            return ObjectRenderManager.SpringVariant.DIAGONAL;
        }
        return ObjectRenderManager.SpringVariant.VERTICAL;
    }

    private int resolveIdleAnimId() {
        int type = getType();
        if (type == TYPE_HORIZONTAL) {
            return ANIM_HORIZONTAL_IDLE;
        }
        if (type == TYPE_DIAGONAL_UP || type == TYPE_DIAGONAL_DOWN) {
            return ANIM_DIAGONAL_IDLE;
        }
        return ANIM_VERTICAL_IDLE;
    }

    private int resolveTriggeredAnimId() {
        int type = getType();
        if (type == TYPE_HORIZONTAL) {
            return ANIM_HORIZONTAL_TRIGGER;
        }
        if (type == TYPE_DIAGONAL_UP || type == TYPE_DIAGONAL_DOWN) {
            return ANIM_DIAGONAL_TRIGGER;
        }
        return ANIM_VERTICAL_TRIGGER;
    }

    private int resolveIdleMappingFrame() {
        int type = getType();
        if (type == TYPE_HORIZONTAL) {
            return 3;
        }
        if (type == TYPE_DIAGONAL_UP || type == TYPE_DIAGONAL_DOWN) {
            return 7;
        }
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
