package com.openggf.level.objects;

/**
 * Utility for ROM-accurate subpixel position updates.
 *
 * <p>Provides two families of methods matching the ROM's two fixed-point formats:
 * <ul>
 *   <li><b>16:8 (MoveSprite/MoveSprite2/MoveX):</b> 8-bit sub-pixel accumulator.
 *       Used by player sprites and lightweight objects.</li>
 *   <li><b>16.16 (ObjectFall/SpeedToPos):</b> 16-bit sub-pixel accumulator.
 *       Used by heavy objects (platforms, debris, collapsing fragments).</li>
 * </ul>
 *
 * <p>Velocity is a signed 16-bit value where 0x100 = 1 pixel/frame.
 * Callers create a {@link State} object to hold position, velocity,
 * and sub-pixel accumulators, then call the appropriate static method.
 */
public final class SubpixelMotion {

    /** Standard S3K gravity in subpixels per frame. */
    public static final int S3K_GRAVITY = 0x38;

    private SubpixelMotion() {}

    /**
     * Mutable state holder for subpixel position tracking.
     * Fields are public for direct access from callers.
     */
    public static final class State {
        public int x, y, xSub, ySub, xVel, yVel;

        public State(int x, int y, int xSub, int ySub, int xVel, int yVel) {
            this.x = x;
            this.y = y;
            this.xSub = xSub;
            this.ySub = ySub;
            this.xVel = xVel;
            this.yVel = yVel;
        }
    }

    /**
     * MoveSprite: apply velocity to position with gravity on Y axis.
     *
     * <p>ROM order: move with current y_vel, then apply gravity.
     * This means the gravity effect is delayed by one frame.
     *
     * @param s       mutable state (x, y, xSub, ySub, xVel, yVel updated in place)
     * @param gravity gravity in subpixels per frame (typically {@link #S3K_GRAVITY})
     */
    public static void moveSprite(State s, int gravity) {
        // X: position += xVel (16:8 fixed point)
        int xTotal = (s.xSub & 0xFF) + (s.xVel & 0xFF);
        s.x += (s.xVel >> 8) + (xTotal >> 8);
        s.xSub = xTotal & 0xFF;

        // Y: move with old y_vel, then apply gravity
        int oldYVel = s.yVel;
        s.yVel += gravity;
        int yTotal = (s.ySub & 0xFF) + (oldYVel & 0xFF);
        s.y += (oldYVel >> 8) + (yTotal >> 8);
        s.ySub = yTotal & 0xFF;
    }

    /**
     * MoveSprite2: apply velocity to position without gravity.
     *
     * @param s mutable state (x, y, xSub, ySub updated in place; xVel/yVel unchanged)
     */
    public static void moveSprite2(State s) {
        int xTotal = (s.xSub & 0xFF) + (s.xVel & 0xFF);
        s.x += (s.xVel >> 8) + (xTotal >> 8);
        s.xSub = xTotal & 0xFF;

        int yTotal = (s.ySub & 0xFF) + (s.yVel & 0xFF);
        s.y += (s.yVel >> 8) + (yTotal >> 8);
        s.ySub = yTotal & 0xFF;
    }

    /**
     * MoveX: apply X velocity only (no Y movement).
     *
     * @param s mutable state (x, xSub updated in place; xVel unchanged)
     */
    public static void moveX(State s) {
        int xTotal = (s.xSub & 0xFF) + (s.xVel & 0xFF);
        s.x += (s.xVel >> 8) + (xTotal >> 8);
        s.xSub = xTotal & 0xFF;
    }

    // ── 16.16 fixed-point methods (ObjectFall / SpeedToPos) ──────────────
    //
    // The ROM's ObjectFall and SpeedToPos routines use 16.16 fixed-point
    // arithmetic: position is stored as (y << 16) | ySub, and velocity is
    // sign-extended to 32 bits then shifted left 8 before adding.
    // This differs from MoveSprite which uses 16:8 (8-bit sub-pixel).

