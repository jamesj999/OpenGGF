package com.openggf.sprites.managers;

import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

/**
 * Controls animation and rendering of Tails' tails (the rotating appendage).
 * ROM: Object 05 (Obj05) - a separate sprite that follows Tails and shows
 * his tails spinning based on his current animation state.
 *
 * During walk/run, the tails are part of the main sprite's mapping frames,
 * so Obj05 shows "Blank". During rolling, spindash, pushing, etc., the main
 * sprite's ball/body form doesn't include tails, so Obj05 renders them.
 *
 * ROM reference: s2.asm Obj05 (lines 41278-41414)
 */
public class TailsTailsController {
    // Obj05 animation indices
    private static final int ANIM_BLANK = 0;
    private static final int ANIM_SWISH = 1;
    private static final int ANIM_FLICK = 2;
    private static final int ANIM_DIRECTIONAL = 3;
    private static final int ANIM_SPINDASH = 7;
    private static final int ANIM_SKIDDING = 8;
    private static final int ANIM_PUSHING = 9;
    private static final int ANIM_HANGING = 10;
    private static final int ANIM_FLY1 = 0xB;
    private static final int ANIM_FLY2 = 0xC;

    /**
     * Obj05AniSelection: maps parent animation ID to Obj05 animation.
     * ROM (S2): byte_1D29E
     */
    private static final int[] ANI_SELECTION_S2 = {
        0,  // 0x00 Walk -> Blank
        0,  // 0x01 Run -> Blank
        3,  // 0x02 Roll -> Directional
        3,  // 0x03 Roll2 -> Directional
        9,  // 0x04 Push -> Pushing
        1,  // 0x05 Wait -> Swish
        0,  // 0x06 Balance -> Blank
        2,  // 0x07 LookUp -> Flick
        1,  // 0x08 Duck -> Swish
        7,  // 0x09 Spindash -> Spindash
        0,  // 0x0A Dummy1 -> Blank
        0,  // 0x0B Dummy2 -> Blank
        0,  // 0x0C Balance2 -> Blank
        8,  // 0x0D Skid -> Skidding
        0,  // 0x0E Float -> Blank
        0,  // 0x0F Float2 -> Blank
        0,  // 0x10 Spring -> Blank
        0,  // 0x11 Hang -> Blank
        0,  // 0x12 Blink -> Blank
        0,  // 0x13 Blink2 -> Blank
        10, // 0x14 Hang2 -> Hanging
        0,  // 0x15 Bubble -> Blank
        0,  // 0x16 -> Blank
        0,  // 0x17 -> Blank
        0,  // 0x18 Death -> Blank
        0,  // 0x19 Hurt -> Blank
        0,  // 0x1A Hurt2 -> Blank
        0,  // 0x1B -> Blank
        0,  // 0x1C -> Blank
        0,  // 0x1D Balance3 -> Blank
        0,  // 0x1E Balance4 -> Blank
        0,  // 0x1F HaulAss -> Blank
        0,  // 0x20 Fly -> Blank
    };

