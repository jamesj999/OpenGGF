package com.openggf.physics;

public final class SensorResult {
    private byte angle;
    private byte distance;
    private int tileId;
    private Direction direction;

    public SensorResult() {}

    public SensorResult(byte angle, byte distance, int tileId, Direction direction) {
        this.angle = angle;
        this.distance = distance;
        this.tileId = tileId;
        this.direction = direction;
    }

    public SensorResult set(byte angle, byte distance, int tileId, Direction direction) {
        this.angle = angle;
        this.distance = distance;
        this.tileId = tileId;
        this.direction = direction;
        return this;
    }

    public byte angle() { return angle; }
    public byte distance() { return distance; }
    public int tileId() { return tileId; }
    public Direction direction() { return direction; }

    public SensorResult copyFrom(SensorResult other) {
        this.angle = other.angle;
        this.distance = other.distance;
        this.tileId = other.tileId;
        this.direction = other.direction;
        return this;
    }
}
