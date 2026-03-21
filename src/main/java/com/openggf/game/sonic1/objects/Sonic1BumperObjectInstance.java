package com.openggf.game.sonic1.objects;

import com.openggf.audio.AudioManager;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Pinball Bumper (Object 0x47) - Spring Yard Zone.
 * <p>
 * From docs/s1disasm/_incObj/47 Bumper.asm:
 * <ul>
 *   <li>Routine 0 (Bump_Main): Init - set art, mappings, collision</li>
 *   <li>Routine 2 (Bump_Hit): Main loop - check touch, apply bounce, animate</li>
 * </ul>
 * <p>
 * Bounces Sonic radially away from the bumper center when contacted.
 * Awards 10 points per hit, up to 10 hits per bumper (tracked via respawn state).
 * <p>
 * <b>Art:</b> Nem_Bumper at ArtTile_SYZ_Bumper ($380), palette 0.
 * 3 mapping frames: idle, compressed hit, expanded hit.
 * <p>
 * <b>Animation (Ani_Bump):</b>
 * <ul>
 *   <li>Anim 0 (idle): frame 0, duration $F, loop</li>
 *   <li>Anim 1 (hit): frames 1,2,1,2 at duration 3, then change to anim 0</li>
 * </ul>
 * <p>
 * <b>Collision:</b> obColType $D7 — size index $17 (16x16 box from center).
 * Uses obColProp to detect Sonic touch. Engine uses direct collision check
 * since this object bounces rather than hurting/being destroyed.
 * <p>
 * <b>Physics (Bump_Hit):</b>
 * <pre>
 * dx = bumper_x - sonic_x
 * dy = bumper_y - sonic_y
 * angle = CalcAngle(dx, dy)
 * sin, cos = CalcSine(angle)
 * x_vel = -$700 * cos >> 8
 * y_vel = -$700 * sin >> 8
 * </pre>
 */
public class Sonic1BumperObjectInstance extends AbstractObjectInstance {

    // ---- ROM Constants ----

    // From disassembly: muls.w #-$700,d1 / muls.w #-$700,d0
    private static final int BOUNCE_VELOCITY = 0x700;

    // From disassembly: move.b #1,obPriority(a0)
    private static final int PRIORITY = 1;

    // From disassembly: move.b #$10,obActWid(a0) — active width 16 pixels
    private static final int ACTIVE_WIDTH = 0x10;

    // obColType $D7 — size index $17 = 8x8 half-widths (16x16 box)
    private static final int COLLISION_HALF_WIDTH = 8;
    private static final int COLLISION_HALF_HEIGHT = 8;

    // From disassembly: cmpi.b #$8A,2(a2,d0.w) — max 10 hits ($80 + 10 = $8A)
    private static final int MAX_HIT_COUNT = 10;

    // From disassembly: moveq #1,d0 / jsr (AddPoints).l — 10 points
    private static final int POINTS_PER_HIT = 10;

    // Points popup frame index 4 = "10" display
    private static final int POINTS_FRAME_INDEX = 4;

    // ---- Animation Constants (from Ani_Bump) ----

    // Anim 0: idle — frame 0, duration $F (15), loop
    private static final int FRAME_IDLE = 0;

    // Anim 1: hit sequence — frames 1,2,1,2 at duration 3, then revert to idle
    private static final int[] HIT_FRAMES = {1, 2, 1, 2};
    private static final int HIT_FRAME_DURATION = 4; // dc.b 3 = 3+1 frames per step

    // Cooldown to prevent repeated hits while overlapping
    private static final int BOUNCE_COOLDOWN = 8;

    // ---- Instance State ----

    private int mappingFrame = FRAME_IDLE;
    private int hitAnimIndex = -1;  // -1 = not playing hit anim
    private int hitAnimTimer = 0;
    private int bounceCooldown = 0;

    public Sonic1BumperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Bumper");
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Update hit animation sequence
        if (hitAnimIndex >= 0) {
            if (hitAnimTimer > 0) {
                hitAnimTimer--;
            } else {
                hitAnimIndex++;
                if (hitAnimIndex >= HIT_FRAMES.length) {
                    // afChange,0 — revert to idle animation
                    hitAnimIndex = -1;
                    mappingFrame = FRAME_IDLE;
                } else {
                    mappingFrame = HIT_FRAMES[hitAnimIndex];
                    hitAnimTimer = HIT_FRAME_DURATION - 1;
                }
            }
        }

        // Update bounce cooldown
        if (bounceCooldown > 0) {
            bounceCooldown--;
        }

        // Check collision with player
        if (player != null && !player.isHurt() && !player.getDead() && bounceCooldown == 0) {
            if (checkCollision(player)) {
                applyBounce(player);
            }
        }