    /**
     * Obj_Tails_Tail_AniSelection: maps parent animation ID to Obj05 animation.
     * ROM (S3K): sonic3k.asm:30076
     */
    private static final int[] ANI_SELECTION_S3K = {
        0,     // 0x00 Walk -> Blank
        0,     // 0x01 Run -> Blank
        3,     // 0x02 Roll -> Directional
        3,     // 0x03 Roll2 -> Directional
        9,     // 0x04 Push -> Pushing
        1,     // 0x05 Wait -> Swish
        0,     // 0x06 Balance -> Blank
        2,     // 0x07 LookUp -> Flick
        1,     // 0x08 Duck -> Swish
        7,     // 0x09 Spindash -> Spindash
        0,     // 0x0A Dummy1 -> Blank
        0,     // 0x0B Dummy2 -> Blank
        0,     // 0x0C Dummy3 -> Blank
        8,     // 0x0D Stop/Skid -> Skidding
        0,     // 0x0E Float -> Blank
        0,     // 0x0F Float2 -> Blank
        0,     // 0x10 Spring -> Blank
        0,     // 0x11 Hang -> Blank
        0,     // 0x12 -> Blank
        0,     // 0x13 Victory -> Blank
        10,    // 0x14 Hang2 -> Hanging
        0,     // 0x15 Bubble -> Blank
        0,     // 0x16 Death -> Blank
        0,     // 0x17 Death2 -> Blank
        0,     // 0x18 Death3 -> Blank
        0,     // 0x19 Slide2 -> Blank
        0,     // 0x1A Hurt -> Blank
        0,     // 0x1B Slide -> Blank
        0,     // 0x1C Blank -> Blank
        0,     // 0x1D Dummy4 -> Blank
        0,     // 0x1E Dummy5 -> Blank
        0,     // 0x1F HaulAss -> Blank
        0xB,   // 0x20 Fly -> Fly1
        0xC,   // 0x21 Fly2 -> Fly2
        0xB,   // 0x22 Carry -> Fly1
        0xC,   // 0x23 Ascend -> Fly2
        0xB,   // 0x24 Tired -> Fly1
        0,     // 0x25 Swim -> Blank
        0,     // 0x26 Swim2 -> Blank
        0,     // 0x27 Tired2 -> Blank
        0,     // 0x28 Tired3 -> Blank
    };

    // --- S2 frame data (mapping frame indices from MapUnc_Tails) ---
    private static final int[] SWISH_FRAMES_S2 = { 0x09, 0x0A, 0x0B, 0x0C, 0x0D };
    private static final int[] FLICK_FRAMES_S2 = { 0x09, 0x0A, 0x0B, 0x0C, 0x0D };
    private static final int[] DIRECTIONAL_FRAMES_S2 = { 0x49, 0x4A, 0x4B, 0x4C };
    private static final int[] SPINDASH_FRAMES_S2 = { 0x81, 0x82, 0x83, 0x84 };
    private static final int[] SKID_PUSH_FRAMES_S2 = { 0x87, 0x88, 0x89, 0x8A };

    // --- S3K frame data (mapping frame indices from Map_Tails_Tail) ---
    // Verified against Anim - Tails Tail.asm (AniTails_Tail01..0C)
    private static final int[] SWISH_FRAMES_S3K = { 0x22, 0x23, 0x24, 0x25, 0x26 };
    private static final int[] FLICK_FRAMES_S3K = { 0x22, 0x23, 0x24, 0x25, 0x26 };
    private static final int[] DIRECTIONAL_FRAMES_S3K = { 5, 6, 7, 8 };  // AniTails_Tail03
    private static final int[] SPINDASH_FRAMES_S3K = { 1, 2, 3, 4 };     // AniTails_Tail07
    private static final int[] SKID_FRAMES_S3K = { 0x1A, 0x1B, 0x1C, 0x1D };  // AniTails_Tail08
    private static final int[] PUSH_FRAMES_S3K = { 0x1E, 0x1F, 0x20, 0x21 };  // AniTails_Tail09
    private static final int[] HANG_FRAMES_S3K = { 0x29, 0x2A, 0x2B, 0x2C };  // AniTails_Tail0A
    private static final int[] FLY_FRAMES_S3K = { 0x27, 0x28 };                // AniTails_Tail0B/0C

    // Animation delays (frame duration)
    private static final int SWISH_DELAY = 7;
    private static final int FLICK_DELAY = 3;
    private static final int DIRECTIONAL_DELAY = 3;
    private static final int SPINDASH_DELAY = 2;
    private static final int SKID_DELAY = 2;
    private static final int PUSH_DELAY = 9;
    private static final int HANG_DELAY = 9;
    private static final int FLY1_DELAY = 1;
    private static final int FLY2_DELAY = 0;

