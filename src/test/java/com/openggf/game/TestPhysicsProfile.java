package com.openggf.game;

import com.openggf.game.sonic1.Sonic1PhysicsProvider;
import com.openggf.game.sonic2.Sonic2PhysicsProvider;
import com.openggf.game.sonic3k.Sonic3kPhysicsProvider;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for PhysicsProfile, PhysicsModifiers, and PhysicsFeatureSet.
 * Verifies profile values match disassembly-verified constants and
 * modifier math produces correct results.
 */
public class TestPhysicsProfile {

    // ========================================
    // PhysicsProfile constants
    // ========================================

    @Test
    public void testSonic2SonicProfile_MatchesDisassembly() {
        PhysicsProfile p = PhysicsProfile.SONIC_2_SONIC;
        assertEquals("runAccel", 0x0C, p.runAccel());
        assertEquals("runDecel", 0x80, p.runDecel());
        assertEquals("friction", 0x0C, p.friction());
        assertEquals("max", 0x600, p.max());
        assertEquals("jump", 0x680, p.jump());
        assertEquals("slopeRunning", 0x20, p.slopeRunning());
        assertEquals("slopeRollingUp", 0x14, p.slopeRollingUp());
        assertEquals("slopeRollingDown", 0x50, p.slopeRollingDown());
        assertEquals("rollDecel", 0x20, p.rollDecel());
        assertEquals("minStartRollSpeed", 0x80, p.minStartRollSpeed());
        assertEquals("minRollSpeed", 0x80, p.minRollSpeed());
        assertEquals("maxRoll", 0x1000, p.maxRoll());
        assertEquals("rollHeight", 28, p.rollHeight());
        assertEquals("runHeight", 38, p.runHeight());
        assertEquals("standXRadius", 9, p.standXRadius());
        assertEquals("standYRadius", 19, p.standYRadius());
        assertEquals("rollXRadius", 7, p.rollXRadius());
        assertEquals("rollYRadius", 14, p.rollYRadius());
    }

    @Test
    public void testSonic2TailsProfile_DiffersFromSonic() {
        PhysicsProfile tails = PhysicsProfile.SONIC_2_TAILS;
        PhysicsProfile sonic = PhysicsProfile.SONIC_2_SONIC;

        // Shared values
        assertEquals("accel same", sonic.runAccel(), tails.runAccel());
        assertEquals("max same", sonic.max(), tails.max());
        assertEquals("jump same", sonic.jump(), tails.jump());

        // Tails-specific differences
        assertEquals("minStartRollSpeed", 264, tails.minStartRollSpeed());
        assertEquals("runHeight", 30, tails.runHeight());
        assertEquals("standYRadius", 15, tails.standYRadius());
    }

    @Test
    public void testSuperSonicProfile_HigherSpeeds() {
        PhysicsProfile sup = PhysicsProfile.SONIC_3K_SUPER_SONIC;
        assertEquals("max", 0xA00, sup.max());
        assertEquals("runAccel", 0x30, sup.runAccel());
        assertEquals("runDecel", 0x100, sup.runDecel());
    }

    @Test
    public void testSonic2SuperSonicProfile_MatchesDisassembly() {
        PhysicsProfile profile = PhysicsProfile.SONIC_2_SUPER_SONIC;
        assertEquals("runAccel", (short) 0x30, profile.runAccel());
        assertEquals("runDecel", (short) 0x100, profile.runDecel());
        assertEquals("friction", (short) 0x30, profile.friction());
        assertEquals("max", (short) 0xA00, profile.max());
        assertEquals("jump", (short) 0x800, profile.jump());
        assertEquals("standYRadius", (short) 19, profile.standYRadius());
        assertEquals("rollYRadius", (short) 14, profile.rollYRadius());
    }

