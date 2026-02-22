package com.openggf.game;

/**
 * Immutable per-character physics constants.
 * All values are in subpixels (256 subpixels = 1 pixel).
 *
 * <p>Replaces the role of {@code defineSpeeds()} as the authoritative source
 * for character physics when a GameModule is active.
 */
public record PhysicsProfile(
        short runAccel,
        short runDecel,
        short friction,
        short max,
        short jump,
        short slopeRunning,
        short slopeRollingUp,
        short slopeRollingDown,
        short rollDecel,
        short minStartRollSpeed,
        short minRollSpeed,
        short maxRoll,
        short rollHeight,
        short runHeight,
        short standXRadius,
        short standYRadius,
        short rollXRadius,
        short rollYRadius
) {
    // Sonic 2 Sonic (also identical for S1 Sonic and S3K Sonic)
    public static final PhysicsProfile SONIC_2_SONIC = new PhysicsProfile(
            (short) 12,    // runAccel (0x0C)
            (short) 128,   // runDecel (0x80)
            (short) 12,    // friction (0x0C)
            (short) 1536,  // max (0x600)
            (short) 1664,  // jump (0x680)
            (short) 32,    // slopeRunning (0x20)
            (short) 20,    // slopeRollingUp (0x14)
            (short) 80,    // slopeRollingDown (0x50)
            (short) 32,    // rollDecel (0x20)
            (short) 128,   // minStartRollSpeed (0x80)
            (short) 128,   // minRollSpeed (0x80)
            (short) 4096,  // maxRoll (0x1000)
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius (0x13)
            (short) 7,     // rollXRadius
            (short) 14     // rollYRadius
    );

    // Sonic 2 Tails (differs in minStartRollSpeed, runHeight, standYRadius)
    public static final PhysicsProfile SONIC_2_TAILS = new PhysicsProfile(
            (short) 12,    // runAccel
            (short) 128,   // runDecel
            (short) 12,    // friction
            (short) 1536,  // max
            (short) 1664,  // jump
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 264,   // minStartRollSpeed (Tails-specific)
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 30,    // runHeight (Tails is shorter: 2 * 15)
            (short) 9,     // standXRadius
            (short) 15,    // standYRadius (0x0F, shorter than Sonic)
            (short) 7,     // rollXRadius
            (short) 14     // rollYRadius
    );

    // S3K Super Sonic (higher speeds: max=0xA00, accel=0x30, decel=0x100)
    public static final PhysicsProfile SONIC_3K_SUPER_SONIC = new PhysicsProfile(
            (short) 0x30,  // runAccel
            (short) 0x100, // runDecel
            (short) 0x30,  // friction (same as accel for Super)
            (short) 0xA00, // max
            (short) 1664,  // jump (unchanged)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius
            (short) 7,     // rollXRadius
            (short) 14     // rollYRadius
    );

    // S2 Super Sonic (same values as S3K: max=0xA00, accel=0x30, decel=0x100)
    public static final PhysicsProfile SONIC_2_SUPER_SONIC = new PhysicsProfile(
            (short) 0x30,  // runAccel
            (short) 0x100, // runDecel
            (short) 0x30,  // friction (same as accel for Super)
            (short) 0xA00, // max
            (short) 1664,  // jump (unchanged)
            (short) 32,    // slopeRunning
            (short) 20,    // slopeRollingUp
            (short) 80,    // slopeRollingDown
            (short) 32,    // rollDecel
            (short) 128,   // minStartRollSpeed
            (short) 128,   // minRollSpeed
            (short) 4096,  // maxRoll
            (short) 28,    // rollHeight
            (short) 38,    // runHeight
            (short) 9,     // standXRadius
            (short) 19,    // standYRadius
            (short) 7,     // rollXRadius
            (short) 14     // rollYRadius
    );
}