    private final AbstractPlayableSprite sprite;
    private final PlayerSpriteRenderer renderer;
    private final boolean isS3k;

    private int currentAnim = ANIM_BLANK;
    private int lastParentAnim = -1;
    private int frameIndex;
    private int frameTick;

    public TailsTailsController(AbstractPlayableSprite sprite, PlayerSpriteRenderer renderer) {
        this(sprite, renderer, false);
    }

    public TailsTailsController(AbstractPlayableSprite sprite, PlayerSpriteRenderer renderer, boolean isS3k) {
        this.sprite = sprite;
        this.renderer = renderer;
        this.isS3k = isS3k;
    }

    public void update() {
        int parentAnimId = sprite.getAnimationId();

        // ROM: Only update Obj05 animation when parent's animation changes
        // This allows Flick -> Swish transition without being overridden
        if (parentAnimId != lastParentAnim) {
            lastParentAnim = parentAnimId;
            int obj05Anim = resolveObj05Animation(parentAnimId);
            if (obj05Anim != currentAnim) {
                currentAnim = obj05Anim;
                frameIndex = 0;
                frameTick = 0;
            }
        }

        if (currentAnim == ANIM_BLANK) {
            return;
        }

        int[] frames = getFrames(currentAnim);
        if (frames == null) {
            return;
        }

        // Advance animation timer
        int duration = frameTick - 1;
        boolean advance = duration < 0;
        if (advance) {
            duration = getDelay(currentAnim);
        }
        frameTick = duration;

        if (advance) {
            frameIndex++;
            if (frameIndex >= frames.length) {
                if (currentAnim == ANIM_FLICK) {
                    // ROM: $FD, 1 -> switch to Swish after Flick completes
                    currentAnim = ANIM_SWISH;
                    frameIndex = 0;
                    frameTick = 0;
                } else {
                    // ROM: $FF -> loop
                    frameIndex = 0;
                }
            }
        }
    }

    public void draw() {
        if (currentAnim == ANIM_BLANK || renderer == null) {
            return;
        }

        int[] frames = getFrames(currentAnim);
        if (frames == null || frameIndex >= frames.length) {
            return;
        }

        int mappingFrame = frames[frameIndex];
        boolean hFlip;
        boolean vFlip;

        if (currentAnim == ANIM_DIRECTIONAL) {
            // ROM: TAnim_GetTailFrame - compute directional offset and flip from velocity angle
            int dirOffset = computeDirectionalOffset();
            mappingFrame += dirOffset;
            boolean[] flips = computeDirectionalFlips();
            hFlip = flips[0];
            vFlip = flips[1];
        } else {
            // Standard animations: flip matches parent's facing direction
            hFlip = Direction.LEFT.equals(sprite.getDirection());
            vFlip = false;
        }

        int originX = sprite.getRenderCentreX();
        int originY = sprite.getRenderCentreY();
        renderer.drawFrame(mappingFrame, originX, originY, hFlip, vFlip);
    }

    private int resolveObj05Animation(int parentAnimId) {
        int[] table = isS3k ? ANI_SELECTION_S3K : ANI_SELECTION_S2;
        if (parentAnimId >= 0 && parentAnimId < table.length) {
            return table[parentAnimId];
        }
        return ANIM_BLANK;
    }

