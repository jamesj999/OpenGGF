package uk.co.jamesj999.sonic.game;

/**
 * Feature flags gating optional physics mechanics per game.
 *
 * <p>Controls whether mechanics like spindash are available in the current game.
 * When a mechanic is disabled, the movement kernel skips it entirely.
 */
public record PhysicsFeatureSet(
        boolean spindashEnabled,
        short[] spindashSpeedTable
) {
    /** Sonic 1: no spindash. */
    public static final PhysicsFeatureSet SONIC_1 = new PhysicsFeatureSet(false, null);

    /** Sonic 2: spindash with standard speed table (s2.asm:37294). */
    public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    });

    /** Sonic 3&K: spindash with same speed table as S2. */
    public static final PhysicsFeatureSet SONIC_3K = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    });
}
