package uk.co.jamesj999.sonic.game.sonic3k.objects;

/**
 * Utility for ROM-accurate subpixel position updates (MoveSprite / MoveSprite2).
 *
 * <p>The S3K ROM uses 16:8 fixed-point arithmetic for position updates.
 * Velocity is a signed 16-bit value where 0x100 = 1 pixel/frame.
 * The fractional "sub" accumulator carries over between frames.
 *
 * <p>Callers create a {@link State} object to hold position, velocity,
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
}
