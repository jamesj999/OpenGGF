package com.openggf.game.sonic1.constants;

import com.openggf.game.AnimationId;
import com.openggf.game.CanonicalAnimation;

/**
 * Animation script IDs for Sonic 1 player sprites.
 * These are indices into the Ani_Sonic table (from s1disasm _anim/Sonic.asm).
 */
public enum Sonic1AnimationIds implements AnimationId {
    WALK(0x00),
    RUN(0x01),
    ROLL(0x02),
    ROLL2(0x03),
    PUSH(0x04),
    WAIT(0x05),
    BALANCE(0x06),
    LOOK_UP(0x07),
    DUCK(0x08),
    WARP1(0x09),
    WARP2(0x0A),
    WARP3(0x0B),
    WARP4(0x0C),
    STOP(0x0D),       // Skidding/braking
    FLOAT1(0x0E),
    FLOAT2(0x0F),
    SPRING(0x10),
    HANG(0x11),
    LEAP1(0x12),
    LEAP2(0x13),
    SURF(0x14),
    GET_AIR(0x15),
    BURNT(0x16),
    DROWN(0x17),
    DEATH(0x18),
    SHRINK(0x19),
    HURT(0x1A),
    WATER_SLIDE(0x1B),
    NULL(0x1C),
    FLOAT3(0x1D),
    FLOAT4(0x1E);

    private final int id;

    Sonic1AnimationIds(int id) {
        this.id = id;
    }

    @Override
    public int id() {
        return id;
    }

    /**
     * Maps this S1 animation to its canonical cross-game equivalent.
     * FLOAT1 maps to {@link CanonicalAnimation#FLOAT} (naming bridge).
     * NULL maps to {@link CanonicalAnimation#NULL_ANIM}.
     */
    public CanonicalAnimation toCanonical() {
        return switch (this) {
            case WALK       -> CanonicalAnimation.WALK;
            case RUN        -> CanonicalAnimation.RUN;
            case ROLL       -> CanonicalAnimation.ROLL;
            case ROLL2      -> CanonicalAnimation.ROLL2;
            case PUSH       -> CanonicalAnimation.PUSH;
            case WAIT       -> CanonicalAnimation.WAIT;
            case BALANCE    -> CanonicalAnimation.BALANCE;
            case LOOK_UP    -> CanonicalAnimation.LOOK_UP;
            case DUCK       -> CanonicalAnimation.DUCK;
            case WARP1      -> CanonicalAnimation.WARP1;
            case WARP2      -> CanonicalAnimation.WARP2;
            case WARP3      -> CanonicalAnimation.WARP3;
            case WARP4      -> CanonicalAnimation.WARP4;
            case STOP       -> CanonicalAnimation.STOP;
            case FLOAT1     -> CanonicalAnimation.FLOAT;       // naming bridge: FLOAT1 -> FLOAT
            case FLOAT2     -> CanonicalAnimation.FLOAT2;
            case SPRING     -> CanonicalAnimation.SPRING;
            case HANG       -> CanonicalAnimation.HANG;
            case LEAP1      -> CanonicalAnimation.LEAP1;
            case LEAP2      -> CanonicalAnimation.LEAP2;
            case SURF       -> CanonicalAnimation.SURF;
            case GET_AIR    -> CanonicalAnimation.GET_AIR;
            case BURNT      -> CanonicalAnimation.BURNT;
            case DROWN      -> CanonicalAnimation.DROWN;
            case DEATH      -> CanonicalAnimation.DEATH;
            case SHRINK     -> CanonicalAnimation.SHRINK;
            case HURT       -> CanonicalAnimation.HURT;
            case WATER_SLIDE -> CanonicalAnimation.WATER_SLIDE;
            case NULL       -> CanonicalAnimation.NULL_ANIM;
            case FLOAT3     -> CanonicalAnimation.FLOAT3;
            case FLOAT4     -> CanonicalAnimation.FLOAT4;
        };
    }

    /**
     * Returns the S1 animation ID for the given canonical animation,
     * or -1 if S1 does not have an equivalent for that animation.
     */
    public static int fromCanonical(CanonicalAnimation canonical) {
        return AnimationId.fromCanonical(values(), Sonic1AnimationIds::toCanonical, canonical);
    }
}
