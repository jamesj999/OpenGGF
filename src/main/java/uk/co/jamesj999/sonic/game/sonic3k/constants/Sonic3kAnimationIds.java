package uk.co.jamesj999.sonic.game.sonic3k.constants;

/**
 * Animation script IDs for Sonic in S3K (indices into AniSonic_ table).
 *
 * <p>IDs 0x00-0x1E match the S2 layout. S3K adds 0x1F (Super transformation)
 * and 0x20-0x23 (snowboard/surfboard).
 */
public final class Sonic3kAnimationIds {
    public static final int WALK = 0x00;
    public static final int RUN = 0x01;
    public static final int ROLL = 0x02;
    public static final int ROLL2 = 0x03;
    public static final int PUSH = 0x04;
    public static final int WAIT = 0x05;
    public static final int BALANCE = 0x06;
    public static final int LOOK_UP = 0x07;
    public static final int DUCK = 0x08;
    public static final int SPINDASH = 0x09;
    public static final int BALANCE2 = 0x0C;
    public static final int SKID = 0x0D;
    public static final int SPRING = 0x10;
    public static final int HANG = 0x11;
    public static final int HANG2 = 0x14;
    public static final int DEATH = 0x18;
    public static final int HURT = 0x19;
    public static final int BALANCE3 = 0x1D;
    public static final int BALANCE4 = 0x1E;
    public static final int SUPER_TRANSFORM = 0x1F;

    private Sonic3kAnimationIds() {
    }
}
