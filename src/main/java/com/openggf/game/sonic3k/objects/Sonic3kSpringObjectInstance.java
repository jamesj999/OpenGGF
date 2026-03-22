package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.audio.GameSound;
import com.openggf.game.GameStateManager;
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

    private final int springType;
    private final boolean redSpring;
    private final ObjectAnimationState animationState;
    private int mappingFrame;

    public Sonic3kSpringObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Spring");
        this.redSpring = (spawn.subtype() & 0x02) == 0;

        // ROM: Reverse_gravity_flag swaps UP↔DOWN during init (sonic3k.asm:47622-47627)
        int type = (spawn.subtype() >> 3) & 0xE;
        if (GameStateManager.getInstance().isReverseGravityActive()) {
            if (type == TYPE_UP) {
                type = TYPE_DOWN;
            } else if (type == TYPE_DOWN) {
                type = TYPE_UP;
            }
        }
        this.springType = type;

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
            if (!contact.pushing()) {
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
        player.setYSpeed((short) getStrength());
        player.setAir(true);
        player.setGSpeed((short) 0);

        // ROM: sub_22F98 line 47729-47731 - if bit 7 set, clear x velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }

        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);
        trigger(player);
    }

    /**
     * ROM: Obj_Spring_Down
     * S3K-specific: red down springs cap at $D00 instead of $1000.
     */
    private void applyDownSpring(AbstractPlayableSprite player) {
        player.setY((short) (player.getY() - 8));

        // ROM negates strength for down springs (positive = down)
        int yVel = -getStrength();
        // S3K-specific: red down spring velocity cap at $D00
        if (yVel == -SpringBounceHelper.STRENGTH_RED) {
            yVel = 0x0D00;
        }
        player.setYSpeed((short) yVel);

        player.setAir(true);
        player.setGSpeed((short) 0);

        // ROM: sub_233CA line 48103-48105 - if bit 7 set, clear x velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }

        player.setSpringing(SpringBounceHelper.CONTROL_LOCK_FRAMES);
        trigger(player);
    }

    /**
     * ROM: Obj_Spring_Horizontal (sonic3k.asm)
     * - move.w objoff_30(a0),x_vel(a1) [starts negative]
     * - addq.w #8,x_pos(a1) / subi.w #$10 if not flipped
     * - Sets gSpeed = x_vel, control lock 16 frames
     * - Does NOT set airborne
     */
    private void applyHorizontalSpring(AbstractPlayableSprite player) {
        int strength = getStrength(); // starts negative
        boolean flipped = isFlippedHorizontal();

        int newX = player.getX() + 8;
        Direction dir = Direction.RIGHT;

        if (!flipped) {
            newX -= 16;
            strength = -strength; // now positive (right)
        } else {
            dir = Direction.LEFT;
        }

        player.setX((short) newX);
        player.setXSpeed((short) strength);
        player.setDirection(dir);

        // ROM: Horizontal springs set gSpeed = x_vel, stay grounded
        player.setGSpeed((short) strength);

        // ROM: If bit 7 set, clear Y velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setYSpeed((short) 0);
        }

        // ROM: Control lock 16 frames
        player.setSpringing(16);

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

        int xStrength = flipped ? strength : -strength;
        int yStrength = up ? strength : -strength;

        player.setXSpeed((short) xStrength);
        player.setYSpeed((short) yStrength);

        // ROM position nudge: separate player from spring surface
        // Up diagonal: y += 6; flipped: x += 6, unflipped: x -= 6
        // Down diagonal: y -= 6 (inverted); same x logic
        int yNudge = up ? 6 : -6;
        int xNudge = flipped ? 6 : -6;
        player.setX((short) (player.getX() + xNudge));
        player.setY((short) (player.getY() + yNudge));

        player.setDirection(xStrength < 0 ? Direction.LEFT : Direction.RIGHT);
        player.setAir(true);
        player.setGSpeed((short) 0);
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
            if (GameServices.audio() != null) {
                GameServices.audio().playSfx(GameSound.SPRING);
            }
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // S3K horizontal approach detection (sonic3k.asm sub_2326C)
        if (springType == TYPE_HORIZONTAL && player != null && animationState.getAnimId() == ANIM_IDLE) {
            checkHorizontalApproach(player);
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
        if (player.getAir()) {
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
            if (player.getGSpeed() >= 0) {
                return;
            }
        } else {
            // Unflipped spring faces right: player must be to the right (positive dx)
            if (dx > HORIZ_DETECT_X || dx < 0) {
                return;
            }
            // Player must be moving right (positive gSpeed)
            if (player.getGSpeed() <= 0) {
                return;
            }
        }

        applyHorizontalSpring(player);
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
            return new SolidObjectParams(19, 8, 8);
        }
        if (springType == TYPE_DIAGONAL_UP || springType == TYPE_DIAGONAL_DOWN) {
            return new SolidObjectParams(27, 16, 16);
        }
        return new SolidObjectParams(27, 8, 8);
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
