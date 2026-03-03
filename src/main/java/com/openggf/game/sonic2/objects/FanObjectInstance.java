package com.openggf.game.sonic2.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * OOZ Fan (Object 0x3F).
 * Wind-blowing object that pushes the player horizontally or vertically.
 * The vertical variant causes the player to tumble in the air.
 * <p>
 * Based on Obj3F from the Sonic 2 disassembly (s2.asm lines 57155-57381).
 * <p>
 * Subtype encoding:
 * - Bit 7 (0x80): Vertical fan (upward blowing) if set, horizontal if clear
 * - Bit 0 (0x01): Reverse direction (affects mapping frame offset)
 * <p>
 * Render flag bit 0 controls X-flip for horizontal fans.
 */
public class FanObjectInstance extends AbstractObjectInstance {

    // Timer durations (in frames)
    private static final int ACTIVE_DURATION = 0xB4;  // 180 frames
    private static final int IDLE_DURATION = 0x78;    // 120 frames

    // Number of mapping frames per variant
    private static final int MAPPING_FRAME_COUNT = 11;

    private final boolean isVertical;
    private final boolean reverseDirection;
    private final boolean alwaysOn;
    private final boolean xFlipped;

    // Timer state machine
    // NOTE: 'spinUp' maps to objoff_32 != 0 in the disassembly.
    // When spinUp=true (180 frames): fan accelerates animation, no wind push.
    // When spinUp=false (120 frames): fan blows at full speed, pushes player.
    private int timer;
    private boolean spinUp; // objoff_32: true = spinning up, false = blowing

    // Animation state
    private int accumulator;       // objoff_34: ramps 0..0x400 during spin-up
    private int animFrameDuration; // anim_frame_duration
    private int animFrame;         // anim_frame: cycles 0-5
    private int mappingFrame;      // mapping_frame: animFrame + base offset

    public FanObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.isVertical = (spawn.subtype() & 0x80) != 0;
        this.reverseDirection = (spawn.subtype() & 0x01) != 0;
        this.alwaysOn = (spawn.subtype() & 0x02) != 0;
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;

        this.mappingFrame = 0;
        this.animFrame = 0;
        this.timer = 0;
        this.spinUp = false;
        this.accumulator = 0;
        this.animFrameDuration = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Timer state machine (skipped for always-on fans)
        if (!alwaysOn) {
            timer--;
            if (timer < 0) {
                accumulator = 0;
                spinUp = !spinUp;
                timer = spinUp ? ACTIVE_DURATION : IDLE_DURATION;
            }
        }

        if (spinUp) {
            // Spin-up phase (objoff_32 != 0): animation ramps up, no push
            updateSpinUpAnimation();
        } else {
            // Blowing phase (objoff_32 == 0): push players, fast animation
            if (player != null) {
                if (isVertical) {
                    applyVerticalPush(player);
                } else {
                    applyHorizontalPush(player);
                }
            }

            AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
            if (sidekick != null) {
                if (isVertical) {
                    applyVerticalPush(sidekick);
                } else {
                    applyHorizontalPush(sidekick);
                }
            }
            updateBlowingAnimation();
        }
    }

    /**
     * Animation during spin-up phase (objoff_32 != 0).
     * Accumulator ramps from 0 to 0x400. High byte reloads anim_frame_duration.
     * Once accumulator reaches max, animation freezes.
     */
    private void updateSpinUpAnimation() {
        animFrameDuration--;
        if (animFrameDuration < 0) {
            if (accumulator >= 0x400) {
                // Max reached: don't advance frame (bhs.s MarkObjGone)
                return;
            }
            accumulator += 0x2A;
            animFrameDuration = (accumulator >> 8) & 0xFF;
            advanceAnimFrame();
        }
    }

    /**
     * Animation during blowing phase (objoff_32 == 0).
     * Duration fixed at 0 (fastest cycling).
     */
    private void updateBlowingAnimation() {
        animFrameDuration--;
        if (animFrameDuration < 0) {
            animFrameDuration = 0;
            advanceAnimFrame();
        }
    }

    private void advanceAnimFrame() {
        animFrame++;
        if (animFrame >= 6) {
            animFrame = 0;
        }
        // mapping_frame = base offset (0 or 5) + anim_frame
        mappingFrame = (reverseDirection ? 5 : 0) + animFrame;
    }

    /**
     * Horizontal fan push subroutine.
     * Based on Obj3F_PushPlayerHoriz from the disassembly.
     */
    private void applyHorizontalPush(AbstractPlayableSprite player) {
        if (player.isHurt() || player.isObjectControlled()) {
            return;
        }

        int objX = spawn.x();
        int objY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // X range check
        int dx = playerX - objX;
        if (!xFlipped) {
            dx = -dx;
        }
        dx += 0x50;
        if (dx < 0 || dx >= 0xF0) {
            return;
        }

        // Y range check
        int dy = playerY + 0x60 - objY;
        if (dy < 0 || dy >= 0x70) {
            return;
        }

        // Push calculation
        dx -= 0x50;
        if (dx < 0) {
            // NOT then double (ROM: not.w d0 / add.w d0,d0)
            dx = (~dx & 0xFFFF) * 2;
        }
        dx += 0x60;
        if (xFlipped) {
            dx = -dx;
        }
        // Negate low byte, arithmetic shift right 4
        // ROM: neg.b d0 / ext.w d0 / asr.w #4,d0
        int lowByte = dx & 0xFF;
        int negated = (-lowByte) & 0xFF;
        // Sign-extend byte to word
        if (negated >= 0x80) {
            negated -= 0x100;
        }
        int push = negated >> 4; // arithmetic shift right 4
        if (reverseDirection) {
            push = -push;
        }

        player.setX((short) (player.getX() + push));
    }

    /**
     * Vertical fan push subroutine.
     * Based on Obj3F_PushPlayerVert from the disassembly.
     */
    private void applyVerticalPush(AbstractPlayableSprite player) {
        if (player.isHurt() || player.isObjectControlled()) {
            return;
        }

        int objX = spawn.x();
        int objY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // X range check
        int dx = playerX - objX + 0x40;
        if (dx < 0 || dx >= 0x80) {
            return;
        }

        // Y range check with oscillation
        int oscillation = (byte) OscillationManager.getByte(0x14); // sign-extend
        int dy = playerY + oscillation + 0x60 - objY;
        if (dy < 0 || dy >= 0x90) {
            return;
        }

        // Push calculation
        // ROM: subi.w #$60,d1 / bcs.s + / not.w d1 / add.w d1,d1 / + addi.w #$60,d1
        // bcs branches (skips not/double) when dy < 0x60 (result negative)
        // Fall-through (not/double) when dy >= 0x60 (result non-negative)
        dy -= 0x60;
        if (dy >= 0) {
            dy = (~dy & 0xFFFF) * 2;
        }
        dy += 0x60;
        // ROM: neg.w d1 / asr.w #4,d1
        int push = (-dy) >> 4;
        player.setY((short) (player.getY() + push));

        // Set player airborne state and tumble
        player.setAir(true);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 1);
        if (player.getFlipAngle() == 0) {
            player.setFlipAngle(1);
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.setFlipsRemaining(0x7F);
            player.setFlipSpeed(8);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        String artKey = isVertical ? Sonic2ObjectArtKeys.OOZ_FAN_VERT : Sonic2ObjectArtKeys.OOZ_FAN_HORIZ;
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), xFlipped, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
