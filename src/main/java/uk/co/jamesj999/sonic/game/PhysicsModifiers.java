package uk.co.jamesj999.sonic.game;

/**
 * Per-game rules for how water and speed shoes affect the base physics profile.
 * All multipliers are applied to the base profile values.
 *
 * <p>Water jump is an absolute override (0x380) rather than a multiplier,
 * matching ROM behavior where the jump force is replaced entirely underwater.
 */
public record PhysicsModifiers(
        float waterAccelMul,
        float waterDecelMul,
        float waterFrictionMul,
        float waterMaxMul,
        short waterJump,
        short waterGravityReduction,
        short waterHurtGravityReduction,
        float shoesAccelMul,
        float shoesDecelMul,
        float shoesFrictionMul,
        float shoesMaxMul
) {
    /**
     * Standard modifiers for Sonic 1, 2, and 3K.
     * Water: halves accel/decel/friction/max. Speed shoes: doubles accel/friction/max, decel unchanged.
     * Source: s1disasm/_incObj/2E Monitor Content Power-Up.asm:70-72, s1disasm/_incObj/01 Sonic.asm:206-208
     */
    public static final PhysicsModifiers STANDARD = new PhysicsModifiers(
            0.5f,       // waterAccelMul
            0.5f,       // waterDecelMul
            0.5f,       // waterFrictionMul
            0.5f,       // waterMaxMul
            (short) 0x380, // waterJump (absolute)
            (short) 0x28,  // waterGravityReduction (normal airborne: $38-$28=$10)
            (short) 0x20,  // waterHurtGravityReduction (hurt: $30-$20=$10; s2.asm:37802, s1:01 Sonic.asm:1410)
            2.0f,       // shoesAccelMul
            1.0f,       // shoesDecelMul (unchanged)
            2.0f,       // shoesFrictionMul
            2.0f        // shoesMaxMul
    );

    /** Computes effective acceleration given water and speed shoes state. */
    public short effectiveAccel(short base, boolean inWater, boolean speedShoes) {
        short value = base;
        if (inWater) {
            value = (short) (value * waterAccelMul);
        }
        if (speedShoes) {
            value = (short) (value * shoesAccelMul);
        }
        return value;
    }

    /** Computes effective deceleration given water and speed shoes state. */
    public short effectiveDecel(short base, boolean inWater, boolean speedShoes) {
        short value = base;
        if (inWater) {
            value = (short) (value * waterDecelMul);
        }
        if (speedShoes) {
            value = (short) (value * shoesDecelMul);
        }
        return value;
    }

    /** Computes effective friction given water and speed shoes state. */
    public short effectiveFriction(short base, boolean inWater, boolean speedShoes) {
        short value = base;
        if (inWater) {
            value = (short) (value * waterFrictionMul);
        }
        if (speedShoes) {
            value = (short) (value * shoesFrictionMul);
        }
        return value;
    }

    /** Computes effective max speed given water and speed shoes state. */
    public short effectiveMax(short base, boolean inWater, boolean speedShoes) {
        short value = base;
        if (inWater) {
            value = (short) (value * waterMaxMul);
        }
        if (speedShoes) {
            value = (short) (value * shoesMaxMul);
        }
        return value;
    }

    /** Computes effective jump force given water state. Speed shoes do not affect jump. */
    public short effectiveJump(short base, boolean inWater) {
        if (inWater) {
            return waterJump;
        }
        return base;
    }
}
