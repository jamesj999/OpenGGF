package com.openggf.game;

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
            (short) 0x380, // waterJump (absolute — Sonic/Tails)
            (short) 0x28,  // waterGravityReduction (normal airborne: $38-$28=$10)
            (short) 0x20,  // waterHurtGravityReduction (hurt: $30-$20=$10; s2.asm:37802, s1:01 Sonic.asm:1410)
            2.0f,       // shoesAccelMul
            1.0f,       // shoesDecelMul (unchanged)
            2.0f,       // shoesFrictionMul
            2.0f        // shoesMaxMul
    );

    /** Knuckles: lower underwater jump ($300 vs Sonic's $380).
     *  ROM: Knux_Jump (sonic3k.asm:32457) move.w #$300,d2 */
    public static final PhysicsModifiers KNUCKLES = new PhysicsModifiers(
            0.5f,       // waterAccelMul
            0.5f,       // waterDecelMul
            0.5f,       // waterFrictionMul
            0.5f,       // waterMaxMul
            (short) 0x300, // waterJump — Knuckles underwater jump
            (short) 0x28,  // waterGravityReduction
            (short) 0x20,  // waterHurtGravityReduction
            2.0f,       // shoesAccelMul
            1.0f,       // shoesDecelMul
            2.0f,       // shoesFrictionMul
            2.0f        // shoesMaxMul
    );

    /**
     * Computes effective acceleration given water and speed shoes state.
     * ROM: water entry replaces speed constants with absolute underwater values
     * (s1:01 Sonic.asm:206-208, s2.asm:36063-36070). Speed shoes are irrelevant
     * while submerged — only the water values apply.
     */
    public short effectiveAccel(short base, boolean inWater, boolean speedShoes) {
        if (inWater) {
            return (short) (base * waterAccelMul);
        }
        if (speedShoes) {
            return (short) (base * shoesAccelMul);
        }
        return base;
    }

    /**
     * Computes effective deceleration given water and speed shoes state.
     * Same override semantics as effectiveAccel — water wins over shoes.
     */
    public short effectiveDecel(short base, boolean inWater, boolean speedShoes) {
        if (inWater) {
            return (short) (base * waterDecelMul);
        }
        if (speedShoes) {
            return (short) (base * shoesDecelMul);
        }
        return base;
    }

    /**
     * Computes effective friction given water and speed shoes state.
     * Same override semantics as effectiveAccel — water wins over shoes.
     */
    public short effectiveFriction(short base, boolean inWater, boolean speedShoes) {
        if (inWater) {
            return (short) (base * waterFrictionMul);
        }
        if (speedShoes) {
            return (short) (base * shoesFrictionMul);
        }
        return base;
    }

    /**
     * Computes effective max speed given water and speed shoes state.
     * Same override semantics as effectiveAccel — water wins over shoes.
     */
    public short effectiveMax(short base, boolean inWater, boolean speedShoes) {
        if (inWater) {
            return (short) (base * waterMaxMul);
        }
        if (speedShoes) {
            return (short) (base * shoesMaxMul);
        }
        return base;
    }

    /** Computes effective jump force given water state. Speed shoes do not affect jump. */
    public short effectiveJump(short base, boolean inWater) {
        if (inWater) {
            return waterJump;
        }
        return base;
    }
}