    @Test
    public void testSonic2SuperSonic_MatchesS3kSuperSonic() {
        // S2 and S3K Super Sonic have identical physics values
        PhysicsProfile s2 = PhysicsProfile.SONIC_2_SUPER_SONIC;
        PhysicsProfile s3k = PhysicsProfile.SONIC_3K_SUPER_SONIC;
        assertEquals("runAccel", s3k.runAccel(), s2.runAccel());
        assertEquals("runDecel", s3k.runDecel(), s2.runDecel());
        assertEquals("friction", s3k.friction(), s2.friction());
        assertEquals("max", s3k.max(), s2.max());
        assertEquals("jump", s3k.jump(), s2.jump());
    }

    // ========================================
    // PhysicsModifiers math
    // ========================================

    @Test
    public void testStandardModifiers_AccelNormal() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Normal accel", 12, m.effectiveAccel((short) 12, false, false));
    }

    @Test
    public void testStandardModifiers_AccelWater() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Water accel (halved)", 6, m.effectiveAccel((short) 12, true, false));
    }

    @Test
    public void testStandardModifiers_AccelSpeedShoes() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Speed shoes accel (doubled)", 24, m.effectiveAccel((short) 12, false, true));
    }

    @Test
    public void testStandardModifiers_AccelWaterAndSpeedShoes() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        // ROM: water entry replaces speed constants with absolute underwater values;
        // shoes are irrelevant while submerged (s1:01 Sonic.asm:206-208)
        assertEquals("Water+shoes accel (water wins)", 6, m.effectiveAccel((short) 12, true, true));
    }

    @Test
    public void testStandardModifiers_DecelNormal() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Normal decel", 128, m.effectiveDecel((short) 128, false, false));
    }

    @Test
    public void testStandardModifiers_DecelWater() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Water decel (halved)", 64, m.effectiveDecel((short) 128, true, false));
    }

    @Test
    public void testStandardModifiers_DecelSpeedShoes_Unchanged() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        // Speed shoes don't affect decel (shoesDecelMul = 1.0)
        assertEquals("Speed shoes decel unchanged", 128, m.effectiveDecel((short) 128, false, true));
    }

    @Test
    public void testStandardModifiers_MaxNormal() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Normal max", 0x600, m.effectiveMax((short) 0x600, false, false));
    }

    @Test
    public void testStandardModifiers_MaxWater() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Water max (halved)", 0x300, m.effectiveMax((short) 0x600, true, false));
    }

    @Test
    public void testStandardModifiers_MaxSpeedShoes() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Speed shoes max (doubled)", 0xC00, m.effectiveMax((short) 0x600, false, true));
    }

    @Test
    public void testStandardModifiers_FrictionNormal() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Normal friction", 12, m.effectiveFriction((short) 12, false, false));
    }

    @Test
    public void testStandardModifiers_FrictionWater() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Water friction (halved)", 6, m.effectiveFriction((short) 12, true, false));
    }

    @Test
    public void testStandardModifiers_FrictionSpeedShoes() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Speed shoes friction (doubled)", 24, m.effectiveFriction((short) 12, false, true));
    }

    @Test
    public void testStandardModifiers_JumpNormal() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Normal jump", 0x680, m.effectiveJump((short) 0x680, false));
    }

    @Test
    public void testStandardModifiers_JumpWater() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Water jump (absolute override)", 0x380, m.effectiveJump((short) 0x680, true));
    }

    @Test
    public void testStandardModifiers_WaterGravityReduction() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        assertEquals("Water gravity reduction", 0x28, m.waterGravityReduction());
    }

    @Test
    public void testStandardModifiers_WaterHurtGravityReduction() {
        PhysicsModifiers m = PhysicsModifiers.STANDARD;
        // ROM: Obj01_Hurt subtracts $20 when underwater (s2.asm:37802, s1:01 Sonic.asm:1410)
        // Net hurt underwater gravity = $30 - $20 = $10 (same as normal $38 - $28 = $10)
        assertEquals("Water hurt gravity reduction", 0x20, m.waterHurtGravityReduction());
    }

    // ========================================
    // PhysicsFeatureSet flags
    // ========================================

    @Test
    public void testSonic1_NoSpindash() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_1;
        assertFalse("S1 spindash disabled", fs.spindashEnabled());
        assertNull("S1 no speed table", fs.spindashSpeedTable());
        assertEquals("S1 unified collision", CollisionModel.UNIFIED, fs.collisionModel());
        assertFalse("S1 no dual paths", fs.hasDualCollisionPaths());
    }

    @Test
    public void testSonic2_HasSpindash() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_2;
        assertTrue("S2 spindash enabled", fs.spindashEnabled());
        assertNotNull("S2 has speed table", fs.spindashSpeedTable());
        assertEquals("S2 speed table length", 9, fs.spindashSpeedTable().length);
        assertEquals("S2 speed table[0]", 0x0800, fs.spindashSpeedTable()[0]);
        assertEquals("S2 speed table[8]", 0x0C00, fs.spindashSpeedTable()[8]);
        assertEquals("S2 dual path collision", CollisionModel.DUAL_PATH, fs.collisionModel());
        assertTrue("S2 has dual paths", fs.hasDualCollisionPaths());
    }

    @Test
    public void testSonic3K_HasSpindash() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_3K;
        assertTrue("S3K spindash enabled", fs.spindashEnabled());
        assertNotNull("S3K has speed table", fs.spindashSpeedTable());
        assertEquals("S3K speed table length", 9, fs.spindashSpeedTable().length);
        assertEquals("S3K dual path collision", CollisionModel.DUAL_PATH, fs.collisionModel());
        assertTrue("S3K has dual paths", fs.hasDualCollisionPaths());
    }

    // ========================================
    // PhysicsProvider implementations
    // ========================================

    @Test
    public void testSonic1Provider_ReturnsSonicProfile() {
        var provider = new Sonic1PhysicsProvider();
        assertSame("S1 returns SONIC_2_SONIC", PhysicsProfile.SONIC_2_SONIC, provider.getProfile("sonic"));
        // Any character type returns the same profile (only Sonic in S1)
        assertSame("S1 returns same for unknown", PhysicsProfile.SONIC_2_SONIC, provider.getProfile("knuckles"));
    }

    @Test
    public void testSonic2Provider_ReturnsSonicOrTails() {
        var provider = new Sonic2PhysicsProvider();
        assertSame("S2 sonic", PhysicsProfile.SONIC_2_SONIC, provider.getProfile("sonic"));
        assertSame("S2 tails", PhysicsProfile.SONIC_2_TAILS, provider.getProfile("tails"));
        assertSame("S2 Tails (uppercase)", PhysicsProfile.SONIC_2_TAILS, provider.getProfile("Tails"));
        assertSame("S2 default", PhysicsProfile.SONIC_2_SONIC, provider.getProfile("knuckles"));
    }

    @Test
    public void testSonic3kProvider_ReturnsProfiles() {
        var provider = new Sonic3kPhysicsProvider();
        assertSame("S3K sonic", PhysicsProfile.SONIC_2_SONIC, provider.getProfile("sonic"));
        assertSame("S3K tails", PhysicsProfile.SONIC_2_TAILS, provider.getProfile("tails"));
        assertSame("S3K knuckles", PhysicsProfile.SONIC_2_SONIC, provider.getProfile("knuckles"));
    }

    @Test
    public void testSonic1Provider_FeatureSet() {
        var provider = new Sonic1PhysicsProvider();
        assertSame("S1 feature set", PhysicsFeatureSet.SONIC_1, provider.getFeatureSet());
        assertFalse("S1 no spindash", provider.getFeatureSet().spindashEnabled());
    }

    @Test
    public void testSonic2Provider_FeatureSet() {
        var provider = new Sonic2PhysicsProvider();
        assertSame("S2 feature set", PhysicsFeatureSet.SONIC_2, provider.getFeatureSet());
        assertTrue("S2 has spindash", provider.getFeatureSet().spindashEnabled());
    }

    @Test
    public void testSonic3kProvider_FeatureSet() {
        var provider = new Sonic3kPhysicsProvider();
        assertSame("S3K feature set", PhysicsFeatureSet.SONIC_3K, provider.getFeatureSet());
        assertTrue("S3K has spindash", provider.getFeatureSet().spindashEnabled());
    }
}
