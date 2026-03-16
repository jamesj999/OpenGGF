package com.openggf.game.sonic1.constants;

import com.openggf.game.AnimationId;

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
}
