package com.openggf.level.rings;

public class LostRing {
    private int xSubpixel;
    private int ySubpixel;
    private int xVel;
    private int yVel;
    private int lifetime;
    private boolean collected;
    private int sparkleStartFrame = -1;
    private int phaseOffset;
    private int slotIndex;
    private boolean active;

    public LostRing() {
    }

    public LostRing(int phaseOffset, int x, int y, int xVel, int yVel, int lifetime) {
        reset(phaseOffset, x, y, xVel, yVel, lifetime);
    }

    public void reset(int phaseOffset, int x, int y, int xVel, int yVel, int lifetime) {
        this.phaseOffset = phaseOffset;
        this.xSubpixel = x << 8;
        this.ySubpixel = y << 8;
        this.xVel = xVel;
        this.yVel = yVel;
        this.lifetime = lifetime;
        this.collected = false;
        this.sparkleStartFrame = -1;
        this.slotIndex = -1;
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public int getPhaseOffset() {
        return phaseOffset;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public int getX() {
        return xSubpixel >> 8;
    }

    public int getY() {
        return ySubpixel >> 8;
    }

    public int getXSubpixel() {
        return xSubpixel;
    }

    public int getYSubpixel() {
        return ySubpixel;
    }

    public int getXVel() {
        return xVel;
    }

    public int getYVel() {
        return yVel;
    }

    public void setXVel(int xVel) {
        this.xVel = xVel;
    }

    public void setYVel(int yVel) {
        this.yVel = yVel;
    }

    public void addXSubpixel(int delta) {
        xSubpixel += delta;
    }

    public void addYSubpixel(int delta) {
        ySubpixel += delta;
    }

    public void addYVel(int delta) {
        yVel += delta;
    }

    public int getLifetime() {
        return lifetime;
    }

    public void decLifetime() {
        lifetime--;
    }

    public boolean isCollected() {
        return collected;
    }

    public void markCollected(int frameCounter) {
        collected = true;
        sparkleStartFrame = frameCounter;
    }

    public int getSparkleStartFrame() {
        return sparkleStartFrame;
    }
}