    private int[] getFrames(int anim) {
        if (isS3k) {
            return switch (anim) {
                case ANIM_SWISH -> SWISH_FRAMES_S3K;
                case ANIM_FLICK -> FLICK_FRAMES_S3K;
                case ANIM_DIRECTIONAL -> DIRECTIONAL_FRAMES_S3K;
                case ANIM_SPINDASH -> SPINDASH_FRAMES_S3K;
                case ANIM_SKIDDING -> SKID_FRAMES_S3K;
                case ANIM_PUSHING -> PUSH_FRAMES_S3K;
                case ANIM_HANGING -> HANG_FRAMES_S3K;
                case ANIM_FLY1, ANIM_FLY2 -> FLY_FRAMES_S3K;
                default -> null;
            };
        }
        return switch (anim) {
            case ANIM_SWISH -> SWISH_FRAMES_S2;
            case ANIM_FLICK -> FLICK_FRAMES_S2;
            case ANIM_DIRECTIONAL -> DIRECTIONAL_FRAMES_S2;
            case ANIM_SPINDASH, ANIM_HANGING -> SPINDASH_FRAMES_S2;
            case ANIM_SKIDDING, ANIM_PUSHING -> SKID_PUSH_FRAMES_S2;
            default -> null;
        };
    }

    private int getDelay(int anim) {
        return switch (anim) {
            case ANIM_SWISH -> SWISH_DELAY;
            case ANIM_FLICK -> FLICK_DELAY;
            case ANIM_DIRECTIONAL -> DIRECTIONAL_DELAY;
            case ANIM_SPINDASH -> SPINDASH_DELAY;
            case ANIM_SKIDDING -> SKID_DELAY;
            case ANIM_PUSHING -> PUSH_DELAY;
            case ANIM_HANGING -> HANG_DELAY;
            case ANIM_FLY1 -> FLY1_DELAY;
            case ANIM_FLY2 -> FLY2_DELAY;
            default -> 0;
        };
    }

    /**
     * Compute directional offset for tails based on velocity angle.
     * ROM: TAnim_GetTailFrame calculates angle from (x_vel, y_vel),
     * adjusts for facing direction, then divides into 4 quadrants
     * giving offsets 0, 4, 8, or 12.
     */
    private int computeDirectionalOffset() {
        short xVel = sprite.getXSpeed();
        short yVel = sprite.getYSpeed();
        if (xVel == 0 && yVel == 0) {
            return 0;
        }

        // CalcAngle: 0=right, 64=down, 128=left, 192=up (Genesis convention, Y-down)
        double rad = Math.atan2(yVel, xVel);
        int d0 = ((int) Math.round(rad * 128.0 / Math.PI)) & 0xFF;

        // ROM: Adjust for facing direction
        boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
        if (!facingLeft) {
            d0 = (~d0) & 0xFF;
        } else {
            d0 = (d0 + 0x80) & 0xFF;
        }

        // ROM: Add $10, divide by 8, mask to get quadrant offset
        d0 = (d0 + 0x10) & 0xFF;
        d0 = (d0 >> 3) & 0x0C;
        return d0;
    }

    /**
     * Compute flip flags for directional tails animation.
     * ROM: TAnim_GetTailFrame sets render flags based on velocity angle.
     * Returns [hFlip, vFlip].
     */
    private boolean[] computeDirectionalFlips() {
        short xVel = sprite.getXSpeed();
        short yVel = sprite.getYSpeed();
        if (xVel == 0 && yVel == 0) {
            boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
            return new boolean[]{ facingLeft, false };
        }

        // CalcAngle
        double rad = Math.atan2(yVel, xVel);
        int d0 = ((int) Math.round(rad * 128.0 / Math.PI)) & 0xFF;

        boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
        if (!facingLeft) {
            d0 = (~d0) & 0xFF;
        } else {
            d0 = (d0 + 0x80) & 0xFF;
        }

        d0 = (d0 + 0x10) & 0xFF;

        // ROM: If (d0 + $10) >= $80, set both flip flags
        int d1 = 0;
        if ((d0 & 0x80) != 0) {
            d1 = 3; // x_flip | y_flip
        }
        int d2 = facingLeft ? 1 : 0;
        int flipResult = d1 ^ d2;
        boolean hFlip = (flipResult & 1) != 0;
        boolean vFlip = (flipResult & 2) != 0;
        return new boolean[]{ hFlip, vFlip };
    }
}
