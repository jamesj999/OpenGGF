package com.openggf.game.sonic2.constants;

import com.openggf.game.AnimationId;

public enum Sonic2AnimationIds implements AnimationId {
    WALK(0x00),
    RUN(0x01),
    ROLL(0x02),
    ROLL2(0x03),
    PUSH(0x04),
    WAIT(0x05),
    BALANCE(0x06),       // Balancing on edge, facing toward edge
    LOOK_UP(0x07),
    DUCK(0x08),
    SPINDASH(0x09),
    BALANCE2(0x0C),      // Balancing on edge, more precarious (closer to falling)
    SKID(0x0D),          // Braking/halt animation
    FLOAT(0x0E),         // Suspended/floating (used by Grabber)
    FLOAT2(0x0F),        // Alternate float
    SPRING(0x10),
    HANG(0x11),          // Hanging from horizontal bar
    HANG2(0x14),         // Alternate hang
    BUBBLE(0x15),        // Breathing air bubble underwater
    DROWN(0x17),         // Drowning animation (pre-death sink)
    DEATH(0x18),
    HURT(0x19),
    HURT2(0x1A),         // Tails: frame $5C (distinct from death frame $5D)
    SLIDE(0x1B),         // Oil slide in OOZ
    BALANCE3(0x1D),      // Balancing on edge, facing away from edge
    BALANCE4(0x1E),      // Balancing on edge, facing away, more precarious
    FLY(0x20),           // Tails helicopter fly (Tails only)

    // Super Sonic animation IDs (indices into SuperSonicAniData table, s2.asm:38415).
    // Values intentionally duplicate normal IDs — these index a SEPARATE animation table.
    SUPER_WALK(0x00),
    SUPER_RUN(0x01),
    SUPER_ROLL(0x02),
    SUPER_ROLL2(0x03),
    SUPER_PUSH(0x04),
    SUPER_STAND(0x05),
    SUPER_BALANCE(0x06),
    SUPER_LOOK_UP(0x07),
    SUPER_DUCK(0x08),
    SUPER_SPINDASH(0x09),
    SUPER_TRANSFORM(0x1F); // AniIDSupSonAni_Transform (index 31 in normal table)

    private final int id;

    Sonic2AnimationIds(int id) {
        this.id = id;
    }

    @Override
    public int id() {
        return id;
    }
}
