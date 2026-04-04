package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
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
 * Object 0x87 - Gumball Triangle Bumper (Sonic 3 &amp; Knuckles Gumball bonus stage).
 * <p>
 * ROM reference: sonic3k.asm Obj_GumballTriangleBumper (line 127634).
 * <p>
 * A fixed triangular bumper that bounces the player with fixed velocity on contact:
 * <ul>
 *   <li>X velocity: +/-0x300 (direction determined by h-flip render flag)</li>
 *   <li>Y velocity: -0x600 (always upward)</li>
 *   <li>Sets player airborne, clears riding/on-object flags, sets facing direction</li>
 *   <li>Sets player animation to SPRING (0x10), clears jumping flag</li>
 *   <li>0x0F frame cooldown between bounces</li>
 *   <li>Plays sfx_Spring on bounce</li>
 * </ul>
 * <p>
 * ROM attributes (ObjDat3_613A4):
 * <ul>
 *   <li>Mappings: Map_GumballBonus</li>
 *   <li>Art tile: make_art_tile(ArtTile_BonusStage, 1, 1) = palette 1, high priority</li>
 *   <li>Priority: $0100</li>
 *   <li>Sprite width: 4, height: $10</li>
 *   <li>Mapping frame: $12</li>
 * </ul>
 * <p>
 * ROM collision: SolidObjectFull with D1=$D (13), D2=8, D3=$11 (17).
 * On player standing or side push contact, applies bounce and deletes self
 * (the Gumball machine respawns bumpers). For standalone operation without
 * the machine, this implementation uses a per-object cooldown instead of
 * self-destruction.
 */
public class GumballTriangleBumperObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // ROM: sub_60F94 bounce velocities
    private static final int BOUNCE_X_SPEED = 0x300;
    private static final int BOUNCE_Y_SPEED = -0x600;

    // ROM: move.w #$F,($FF2020).l — cooldown frames between bounces
    private static final int BOUNCE_COOLDOWN_FRAMES = 0x0F;

    // ROM: ObjDat3_613A4 mapping frame
    private static final int MAPPING_FRAME = 0x12;

    // ROM: SolidObjectFull params — D1=$D (halfWidth=13), D2=8 (airHalfHeight),
    // D3=$11 (groundHalfHeight=17). The ground half-height is larger to account
    // for the triangle's tall upper hitbox that catches the player standing on top.
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(13, 8, 17);

    /** Cooldown timer; when > 0, collision is skipped. */
    private int cooldownTimer;

    public GumballTriangleBumperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "GumballTriangleBumper");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (cooldownTimer > 0) {
            cooldownTimer--;
        }
    }

    // --- SolidObjectProvider ---

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        // ROM: tst.w ($FF2020).l / bpl.s loc_60F8E — skip collision when cooldown active
        return cooldownTimer <= 0;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    // --- SolidObjectListener ---

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (cooldownTimer > 0) {
            return;
        }

        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (player == null) {
            return;
        }

        // ROM: btst #$10,d6 — side-touch bit, or p1_standing_bit.
        // Engine: contact.touchSide() or contact.standing().
        if (!contact.standing() && !contact.touchSide()) {
            return;
        }

        applyBounce(player);
    }

    /**
     * ROM: sub_60F94 — Applies fixed-velocity bounce to player.
     * <p>
     * The X direction is determined by the object's h-flip render flag (bit 0):
     * <ul>
     *   <li>h-flip set (render_flags bit 0): X = -0x300 (bounce left), face left</li>
     *   <li>h-flip clear: X = +0x300 (bounce right), face right</li>
     * </ul>
     */
    private void applyBounce(AbstractPlayableSprite player) {
        boolean hFlipped = (spawn.renderFlags() & 0x1) != 0;

        // ROM: move.w #-$300,d0 ... btst #0,render_flags(a0) / bne.s loc_60FB4
        //      bclr #Status_Facing,status(a1) / neg.w d0
        // When h-flipped: d0 stays -$300 (left), Status_Facing is SET (left)
        // When not flipped: d0 becomes +$300 (right), Status_Facing is CLEARED (right)
        int xSpeed;
        if (hFlipped) {
            xSpeed = -BOUNCE_X_SPEED;
            player.setDirection(Direction.LEFT);
        } else {
            xSpeed = BOUNCE_X_SPEED;
            player.setDirection(Direction.RIGHT);
        }

        // ROM: move.w d0,x_vel(a1) / move.w d0,ground_vel(a1)
        player.setXSpeed((short) xSpeed);
        player.setGSpeed((short) xSpeed);

        // ROM: move.w #-$600,y_vel(a1)
        player.setYSpeed((short) BOUNCE_Y_SPEED);

        // ROM: bset #Status_InAir,status(a1)
        player.setAir(true);

        // ROM: bclr #Status_OnObj,status(a1)
        player.setOnObject(false);

        // ROM: move.b #$10,anim(a1) — SPRING animation
        player.setAnimationId(Sonic3kAnimationIds.SPRING);

        // ROM: clr.b jumping(a1)
        player.setJumping(false);

        // ROM: move.w #$F,($FF2020).l — set cooldown
        cooldownTimer = BOUNCE_COOLDOWN_FRAMES;

        // ROM: moveq #signextendB(sfx_Spring),d0 / jsr (Play_SFX).l
        try {
            services().playSfx(GameSound.SPRING);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }

        // ROM: jmp (Delete_Current_Sprite).l — bumper is consumed after bounce.
        // The gumball machine respawns bumpers from its object placement data.
        setDestroyed(true);
    }

    // --- Rendering ---

    @Override
    public int getPriorityBucket() {
        // ROM: priority $0100 → bucket 1 (lower bits of priority word / $80)
        return RenderPriority.clamp(1);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
