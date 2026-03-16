package com.openggf.game.sonic3k.constants;

import com.openggf.game.AnimationId;

/**
 * Animation script IDs for Sonic/Tails/Knuckles in S3K (indices into AniSonic_ table).
 *
 * <p>IDs 0x00-0x1E largely follow the S2 table layout, but several entries
 * were repurposed (0x13 = Victory, 0x1A/0x1B = hurt variants, 0x19 = drown).
 * S3K adds 0x1F (Super transformation), 0x20 (Tails flight), and
 * 0x21-0x23 (Knuckles glide states).
 */
public enum Sonic3kAnimationIds implements AnimationId {
    WALK(0x00),
    RUN(0x01),
    ROLL(0x02),
    ROLL2(0x03),
    PUSH(0x04),
    WAIT(0x05),
    BALANCE(0x06),
    LOOK_UP(0x07),
    DUCK(0x08),
    SPINDASH(0x09),
    BLINK(0x0A),         // Idle blink/tapping foot interrupt (sonic3k.asm:21613)
    GET_UP(0x0B),        // Get up from idle blink sequence (sonic3k.asm:21616)
    BALANCE2(0x0C),      // Balancing on edge, more precarious
    SKID(0x0D),
    FLOAT(0x0E),         // Suspended/floating (single frame $C8)
    FLOAT2(0x0F),        // Extended float animation sequence
    SPRING(0x10),
    HANG(0x11),
    VICTORY(0x13),       // Victory/celebration pose (Set_PlayerEndingPose, sonic3k.asm:181979)
    HANG2(0x14),         // Hanging from object
    BUBBLE(0x15),        // Breathing air bubble underwater (sonic3k.asm:64707)
    DROWN(0x17),         // Drowning death (s3.asm:27706, sonic3k.asm:33553)
    DEATH(0x18),         // Death (Kill_Character, sonic3k.asm:21152)
    HURT(0x1A),          // Hurt recoil (Player_Hurt, sonic3k.asm:21109)
    HURT_FALL(0x1B),     // Hurt/fall in intros (sonic3k.asm:8135, 9089)
    BLANK(0x1C),         // Blank/invisible animation (sonic3k.asm:67021)
    BALANCE3(0x1D),      // Balancing on edge, facing away
    BALANCE4(0x1E),      // Balancing on edge, facing away, more precarious
    SUPER_TRANSFORM(0x1F), // Super transformation (s3.asm:21148)
    FLY(0x20),           // Tails helicopter flight (s3.asm:23944, sonic3k.asm:26664)
    GLIDE_DROP(0x21),    // Knuckles falling after glide (sonic3k.asm:20930)
    GLIDE_LAND(0x22),    // Knuckles glide landing (sonic3k.asm:30987)
    GLIDE_SLIDE(0x23);   // Knuckles glide slide on ground (sonic3k.asm:30940)

    private final int id;

    Sonic3kAnimationIds(int id) {
        this.id = id;
    }

    @Override
    public int id() {
        return id;
    }
}
