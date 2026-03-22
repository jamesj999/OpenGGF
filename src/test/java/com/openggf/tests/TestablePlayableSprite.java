package com.openggf.tests;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.ShieldType;

/**
 * Shared test subclass of AbstractPlayableSprite for unit tests that need
 * a concrete playable sprite without OpenGL or ROM dependencies.
 */
public class TestablePlayableSprite extends AbstractPlayableSprite {

    public TestablePlayableSprite(String code, short x, short y) {
        super(code, x, y);
    }

    @Override
    public void draw() {
    }

    @Override
    public void defineSpeeds() {
        runAccel = 12;
        runDecel = 128;
        friction = 12;
        max = 1536;
        jump = 1664;
        slopeRunning = 32;
        slopeRollingDown = 80;
        slopeRollingUp = 20;
        rollDecel = 32;
        minStartRollSpeed = 128;
        minRollSpeed = 128;
        maxRoll = 4096;
        rollHeight = 28;
        runHeight = 38;
        standXRadius = 9;
        standYRadius = 19;
        rollXRadius = 7;
        rollYRadius = 14;
    }

    @Override
    protected void createSensorLines() {
    }

    public void setTestY(short y) {
        this.yPixel = y;
    }

    /** Override the physics feature set for testing without a GameModule. */
    public void setPhysicsFeatureSetForTest(PhysicsFeatureSet fs) {
        setPhysicsFeatureSet(fs);
    }

    /** Set shield state directly without spawning a shield object. For testing only. */
    public void setShieldStateForTest(boolean hasShield, ShieldType type) {
        setShieldState(hasShield, type);
    }
}
