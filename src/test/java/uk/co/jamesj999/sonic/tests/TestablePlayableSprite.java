package uk.co.jamesj999.sonic.tests;

import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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
}