        // out_of_range check — delete if too far from camera
        if (!isOnScreenX(0x80)) {
            setDestroyed(true);
        }
    }

    /**
     * Check rectangular collision using obColType $D7 size index $17.
     * ROM collision box: 8x8 half-widths = 16x16 total.
     */
    private boolean checkCollision(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - spawn.x());
        int dy = Math.abs(player.getCentreY() - spawn.y());
        int playerHalfWidth = 8;
        int playerHalfHeight = player.getYRadius();
        return dx < (COLLISION_HALF_WIDTH + playerHalfWidth)
                && dy < (COLLISION_HALF_HEIGHT + playerHalfHeight);
    }

    /**
     * Apply radial bounce to player.
     * <p>
     * ROM: Bump_Hit (docs/s1disasm/_incObj/47 Bumper.asm lines 29-43)
     * <pre>
     * sub.w obX(a1),d1     ; dx = bumper_x - sonic_x
     * sub.w obY(a1),d2     ; dy = bumper_y - sonic_y
     * jsr   (CalcAngle).l  ; d0 = angle (0-255)
     * jsr   (CalcSine).l   ; d0 = sin, d1 = cos
     * muls.w #-$700,d1     ; x_vel = -$700 * cos
     * asr.l  #8,d1         ; x_vel >>= 8
     * muls.w #-$700,d0     ; y_vel = -$700 * sin
     * asr.l  #8,d0         ; y_vel >>= 8
     * </pre>
     * Note: CalcAngle takes (dx, dy) and returns angle.
     * CalcSine returns (sin, cos) in (d0, d1).
     * The negation pushes Sonic AWAY from the bumper.
     */
    private void applyBounce(AbstractPlayableSprite player) {
        int dx = spawn.x() - player.getCentreX();
        int dy = spawn.y() - player.getCentreY();

        double angle = StrictMath.atan2(dy, dx);

        // If player is exactly at center, push them up
        if (dx == 0 && dy == 0) {
            angle = StrictMath.PI / 2;
        }

        // ROM: muls.w #-$700,d1 / asr.l #8,d1 (cos component for X)
        // ROM: muls.w #-$700,d0 / asr.l #8,d0 (sin component for Y)
        // Negate to push away from bumper center
        int xVel = (int) (-StrictMath.cos(angle) * BOUNCE_VELOCITY);
        int yVel = (int) (-StrictMath.sin(angle) * BOUNCE_VELOCITY);

        // ROM: move.w d1,obVelX(a1) / move.w d0,obVelY(a1)
        player.setXSpeed((short) xVel);
        player.setYSpeed((short) yVel);

        // ROM: bset #1,obStatus(a1) — set airborne
        // ROM: bclr #4,obStatus(a1) — clear roll-jumping
        // ROM: bclr #5,obStatus(a1) — clear pushing
        // ROM: clr.b objoff_3C(a1)  — clear jumping flag
        player.setAir(true);
        player.setPushing(false);
        player.setGSpeed((short) 0);

        // ROM: move.b #1,obAnim(a0) — trigger hit animation
        hitAnimIndex = 0;
        mappingFrame = HIT_FRAMES[0];
        hitAnimTimer = HIT_FRAME_DURATION - 1;

        bounceCooldown = BOUNCE_COOLDOWN;

        // ROM: move.w #sfx_Bumper,d0 / jsr (QueueSound2).l
        try {
            AudioManager.getInstance().playSfx(Sonic1Sfx.BUMPER.id);
        } catch (Exception e) {
            // Don't let audio failure break game logic
        }

        // Score: up to MAX_HIT_COUNT hits award points
        awardPoints();
    }

    /**
     * Award 10 points per hit, up to 10 hits per bumper.
     * <p>
     * ROM tracks hits via v_objstate table:
     * <pre>
     * cmpi.b #$8A,2(a2,d0.w)  ; $80 (spawned flag) + 10 = $8A
     * bhs.s  .display          ; if >= 10 hits, no points
     * addq.b #1,2(a2,d0.w)    ; increment hit counter
     * moveq  #1,d0
     * jsr    (AddPoints).l     ; add 10 to score
     * </pre>
     * The engine doesn't expose the respawn state byte directly,
     * so we use a local hit counter as equivalent behavior.
     */
    private int hitCount = 0;

    private void awardPoints() {
        if (hitCount >= MAX_HIT_COUNT) {
            return;
        }
        hitCount++;

        // ROM: moveq #1,d0 / jsr (AddPoints).l — adds 10 points
        GameServices.gameState().addScore(POINTS_PER_HIT);

        // Spawn points popup (id_Points = 0x29)
        ObjectManager objectManager = services() != null ? services().objectManager() : null;
        if (objectManager != null) {
            ObjectSpawn pointsSpawn = new ObjectSpawn(
                    spawn.x(), spawn.y(), 0x29, 0, 0, false, 0);
            Sonic1PointsObjectInstance pointsObj = new Sonic1PointsObjectInstance(
                    pointsSpawn, services(), POINTS_PER_HIT);
            pointsObj.setScoreFrameIndex(POINTS_FRAME_INDEX);
            objectManager.addDynamicObject(pointsObj);
        }
    }

    // ---- Rendering ----

    @Override
    public int getPriorityBucket() {
        // ROM: move.b #1,obPriority(a0)
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.BUMPER);
        if (renderer == null) return;
        // ROM: move.b #4,obRender(a0) — bit 2 set = use screen coordinates
        // No H-flip or V-flip for bumpers (all subtypes are 0x00)
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw collision box (16x16 from center)
        float r = bounceCooldown > 0 ? 1f : 0f;
        float g = bounceCooldown > 0 ? 0.5f : 1f;
        float b = 0f;
        ctx.drawRect(spawn.x(), spawn.y(), COLLISION_HALF_WIDTH, COLLISION_HALF_HEIGHT, r, g, b);
    }
}
