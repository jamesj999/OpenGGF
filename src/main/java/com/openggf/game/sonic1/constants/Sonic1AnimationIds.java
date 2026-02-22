package com.openggf.game.sonic1.constants;

/**
 * Animation script IDs for Sonic 1 player sprites.
 * These are indices into the Ani_Sonic table (from s1disasm _anim/Sonic.asm).
 */
public final class Sonic1AnimationIds {
    public static final int WALK       = 0x00;
    public static final int RUN        = 0x01;
    public static final int ROLL       = 0x02;
    public static final int ROLL2      = 0x03;
    public static final int PUSH       = 0x04;
    public static final int WAIT       = 0x05;
    public static final int BALANCE    = 0x06;
    public static final int LOOK_UP    = 0x07;
    public static final int DUCK       = 0x08;
    public static final int WARP1      = 0x09;
    public static final int WARP2      = 0x0A;
    public static final int WARP3      = 0x0B;
    public static final int WARP4      = 0x0C;
    public static final int STOP       = 0x0D; // Skidding/braking
    public static final int FLOAT1     = 0x0E;
    public static final int FLOAT2     = 0x0F;
    public static final int SPRING     = 0x10;
    public static final int HANG       = 0x11;
    public static final int LEAP1      = 0x12;
    public static final int LEAP2      = 0x13;
    public static final int SURF       = 0x14;
    public static final int GET_AIR    = 0x15;
    public static final int BURNT      = 0x16;
    public static final int DROWN      = 0x17;
    public static final int DEATH      = 0x18;
    public static final int SHRINK     = 0x19;
    public static final int HURT       = 0x1A;
    public static final int WATER_SLIDE = 0x1B;
    public static final int NULL       = 0x1C;
    public static final int FLOAT3     = 0x1D;
    public static final int FLOAT4     = 0x1E;

    private Sonic1AnimationIds() {
    }
}