    /**
     * ObjectFall (Y-only): apply gravity to Y velocity and update Y position
     * using 16.16 fixed-point arithmetic.
     *
     * <p>ROM order (from _incObj/sub ObjectFall.asm):
     * <pre>
     *     move.w  obVelY(a0),d0           ; read OLD velocity
     *     addi.w  #$38,obVelY(a0)         ; apply gravity for NEXT frame
     *     ext.l   d0 / asl.l #8,d0        ; sign-extend and shift
     *     add.l   d0,obY(a0)              ; update 32-bit position
     * </pre>
     *
     * <p>The gravity effect is delayed by one frame because the old velocity
     * is used for this frame's position update.
     *
     * @param s       mutable state (y, ySub, yVel updated in place)
     * @param gravity gravity in subpixels per frame (typically 0x38)
     */
    public static void objectFall(State s, int gravity) {
        int oldVel = (int) (short) s.yVel;
        s.yVel += gravity;
        int y32 = (s.y << 16) | (s.ySub & 0xFFFF);
        y32 += oldVel << 8;
        s.y = y32 >> 16;
        s.ySub = y32 & 0xFFFF;
    }

    /**
     * ObjectFall (X+Y): apply velocity to both axes and gravity to Y,
     * using 16.16 fixed-point arithmetic.
     *
     * <p>ROM ObjectFall processes X first (no gravity), then Y with gravity.
     * Both axes use the same 16.16 integration.
     *
     * @param s       mutable state (x, xSub, y, ySub, xVel, yVel updated in place)
     * @param gravity gravity in subpixels per frame (typically 0x38)
     */
    public static void objectFallXY(State s, int gravity) {
        // X axis: no gravity
        int xVel32 = (int) (short) s.xVel;
        int x32 = (s.x << 16) | (s.xSub & 0xFFFF);
        x32 += xVel32 << 8;
        s.x = x32 >> 16;
        s.xSub = x32 & 0xFFFF;

        // Y axis: read old velocity, then apply gravity
        int oldYVel = (int) (short) s.yVel;
        s.yVel += gravity;
        int y32 = (s.y << 16) | (s.ySub & 0xFFFF);
        y32 += oldYVel << 8;
        s.y = y32 >> 16;
        s.ySub = y32 & 0xFFFF;
    }

    /**
     * SpeedToPos (Y-only): apply Y velocity to Y position using 16.16
     * fixed-point arithmetic, without gravity.
     *
     * <p>ROM equivalent: ext.l d0 / asl.l #8,d0 / add.l d0,obY(a0)
     *
     * @param s mutable state (y, ySub updated in place; yVel unchanged)
     */
    public static void speedToPosY(State s) {
        int vel32 = (int) (short) s.yVel;
        int y32 = (s.y << 16) | (s.ySub & 0xFFFF);
        y32 += vel32 << 8;
        s.y = y32 >> 16;
        s.ySub = y32 & 0xFFFF;
    }

    /**
     * SpeedToPos (X+Y): apply velocity to both axes using 16.16
     * fixed-point arithmetic, without gravity.
     *
     * <p>ROM equivalent of SpeedToPos for both X and Y axes.
     *
     * @param s mutable state (x, xSub, y, ySub updated in place; xVel/yVel unchanged)
     */
    public static void speedToPos(State s) {
        int xVel32 = (int) (short) s.xVel;
        int x32 = (s.x << 16) | (s.xSub & 0xFFFF);
        x32 += xVel32 << 8;
        s.x = x32 >> 16;
        s.xSub = x32 & 0xFFFF;

        int yVel32 = (int) (short) s.yVel;
        int y32 = (s.y << 16) | (s.ySub & 0xFFFF);
        y32 += yVel32 << 8;
        s.y = y32 >> 16;
        s.ySub = y32 & 0xFFFF;
    }
}
