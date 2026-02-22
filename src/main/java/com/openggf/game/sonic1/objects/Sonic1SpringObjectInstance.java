package com.openggf.game.sonic1.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.game.sonic2.objects.ObjectAnimationState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Springs - Object ID 0x41.
 * <p>
 * Subtype encoding (from docs/s1disasm/_incObj/41 Springs.asm):
 * <ul>
 *   <li>Bit 1: Yellow spring (0=red/-$1000, 2=yellow/-$A00)</li>
 *   <li>Bit 4: Left/Right spring</li>
 *   <li>Bit 5: Downward spring</li>
 *   <li>Neither bit 4 nor 5: Upward spring (default)</li>
 * </ul>
 * <p>
 * No diagonal springs in S1 (unlike S2).
 * No collision layer switching (S1 UNIFIED collision model).
 * No flip/twirl subtype bit (S2-only feature).
 */
public class Sonic1SpringObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int TYPE_UP = 0;
    private static final int TYPE_HORIZONTAL = 1;
    private static final int TYPE_DOWN = 2;

    // Spring_Powers: dc.w -$1000, -$A00
    private static final int RED_STRENGTH = -0x1000;    // Red spring power
    private static final int YELLOW_STRENGTH = -0x0A00;  // Yellow spring power

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    // From disassembly: move.w #$F,objoff_3E(a1) — horizontal control lock
    private static final int HORIZONTAL_CONTROL_LOCK = 15;

    // Animation IDs (registered in Sonic1ObjectArtProvider)
    private static final int ANIM_IDLE = 0;
    private static final int ANIM_TRIGGERED = 1;

    private final int springType;
    private final boolean yellow;
    private final int strength;
    private final ObjectAnimationState animationState;
    private int mappingFrame;

    public Sonic1SpringObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Spring");

        int subtype = spawn.subtype();

        // Determine spring type from subtype bits
        if ((subtype & 0x10) != 0) {
            this.springType = TYPE_HORIZONTAL;
        } else if ((subtype & 0x20) != 0) {
            this.springType = TYPE_DOWN;
        } else {
            this.springType = TYPE_UP;
        }

        // Bit 1: yellow flag. andi.w #$F,d0 then index into Spring_Powers
        this.yellow = (subtype & 0x02) != 0;
        this.strength = yellow ? YELLOW_STRENGTH : RED_STRENGTH;

        // Initial mapping frame: 0 = idle for both vertical and horizontal sheets
        this.mappingFrame = 0;

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getSpringAnimations() : null,
                ANIM_IDLE,
                0);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }

        switch (springType) {
            case TYPE_UP -> {
                // Spring_Up: triggers when Sonic is standing on top (obSolid set)
                if (!contact.standing()) {
                    return;
                }
                applyUpSpring(player);
            }
            case TYPE_HORIZONTAL -> {
                // Spring_LR: triggers when Sonic pushes against side (obStatus bit 5)
                if (!contact.pushing()) {
                    return;
                }
                applyHorizontalSpring(player);
            }
            case TYPE_DOWN -> {
                // Spring_Dwn: triggers on bottom contact (d4 < 0), NOT when standing on top
                if (contact.standing() || !contact.touchBottom()) {
                    return;
                }
                applyDownSpring(player);
            }
        }
    }

    /**
     * ROM: Spring_BounceUp
     * - addq.w #8,obY(a1) — push Sonic down 8px (away from spring face)
     * - move.w spring_pow(a0),obVelY(a1) — set Y velocity (negative = up)
     * - bset #1,obStatus(a1) — set airborne
     * - bclr #3,obStatus(a1) — clear standing on object
     * - move.b #id_Spring,obAnim(a1) — set Sonic animation to Spring (0x10)
     */
    private void applyUpSpring(AbstractPlayableSprite player) {
        // Y positioning handled by ObjectManager collision resolution
        player.setYSpeed((short) strength);
        player.setAir(true);
        player.setGSpeed((short) 0);
        player.setSpringing(HORIZONTAL_CONTROL_LOCK);

        // Up spring sets Sonic's animation to Spring (id_Spring = 0x10)
        player.setAnimationId(Sonic1AnimationIds.SPRING);

        triggerSpring();
    }

    /**
     * ROM: Spring_BounceDwn
     * - subq.w #8,obY(a1) — push Sonic up 8px (away from spring face)
     * - move.w spring_pow(a0),obVelY(a1) then neg.w — positive = downward
     * - bset #1,obStatus(a1) — set airborne
     * - bclr #3,obStatus(a1) — clear standing on object
     * - Does NOT set Sonic's animation (unlike up spring)
     */
    private void applyDownSpring(AbstractPlayableSprite player) {
        // ROM: subq.w #8,obY(a1) — push player up (away from spring face)
        player.setY((short) (player.getY() - 8));

        // ROM negates strength for down springs: positive = downward
        player.setYSpeed((short) -strength);
        player.setAir(true);
        player.setGSpeed((short) 0);
        player.setSpringing(HORIZONTAL_CONTROL_LOCK);

        // Down spring does NOT change Sonic's animation
        triggerSpring();
    }

    /**
     * ROM: Spring_BounceLR
     * - move.w spring_pow(a0),obVelX(a1) — starts negative (leftward)
     * - addq.w #8,obX(a1) — push right 8px
     * - btst #0,obStatus(a0) — check H-flip
     * - bne.s Spring_Flipped — if flipped, keep negative vel + right offset
     * - subi.w #$10,obX(a1) — net: push left 8px
     * - neg.w obVelX(a1) — now positive (rightward)
     * Spring_Flipped:
     * - move.w #$F,objoff_3E(a1) — 15 frame control lock
     * - move.w obVelX(a1),obInertia(a1) — set ground speed
     * - bchg #0,obStatus(a1) — toggle facing direction
     * - btst #2,obStatus(a1) / bne skip — if rolling, skip animation change
     * - move.b #id_Walk,obAnim(a1) — set Walk animation
     */
    private void applyHorizontalSpring(AbstractPlayableSprite player) {
        int xVel = strength; // starts negative (leftward)
        boolean flipped = isFlippedHorizontal();

        // Always add 8 first
        int newX = player.getX() + 8;

        if (!flipped) {
            // Unflipped spring: subtract 16 (net -8), negate velocity to rightward
            newX -= 16;
            xVel = -xVel;
        }
        // Flipped spring: keep +8, keep negative velocity (leftward)

        player.setX((short) newX);
        player.setXSpeed((short) xVel);

        // ROM: move.w obVelX(a1),obInertia(a1) — horizontal springs set ground speed
        // Horizontal springs do NOT set airborne
        player.setGSpeed((short) xVel);

        // ROM: bchg #0,obStatus(a1) — toggle facing direction
        player.setDirection(xVel > 0 ? Direction.RIGHT : Direction.LEFT);

        // ROM: move.w #$F,objoff_3E(a1) — 15 frame control lock
        player.setSpringing(HORIZONTAL_CONTROL_LOCK);

        // ROM: btst #2,obStatus(a1) / bne.s loc_DC56 — skip Walk anim if rolling
        if (!player.getRolling()) {
            player.setAnimationId(Sonic1AnimationIds.WALK);
        }

        // ROM: bclr #5,obStatus(a0) / bclr #5,obStatus(a1) — clear pushing flags
        player.setPushing(false);

        triggerSpring();
    }

    private void triggerSpring() {
        animationState.setAnimId(ANIM_TRIGGERED);

        try {
            AudioManager.getInstance().playSfx(Sonic1Sfx.SPRING.id);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Springs are always solid — collision resolution prevents re-triggering
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (springType == TYPE_HORIZONTAL) {
            // Spring_LR: d1=$13 (19), d2=$E (14), d3=$F (15)
            return new SolidObjectParams(19, 14, 15);
        }
        // Spring_Up / Spring_Dwn: d1=$1B (27), d2=8, d3=$10 (16)
        return new SolidObjectParams(27, 8, 16);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        String artKey = resolveArtKey();
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = isFlippedHorizontal();
        // Down springs have V-flip (from disassembly: bset #1,obStatus in Spring_Main)
        boolean vFlip = (springType == TYPE_DOWN);

        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    private String resolveArtKey() {
        // S2 convention: default keys = red, "_RED" keys = yellow (inverted naming)
        if (springType == TYPE_HORIZONTAL) {
            return yellow ? ObjectArtKeys.SPRING_HORIZONTAL_RED : ObjectArtKeys.SPRING_HORIZONTAL;
        }
        return yellow ? ObjectArtKeys.SPRING_VERTICAL_RED : ObjectArtKeys.SPRING_VERTICAL;
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }
}
